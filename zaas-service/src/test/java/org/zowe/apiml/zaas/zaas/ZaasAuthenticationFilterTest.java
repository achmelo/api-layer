/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.zaas;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.zowe.apiml.security.common.error.AuthExceptionHandler;
import org.zowe.apiml.security.common.token.TokenExpireException;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSource;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.zaas.security.service.schema.source.JwtAuthSource;
import org.zowe.apiml.zaas.security.service.schema.source.ParsedTokenAuthSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.zowe.apiml.zaas.zaas.ExtractAuthSourceFilter.AUTH_SOURCE_ATTR;
import static org.zowe.apiml.zaas.zaas.ExtractAuthSourceFilter.AUTH_SOURCE_PARSED_ATTR;

@ExtendWith(MockitoExtension.class)
class ZaasAuthenticationFilterTest {

    @Mock
    private AuthSourceService authSourceService;

    @Mock
    private AuthExceptionHandler authExceptionHandler;

    @Mock
    private FilterChain filterChain;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private ZaasAuthenticationFilter underTest;

    @BeforeEach
    void setup() {
        underTest = new ZaasAuthenticationFilter(authSourceService, authExceptionHandler);
    }

    @Nested
    class ThenContinue {

        @Test
        void givenValidAuth_whenFilter() throws ServletException, IOException {
            mockAuthSource(true);
            mockAuthSourceParsed(true);

            underTest.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

    }

    @Nested
    class ThenHandleError {

        @Test
        void givenNoAuth_whenFilter() throws ServletException, IOException {
            underTest.doFilterInternal(request, response, filterChain);

            assertException(InsufficientAuthenticationException.class);
        }

        @Test
        void givenInvalidAuth_whenFilter() throws ServletException, IOException {
            mockAuthSource(false);

            underTest.doFilterInternal(request, response, filterChain);

            assertException(InsufficientAuthenticationException.class);
        }

        @Test
        void givenInvalidAuthParsedUsername_whenFilter() throws ServletException, IOException {
            mockAuthSource(true);
            mockAuthSourceParsed(false);

            underTest.doFilterInternal(request, response, filterChain);

            assertException(InsufficientAuthenticationException.class);
        }

        @Test
        void givenAuthException_whenFilter() throws ServletException, IOException {
            AuthSource authSource = new JwtAuthSource("token");
            request.setAttribute(AUTH_SOURCE_ATTR, authSource);
            when(authSourceService.isValid(authSource)).thenThrow(new TokenExpireException("Expired"));


            underTest.doFilterInternal(request, response, filterChain);

            ArgumentCaptor<RuntimeException> exceptionCaptor = ArgumentCaptor.forClass(RuntimeException.class);
            verify(authExceptionHandler).handleException(
                eq(request.getRequestURI()),
                any(),
                any(),
                exceptionCaptor.capture()
            );

            assertEquals(TokenExpireException.class, exceptionCaptor.getValue().getClass());
        }

        private void assertException(Class<? extends RuntimeException> exceptionClass) throws ServletException {
            ArgumentCaptor<RuntimeException> exceptionCaptor = ArgumentCaptor.forClass(RuntimeException.class);

            verify(authExceptionHandler).handleException(
                eq(request.getRequestURI()),
                any(), // BiConsumer<ApiMessageView, HttpStatus>
                any(), // BiConsumer<String, String>
                exceptionCaptor.capture()
            );

            assertEquals(exceptionClass, exceptionCaptor.getValue().getClass());
        }


    }

    private void mockAuthSource(boolean isValid) {
        AuthSource authSource = new JwtAuthSource("token");
        request.setAttribute(AUTH_SOURCE_ATTR, authSource);
        when(authSourceService.isValid(authSource)).thenReturn(isValid);
    }

    private void mockAuthSourceParsed(boolean isValid) {
        AuthSource.Parsed parsedAuthSource = new ParsedTokenAuthSource(isValid ? "user" : null, null, null, null);
        request.setAttribute(AUTH_SOURCE_PARSED_ATTR, parsedAuthSource);
    }

}
