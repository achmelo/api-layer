/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.UnexpectedTypeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.zowe.apiml.apicatalog.swagger.api.AbstractApiDocService;
import org.zowe.apiml.apicatalog.swagger.api.ApiDocV2Service;
import org.zowe.apiml.apicatalog.swagger.api.ApiDocV3Service;
import org.zowe.apiml.config.ApplicationInfo;
import org.zowe.apiml.product.gateway.GatewayClient;

import java.io.IOException;
import java.util.function.Function;

@Configuration
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("squid:S1452")
public class ApiTransformationConfig {

    private final ApplicationInfo applicationInfo;
    private final GatewayClient gatewayClient;

    @Bean
    public Function<String, AbstractApiDocService<?, ?>> beanApiDocFactory() {
        return this::abstractApiDocService;
    }

    @Bean
    @Scope(value = "prototype")
    public AbstractApiDocService<?, ?> abstractApiDocService(String content) {
        JsonFactory jsonFactory;
        if (String.valueOf(content).trim().startsWith("{")) {
            jsonFactory = new JsonFactory();
        } else {
            jsonFactory = new YAMLFactory();
        }

        ObjectMapper mapper = new ObjectMapper(jsonFactory)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            ObjectNode objectNode = mapper.readValue(content, ObjectNode.class);
            JsonNode openApiNode = objectNode.get("openapi");
            if (openApiNode != null) {
                return new ApiDocV3Service(applicationInfo, gatewayClient);
            } else {
                JsonNode swaggerNode = objectNode.get("swagger");
                if (swaggerNode != null) {
                    return new ApiDocV2Service(applicationInfo, gatewayClient);
                }
            }
        } catch (IOException e) {
            log.debug("Could not convert response body to a Swagger/OpenAPI object.", e);
            throw new UnexpectedTypeException("Response is not a Swagger or OpenAPI type object.", e);
        }

        return null;
    }

}
