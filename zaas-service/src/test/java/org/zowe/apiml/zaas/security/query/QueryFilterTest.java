/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.security.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zowe.apiml.security.common.error.AuthMethodNotSupportedException;
import org.zowe.apiml.security.common.error.InvalidCertificateException;
import org.zowe.apiml.security.common.token.TokenAuthentication;
import org.zowe.apiml.security.common.token.TokenNotProvidedException;
import org.zowe.apiml.zaas.security.service.AuthenticationService;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.http.HttpMethod;
import org.zowe.apiml.security.common.token.TokenNotValidException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryFilterTest {
    private MockHttpServletRequest httpServletRequest;
    private MockHttpServletResponse httpServletResponse;
    private QueryFilter queryFilter;

    private final String VALID_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJNZSIsImRvbSI6InRoaXMuY29tIiwibHRwYSI6Imx0cGFUb2tlbiIsImlhdCI6MTU1NDg4MzMzNCwiZXhwIjoxNTU0OTY5NzM0LCJpc3MiOiJBUElNTCIsImp0aSI6IjNkMzU3M2VhLWMxMzktNGE5Yy1iZDU5LWVjYmIyMmM0ZDcxZCJ9.bLe_d3b3bZC-K5K49fj1aHL_xDWMPsAgwKkrfewOrHhrxVL6lSphpGx52b8YvjaMUkFpVO12jCEDoYC1JLaQhQ";

    @Mock
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    @Mock
    private AuthenticationFailureHandler authenticationFailureHandler;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuthenticationService authenticationService;

    @BeforeEach
    void setup() {
        queryFilter = new QueryFilter("TEST_ENDPOINT",
            authenticationSuccessHandler,
            authenticationFailureHandler,
            authenticationService,
            HttpMethod.GET,
            false,
            authenticationManager);
    }

    @Test
    void shouldCallAuthenticationManagerAuthenticate() {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.GET.name());
        httpServletResponse = new MockHttpServletResponse();
        queryFilter.setAuthenticationManager(authenticationManager);
        when(authenticationService.getJwtTokenFromRequest(any())).thenReturn(
            Optional.of(VALID_TOKEN)
        );
        TokenAuthentication valid = new TokenAuthentication(VALID_TOKEN, TokenAuthentication.Type.JWT);
        valid.setAuthenticated(true);
        when(authenticationManager.authenticate(new TokenAuthentication(VALID_TOKEN, TokenAuthentication.Type.JWT))).thenReturn(valid);

        queryFilter.attemptAuthentication(httpServletRequest, httpServletResponse);

        verify(authenticationManager).authenticate(any());
    }

    @Test
    void shouldRejectHttpMethods() {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.POST.name());
        httpServletResponse = new MockHttpServletResponse();

        assertThrows(AuthMethodNotSupportedException.class,
            () -> queryFilter.attemptAuthentication(httpServletRequest, httpServletResponse),
            "Expected exception is not AuthMethodNotSupportedException");
    }

    @Test
    void shouldRejectIfTokenIsNotPresent() {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.GET.name());
        httpServletResponse = new MockHttpServletResponse();
        assertThrows(TokenNotProvidedException.class,
            () -> queryFilter.attemptAuthentication(httpServletRequest, httpServletResponse),
            "Expected exception is not TokenNotProvidedException");
    }

    @Test
    void shouldRejectIfNotAuthenticatedByCertficate() {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.GET.name());
        httpServletResponse = new MockHttpServletResponse();
        TokenAuthentication authentication = new TokenAuthentication("token");
        authentication.setAuthenticated(true);
        SecurityContextHolder.setContext(new SecurityContextImpl(authentication));

        QueryFilter protectedQueryFilter = new QueryFilter("TEST_ENDPOINT",
            authenticationSuccessHandler,
            authenticationFailureHandler,
            authenticationService,
            HttpMethod.GET,
            true,
            authenticationManager);

        assertThrows(InvalidCertificateException.class,
            () -> protectedQueryFilter.attemptAuthentication(httpServletRequest, httpServletResponse),
            "Expected exception is not InvalidCertificateException");
    }

    @Test
    void givenInvalidToken_whenQueryIsCalled_thenThrowTokenNotValidException() {
        httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setMethod(HttpMethod.GET.name());
        httpServletResponse = new MockHttpServletResponse();
        queryFilter.setAuthenticationManager(authenticationManager);
        when(authenticationService.getJwtTokenFromRequest(any())).thenReturn(
            Optional.of(VALID_TOKEN)
        );

        TokenAuthentication invalid = new TokenAuthentication(VALID_TOKEN, TokenAuthentication.Type.JWT);
        invalid.setAuthenticated(false);
        when(authenticationManager.authenticate(new TokenAuthentication(VALID_TOKEN, TokenAuthentication.Type.JWT))).thenReturn(invalid);

        assertThrows(TokenNotValidException.class, () -> {
            queryFilter.attemptAuthentication(httpServletRequest, httpServletResponse);
        });
    }
}
