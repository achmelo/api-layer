/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.caching.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.zowe.apiml.filter.AttlsHttpHandler;
import org.zowe.apiml.product.service.ServiceStartupEventHandler;
import org.zowe.apiml.product.web.ApimlTomcatCustomizer;
import org.zowe.apiml.product.web.TomcatAcceptFixConfig;
import org.zowe.apiml.product.web.TomcatKeyringFix;

@Configuration
@Import({TomcatKeyringFix.class, TomcatAcceptFixConfig.class, ApimlTomcatCustomizer.class, AttlsHttpHandler.class,})
@Data
@ToString
public class GeneralConfig implements WebMvcConfigurer {

    @Value("${caching.storage.evictionStrategy:reject}")
    private String evictionStrategy;
    @Value("${caching.storage.size:100}")
    private int maxDataSize;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }

    @Bean
    @ConditionalOnMissingBean(name = "modulithConfig")
    ServiceStartupEventHandler serviceStartupEventHandler() {
        return new ServiceStartupEventHandler();
    }

}
