/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.zowe.apiml.client.configuration.ApplicationConfiguration;
import org.zowe.apiml.client.configuration.SecurityConfiguration;
import org.zowe.apiml.client.configuration.SpringComponentsConfiguration;
import org.zowe.apiml.zaasclient.exception.ZaasClientErrorCodes;
import org.zowe.apiml.zaasclient.exception.ZaasClientException;
import org.zowe.apiml.zaasclient.service.ZaasClient;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ZaasClientTestController.class})
@Import(value = {SecurityConfiguration.class, SpringComponentsConfiguration.class, ApplicationConfiguration.class, AnnotationConfigContextLoader.class})
class ZaasClientTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private ZaasClient zaasClient;

    private static final String TOKEN_PREFIX = "apimlAuthenticationToken";

    @Test
    void forwardLoginTest_successfulLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest("username", "password".toCharArray());
        when(zaasClient.login("username", "password".toCharArray())).thenReturn("token");

        this.mockMvc.perform(
            post("/api/v1/zaasClient/login")
                .content(mapper.writeValueAsString(loginRequest))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string("token"));
    }

    @Test
    void forwardLoginTest_invalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest("incorrectUser", "incorrectPass".toCharArray());
        when(zaasClient.login("incorrectUser", "incorrectPass".toCharArray()))
            .thenThrow(new ZaasClientException(ZaasClientErrorCodes.INVALID_AUTHENTICATION));

        this.mockMvc.perform(
            post("/api/v1/zaasClient/login")
                .content(mapper.writeValueAsString(loginRequest))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(401))
            .andExpect(content().string("Invalid username or password"));
    }

    @Test
    void givenValidToken_whenPerformingLogout_thenSuccessLogout() throws Exception {
        String token = "token";
        this.mockMvc.perform(
            post("/api/v1/zaasClient/logout")
                .cookie(new Cookie(TOKEN_PREFIX, token))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(204));
    }

    @Test
    void givenNoToken_whenPerformingLogout_thenFailLogout() throws Exception {
        this.mockMvc.perform(
            post("/api/v1/zaasClient/logout")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(500))
            .andExpect(content().string("Missing cookie or authorization header in the request"));
    }

}
