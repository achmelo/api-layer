/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.swagger.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.models.*;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import jakarta.validation.UnexpectedTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.zowe.apiml.apicatalog.model.ApiDocInfo;
import org.zowe.apiml.apicatalog.exceptions.ApiDocTransformationException;
import org.zowe.apiml.config.ApiInfo;
import org.zowe.apiml.config.ApplicationInfo;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.instance.ServiceAddress;

import java.util.Collections;
import java.util.Map;

@Slf4j
public class ApiDocV2Service extends AbstractApiDocService<Swagger, Path> {

    @Value("${gateway.scheme.external:https}")
    private String scheme;

    public ApiDocV2Service(ApplicationInfo applicationInfo, GatewayClient gatewayClient) {
        super(applicationInfo, gatewayClient);
    }

    public String transformApiDoc(String serviceId, ApiDocInfo apiDocInfo) {
        Swagger swagger = new SwaggerParser().readWithInfo(apiDocInfo.getApiDocContent()).getSwagger();
        if (swagger == null) {
            log.debug("Could not convert response body to a Swagger object.");
            throw new UnexpectedTypeException(String.format("The Swagger definition for service '%s' was retrieved but was not a valid JSON document.", serviceId));
        }

        if (swagger.getInfo() == null) {
            swagger.setInfo(new Info());
        }
        if (swagger.getInfo().getVersion() == null) {
            swagger.getInfo().setVersion(apiDocInfo.getApiInfo().getVersion());
        }

        boolean hidden = swagger.getTag(HIDDEN_TAG) != null;

        /**
         * When microservices are in place it is necessary to use path updates, it basically adds into the swagger
         * routing. In case of modulith it is not wanted. The paths are the final one (REST calls does not use Gateway).
         * One specific case is microservices and API Catalog. Even the api doc is downloaded locally it has to be
         * handled by Gateway, so the routes should be added.
         */
        if (!isDefinedOnlyBypassRoutes(apiDocInfo) && !(apiDocInfo.isLocal() && applicationInfo.isModulith())) {
            updateSchemeHost(swagger, serviceId);
            updatePaths(swagger, serviceId, apiDocInfo, hidden);
        }
        updateSwaggerUrl(swagger, serviceId, apiDocInfo.getApiInfo(), hidden, scheme);
        updateExternalDoc(swagger, apiDocInfo);

        try {
            return Json.mapper().writeValueAsString(swagger);
        } catch (JsonProcessingException e) {
            log.debug("Could not convert Swagger to JSON", e);
            throw new ApiDocTransformationException("Could not convert Swagger to JSON");
        }
    }

    /**
     * Updates scheme and hostname, and adds API doc link to Swagger
     *
     * @param swagger   the API doc
     * @param serviceId the unique service id
     */
    private void updateSchemeHost(Swagger swagger, String serviceId) {
        log.debug("Updating host for service with id: " + serviceId + " to: " + getHostname());
        swagger.setSchemes(Collections.singletonList(Scheme.forValue(scheme)));
        swagger.setHost(getHostname());
    }

    private void updateSwaggerUrl(Swagger swagger, String serviceId, ApiInfo apiInfo, boolean hidden, String scheme) {
        ServiceAddress gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String swaggerLink = OpenApiUtil.getOpenApiLink(serviceId, apiInfo, gatewayConfigProperties, scheme);
        if (!hidden) {
            swagger.getInfo().setDescription(swagger.getInfo().getDescription() + swaggerLink);
        }
    }

    /**
     * Updates BasePath and Paths in Swagger
     *
     * @param swagger    the API doc
     * @param serviceId  the unique service id
     * @param apiDocInfo the service information
     * @param hidden     do not set Paths for automatically generated API doc
     */
    protected void updatePaths(Swagger swagger, String serviceId, ApiDocInfo apiDocInfo, boolean hidden) {
        ApiDocPath<Path> apiDocPath = new ApiDocPath<>();
        String basePath = swagger.getBasePath();

        if (swagger.getPaths() != null && !swagger.getPaths().isEmpty()) {
            swagger.getPaths()
                .forEach((originalEndpoint, path)
                    -> preparePath(path, apiDocPath, apiDocInfo, basePath, originalEndpoint, serviceId));
        }

        Map<String, Path> updatedPaths;
        if (apiDocPath.getPrefixes().size() == 1) {
            swagger.setBasePath(OpenApiUtil.getBasePath(serviceId, apiDocPath));
            updatedPaths = apiDocPath.getShortPaths();
        } else {
            swagger.setBasePath("");
            updatedPaths = apiDocPath.getLongPaths();
        }

        if (!hidden) {
            swagger.setPaths(updatedPaths);
        }
    }

    /**
     * Updates External documentation in Swagger
     *
     * @param swagger    the API doc
     * @param apiDocInfo the service information
     */
    protected void updateExternalDoc(Swagger swagger, ApiDocInfo apiDocInfo) {
        if (apiDocInfo.getApiInfo() == null)
            return;

        String externalDoc = apiDocInfo.getApiInfo().getDocumentationUrl();

        if (externalDoc != null) {
            swagger.setExternalDocs(new ExternalDocs(EXTERNAL_DOCUMENTATION, externalDoc));
        }
    }
}
