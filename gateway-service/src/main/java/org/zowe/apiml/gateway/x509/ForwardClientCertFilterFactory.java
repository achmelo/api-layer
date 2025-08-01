/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.x509;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.security.common.util.X509Util;

import java.security.cert.CertificateEncodingException;

import static org.zowe.apiml.constants.ApimlConstants.HTTP_CLIENT_USE_CLIENT_CERTIFICATE;

/**
 * Objective is to include new header in the request which contains incoming client certificate
 * so that further processing (mapping to mainframe userId) is possible by the domain gateway.
 */
@Service
@Slf4j
public class ForwardClientCertFilterFactory extends AbstractGatewayFilterFactory<ForwardClientCertFilterFactory.Config> {

    public static final String CLIENT_CERT_HEADER = "Client-Cert";

    public ForwardClientCertFilterFactory() {
        super(Config.class);
    }

    /**
     * Filter business logic - Always remove any existing Client-Cert header from incoming request.
     * If feature is enabled, then extracts the client certificate, encode it and put it to new Client-Cert header.
     * If encoding fails then add X-Zowe-Auth-Failure header with the error message to the request.
     *
     * @param config Configuration values of this filter
     * @return GatewayFilter object
     */
    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
                headers.remove(CLIENT_CERT_HEADER);
                try {
                    final String encodedCert = X509Util.getEncodedClientCertificate(exchange.getRequest().getSslInfo());
                    if (encodedCert != null) {
                        exchange.getAttributes().put(HTTP_CLIENT_USE_CLIENT_CERTIFICATE, Boolean.TRUE);
                        headers.add(CLIENT_CERT_HEADER, encodedCert);
                        log.debug("Incoming client certificate {} has been added to the {} header.", encodedCert, CLIENT_CERT_HEADER);
                    }
                } catch (CertificateEncodingException e) {
                    log.debug("Failed to encode the incoming client certificate. Error message: {}", e.getMessage());
                    headers.add(ApimlConstants.AUTH_FAIL_HEADER, "Invalid client certificate in request. Error message: " + e.getMessage());
                }
            }).build();
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @SuppressWarnings("squid:S2094")
    public static class Config {
    }

}
