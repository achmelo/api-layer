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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.zowe.apiml.security.common.audit.RauditxService;
import org.zowe.apiml.security.common.error.AuthExceptionHandler;

import java.io.IOException;

@Component
public class FailedAccessTokenHandler extends FailedAuthenticationHandler {

    private final RauditxService rauditxService;

    public FailedAccessTokenHandler(AuthExceptionHandler handler, RauditxService rauditxService) {
        super(handler);
        this.rauditxService = rauditxService;
    }

    /**
     * Handles authentication failure by printing a debug message and passes control to {@link AuthExceptionHandler}
     *
     * @param request   the http request
     * @param response  the http response
     * @param exception to be checked
     * @throws ServletException when the response cannot be written
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws ServletException, IOException {
        rauditxService.builder()
            .messageSegment("Authentication failed. Cannot generate PAT")
            .alwaysLogSuccesses()
            .alwaysLogFailures()
            .failure()
            .issue();

        super.onAuthenticationFailure(request, response, exception);
    }

}
