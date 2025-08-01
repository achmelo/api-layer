/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.integration.zaas;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zowe.apiml.passticket.PassTicketService;
import org.zowe.apiml.ticket.TicketRequest;
import org.zowe.apiml.util.TestWithStartedInstances;
import org.zowe.apiml.util.categories.SafIdTokenTest;
import org.zowe.apiml.util.categories.ZaasTest;
import org.zowe.apiml.util.config.ConfigReader;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static jakarta.servlet.http.HttpServletResponse.*;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.*;
import static org.zowe.apiml.integration.zaas.ZaasTestUtil.*;
import static org.zowe.apiml.util.SecurityUtils.*;

@ZaasTest
@SafIdTokenTest
public class SafIdTokensTest implements TestWithStartedInstances {

    private final static String APPLICATION_NAME = ConfigReader.environmentConfiguration().getDiscoverableClientConfiguration().getApplId();

    @BeforeEach
    void setUpCertificate() {
        RestAssured.config = RestAssured.config().sslConfig(getConfiguredSslConfig());
    }

    @Nested
    class WhenGeneratingSafIdToken_thenReturnValidToken {

        private final TicketRequest ticketRequest = new TicketRequest(APPLICATION_NAME);

        @Test
        void givenValidZosmfToken() {
            String zosmfToken = getZosmfJwtToken();

            //@formatter:off
            given()
                .cookie(COOKIE, zosmfToken)
                .body(ticketRequest)
                .contentType(JSON)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(SC_OK)
                .body("token", not(isEmptyOrNullString()))
                .body("cookieName", isEmptyOrNullString());
            //@formatter:on
        }

        @Test
        void givenValidZoweTokenWithLtpa() throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
            String ltpaToken = getZosmfToken(LTPA_COOKIE);
            String zoweToken = generateZoweJwtWithLtpa(ltpaToken);

            //@formatter:off
            given()
                .header("Authorization", "Bearer " + zoweToken)
                .body(ticketRequest)
                .contentType(JSON)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(SC_OK)
                .body("token", not(isEmptyOrNullString()))
                .body("cookieName", isEmptyOrNullString());
            //@formatter:on
        }

        @Test
        void givenValidAccessToken() {
            String serviceId = "gateway";
            String pat = personalAccessToken(Collections.singleton(serviceId));

            //@formatter:off
            given()
                .header("Authorization", "Bearer " + pat)
                .header("X-Service-Id", serviceId)
                .body(ticketRequest)
                .contentType(JSON)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(SC_OK)
                .body("token", not(isEmptyOrNullString()))
                .body("cookieName", isEmptyOrNullString());
            //@formatter:on
        }

        @ParameterizedTest(name = "SafIdtTokensTest.givenX509Certificate {1}")
        @MethodSource("org.zowe.apiml.integration.zaas.ZaasTestUtil#provideClientCertificates")
        void givenX509Certificate(String certificate, String description) {
            //@formatter:off
            given()
                .header("Client-Cert", certificate)
                .body(ticketRequest)
                .contentType(JSON)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(SC_OK)
                .body("token", not(isEmptyOrNullString()))
                .body("cookieName", isEmptyOrNullString());
            //@formatter:on
        }

        @Test
        void givenValidOAuthToken() {
            String oAuthToken = validOktaAccessToken(true);

            //@formatter:off
            given()
                .cookie(COOKIE, oAuthToken)
                .body(ticketRequest)
                .contentType(JSON)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(SC_OK)
                .body("token", not(isEmptyOrNullString()))
                .body("cookieName", isEmptyOrNullString());
            //@formatter:on
        }
    }

    @Nested
    class WhenGeneratingSafIdToken_returnBadRequest {

        private final String jwt = getZosmfJwtToken();

        @Test
        void givenNoApplicationName() {
            String expectedMessage = "The 'applicationName' parameter name is missing.";

            //@formatter:off
            given()
                .contentType(JSON)
                .body(new TicketRequest())
                .cookie(COOKIE, jwt)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(is(SC_BAD_REQUEST))
                .body("messages.find { it.messageNumber == 'ZWEAG140E' }.messageContent", equalTo(expectedMessage));
            //@formatter:on
        }

        @Test
        void givenInvalidApplicationName() {
            String expectedMessage = "The generation of the PassTicket failed. Reason:";
            TicketRequest ticketRequest = new TicketRequest(PassTicketService.DefaultPassTicketImpl.UNKNOWN_APPLID);

            //@formatter:off
            given()
                .contentType(JSON)
                .body(ticketRequest)
                .cookie(COOKIE, jwt)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(is(SC_INTERNAL_SERVER_ERROR))
                .body("messages.find { it.messageNumber == 'ZWEAG141E' }.messageContent", containsString(expectedMessage));
            //@formatter:on
        }

        @Test
        @Disabled("Enable once it runs on z/OS. Mimic the behaviour in Mock service.")
        void givenLongApplicationName() {
            //@formatter:off
            given()
                .contentType(JSON)
                .body(new TicketRequest("TooLongAppName"))
                .cookie(COOKIE, jwt)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(is(SC_BAD_REQUEST));
            //@formatter:on
        }

    }

    @Nested
    class WhenGeneratingSafIdToken_returnNotFound {

        private final String jwt = getZosmfJwtToken();

        @Test
        void givenInvalidContentType() {
            //@formatter:off
            given()
                .body(new TicketRequest(APPLICATION_NAME))
                .cookie(COOKIE, jwt)
                .contentType(XML)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(is(SC_UNSUPPORTED_MEDIA_TYPE));
            //@formatter:on
        }

        @Test
        void givenNoBodyWithoutContentType() {
            //@formatter:off
            given()
                .cookie(COOKIE, jwt)
                .noContentType()
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(is(SC_UNSUPPORTED_MEDIA_TYPE));
            //@formatter:on
        }

        @Test
        void givenNoBody() {
            //@formatter:off
            given()
                .cookie(COOKIE, jwt)
                .contentType(JSON)
            .when()
                .post(ZAAS_SAFIDT_URI)
            .then()
                .statusCode(is(SC_BAD_REQUEST));
            //@formatter:on
        }
    }
    // Additional negative tests are in ZaasNegativeTest since they are common for the whole service
}
