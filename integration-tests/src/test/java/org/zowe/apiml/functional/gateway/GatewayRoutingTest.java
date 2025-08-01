/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.functional.gateway;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.zowe.apiml.util.TestWithStartedInstances;
import org.zowe.apiml.util.categories.DiscoverableClientDependentTest;
import org.zowe.apiml.util.config.ConfigReader;
import org.zowe.apiml.util.config.GatewayServiceConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.zowe.apiml.util.requests.Endpoints.DISCOVERABLE_GREET;

@DiscoverableClientDependentTest
@Tag("GatewayServiceRouting")
class GatewayRoutingTest implements TestWithStartedInstances {

    private static final String HEADER_X_FORWARD_TO = "X-Forward-To";
    private static final String NON_EXISTING_SERVICE_ID = "noservice";
    private static final String NON_EXISTING_SERVICE_ENDPOINT = "/noservice/api/v1/something";
    private static final String WRONG_VERSION_ENPOINT = "/discoverableclient/api/v10/greeting";

    private static final GatewayServiceConfiguration conf = ConfigReader.environmentConfiguration().getGatewayServiceConfiguration();

    @BeforeAll
    static void setup() {
        RestAssured.useRelaxedHTTPSValidation();
    }

    @ParameterizedTest(name = "When base path is {0} should return 200")
    @CsvSource({
        "/apiml1" + DISCOVERABLE_GREET,
        DISCOVERABLE_GREET,
    })
    void testRoutingWithBasePath(String basePath) throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), basePath);
        given()
            .get(new URI(scgUrl))
        .then()
            .statusCode(200);
    }

    @ParameterizedTest(name = "When header X-Forward-To is set to {0} should return 200")
    @CsvSource({
        "apiml1",
        "discoverableclient",
    })
    void testRoutingWithHeader(String forwardTo) throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), DISCOVERABLE_GREET);
        given()
            .header(HEADER_X_FORWARD_TO, forwardTo)
            .get(new URI(scgUrl))
        .then()
            .statusCode(200);
    }

    @ParameterizedTest(name = "When base path is {0} should return 404")
    @CsvSource({
        "/apiml1" + NON_EXISTING_SERVICE_ENDPOINT,
        NON_EXISTING_SERVICE_ENDPOINT,
    })
    void testRoutingWithIncorrectServiceInBasePath(String basePath) throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), basePath);
        given()
            .get(new URI(scgUrl))
        .then()
            .statusCode(404);
    }

    @ParameterizedTest(name = "When header X-Forward-To is set to {0} should return 404")
    @CsvSource({
        "apiml1",
        NON_EXISTING_SERVICE_ID,
    })
    void testRoutingWithIncorrectServiceInHeader(String forwardTo) throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), NON_EXISTING_SERVICE_ENDPOINT);
        given()
            .header(HEADER_X_FORWARD_TO, forwardTo)
            .get(new URI(scgUrl))
        .then()
            .statusCode(404);
    }

    @ParameterizedTest(name = "When header X-Forward-To is set to {0} and base path is {1} should return 200 - loopback")
    @CsvSource({
        "apiml1/apiml1,/apiml1/apiml1" + DISCOVERABLE_GREET,
    })
    void testWrongRoutingWithHeader(String forwardTo, String endpoint) throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), endpoint);
        given()
            .header(HEADER_X_FORWARD_TO, forwardTo)
            .get(new URI(scgUrl))
        .then()
            .statusCode(200);
    }

    @ParameterizedTest(name = "When base path is {0} should return 404")
    @CsvSource({
        "/apiml1" + WRONG_VERSION_ENPOINT,
        WRONG_VERSION_ENPOINT,
    })
    void testWrongRoutingWithBasePath(String basePath) throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), basePath);
        given()
            .get(new URI(scgUrl))
        .then()
            .statusCode(404);
    }

    @Test
    void givenEndpointDoesNotExistOnRegisteredService() throws URISyntaxException {
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(), "/dcpassticket/api/v1/unknown");
        given()
            .get(new URI(scgUrl))
        .then()
            .statusCode(404);
    }

    @ParameterizedTest
    @MethodSource("namedUrlChars")
    void testRoutingWithSpecialCharacters(String characters) throws URISyntaxException {
        //
        String scgUrl = String.format("%s://%s:%s%s", conf.getScheme(), conf.getHost(), conf.getPort(),
            "/discoverableclient/api/v1/" + URLEncoder.encode(characters, StandardCharsets.UTF_8) + "/greeting");
        given()
            .urlEncodingEnabled(false)
            .get(new URI(scgUrl)).then().statusCode(200);
    }

    static Stream<Arguments> namedUrlChars() {
        return Stream.of(
            Arguments.of(Named.of("Watchtower metrics", "\\/%.;")),
            Arguments.of(Named.of("USS files", "/.$-_#@{A-Z0-9/+ *%")),
            Arguments.of(Named.of("Data sets", "#@$-."))

        );
    }
}
