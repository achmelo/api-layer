/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.zowe.apiml.product.logging.annotations.EnableApimlLogger;
import org.zowe.apiml.product.monitoring.LatencyUtilsConfigInitializer;
import org.zowe.apiml.product.version.BuildInfo;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(value = {
    "org.zowe.apiml.apicatalog",
    "org.zowe.apiml.product.compatibility",
    "org.zowe.apiml.product.security",
    "org.zowe.apiml.product.web",
    "org.zowe.apiml.product.gateway",
    "org.zowe.apiml.product.service",
    "org.zowe.apiml.filter"
})
@EnableScheduling
@EnableRetry
@EnableAsync
@EnableApimlLogger
public class ApiCatalogApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ApiCatalogApplication.class);
        app.addInitializers(new LatencyUtilsConfigInitializer());
        app.setLogStartupInfo(false);
        new BuildInfo().logBuildInfo();
        app.run(args);
    }
}
