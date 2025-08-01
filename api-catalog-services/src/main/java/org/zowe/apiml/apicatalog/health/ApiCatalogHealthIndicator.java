/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.zowe.apiml.product.constants.CoreService;

/**
 * Api Catalog health information (/application/health)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(name = "modulithConfig")
public class ApiCatalogHealthIndicator extends AbstractHealthIndicator {

    private final DiscoveryClient discoveryClient;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String gatewayServiceId = CoreService.GATEWAY.getServiceId();

        boolean gatewayUp = !this.discoveryClient.getInstances(gatewayServiceId).isEmpty();
        Status healthStatus = gatewayUp ? Status.UP : Status.DOWN;

        builder
            .status(healthStatus)
            .withDetail(gatewayServiceId, healthStatus.getCode());
    }

}
