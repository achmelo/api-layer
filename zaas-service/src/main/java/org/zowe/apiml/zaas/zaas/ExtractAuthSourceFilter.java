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

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.security.common.handler.ServletErrorUtils;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSource;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.security.common.error.AuthExceptionHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.function.BiConsumer;


@RequiredArgsConstructor
public class ExtractAuthSourceFilter extends OncePerRequestFilter {

    public static final String AUTH_SOURCE_ATTR = "zaas.auth.source";
    public static final String AUTH_SOURCE_PARSED_ATTR = "zaas.auth.source.parsed";

    private final AuthSourceService authSourceService;
    private final AuthExceptionHandler authExceptionHandler;
    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            Optional<AuthSource> authSource = authSourceService.getAuthSourceFromRequest(request);
            if (authSource.isPresent()) {
                AuthSource.Parsed parsed = authSourceService.parse(authSource.get());
                request.setAttribute(AUTH_SOURCE_ATTR, authSource.get());
                request.setAttribute(AUTH_SOURCE_PARSED_ATTR, parsed);
                filterChain.doFilter(request, response);
            } else {
                throw new InsufficientAuthenticationException("No authentication source found in the request.");
            }
        }
        catch (RuntimeException ex) {
            var consumer = ServletErrorUtils.createApiErrorWriter(response, apimlLog);
            var addHeader = (BiConsumer<String, String>) response::addHeader;
            authExceptionHandler.handleException(request.getRequestURI(), consumer, addHeader, ex);
        }

    }

}
