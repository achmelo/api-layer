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

import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.gateway.service.InstanceInfoService;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.util.CookieUtil;
import org.zowe.apiml.zaas.ZaasTokenResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.zowe.apiml.constants.ApimlConstants.BEARER_AUTHENTICATION_PREFIX;

public abstract class AbstractTokenFilterFactory<T extends AbstractTokenFilterFactory.Config> extends AbstractAuthSchemeFactory<T, ZaasTokenResponse> {

    protected AbstractTokenFilterFactory(Class<T> configClazz, InstanceInfoService instanceInfoService, MessageService messageService) {
        super(configClazz, instanceInfoService, messageService);
    }

    @Override
    public GatewayFilter apply(T config) {
        try {
            return createGatewayFilter(config);
        } catch (Exception e) {
            return ((exchange, chain) -> {
                ServerHttpRequest request = updateHeadersForError(exchange, e.getMessage());
                return chain.filter(exchange.mutate().request(request).build());
            });
        }
    }

    @Override
    @SuppressWarnings("squid:S2092")    // the internal API cannot define generic more specifically
    protected Mono<Void> processResponse(ServerWebExchange exchange, GatewayFilterChain chain, AuthorizationResponse<ZaasTokenResponse> tokenResponse) {
        ServerHttpRequest request = null;
        var response = new AtomicReference<>(tokenResponse.getBody());
        var failureHeader = Optional.of(tokenResponse)
            .map(AuthorizationResponse::getHeaders)
            .map(headers -> headers.header(ApimlConstants.AUTH_FAIL_HEADER.toLowerCase()))
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0));
        if (response.get() == null) {
            //In case ZAAS will return 401, and there is OIDC token that used for authentication. See use case with valid OIDC token, but missing user mapping.
            response.set(Optional.ofNullable(tokenResponse.getHeaders())
                .map(ClientResponse.Headers::asHttpHeaders)
                .map(httpHeaders -> httpHeaders.getFirst(ApimlConstants.HEADER_OIDC_TOKEN))
                .map(oidcToken -> ZaasTokenResponse.builder().headerName(ApimlConstants.HEADER_OIDC_TOKEN).token(oidcToken).build())
                .orElse(null));
        }
        if (response.get() != null) {
            if (!StringUtils.isEmpty(response.get().getCookieName())) {
                request = cleanHeadersOnAuthSuccess(exchange);
                request = request.mutate().headers(headers -> {
                    String cookieHeader = CookieUtil.setCookie(
                        StringUtils.join(headers.get(HttpHeaders.COOKIE), ';'),
                        response.get().getCookieName(),
                        response.get().getToken()
                    );
                    headers.set(HttpHeaders.COOKIE, cookieHeader);
                    headers.set(HttpHeaders.AUTHORIZATION, BEARER_AUTHENTICATION_PREFIX + " " + response.get().getToken());
                }).build();
                exchange = exchange.mutate().request(request).build();
            }
            if (!StringUtils.isEmpty(response.get().getHeaderName())) {
                request = cleanHeadersOnAuthSuccess(exchange);
                request = request.mutate().headers(headers ->
                    headers.add(response.get().getHeaderName(), response.get().getToken())
                ).build();
                exchange = exchange.mutate().request(request).build();
            }
            if (failureHeader.isPresent()) {
                if (request != null) {
                    request = request.mutate().headers(httpHeaders -> httpHeaders.add(ApimlConstants.AUTH_FAIL_HEADER, failureHeader.get())).build();
                    exchange = exchange.mutate().request(request).build();
                }
                exchange.getResponse().getHeaders().add(ApimlConstants.AUTH_FAIL_HEADER, failureHeader.get());
            }
        }
        if (request == null) {
            request = cleanHeadersOnAuthFail(exchange, failureHeader.orElse("Invalid or missing authentication"));
            exchange = exchange.mutate().request(request).build();
        }

        return chain.filter(exchange);
    }

    @EqualsAndHashCode(callSuper = true)
    public static class Config extends AbstractAuthSchemeFactory.AbstractConfig {

    }

}
