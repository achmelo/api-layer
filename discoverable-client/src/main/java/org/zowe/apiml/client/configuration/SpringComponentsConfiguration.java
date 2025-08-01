/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.client.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.zowe.apiml.product.service.ServiceStartupEventHandler;

/**
 * Configuration for Spring Boot components.
 * This is the customization of Jackson deserializer.
 */
@SpringBootConfiguration
public class SpringComponentsConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer failOnUnknownProperties() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder
            .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    ServiceStartupEventHandler serviceStartupEventHandler() {
        return new ServiceStartupEventHandler();
    }

}
