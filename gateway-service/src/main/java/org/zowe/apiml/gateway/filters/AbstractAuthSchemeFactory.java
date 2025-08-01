/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.filters;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.gateway.service.InstanceInfoService;
import org.zowe.apiml.security.common.util.X509Util;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.util.CookieUtil;
import reactor.core.publisher.Mono;

import java.net.HttpCookie;
import java.security.cert.CertificateEncodingException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.zowe.apiml.constants.ApimlConstants.PAT_COOKIE_AUTH_NAME;
import static org.zowe.apiml.constants.ApimlConstants.PAT_HEADER_NAME;
import static org.zowe.apiml.gateway.x509.ForwardClientCertFilterFactory.CLIENT_CERT_HEADER;
import static org.zowe.apiml.security.SecurityUtils.COOKIE_AUTH_NAME;

/**
 * This class is responsible for the shared part about decoration of user request with authentication scheme. The
 * service defines its own authentication scheme, and it could evaluate a request mutation. The aim is to have as
 * small implementation as possible. Therefore, the implementation itself should construct the request to ZAAS with
 * a minimal requirements and process the result. The rest (common values for ZAAS, retrying, HA evaluation and
 * sanitation of user request should be done by this class).
 * <p>
 * To prepare a new implementation of authentication scheme decoration is required to implement those methods:
 * - {@link AbstractAuthSchemeFactory#createRequestCredentials(ServerWebExchange, AbstractConfig)}
 * - if you need add another information for treatment such as applId
 * - {@link AbstractAuthSchemeFactory#processResponse(ServerWebExchange, GatewayFilterChain, AuthorizationResponse<R>)}
 * - it is responsible for reading the response from the ZAAS and modifying the clients request to provide new credentials
 * <p>
 * Example:
 * class MyScheme extends AbstractAuthSchemeFactory<MyScheme.Config, MyResponse, MyData> {
 *
 *   @param <T> Class of config class. It should extend {@link AbstractAuthSchemeFactory.AbstractConfig}
 *   @param <R> Class of expended response from the ZAAS
 *   @Override public GatewayFilter apply(Config config) {
 *     try {
 *       return createGatewayFilter(config);
 *     } catch (Exception e) {
 *       return ((exchange, chain) -> {
 *         ServerHttpRequest request = updateHeadersForError(exchange, e.getMessage());
 *         return chain.filter(exchange.mutate().request(request).build());
 *       });
 *     }
 *   }
 *
 *   @Override
 *   protected RequestCredentials.RequestCredentialsBuilder createRequestCredentials(ServerWebExchange exchange, Config config) {
 *       return super.createRequestCredentials(exchange, config)
 *           .applId(config.getApplicationName());
 *   }
 *
 *   @Override protected Mono<Void> processResponse(ServerWebExchange exchange, GatewayFilterChain chain, MyResponse response) {
 *     ServerHttpRequest request;
 *     if (response.getToken() != null) {
 *       request = exchange.getRequest().mutate().headers(headers ->
 *         headers.add("mySchemeHeader", response.getToken())
 *       ).build();
 *     } else {
 *       request = updateHeadersForError(exchange, "Invalid or missing authentication");
 *     }
 *     exchange = exchange.mutate().request(request).build();
 *     return chain.filter(exchange);
 *   }
 *
 *   @EqualsAndHashCode(callSuper = true)
 *   public static class Config extends AbstractAuthSchemeFactory.AbstractConfig {
 *   }
 * }
 *
 * @Data class MyResponse {
 * private String token;
 * }
 */
@Slf4j
public abstract class AbstractAuthSchemeFactory<T extends AbstractAuthSchemeFactory.AbstractConfig, R> extends AbstractGatewayFilterFactory<T> {

    private static final String[] CERTIFICATE_HEADERS = {
        "X-Certificate-Public",
        "X-Certificate-DistinguishedName",
        "X-Certificate-CommonName"
    };

