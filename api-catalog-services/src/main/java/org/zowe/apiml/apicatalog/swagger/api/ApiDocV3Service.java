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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.core.jackson.mixin.MediaTypeMixin;
import io.swagger.v3.core.jackson.mixin.SchemaMixin;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.validation.UnexpectedTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.zowe.apiml.apicatalog.model.ApiDocInfo;
import org.zowe.apiml.apicatalog.exceptions.ApiDocTransformationException;
import org.zowe.apiml.apicatalog.swagger.SecuritySchemeSerializer;
import org.zowe.apiml.config.ApiInfo;
import org.zowe.apiml.config.ApplicationInfo;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.instance.ServiceAddress;
import org.zowe.apiml.product.routing.RoutedService;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@Slf4j
public class ApiDocV3Service extends AbstractApiDocService<OpenAPI, PathItem> {
    @Value("${gateway.scheme.external:https}")
    private String scheme;

    public ApiDocV3Service(ApplicationInfo applicationInfo, GatewayClient gatewayClient) {
        super(applicationInfo, gatewayClient);
    }

    public String transformApiDoc(String serviceId, ApiDocInfo apiDocInfo) {
        SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(apiDocInfo.getApiDocContent());
        OpenAPI openAPI = parseResult.getOpenAPI();

        if (openAPI == null) {
            log.debug("Could not convert response body to an OpenAPI object for service {}. {}", serviceId, parseResult.getMessages());

            if (parseResult.getMessages() == null) {
                throw new UnexpectedTypeException("Response is not an OpenAPI type object.");
            } else {
                throw new UnexpectedTypeException(
                    String.format("The OpenAPI for service '%s' was retrieved but was not a valid JSON document. '%s'", serviceId, parseResult.getMessages().toString()));
            }
        }

        if (openAPI.getInfo() == null) {
            openAPI.setInfo(new Info());
        }
        if (openAPI.getInfo().getVersion() == null) {
            openAPI.getInfo().setVersion(apiDocInfo.getApiInfo().getVersion());
        }

        boolean hidden = isHidden(openAPI.getTags());

        /**
         * When microservices are in place it is necessary to use path updates, it basically adds into the swagger
         * routing. In case of modulith it is not wanted. The paths are the final one (REST calls does not use Gateway).
         * One specific case is microservices and API Catalog. Even the api doc is downloaded locally it has to be
         * handled by Gateway, so the routes should be added.
         */
        if (!isDefinedOnlyBypassRoutes(apiDocInfo) && !(apiDocInfo.isLocal() && applicationInfo.isModulith())) {
            updatePaths(openAPI, serviceId, apiDocInfo, hidden);
            updateServer(openAPI);
        }
        updateSwaggerUrl(openAPI, serviceId, apiDocInfo.getApiInfo(), hidden, scheme);
        updateExternalDoc(openAPI, apiDocInfo);

        try {
            return objectMapper().writeValueAsString(openAPI);
        } catch (JsonProcessingException e) {
            log.debug("Could not convert OpenAPI to JSON", e);
            throw new ApiDocTransformationException("Could not convert Swagger to JSON");
        }
    }

    private void updateServer(OpenAPI openAPI) {
        if (openAPI.getServers() != null) {
            openAPI.getServers()
                .forEach(server -> server.setUrl(
                    String.format("%s://%s/%s", scheme, getHostname(), server.getUrl())));
        }
    }

    private void updateSwaggerUrl(OpenAPI openAPI, String serviceId, ApiInfo apiInfo, boolean hidden, String scheme) {
        ServiceAddress gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String swaggerLink = OpenApiUtil.getOpenApiLink(serviceId, apiInfo, gatewayConfigProperties, scheme);
        if (!hidden) {
            openAPI.getInfo().setDescription(openAPI.getInfo().getDescription() + swaggerLink);
        }
    }

    /**
     * Updates Servers and Paths in OpenAPI
     *
     * @param openAPI    the API doc
     * @param serviceId  the unique service id
     * @param apiDocInfo the service information
     * @param hidden     do not set Paths for automatically generated API doc
     */
    protected void updatePaths(OpenAPI openAPI, String serviceId, ApiDocInfo apiDocInfo, boolean hidden) {
        ApiDocPath<PathItem> apiDocPath = new ApiDocPath<>();
        Server server = getBestMatchingServer(openAPI.getServers(), apiDocInfo);
        String basePath = server != null ? getBasePath(server.getUrl()) : "";

        if (openAPI.getPaths() != null && !openAPI.getPaths().isEmpty()) {
            openAPI.getPaths()
                .forEach((originalEndpoint, path)
                    -> preparePath(path, apiDocPath, apiDocInfo, basePath, originalEndpoint, serviceId));
        }

        Map<String, PathItem> updatedPaths;
        if (apiDocPath.getPrefixes().size() == 1) {
            updateServerUrl(openAPI, server, OpenApiUtil.getBasePath(serviceId, apiDocPath));
            updatedPaths = apiDocPath.getShortPaths();
        } else {
            updateServerUrl(openAPI, server, "/");
            updatedPaths = apiDocPath.getLongPaths();
        }

        if (!hidden) {
            Paths paths = new Paths();
            updatedPaths.keySet().forEach(pathName -> paths.addPathItem(pathName, updatedPaths.get(pathName)));
            openAPI.setPaths(paths);
        }
    }

    private Server getBestMatchingServer(List<Server> servers, ApiDocInfo apiDocInfo) {
        if (servers != null && !servers.isEmpty()) {
            for (Server server : servers) {
                String basePath = getBasePath(server.getUrl());
                RoutedService route = getRoutedServiceByApiInfo(apiDocInfo, basePath);
                if (route != null) {
                    return server;
                }
            }
            return servers.get(0);
        }
        return null;
    }

    private String getBasePath(String serverUrl) {
        String basePath = "";
        try {
            URI uri = new URI(serverUrl);
            basePath = uri.getPath();
        } catch (Exception e) {
            log.debug("serverUrl is not parse-able");
        }
        return basePath;
    }

    /**
     * Updates External documentation in OpenAPI
     *
     * @param openAPI    the API doc
     * @param apiDocInfo the service information
     */
    protected void updateExternalDoc(OpenAPI openAPI, ApiDocInfo apiDocInfo) {
        if (apiDocInfo.getApiInfo() == null)
            return;

        String externalDocUrl = apiDocInfo.getApiInfo().getDocumentationUrl();

        if (externalDocUrl != null) {
            ExternalDocumentation externalDoc = new ExternalDocumentation();
            externalDoc.setDescription(EXTERNAL_DOCUMENTATION);
            externalDoc.setUrl(externalDocUrl);
            openAPI.setExternalDocs(externalDoc);
        }
    }

    private void updateServerUrl(OpenAPI openAPI, Server server, String basePath) {
        if (server != null) {
            server.setUrl(basePath.startsWith("/") ? basePath.substring(1) : basePath); // server expects no / at start of url
            openAPI.setServers(Collections.singletonList(server));
        } else {
            openAPI.addServersItem(new Server().url(basePath));
        }
    }

    private boolean isHidden(List<Tag> tags) {
        return tags != null && tags.stream().anyMatch(tag -> tag.getName().equals(HIDDEN_TAG));
    }

    ObjectMapper objectMapper() {
        return new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new SimpleModule().addSerializer(SecurityScheme.class, new SecuritySchemeSerializer()))
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
            .addMixIn(Schema.class, SchemaMixin.class)
            .addMixIn(MediaType.class, MediaTypeMixin.class);
    }

}
