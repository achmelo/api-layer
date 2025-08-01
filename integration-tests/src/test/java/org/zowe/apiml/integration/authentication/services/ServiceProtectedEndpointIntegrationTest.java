/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.integration.authentication.services;

import io.restassured.RestAssured;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.zowe.apiml.util.SecurityUtils;
import org.zowe.apiml.util.TestWithStartedInstances;
import org.zowe.apiml.util.categories.zOSMFAuthTest;
import org.zowe.apiml.util.config.ConfigReader;
import org.zowe.apiml.util.http.HttpRequestUtils;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.core.Is.is;

/**
 * This test suite verifies integration between the API ML and the service instance. Especially it verifies that gateway
 * properly accepts and translates the token.
 * The expectation is that the call to the service instance will be authenticated.
 * The tests depend on the availability of either zOSMF or the mock.
 */
@zOSMFAuthTest
class ServiceProtectedEndpointIntegrationTest implements TestWithStartedInstances {

    private static final boolean ZOS_TARGET = Boolean.parseBoolean(System.getProperty("environment.zos.target", "false"));
    private static final String PASSWORD = ConfigReader.environmentConfiguration().getCredentials().getPassword();
    private static final String USERNAME = ConfigReader.environmentConfiguration().getCredentials().getUser();

    private static final String ZOSMF_SERVICE_ID = ConfigReader.environmentConfiguration().getZosmfServiceConfiguration().getServiceId();
    private static final String ZOSMF_CONTEXT_ROOT = ConfigReader.environmentConfiguration().getZosmfServiceConfiguration().getContextRoot();

    private static final String ZOSMF_ENDPOINT_MOCK = "/" + ZOSMF_SERVICE_ID + "/api/zosmf/restfiles/ds";
    private static final String ZOSMF_ENDPOINT_GW = "/" + ZOSMF_SERVICE_ID + "/api/v1/" + (StringUtils.hasText(ZOSMF_CONTEXT_ROOT) ? ZOSMF_CONTEXT_ROOT + "/" : "") + "restfiles/ds";
    private static final String ZOSMF_ENDPOINT = ZOS_TARGET ? ZOSMF_ENDPOINT_GW : ZOSMF_ENDPOINT_MOCK;

    private static final NameValuePair ARGUMENT = new BasicNameValuePair("dslevel", "sys1.p*");

    private String token;

    @BeforeEach
    void setUp() {
        RestAssured.useRelaxedHTTPSValidation();
        token = SecurityUtils.gatewayToken(USERNAME, PASSWORD);
    }

    //@formatter:off
    @Nested
    class AcceptAuthentication {
        @Nested
        class WhenCallingTheServiceWithValidToken {
            @Test
            void viaBearerHeader() {
                String dsname1 = "SYS1.PARMLIB";
                String dsname2 = "SYS1.PROCLIB";

                URI uri = HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT);

                given()
                    .header("Authorization", "Bearer " + token)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(uri)
                .then()
                    .statusCode(is(SC_OK))
                    .body(
                        "items.dsname", hasItems(dsname1, dsname2))
                        .onFailMessage("Calling " + uri);
            }

            @Test
            void viaCookie() {
                String dsname1 = "SYS1.PARMLIB";
                String dsname2 = "SYS1.PROCLIB";

                URI uri = HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT);

                given()
                    .cookie("apimlAuthenticationToken", token)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(uri)
                .then()
                    .statusCode(is(SC_OK))
                    .body(
                        "items.dsname", hasItems(dsname1, dsname2))
                        .onFailMessage("Accessing " + uri);
            }

            @Test
            void viaBasicAuthHeader() {
                String dsname1 = "SYS1.PARMLIB";
                String dsname2 = "SYS1.PROCLIB";

                URI uri = HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT);

                given()
                    .auth().preemptive().basic(USERNAME, PASSWORD)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(uri)
                .then()
                    .statusCode(is(SC_OK))
                    .body(
                        "items.dsname", hasItems(dsname1, dsname2))
                    .onFailMessage("Accessing " + uri);
            }
        }
    }

    @Nested
    class RejectAuthentication {
        @Nested
        class WhenProvidingInvalidAuthentication {

            @Test
            void viaBearerHeader() {
                String invalidToken = "token";

                given()
                    .header("Authorization", "Bearer " + invalidToken)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT))
                .then()
                    .statusCode(is(SC_UNAUTHORIZED));
            }

            @Test
            void viaApimlCookie() {
                String invalidToken = "token";

                given()
                    .cookie("apimlAuthenticationToken", invalidToken)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT))
                .then()
                    .statusCode(is(SC_UNAUTHORIZED));
            }
        }
    }

    @Nested
    class GivenNoAuthentication {
        @Nested
        class RejectAuthentication {
            @Test
            void withoutAnyAuthenticationMethod() {
                given()
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT))
                .then()
                    .statusCode(is(SC_UNAUTHORIZED));
            }

            @Test
            void withEmptyHeader() {
                String emptyToken = " ";

                given()
                    .header("Authorization", "Bearer " + emptyToken)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT))
                .then()
                    .statusCode(is(SC_UNAUTHORIZED));
            }

            @Test
            void withEmptyCookie() {
                String emptyToken = "";

                given()
                    .cookie("apimlAuthenticationToken", emptyToken)
                    .header("X-CSRF-ZOSMF-HEADER", "zosmf")
                .when()
                    .get(HttpRequestUtils.getUriFromGateway(ZOSMF_ENDPOINT, ARGUMENT))
                .then()
                    .statusCode(is(SC_UNAUTHORIZED));
            }
        }
    }
    //@formatter:on
}