    private static final Predicate<String> CERTIFICATE_HEADERS_TEST = headerName ->
        StringUtils.equalsIgnoreCase(headerName, CERTIFICATE_HEADERS[0]) ||
        StringUtils.equalsIgnoreCase(headerName, CERTIFICATE_HEADERS[1]) ||
        StringUtils.equalsIgnoreCase(headerName, CERTIFICATE_HEADERS[2]);

    private static final Predicate<HttpCookie> CREDENTIALS_COOKIE_INPUT = cookie ->
        StringUtils.equalsIgnoreCase(cookie.getName(), PAT_COOKIE_AUTH_NAME) ||
        StringUtils.equalsIgnoreCase(cookie.getName(), COOKIE_AUTH_NAME) ||
        StringUtils.startsWithIgnoreCase(cookie.getName(), COOKIE_AUTH_NAME + ".");
    private static final Predicate<HttpCookie> CREDENTIALS_COOKIE = cookie ->
        CREDENTIALS_COOKIE_INPUT.test(cookie) ||
        StringUtils.equalsIgnoreCase(cookie.getName(), "jwtToken") ||
        StringUtils.equalsIgnoreCase(cookie.getName(), "LtpaToken2");

    private static final Predicate<String> CREDENTIALS_HEADER_INPUT = headerName ->
        StringUtils.equalsIgnoreCase(headerName, HttpHeaders.AUTHORIZATION) ||
        StringUtils.equalsIgnoreCase(headerName, PAT_HEADER_NAME);
    private static final Predicate<String> CREDENTIALS_HEADER = headerName ->
        CREDENTIALS_HEADER_INPUT.test(headerName) ||
        CERTIFICATE_HEADERS_TEST.test(headerName) ||
        StringUtils.equalsIgnoreCase(headerName, "X-SAF-Token") ||
        StringUtils.equalsIgnoreCase(headerName, CLIENT_CERT_HEADER) ||
        StringUtils.equalsIgnoreCase(headerName, HttpHeaders.COOKIE);

    protected final InstanceInfoService instanceInfoService;
    protected final MessageService messageService;

    protected AbstractAuthSchemeFactory(Class<T> configClazz, InstanceInfoService instanceInfoService, MessageService messageService) {
        super(configClazz);
        this.instanceInfoService = instanceInfoService;
        this.messageService = messageService;
    }

    protected abstract Function<RequestCredentials, Mono<AbstractAuthSchemeFactory.AuthorizationResponse<R>>> getAuthorizationResponseTransformer();

    /**
     * The method responsible for reading a response from a ZAAS component and decorating of user request (i.e. set
     * credentials as header, etc.)
     *
     * @param clientCallBuilder builder of customer request (to set new credentials)
     * @param chain             chain of filter to be evaluated. Method should return `return chain.filter(exchange)`
     * @param response          response body from the ZAAS containing new credentials or and empty object
     * @return response of chain evaluation (`return chain.filter(exchange)`)
     */
    @SuppressWarnings("squid:S2092")    // the cookie is used just for internal purposes (off the browser)
    protected abstract Mono<Void> processResponse(ServerWebExchange clientCallBuilder, GatewayFilterChain chain, AuthorizationResponse<R> response);

    protected RequestCredentials.RequestCredentialsBuilder createRequestCredentials(ServerWebExchange exchange, T config) {
        var headers = exchange.getRequest().getHeaders();

        var zaasRequestBuilder = RequestCredentials.builder()
            .serviceId(config.getServiceId());

        // get all current cookies
        List<HttpCookie> cookies = CookieUtil.readCookies(headers).toList();

        // set in the request to ZAAS all cookies and headers that contain credentials
        headers.entrySet().stream()
            .filter(e -> CREDENTIALS_HEADER_INPUT.test(e.getKey()))
            .forEach(e -> zaasRequestBuilder.addHeader(e.getKey(), e.getValue().toArray(new String[0])));
        cookies.stream()
            .filter(CREDENTIALS_COOKIE_INPUT)
            .forEach(c -> zaasRequestBuilder.addCookie(c.getName(), c.getValue()));

        try {
            String encodedCertificate = X509Util.getEncodedClientCertificate(exchange.getRequest().getSslInfo());
            if (encodedCertificate != null) {
                zaasRequestBuilder.x509Certificate(encodedCertificate);
            }
        } catch (CertificateEncodingException e) {
            exchange.getResponse().getHeaders().add(ApimlConstants.AUTH_FAIL_HEADER, "Invalid client certificate in request. Error message: " + e.getMessage());
        }

        zaasRequestBuilder.requestURI(exchange.getRequest().getURI().toString());

        return zaasRequestBuilder;
    }

