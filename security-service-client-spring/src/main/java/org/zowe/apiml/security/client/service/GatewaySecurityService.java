/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.instance.ServiceAddress;
import org.zowe.apiml.security.client.handler.RestResponseHandler;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.error.ErrorType;
import org.zowe.apiml.security.common.login.LoginRequest;
import org.zowe.apiml.security.common.token.QueryResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Core class of security client
 * provides facility for performing login and validating JWT token
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GatewaySecurityService implements GatewaySecurity {

    private static final String MESSAGE_KEY_STRING = "messageKey\":\"";

    private final GatewayClient gatewayClient;
    private final AuthConfigurationProperties authConfigurationProperties;
    private final CloseableHttpClient closeableHttpClient;
    private final RestResponseHandler responseHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Optional<String> login(String username, char[] password, char[] newPassword) {
        ServiceAddress gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayLoginEndpoint());

        LoginRequest loginRequest = new LoginRequest(username, password);
        if (!ArrayUtils.isEmpty(newPassword)) {
            loginRequest.setNewPassword(newPassword);
        }
        try {
            HttpPost post = new HttpPost(uri);
            String json = objectMapper.writeValueAsString(loginRequest);
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            return closeableHttpClient.execute(post, response -> {
                if (!HttpStatus.valueOf(response.getCode()).is2xxSuccessful()) {
                    final HttpEntity responseEntity = response.getEntity();
                    String responseBody = null;
                    if (responseEntity != null) {
                        responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                    }
                    ErrorType errorType = getErrorType(responseBody);
                    responseHandler.handleErrorType(response, errorType,
                        "Cannot access Gateway service. Uri '{}' returned: {}", uri);
                    return Optional.empty();
                }
                return extractToken(response.getFirstHeader(HttpHeaders.SET_COOKIE).getValue());
            });
        } catch (IOException e) {
            responseHandler.handleException(e);
        } finally {
            // TODO: remove once fixed directly in Spring - org.springframework.security.core.CredentialsContainer#eraseCredentials
            loginRequest.evictSensitiveData();
        }
        return Optional.empty();
    }

    @Override
    public QueryResponse query(String token) {
        ServiceAddress gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayQueryEndpoint());
        String cookie = String.format("%s=%s", authConfigurationProperties.getCookieProperties().getCookieName(), token);

        try {
            HttpGet get = new HttpGet(uri);
            get.addHeader(HttpHeaders.COOKIE, cookie);

            return closeableHttpClient.execute(get, response -> {
                final HttpEntity responseEntity = response.getEntity();
                String responseBody = null;
                if (responseEntity != null) {
                    responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                }
                if (!HttpStatus.valueOf(response.getCode()).is2xxSuccessful()) {
                    log.debug("Cannot access Gateway service to verify JWT token. Uri '{}' returned: {}", uri, response);
                    ErrorType errorType = getErrorType(responseBody);
                    responseHandler.handleErrorType(response, errorType, uri);
                    return null;
                }
                return objectMapper.readValue(responseBody, QueryResponse.class);
            });
        } catch (IOException e) {
            responseHandler.handleException(e);
        }
        return null;
    }

    @Override
    public QueryResponse verifyOidc(String token) {
        ServiceAddress gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayOidcValidateEndpoint());

        try {
            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(new TokenRequest(token)), ContentType.APPLICATION_JSON));

            return closeableHttpClient.execute(post, response -> {
                final HttpEntity responseEntity = response.getEntity();
                String responseBody = null;
                if (responseEntity != null) {
                    responseBody = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                }
                if (!HttpStatus.valueOf(response.getCode()).is2xxSuccessful()) {
                    log.debug("Cannot access Gateway service to verify OIDC token. Uri '{}' returned: {}", uri, response);
                    ErrorType errorType = getErrorType(responseBody);
                    responseHandler.handleErrorType(response, errorType, uri);
                    return null;
                }
                return new QueryResponse();
            });
        } catch (IOException e) {
            responseHandler.handleException(e);
        }
        return null;
    }

    private ErrorType getErrorType(String detailMessage) {
        if (detailMessage == null) {
            return ErrorType.AUTH_GENERAL;
        }

        int indexOfMessageKey = detailMessage.indexOf(MESSAGE_KEY_STRING);
        if (indexOfMessageKey < 0) {
            return ErrorType.AUTH_GENERAL;
        }

        // substring from `messageKey":"` to next `"` - this is the messageKey value
        String messageKeyToEndOfExceptionMessage = detailMessage.substring(indexOfMessageKey + MESSAGE_KEY_STRING.length());
        String messageKey = messageKeyToEndOfExceptionMessage.substring(0, messageKeyToEndOfExceptionMessage.indexOf("\""));

        try {
            return ErrorType.fromMessageKey(messageKey);
        } catch (IllegalArgumentException e) {
            return ErrorType.AUTH_GENERAL;
        }
    }

    private Optional<String> extractToken(String cookies) {
        String cookieName = authConfigurationProperties.getCookieProperties().getCookieName();

        if (cookies == null || cookies.isEmpty() || !cookies.contains(cookieName)) {
            return Optional.empty();
        } else {
            int end = cookies.indexOf(';');
            String cookie = (end > 0) ? cookies.substring(0, end) : cookies;
            return Optional.of(cookie.replace(cookieName + "=", ""));
        }
    }

    @Data
    @AllArgsConstructor
    static class TokenRequest {
        String token;
    }

}
