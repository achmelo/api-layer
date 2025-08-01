/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.product.routing.transform;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.instance.ServiceAddress;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.product.routing.RoutedService;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.ServiceType;
import org.zowe.apiml.util.UrlUtils;

import java.net.URI;

/**
 * Class for producing service URL using Gateway hostname and service route
 */

@RequiredArgsConstructor
public class TransformService {

    private static final String SEPARATOR = "/";

    private final GatewayClient gatewayClient;

    @InjectApimlLogger
    private ApimlLogger apimlLog = ApimlLogger.empty();

    /**
     * Construct the URL using gateway hostname and route
     *
     * @param type       the type of the route
     * @param serviceId  the service id
     * @param serviceUrl the service URL
     * @param routes     the routes
     * @param httpsScheme https scheme flag
     * @return the new URL
     * @throws URLTransformationException if the path of the service URL is not valid
     */
    public String transformURL(ServiceType type,
                               String serviceId,
                               String serviceUrl,
                               RoutedServices routes,
                               boolean httpsScheme) throws URLTransformationException {
        if (!gatewayClient.isInitialized()) {
            apimlLog.log("org.zowe.apiml.common.gatewayNotFoundForTransformRequest");
            throw new URLTransformationException("Gateway not found yet, transform service cannot perform the request");
        }

        URI serviceUri = URI.create(serviceUrl);
        String serviceUriPath = serviceUri.getPath();
        if (serviceUriPath == null) {
            String message = String.format("The URI %s is not valid.", serviceUri);
            throw new URLTransformationException(message);
        }

        RoutedService route = routes.getBestMatchingServiceUrl(serviceUriPath, type);
        if (route == null) {
            String message = String.format("Not able to select route for url %s of the service %s. Original url used.", serviceUri, serviceId);
            throw new URLTransformationException(message);
        }

        if (serviceUri.getQuery() != null) {
            serviceUriPath += "?" + serviceUri.getQuery();
        }

        return transformURL(serviceId, serviceUriPath, route, httpsScheme, serviceUri);
    }

    public String transformURL(String serviceId,
                               String serviceUriPath,
                               RoutedService route,
                               boolean httpsScheme,
                               URI originalUri
    ) throws URLTransformationException {
        if (!gatewayClient.isInitialized()) {
            apimlLog.log("org.zowe.apiml.common.gatewayNotFoundForTransformRequest");
            throw new URLTransformationException("Gateway not found yet, transform service cannot perform the request");
        }

        String endPoint = getShortEndPoint(route.getServiceUrl(), serviceUriPath);
        if (!endPoint.isEmpty() && !endPoint.startsWith("/")) {
            throw new URLTransformationException("The path " + originalUri.getPath() + " of the service URL " + originalUri + " is not valid.");
        }

        ServiceAddress gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();

        String scheme = httpsScheme ? "https" : gatewayConfigProperties.getScheme();
        return String.format("%s://%s/%s/%s%s",
            scheme,
            gatewayConfigProperties.getHostname(),
            serviceId,
            route.getGatewayUrl(),
            endPoint);
    }

    /**
     * Construct the API base path using the route
     *
     * @param serviceId  the service id
     * @param serviceUrl the service URL
     * @param routes     the routes
     * @return the new URL
     * @throws URLTransformationException if the path of the service base path is not valid or cannot be found
     */
    public String retrieveApiBasePath(String serviceId,
                                      String serviceUrl,
                                      RoutedServices routes) throws URLTransformationException {
        serviceUrl = serviceUrl.trim();
        URI serviceUri = URI.create(serviceUrl);
        String serviceUriPath = serviceUri.getPath();
        if (serviceUriPath == null) {
            String message = String.format("The URI %s is not valid.", serviceUri);
            throw new URLTransformationException(message);
        }

        RoutedService route = routes.getBestMatchingApiUrl(serviceUriPath);
        if (route == null) {
            String message = String.format("Not able to select API base path for the service %s. Original url used.", serviceId);
            throw new URLTransformationException(message);
        }

        // Make base path version a template so user can understand base path when looking at different API versions
        String templatedVersionRoute = route.getGatewayUrl().replaceAll("/v\\d", "/{api-version}");

        return String.format("/%s/%s",
            serviceId,
            templatedVersionRoute);
    }

    /**
     * Get short endpoint
     *
     * @param routeServiceUrl service url of route
     * @param endPoint        the endpoint of method
     * @return short endpoint
     */
    private String getShortEndPoint(String routeServiceUrl, String endPoint) {
        String shortEndPoint = endPoint;
        if (!SEPARATOR.equals(routeServiceUrl) && StringUtils.isNotBlank(routeServiceUrl)) {
            shortEndPoint = shortEndPoint.replaceFirst(UrlUtils.removeLastSlash(routeServiceUrl), "");
        }
        return shortEndPoint;
    }

}
