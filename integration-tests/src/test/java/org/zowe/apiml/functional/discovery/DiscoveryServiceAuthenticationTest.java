/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.functional.discovery;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.zowe.apiml.util.SecurityUtils;
import org.zowe.apiml.util.categories.GeneralAuthenticationTest;
import org.zowe.apiml.util.categories.TestsNotMeantForZowe;
import org.zowe.apiml.util.config.ConfigReader;
import org.zowe.apiml.util.config.ItSslConfigFactory;
import org.zowe.apiml.util.config.SslContext;
import org.zowe.apiml.util.service.DiscoveryUtils;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;

@GeneralAuthenticationTest
class DiscoveryServiceAuthenticationTest {

    private final static String PASSWORD = ConfigReader.environmentConfiguration().getCredentials().getPassword();
    private final static String USERNAME = ConfigReader.environmentConfiguration().getCredentials().getUser();
    private static final String ACTUATOR_ENDPOINT = "/application";
    private static final String DISCOVERY_HEALTH_ENDPOINT =  "/application/health";

    @BeforeAll
    static void setup() throws Exception {
        RestAssured.useRelaxedHTTPSValidation();
        SslContext.prepareSslAuthentication(ItSslConfigFactory.integrationTests());
    }

    @Nested
    class GivenBearerAuthentication {

        @Nested
        class WhenAccessingProtectedEndpoint {

            @Test
            void thenAuthenticate() {
                String token = SecurityUtils.gatewayToken(USERNAME, PASSWORD);
                given()
                    .header("Authorization", "Bearer " + token)
                    .when()
                    .get(DiscoveryUtils.getDiscoveryUrl() + ACTUATOR_ENDPOINT)
                    .then()
                    .statusCode(is(SC_OK));
            }

        }

    }

    @Nested
    class GivenInvalidBearerAuthentication {

        @Nested
        class WhenAccessingProtectedEndpoint {

            @Test
            void thenReturnUnauthorized() {
                String expectedMessage = "The request has not been applied because it lacks valid authentication credentials.";
                given()
                    .header("Authorization", "Bearer invalidToken")
                    .when()
                    .get(DiscoveryUtils.getDiscoveryUrl() + ACTUATOR_ENDPOINT)
                    .then()
                    .statusCode(is(SC_UNAUTHORIZED))
                    .body(
                        "messages.find { it.messageNumber == 'ZWEAO402E' }.messageContent", equalTo(expectedMessage)
                    );
            }

        }
    }

        @Test
        @TestsNotMeantForZowe("Automation needs unprotected health endpoint")
        @DisplayName("This test needs to run against discovery service instance that has application/health endpoint authentication enabled.")
        void givenNoCredentials_thenUnauthorized() {
            given()
                .when()
                .get(DiscoveryUtils.getDiscoveryUrl() + DISCOVERY_HEALTH_ENDPOINT)
                .then()
                .statusCode(is(SC_UNAUTHORIZED));

    }

        @Test
        @TestsNotMeantForZowe("Automation needs unprotected health endpoint")
        @DisplayName("This test needs to run against discovery service instance that has application/health endpoint authentication enabled with authentication.")
        void givenBasicAuth_thenIsOk() {
            String token = SecurityUtils.gatewayToken(USERNAME, PASSWORD);
            given()
                .header("Authorization", "Bearer " + token)
                .get(DiscoveryUtils.getDiscoveryUrl() + DISCOVERY_HEALTH_ENDPOINT)
                .then()
                .statusCode(is(SC_OK));

    }
}
