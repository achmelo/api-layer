/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.security.service.schema.source;

import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.zowe.apiml.zaas.security.mapping.X509CommonNameUserMapper;
import org.zowe.apiml.zaas.security.service.AuthenticationService;
import org.zowe.apiml.zaas.security.service.TokenCreationService;

import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.zowe.apiml.security.common.filter.CategorizeCertsFilter.ATTR_NAME_CLIENT_AUTH_X509_CERTIFICATE;
import static org.zowe.apiml.security.common.filter.CategorizeCertsFilter.ATTR_NAME_JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE;

class X509CNAuthSourceServiceTest {

    private HttpServletRequest request;
    private X509CommonNameUserMapper mapper;
    private X509AuthSourceService serviceUnderTest;
    private X509Certificate x509Certificate;

    @Nested
    class GivenValidAuthSource {
        @BeforeEach
        void init() {
            request = mock(HttpServletRequest.class);
            x509Certificate = mock(X509Certificate.class);
            mapper = mock(X509CommonNameUserMapper.class);
            serviceUnderTest = new X509CNAuthSourceService(mapper, mock(TokenCreationService.class), mock(AuthenticationService.class));
        }

        @Test
        void whenClientCertInRequestInCustomAttribute_thenAuthSourceIsPresent() {
            when(request.getAttribute(ATTR_NAME_CLIENT_AUTH_X509_CERTIFICATE)).thenReturn(Arrays.array(x509Certificate));

            Optional<AuthSource> authSource = serviceUnderTest.getAuthSourceFromRequest(request);

            verify(request, times(1)).getAttribute(ATTR_NAME_CLIENT_AUTH_X509_CERTIFICATE);
            verify(request, times(0)).getAttribute(ATTR_NAME_JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE);

            Assertions.assertTrue(authSource.isPresent());
            Assertions.assertTrue(authSource.get() instanceof X509AuthSource);
            Assertions.assertEquals(x509Certificate, authSource.get().getRawSource());
        }


        @Test
        void whenInternalApimlCertInRequestInStandardAttribute_thenAuthSourceIsPresent() {
            when(request.getAttribute(ATTR_NAME_JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE)).thenReturn(Arrays.array(x509Certificate));

            Optional<AuthSource> authSource = serviceUnderTest.getAuthSourceFromRequest(request);

            verify(request, times(1)).getAttribute(ATTR_NAME_CLIENT_AUTH_X509_CERTIFICATE);
            verify(request, times(1)).getAttribute(ATTR_NAME_JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE);

            Assertions.assertTrue(authSource.isPresent());
            Assertions.assertTrue(authSource.get() instanceof X509AuthSource);
            Assertions.assertEquals(x509Certificate, authSource.get().getRawSource());
        }

    }

}