    /**
     * This method remove a necessary subset of credentials in case of authentication fail. If ZAAS cannot generate a
     * new credentials (i.e. because of basic authentication, expired token, etc.) the Gateway should provide the original
     * credentials passed by a user. But there are headers that could be removed to avoid misusing (see forwarding
     * certificate - user cannot provide a public certificate to take foreign privileges).
     * It also set the header to describe an authentication error.
     *
     * @param exchange     exchange of the user request resent to a service
     * @param errorMessage message to be set in the X-Zowe-Auth-Failure header
     * @return mutated request
     */
    protected ServerHttpRequest cleanHeadersOnAuthFail(ServerWebExchange exchange, String errorMessage) {
        return exchange.getRequest().mutate().headers(headers -> {
            // update original request - to remove all potential headers and cookies with credentials
            Arrays.stream(CERTIFICATE_HEADERS).forEach(headers::remove);

            // set error header in both side (request to the service, response to the user)
            headers.add(ApimlConstants.AUTH_FAIL_HEADER, errorMessage);
            exchange.getResponse().getHeaders().add(ApimlConstants.AUTH_FAIL_HEADER, errorMessage);
        }).build();
    }

    /**
     * This method removes from the request all headers and cookie related to the authentication. It is necessary to send
     * the request with multiple auth values. The Gateway would set a new credentials in this case
     *
     * @param exchange exchange of the user request resent to a service
     * @return mutated request
     */
    protected ServerHttpRequest cleanHeadersOnAuthSuccess(ServerWebExchange exchange) {
        return exchange.getRequest().mutate().headers(headers -> {
            // get all current cookies
            List<HttpCookie> cookies = CookieUtil.readCookies(headers).toList();

            // update original request - to remove all potential headers and cookies with credentials
            Stream<Map.Entry<String, String>> nonCredentialHeaders = headers.entrySet().stream()
                .filter(entry -> !CREDENTIALS_HEADER.test(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream().map(v -> new AbstractMap.SimpleEntry<>(entry.getKey(), v)));
            Stream<Map.Entry<String, String>> nonCredentialCookies = cookies.stream()
                .filter(c -> !CREDENTIALS_COOKIE.test(c))
                .map(c -> new AbstractMap.SimpleEntry<>(HttpHeaders.COOKIE, c.toString()));
            List<Map.Entry<String, String>> newHeaders = Stream.concat(
                nonCredentialHeaders,
                nonCredentialCookies
            ).toList();

            headers.clear();
            newHeaders.forEach(newHeader -> headers.add(newHeader.getKey(), newHeader.getValue()));
        }).build();
    }

    protected GatewayFilter createGatewayFilter(T config) {
        return (exchange, chain) -> getAuthorizationResponseTransformer()
            .apply(createRequestCredentials(exchange, config).build())
            .flatMap(response -> processResponse(exchange, chain, response));
    }

    protected ServerHttpRequest addRequestHeader(ServerWebExchange exchange, String key, String value) {
        return exchange.getRequest().mutate()
            .headers(headers -> headers.add(key, value))
            .build();
    }

    protected ServerHttpRequest updateHeadersForError(ServerWebExchange exchange, String errorMessage) {
        ServerHttpRequest request = addRequestHeader(exchange, ApimlConstants.AUTH_FAIL_HEADER, errorMessage);
        exchange.getResponse().getHeaders().add(ApimlConstants.AUTH_FAIL_HEADER, errorMessage);
        return request;
    }

    @Data
    public abstract static class AbstractConfig {

        // service ID of the target service
        private String serviceId;

    }

    @AllArgsConstructor
    @Getter
    public static class AuthorizationResponse<R> {

        private ClientResponse.Headers headers;
        private R body;

    }

}
