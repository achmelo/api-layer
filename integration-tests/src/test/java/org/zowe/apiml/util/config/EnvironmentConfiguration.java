/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.util.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentConfiguration {
    private Credentials credentials;
    private GatewayServiceConfiguration gatewayServiceConfiguration;
    private CentralGatewayServiceConfiguration centralGatewayServiceConfiguration;
    private ZaasConfiguration zaasConfiguration;
    private DiscoveryServiceConfiguration discoveryServiceConfiguration;
    private DiscoverableClientConfiguration discoverableClientConfiguration;
    @JsonProperty("apiCatalogServiceConfiguration") private ApiCatalogServiceConfiguration apiCatalogServiceConfiguration;
    @JsonProperty("apiCatalogStandaloneConfiguration") private ApiCatalogServiceConfiguration apiCatalogStandaloneConfiguration;
    private CachingServiceConfiguration cachingServiceConfiguration;
    private TlsConfiguration tlsConfiguration;
    private ZosmfServiceConfiguration zosmfServiceConfiguration;
    private AuxiliaryUserList auxiliaryUserList;
    private Map<String, String> instanceEnv;
    private IDPConfiguration idpConfiguration;
    private SafIdtConfiguration safIdtConfiguration;
    private OidcConfiguration oidcConfiguration;
}
