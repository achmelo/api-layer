/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = {
        "org.zowe.apiml.gateway",
        "org.zowe.apiml.product.web",
        "org.zowe.apiml.product.config",
        "org.zowe.apiml.product.gateway",
        "org.zowe.apiml.product.version",
        "org.zowe.apiml.product.logging",
        "org.zowe.apiml.product.service",
        "org.zowe.apiml.security"
    },
    exclude = {ReactiveOAuth2ClientAutoConfiguration.class}
)
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

}
