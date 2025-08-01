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

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.passticket.PassTicketException;
import org.zowe.apiml.passticket.PassTicketService;
import org.zowe.apiml.security.common.token.NoMainframeIdentityException;
import org.zowe.apiml.ticket.TicketRequest;
import org.zowe.apiml.ticket.TicketResponse;
import org.zowe.apiml.zaas.ZaasTokenResponse;
import org.zowe.apiml.zaas.security.service.TokenCreationService;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSource;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.zaas.security.service.zosmf.ZosmfService;

import javax.management.ServiceNotFoundException;

import static org.zowe.apiml.security.SecurityUtils.COOKIE_AUTH_NAME;
import static org.zowe.apiml.zaas.zaas.ExtractAuthSourceFilter.AUTH_SOURCE_ATTR;
import static org.zowe.apiml.zaas.zaas.ExtractAuthSourceFilter.AUTH_SOURCE_PARSED_ATTR;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = SchemeController.CONTROLLER_PATH)
public class SchemeController {
    public static final String CONTROLLER_PATH = "/zaas/scheme"; // NOSONAR

    private final AuthSourceService authSourceService;
    private final PassTicketService passTicketService;
    private final ZosmfService zosmfService;
    private final TokenCreationService tokenCreationService;

    @PostMapping(path = "ticket", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Provides PassTicket for authenticated user.")
    public ResponseEntity<TicketResponse> getPassTicket(@RequestBody TicketRequest ticketRequest, @RequestAttribute(AUTH_SOURCE_PARSED_ATTR) AuthSource.Parsed authSourceParsed)
        throws PassTicketException {

        var ticket = passTicketService.generate(authSourceParsed.getUserId(), ticketRequest.getApplicationName());

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(new TicketResponse("", authSourceParsed.getUserId(), ticketRequest.getApplicationName(), ticket));
    }

    @PostMapping(path = "zosmf", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Provides z/OSMF JWT or LTPA token for authenticated user.")
    public ResponseEntity<ZaasTokenResponse> getZosmfToken(@RequestAttribute(AUTH_SOURCE_ATTR) AuthSource authSource,
                                                           @RequestAttribute(AUTH_SOURCE_PARSED_ATTR) AuthSource.Parsed authSourceParsed) throws ServiceNotFoundException {

        ZaasTokenResponse zaasTokenResponse = zosmfService.exchangeAuthenticationForZosmfToken(authSource.getRawSource().toString(), authSourceParsed);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(zaasTokenResponse);
    }

    @PostMapping(path = "zoweJwt", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Provides zoweJwt for authenticated user.")
    public ResponseEntity<ZaasTokenResponse> getZoweJwt(@RequestAttribute(AUTH_SOURCE_ATTR) AuthSource authSource) {

        var token = authSourceService.getJWT(authSource);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ZaasTokenResponse.builder().cookieName(COOKIE_AUTH_NAME).token(token).build());
    }

    /**
     * Controller level exception handler for cases when NO mapping with mainframe ID exists.
     *
     * @param authSource credentials that will be used for authentication translation
     * @param nmie       exception thrown in case of missing user mapping
     * @return status code OK, header name and value if OIDC token is valid, otherwise status code UNAUTHORIZED
     */
    @ExceptionHandler(NoMainframeIdentityException.class)
    public ResponseEntity<ZaasTokenResponse> handleNoMainframeIdException(@RequestAttribute(AUTH_SOURCE_ATTR) AuthSource authSource, NoMainframeIdentityException nmie) {
        if (nmie.isValidToken() && authSource.getType() == AuthSource.AuthSourceType.OIDC) {
            return ResponseEntity
                .status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ZaasTokenResponse.builder().headerName(ApimlConstants.HEADER_OIDC_TOKEN).token(String.valueOf(authSource.getRawSource())).build());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping(path = "safIdt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Provides SAF Identity Token for authenticated user.")
    public ResponseEntity<ZaasTokenResponse> getSafIdToken(@RequestBody TicketRequest ticketRequest, @RequestAttribute(AUTH_SOURCE_PARSED_ATTR) AuthSource.Parsed authSourceParsed)
        throws PassTicketException {

        var safIdToken = tokenCreationService.createSafIdTokenWithoutCredentials(authSourceParsed.getUserId(), ticketRequest.getApplicationName());
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ZaasTokenResponse.builder().headerName(ApimlConstants.SAF_TOKEN_HEADER).token(safIdToken).build());

    }

}
