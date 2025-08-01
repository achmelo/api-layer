/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.common.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.zowe.apiml.config.ApplicationInfo;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.message.core.Message;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.message.yaml.YamlMessageService;
import org.zowe.apiml.security.common.error.AuthExceptionHandler;
import org.zowe.apiml.security.common.error.ErrorType;
import org.zowe.apiml.security.common.token.TokenExpireException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
class BasicUnauthorizedHandlerTest {

    @Autowired
    private MessageService messageService;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void testCommence() throws IOException, ServletException {
        BasicAuthUnauthorizedHandler basicAuthUnauthorizedHandler = new BasicAuthUnauthorizedHandler(
            new AuthExceptionHandler(messageService, objectMapper, ApplicationInfo.builder().isModulith(false).build()));

        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setRequestURI("URI");

        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        basicAuthUnauthorizedHandler.commence(httpServletRequest, httpServletResponse, new TokenExpireException("ERROR"));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), httpServletResponse.getStatus());
        assertEquals(ApimlConstants.BASIC_AUTHENTICATION_PREFIX, httpServletResponse.getHeader(HttpHeaders.WWW_AUTHENTICATE));

        Message message = messageService.createMessage(
            ErrorType.TOKEN_EXPIRED.getErrorMessageKey(),
            httpServletRequest.getRequestURI());

        assertEquals("application/json", httpServletResponse.getContentType());
        assertEquals(new ObjectMapper().writeValueAsString(message.mapToView()), httpServletResponse.getContentAsString());
    }

    @TestConfiguration
    static class ContextConfiguration {

        @Bean
        MessageService messageService() {
            return new YamlMessageService("/security-service-messages.yml");
        }

    }

}
