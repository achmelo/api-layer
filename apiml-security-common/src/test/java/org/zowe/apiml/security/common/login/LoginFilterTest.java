/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.common.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.zowe.apiml.message.core.Message;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.message.yaml.YamlMessageService;
import org.zowe.apiml.product.gateway.GatewayNotAvailableException;
import org.zowe.apiml.security.common.error.AuthMethodNotSupportedException;
import org.zowe.apiml.security.common.error.ErrorType;
import org.zowe.apiml.security.common.error.ResourceAccessExceptionHandler;
import org.zowe.apiml.security.common.error.ServiceNotAccessibleException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginFilterTest {

    private static final String VALID_JSON = "{\"username\": \"user\", \"password\": \"pwd\"}";
    private static final String JSON_WITH_NEW_PW = "{\"username\": \"user\", \"password\": \"pwd\", \"newPassword\": \"newPwd\"}";
    private static final String EMPTY_JSON = "{\"username\": \"\", \"password\": \"\"}";
    private static final String VALID_AUTH_HEADER = "Basic dXNlcjpwd2Q=";
    private static final String INVALID_AUTH_HEADER = "Basic dXNlcj11c2Vy";
    private static final String INVALID_ENCODED_AUTH_HEADER = "Basic dXNlcj1";
    private static final String USER = "user";
    private static final char[] PASSWORD = "pwd".toCharArray();
    private static final char[] NEW_PASSWORD = "newPwd".toCharArray();

    private MockHttpServletRequest httpServletRequest;
    private MockHttpServletResponse httpServletResponse;
    private LoginFilter loginFilter;
    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    @Mock
    private AuthenticationFailureHandler authenticationFailureHandler;

    @Mock
    private ResourceAccessExceptionHandler resourceAccessExceptionHandler;

    @Mock
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setup() {
        loginFilter = new LoginFilter("TEST_ENDPOINT",
            authenticationSuccessHandler,
            authenticationFailureHandler,
            mapper,
            authenticationManager,
            resourceAccessExceptionHandler);
    }

    @Test
    void shouldCallAuthenticationManagerAuthenticateWithAuthHeader() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.addHeader(HttpHeaders.AUTHORIZATION, VALID_AUTH_HEADER);
        httpServletResponse = new MockHttpServletResponse();

        AtomicBoolean called = assertAuthenticateCalled(null);

        loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);

        assertTrue(called.get());
    }

    private AtomicBoolean assertAuthenticateCalled(char[] newPassword) {
        AtomicBoolean called = new AtomicBoolean(false);
        doAnswer(invocation -> {
            UsernamePasswordAuthenticationToken authentication;
            if (newPassword == null) {
                authentication = new UsernamePasswordAuthenticationToken(USER, new LoginRequest(USER, PASSWORD));
            } else {
                 authentication = new UsernamePasswordAuthenticationToken(USER, new LoginRequest(USER, PASSWORD, NEW_PASSWORD));
            }
            assertEquals(authentication, invocation.getArguments()[0]);
            called.set(true);
            return invocation.callRealMethod();
        }).when(authenticationManager).authenticate(any());
        return called;
    }

    @Test
    void shouldCallAuthenticationManagerAuthenticateWithJson() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.setContent(VALID_JSON.getBytes());
        httpServletResponse = new MockHttpServletResponse();

        AtomicBoolean called = assertAuthenticateCalled(null);

        loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);

        assertTrue(called.get());
    }

    @Test
    void shouldCallAuthenticationManagerAuthenticateWithNewPasswordInJson() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.setContent(JSON_WITH_NEW_PW.getBytes());
        httpServletResponse = new MockHttpServletResponse();

        AtomicBoolean called = assertAuthenticateCalled(NEW_PASSWORD);

        loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);

        assertTrue(called.get());
    }

    @Test
    void shouldFailWithJsonEmptyCredentials() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.setContent(EMPTY_JSON.getBytes());
        httpServletResponse = new MockHttpServletResponse();

        Exception exception = assertThrows(AuthenticationCredentialsNotFoundException.class, () -> {
            loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);
        });
        assertEquals("Username or password not provided.", exception.getMessage());
    }

    @Test
    void shouldReturnNullWithoutAuth() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletResponse = new MockHttpServletResponse();

        assertThat(loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse), is(nullValue()));
    }

    @Test
    void shouldFailWithWrongHttpMethod() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.GET.name());
        httpServletResponse = new MockHttpServletResponse();

        Exception exception = assertThrows(AuthMethodNotSupportedException.class, () -> {
            loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);
        });
        assertEquals("GET", exception.getMessage());
    }

    @Test
    void shouldFailWithIncorrectCredentialsFormat() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.addHeader(HttpHeaders.AUTHORIZATION, INVALID_AUTH_HEADER);
        httpServletResponse = new MockHttpServletResponse();

        Exception exception = assertThrows(BadCredentialsException.class, () -> {
            loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);
        });
        assertEquals("Invalid basic authentication header", exception.getMessage());
    }

    @Test
    void shouldFailWithIncorrectlyEncodedBase64() throws ServletException {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.addHeader(HttpHeaders.AUTHORIZATION, INVALID_ENCODED_AUTH_HEADER);
        httpServletResponse = new MockHttpServletResponse();

        Exception exception = assertThrows(BadCredentialsException.class, () -> {
            loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);
        });
        assertEquals("Invalid basic authentication header", exception.getMessage());
    }

    @Test
    void shouldFailWithGatewayNotAvailable() throws IOException, ServletException {
        testFailWithResourceAccessError(new GatewayNotAvailableException("API Gateway service not available"), ErrorType.GATEWAY_NOT_AVAILABLE);
    }

    @Test
    void shouldFailWithServiceNotAccessible() throws IOException, ServletException {
        testFailWithResourceAccessError(new ServiceNotAccessibleException("Authentication service not available"), ErrorType.SERVICE_UNAVAILABLE);
    }

    private void testFailWithResourceAccessError(RuntimeException exception, ErrorType errorType) throws IOException, ServletException {
        ObjectMapper objectMapper = new ObjectMapper();
        MessageService messageService = new YamlMessageService("/security-service-messages.yml");
        ResourceAccessExceptionHandler resourceAccessExceptionHandler = new ResourceAccessExceptionHandler(messageService, objectMapper);
        loginFilter = new LoginFilter(
            "TEST_ENDPOINT",
            authenticationSuccessHandler,
            authenticationFailureHandler,
            objectMapper,
            authenticationManager,
            resourceAccessExceptionHandler);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(USER, new LoginRequest(USER,PASSWORD));
        when(authenticationManager.authenticate(authentication)).thenThrow(exception);

        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletRequest.addHeader(HttpHeaders.AUTHORIZATION, VALID_AUTH_HEADER);
        httpServletResponse = new MockHttpServletResponse();

        assertEquals(HttpStatus.OK.value(), httpServletResponse.getStatus());
        assertNull(httpServletResponse.getContentType());
        assertEquals("", httpServletResponse.getContentAsString());

        var auth = loginFilter.attemptAuthentication(httpServletRequest, httpServletResponse);

        assertNull(auth);
        Message message = messageService.createMessage(errorType.getErrorMessageKey(), httpServletRequest.getRequestURI());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), httpServletResponse.getStatus());
        assertEquals("application/json", httpServletResponse.getContentType());
        assertEquals(objectMapper.writeValueAsString(message.mapToView()), httpServletResponse.getContentAsString());
    }

}
