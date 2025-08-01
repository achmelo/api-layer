/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.security.client.config;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayInstanceInitializer;
import org.zowe.apiml.product.instance.lookup.InstanceLookupExecutor;

/**
 * General configuration of security client
 */
@Configuration
@ComponentScan({"org.zowe.apiml.security", "org.zowe.apiml.product.gateway", "org.zowe.apiml.gateway.security.login"})
public class SecurityServiceConfiguration {

    @Bean
    GatewayInstanceInitializer gatewayInstanceInitializer(
        DiscoveryClient discoveryClient,
        ApplicationEventPublisher applicationEventPublisher,
        GatewayClient gatewayClient) {
        return new GatewayInstanceInitializer(
            new InstanceLookupExecutor(discoveryClient),
            applicationEventPublisher,
            gatewayClient);
    }
}
