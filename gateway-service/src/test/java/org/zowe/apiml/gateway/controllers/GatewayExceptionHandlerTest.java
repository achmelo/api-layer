/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.controllers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.zowe.apiml.gateway.GatewayServiceApplication;
import org.zowe.apiml.gateway.MockService;
import org.zowe.apiml.gateway.acceptance.common.MicroservicesAcceptanceTest;
import org.zowe.apiml.gateway.acceptance.common.AcceptanceTestWithMockServices;
import org.zowe.apiml.gateway.filters.ForbidCharacterException;
import org.zowe.apiml.gateway.filters.ForbidSlashException;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class GatewayExceptionHandlerTest {

    @Nested
    @MicroservicesAcceptanceTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @ActiveProfiles("gatewayExceptionHandlerTest")
    @SpringBootTest(classes = GatewayServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class BasicResponseCodes extends AcceptanceTestWithMockServices {

        private static final AtomicReference<Exception> mockException = new AtomicReference<>();

        @BeforeAll
        void createAllZaasServices() {
            mockService("serv1ce").scope(MockService.Scope.CLASS).start();
        }

        @ParameterizedTest
        @CsvSource({
            "400,org.zowe.apiml.common.badRequest",
            "401,org.zowe.apiml.common.unauthorized",
            "403,org.zowe.apiml.security.forbidden",
            "404,org.zowe.apiml.common.notFound",
            "405,org.zowe.apiml.common.methodNotAllowed",
            "415,org.zowe.apiml.common.unsupportedMediaType",
            "500,org.zowe.apiml.common.internalServerError",
            "503,org.zowe.apiml.common.serviceUnavailable",
        })
        void givenErrorResponse_whenCallGateway_thenDecorateIt(int code, String messageKey) throws MalformedURLException {
            mockException.set(WebClientResponseException.create(code, "msg", null, null, null));

            given().when()
                .get(new URL(basePath + "/serv1ce/api/v1/test"))
                .then()
                .statusCode(code)
                .body("messages[0].messageKey", containsString(messageKey));
        }

        Stream<Arguments> getExceptions() {
            return Stream.of(
                Arguments.of(new ForbidSlashException(""), 400, "org.zowe.apiml.gateway.requestContainEncodedSlash"),
                Arguments.of(new ForbidCharacterException(""), 400, "org.zowe.apiml.gateway.requestContainEncodedCharacter"),
                Arguments.of(new ResponseStatusException(HttpStatusCode.valueOf(504)), 504, "org.zowe.apiml.gateway.responseStatusError")
            );
        }

        @ParameterizedTest
        @MethodSource("getExceptions")
        void whenExceptionIsThrown_thenTranslateToCorrectMessage(Exception ex, int status, String message) throws MalformedURLException {
            mockException.set(ex);
            given().when()
                .get(new URL(basePath + "/serv1ce/api/v1/test"))
                .then()
                .statusCode(status)
                .body("messages[0].messageKey", containsString(message));
        }

        @Configuration
        @Profile("gatewayExceptionHandlerTest")
        static class Config {

            @Bean
            GlobalFilter exceptionFilter() {
                return (exchange, chain) -> Mono.error(mockException.get());
            }

        }

    }

    @Nested
    class Tls {

        private GatewayExceptionHandler gatewayExceptionHandler = spy(new GatewayExceptionHandler(null, null, null) {
            @Override
            public Mono<Void> setBodyResponse(ServerWebExchange exchange, int responseCode, String messageCode, Object... args) {
                return Mono.empty();
            }
        });

        @Test
        void givenTlsError_whenHandleException_thenShowTheDetailMessage() throws URISyntaxException {
            SSLException sslException = new SSLException("Test TLS exception");
            MockServerHttpRequest request = MockServerHttpRequest.get("https://localhost/some/url").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            gatewayExceptionHandler.handleSslException(exchange, sslException);

            verify(gatewayExceptionHandler).setBodyResponse(
                exchange, 500, "org.zowe.apiml.common.tlsError",
                new URI("https://localhost/some/url"), "Test TLS exception"
            );
        }

    }

}
