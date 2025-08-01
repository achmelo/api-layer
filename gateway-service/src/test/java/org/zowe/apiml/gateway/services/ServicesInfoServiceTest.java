/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.services;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaServiceInstance;
import org.zowe.apiml.auth.AuthenticationScheme;
import org.zowe.apiml.config.ApiInfo;
import org.zowe.apiml.constants.EurekaMetadataDefinition;
import org.zowe.apiml.constants.EurekaMetadataDefinition.RegistrationType;
import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.instance.ServiceAddress;
import org.zowe.apiml.product.routing.transform.TransformService;
import org.zowe.apiml.services.ServiceInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.AUTHENTICATION_APPLID;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.AUTHENTICATION_SCHEME;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.ROUTES;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.ROUTES_GATEWAY_URL;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.ROUTES_SERVICE_URL;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.SERVICE_DESCRIPTION;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.SERVICE_TITLE;

@ExtendWith(MockitoExtension.class)
class ServicesInfoServiceTest {

    // Gateway configuration
    private static final String GW_HOSTNAME = "gateway";
    private static final String GW_PORT = "10010";
    private static final String GW_SCHEME = "https";
    private static final String GW_BASE_URL = GW_SCHEME + "://" + GW_HOSTNAME + ":" + GW_PORT;

    // Client test configuration
    private final static String CLIENT_SERVICE_ID = "testclient";
    private final static String CLIENT_INSTANCE_ID = CLIENT_SERVICE_ID + ":";
    private final static String CLIENT_HOSTNAME = "client";
    private final static String CLIENT_IP = "192.168.0.1";
    private static final int CLIENT_PORT = 10;
    private static final String CLIENT_SCHEME = "https";
    private static final String CLIENT_HOMEPAGE = "https://client:10";
    private static final String CLIENT_RELATIVE_HEALTH_URL = "/actuator/health";
    private static final String CLIENT_STATUS_URL = "https://client:10/actuator/info";
    private static final String CLIENT_API_ID = "zowe.client.api";
    private static final String CLIENT_API_VERSION = "1.0.0";
    private static final String CLIENT_API_GW_URL = "api/v1";
    private static final boolean CLIENT_API_DEFAULT = true;
    private static final String CLIENT_API_SWAGGER_URL = CLIENT_HOMEPAGE + "/apiDoc";
    private static final String CLIENT_API_DOC_URL = "https://www.zowe.org";
    private static final String CLIENT_API_BASE_PATH = "/" + CLIENT_SERVICE_ID + "/" + CLIENT_API_GW_URL;
    private static final String CLIENT_API_BASE_URL = GW_BASE_URL + CLIENT_API_BASE_PATH;
    private static final String CLIENT_ROUTE_UI = "ui/v1";
    private static final String CLIENT_SERVICE_TITLE = "Client service";
    private static final String CLIENT_SERVICE_DESCRIPTION = "Client test service";
    private static final String CLIENT_SERVICE_HOMEPAGE = GW_BASE_URL + "/" + CLIENT_SERVICE_ID + "/" + CLIENT_ROUTE_UI;
    private static final AuthenticationScheme CLIENT_AUTHENTICATION_SCHEME = AuthenticationScheme.ZOSMF;
    private static final String CLIENT_AUTHENTICATION_APPLID = "authid";
    private static final boolean CLIENT_AUTHENTICATION_SSO = true;
    private static final String CLIENT_CUSTOM_METADATA_KEY = "custom.test.key";
    private static final String CLIENT_CUSTOM_METADATA_VALUE = "value";

    // ServiceInfo properties
    private static final String SERVICE_SERVICE_ID = "serviceId";
    private static final String SERVICE_API_ID = "apiId";
    private static final String SERVICE_API_VERSION = "version";

    @Mock
    private DiscoveryClient discoveryClient;

    private final ServiceAddress zaasAddress = ServiceAddress.builder()
            .scheme(GW_SCHEME).hostname(GW_HOSTNAME + ":" + GW_PORT).build();

    private final EurekaMetadataParser eurekaMetadataParser = new EurekaMetadataParser();
    private final GatewayClient gatewayClient = new GatewayClient(zaasAddress);
    private final TransformService transformService = new TransformService(gatewayClient);

    private ServicesInfoService servicesInfoService;

    @BeforeEach
    void setUp() {
        servicesInfoService = new ServicesInfoService(discoveryClient, eurekaMetadataParser, gatewayClient, transformService);
    }

    @Test
    void whenListingAllServices_thenReturnList() {
        String clientServiceId2 = "testclient2";

        when(discoveryClient.getServices())
            .thenReturn(List.of(CLIENT_SERVICE_ID, clientServiceId2));

        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(List.of(createBasicTestInstance()));

        List<ServiceInfo> servicesInfo = servicesInfoService.getServicesInfo();
        List<ServiceInfo> servicesInfo2 = servicesInfoService.getServicesInfo(null);

        assertEquals(servicesInfo, servicesInfo2);
        assertEquals(2, servicesInfo.size());
        assertThat(servicesInfo, contains(
                hasProperty(SERVICE_SERVICE_ID, is(CLIENT_SERVICE_ID)),
                hasProperty(SERVICE_SERVICE_ID, is(clientServiceId2))
        ));
    }

    @Test
    void whenListingServicesByApiId_thenReturnList() {
        when(discoveryClient.getServices())
            .thenReturn(List.of(CLIENT_SERVICE_ID, "testclient2"));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(List.of(createFullTestInstance()));
        when(discoveryClient.getInstances("testclient2")).thenReturn(List.of());

        List<ServiceInfo> servicesInfo = servicesInfoService.getServicesInfo(CLIENT_API_ID);

        assertEquals(1, servicesInfo.size());
        assertEquals(CLIENT_SERVICE_ID, servicesInfo.get(0).getServiceId());
    }

    @SuppressWarnings({"java:S5961"})
    @Test
    void whenInstanceProvidesFullInfo_thenReturnAllDetails() {
        when(discoveryClient.getServices()).thenReturn(List.of(CLIENT_SERVICE_ID));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(List.of(createFullTestInstance()));

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(CLIENT_SERVICE_ID, serviceInfo.getServiceId());
        assertEquals(InstanceInfo.InstanceStatus.UP, serviceInfo.getStatus());

        assertEquals(CLIENT_API_ID, serviceInfo.getApiml().getApiInfo().get(0).getApiId());
        assertEquals(CLIENT_API_VERSION, serviceInfo.getApiml().getApiInfo().get(0).getVersion());
        assertEquals(CLIENT_API_GW_URL, serviceInfo.getApiml().getApiInfo().get(0).getGatewayUrl());
        assertEquals(CLIENT_API_SWAGGER_URL, serviceInfo.getApiml().getApiInfo().get(0).getSwaggerUrl());
        assertEquals(CLIENT_API_DOC_URL, serviceInfo.getApiml().getApiInfo().get(0).getDocumentationUrl());
        assertEquals(CLIENT_API_DEFAULT, serviceInfo.getApiml().getApiInfo().get(0).isDefaultApi());
        assertEquals(CLIENT_API_BASE_PATH, serviceInfo.getApiml().getApiInfo().get(0).getBasePath());
        assertEquals(CLIENT_API_BASE_URL, serviceInfo.getApiml().getApiInfo().get(0).getBaseUrl());

        assertEquals(CLIENT_SERVICE_TITLE, serviceInfo.getApiml().getService().getTitle());
        assertEquals(CLIENT_SERVICE_DESCRIPTION, serviceInfo.getApiml().getService().getDescription());
        assertEquals(CLIENT_SERVICE_HOMEPAGE, serviceInfo.getApiml().getService().getHomePageUrl());

        assertEquals(CLIENT_AUTHENTICATION_SCHEME, serviceInfo.getApiml().getAuthentication().get(0).getScheme());
        assertEquals(CLIENT_AUTHENTICATION_APPLID, serviceInfo.getApiml().getAuthentication().get(0).getApplid());
        assertEquals(CLIENT_AUTHENTICATION_SSO, serviceInfo.getApiml().getAuthentication().get(0).supportsSso());

        String instanceId = serviceInfo.getInstances().entrySet().stream().iterator().next().getKey();
        assertTrue(instanceId.contains(CLIENT_INSTANCE_ID));
        assertEquals(InstanceInfo.InstanceStatus.UP, serviceInfo.getInstances().get(instanceId).getStatus());
        assertEquals(CLIENT_HOSTNAME, serviceInfo.getInstances().get(instanceId).getHostname());
        assertEquals(CLIENT_IP, serviceInfo.getInstances().get(instanceId).getIpAddr());
        assertEquals(CLIENT_PORT, serviceInfo.getInstances().get(instanceId).getPort());
        assertEquals(CLIENT_SCHEME, serviceInfo.getInstances().get(instanceId).getProtocol());
        assertEquals(CLIENT_HOMEPAGE, serviceInfo.getInstances().get(instanceId).getHomePageUrl());
        assertEquals(CLIENT_HOMEPAGE + CLIENT_RELATIVE_HEALTH_URL, serviceInfo.getInstances().get(instanceId).getHealthCheckUrl());
        assertEquals(CLIENT_STATUS_URL, serviceInfo.getInstances().get(instanceId).getStatusPageUrl());
        assertEquals(1, serviceInfo.getInstances().get(instanceId).getCustomMetadata().size());
        assertEquals(CLIENT_CUSTOM_METADATA_VALUE, serviceInfo.getInstances().get(instanceId).getCustomMetadata().get(CLIENT_CUSTOM_METADATA_KEY));
    }

    @Test
    void whenInstanceProvidesLittleInfo_thenStillReturnUp() {
        when(discoveryClient.getServices()).thenReturn(List.of(CLIENT_SERVICE_ID));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(List.of(createBasicTestInstance()));

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(InstanceInfo.InstanceStatus.UP, serviceInfo.getStatus());
    }

    @Test
    void whenNoInstances_thenReturnServiceDown() {
        when(discoveryClient.getServices()).thenReturn(List.of(CLIENT_SERVICE_ID));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(List.of());

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(CLIENT_SERVICE_ID, serviceInfo.getServiceId());
        assertEquals(InstanceInfo.InstanceStatus.DOWN, serviceInfo.getStatus());
        assertNull(serviceInfo.getInstances());
        assertNull(serviceInfo.getApiml());
    }

    @Test
    void whenServiceNeverRegistered_thenReturnServiceUnknown() {
        when(discoveryClient.getServices()).thenReturn(List.of());

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(CLIENT_SERVICE_ID, serviceInfo.getServiceId());
        assertEquals(InstanceInfo.InstanceStatus.UNKNOWN, serviceInfo.getStatus());
        assertNull(serviceInfo.getInstances());
        assertNull(serviceInfo.getApiml());
    }

    @Test
    void whenOneInstanceIsUpAndOthersNot_ReturnUp() {
        var instance1 = createBasicTestInstance(InstanceInfo.InstanceStatus.STARTING);
        var instance2 = createBasicTestInstance(InstanceInfo.InstanceStatus.UNKNOWN);
        var instance3 = createBasicTestInstance(InstanceInfo.InstanceStatus.DOWN);
        var instance4 = createBasicTestInstance(InstanceInfo.InstanceStatus.UP);
        List<ServiceInstance> instances = Arrays.asList(instance1, instance2, instance3, instance4);

        when(discoveryClient.getServices()).thenReturn(List.of(CLIENT_SERVICE_ID));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(instances);

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(CLIENT_SERVICE_ID, serviceInfo.getServiceId());
        assertEquals(InstanceInfo.InstanceStatus.UP, serviceInfo.getStatus());
    }

    @Test
    void whenNoInstanceIsUp_ReturnDown() {
        var instance1 = createBasicTestInstance(InstanceInfo.InstanceStatus.STARTING);
        var instance2 = createBasicTestInstance(InstanceInfo.InstanceStatus.UNKNOWN);
        var instance3 = createBasicTestInstance(InstanceInfo.InstanceStatus.DOWN);
        var instance4 = createBasicTestInstance(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
        List<ServiceInstance> instances = Arrays.asList(instance1, instance2, instance3, instance4);

        when(discoveryClient.getServices()).thenReturn(List.of(CLIENT_SERVICE_ID));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(instances);

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(CLIENT_SERVICE_ID, serviceInfo.getServiceId());
        assertEquals(InstanceInfo.InstanceStatus.DOWN, serviceInfo.getStatus());
    }


    /**
     * Check versioning when multiple APIs are present
     * <p>
     * Service section contains details about an instance with the highest version regardless of API
     * API info section contains all major versions. Only the lowest major version is selected.
     */
    @Test
    void checkApisVersioning() {
        var instance1 = createMultipleApisInstance(1, Arrays.asList(
                new ImmutablePair<>("api1", "1.0.0"),
                new ImmutablePair<>("api3", null)));
        var instance2 = createMultipleApisInstance(2, Arrays.asList(
                new ImmutablePair<>(null, "2.7.0"),
                new ImmutablePair<>("api1", "0.0.0"),
                new ImmutablePair<>("api2", "3.0.1")));
        var instance3 = createMultipleApisInstance(3, Arrays.asList(
                new ImmutablePair<>("api1", "1.0.1"),
                new ImmutablePair<>("api3", "2.0.0"),
                new ImmutablePair<>("api1", "1.0-99")));
        List<ServiceInstance> instances = Arrays.asList(instance1, instance2, instance3);

        when(discoveryClient.getServices()).thenReturn(List.of(CLIENT_SERVICE_ID));
        when(discoveryClient.getInstances(CLIENT_SERVICE_ID)).thenReturn(instances);

        ServiceInfo serviceInfo = servicesInfoService.getServiceInfo(CLIENT_SERVICE_ID);

        assertEquals(CLIENT_SERVICE_ID, serviceInfo.getServiceId());
        assertEquals("Client: 2", serviceInfo.getApiml().getService().getTitle());
        assertEquals("Test client: 2", serviceInfo.getApiml().getService().getDescription());

        assertEquals(6, serviceInfo.getApiml().getApiInfo().size());
        assertThat(serviceInfo.getApiml().getApiInfo(), hasItems(allOf(
                hasProperty(SERVICE_API_ID, is("api1")),
                hasProperty(SERVICE_API_VERSION, is("0.0.0"))
        )));
        assertThat(serviceInfo.getApiml().getApiInfo(), hasItems(allOf(
                hasProperty(SERVICE_API_ID, is("api1")),
                hasProperty(SERVICE_API_VERSION, is("1.0.0"))
        )));
        assertThat(serviceInfo.getApiml().getApiInfo(), hasItems(allOf(
                hasProperty(SERVICE_API_ID, is("api2")),
                hasProperty(SERVICE_API_VERSION, is("3.0.1"))
        )));
        assertThat(serviceInfo.getApiml().getApiInfo(), hasItems(allOf(
                hasProperty(SERVICE_API_ID, is("api3")),
                hasProperty(SERVICE_API_VERSION, is(nullValue()))
        )));
        assertThat(serviceInfo.getApiml().getApiInfo(), hasItems(allOf(
                hasProperty(SERVICE_API_ID, is("api3")),
                hasProperty(SERVICE_API_VERSION, is("2.0.0"))
        )));
        assertThat(serviceInfo.getApiml().getApiInfo(), hasItems(allOf(
                hasProperty(SERVICE_API_ID, is(nullValue())),
                hasProperty(SERVICE_API_VERSION, is("2.7.0"))
        )));
    }

    private ServiceInstance createMultipleApisInstance(int clientNumber, List<Pair<String, String>> versions) {
        Map<String, String> metadata = new HashMap<>();
        ApiInfo apiInfo;

        for (Pair<String, String> version : versions) {
            String gatewayUrl = (version.getRight() == null) ? null : "v" + version.getRight().replaceAll("\\.", "-");
            apiInfo = ApiInfo.builder()
                    .apiId(version.getLeft())
                    .version(version.getRight())
                    .gatewayUrl(gatewayUrl)
                    .build();
            metadata.putAll(EurekaMetadataParser.generateMetadata(CLIENT_SERVICE_ID, apiInfo));
        }

        metadata.put(SERVICE_TITLE, "Client: " + clientNumber);
        metadata.put(SERVICE_DESCRIPTION, "Test client: " + clientNumber);

        return createBasicTestInstance(metadata);
    }

    private ServiceInstance createBasicTestInstance() {
        return createBasicTestInstance(InstanceInfo.InstanceStatus.UP, Collections.emptyMap());
    }

    private ServiceInstance createBasicTestInstance(Map<String, String> metadata) {
        return createBasicTestInstance(InstanceInfo.InstanceStatus.UP, metadata);
    }

    private ServiceInstance createBasicTestInstance(InstanceInfo.InstanceStatus status) {
        return createBasicTestInstance(status, Collections.emptyMap());
    }

    private ServiceInstance createBasicTestInstance(InstanceInfo.InstanceStatus status, Map<String, String> metadata) {
        return new EurekaServiceInstance(InstanceInfo.Builder.newBuilder()
                .setAppName(CLIENT_SERVICE_ID)
                .setInstanceId(CLIENT_INSTANCE_ID + Math.random())
                .setStatus(status)
                .setMetadata(metadata)
                .build());
    }

    private ServiceInstance createFullTestInstance() {
        ApiInfo apiInfo = ApiInfo.builder()
                .apiId(CLIENT_API_ID)
                .version(CLIENT_API_VERSION)
                .gatewayUrl(CLIENT_API_GW_URL)
                .isDefaultApi(CLIENT_API_DEFAULT)
                .swaggerUrl(CLIENT_API_SWAGGER_URL)
                .documentationUrl(CLIENT_API_DOC_URL)
                .build();
        Map<String, String> metadata = EurekaMetadataParser.generateMetadata(CLIENT_SERVICE_ID, apiInfo);
        metadata.put(SERVICE_TITLE, CLIENT_SERVICE_TITLE);
        metadata.put(SERVICE_DESCRIPTION, CLIENT_SERVICE_DESCRIPTION);
        metadata.put(ROUTES + ".ui-v1." + ROUTES_SERVICE_URL, "/");
        metadata.put(ROUTES + ".ui-v1." + ROUTES_GATEWAY_URL, CLIENT_ROUTE_UI);
        metadata.put(AUTHENTICATION_SCHEME, CLIENT_AUTHENTICATION_SCHEME.getScheme());
        metadata.put(AUTHENTICATION_APPLID, CLIENT_AUTHENTICATION_APPLID);
        metadata.put(CLIENT_CUSTOM_METADATA_KEY, CLIENT_CUSTOM_METADATA_VALUE);

        return new EurekaServiceInstance(InstanceInfo.Builder.newBuilder()
                .setAppName(CLIENT_SERVICE_ID)
                .setInstanceId(CLIENT_INSTANCE_ID + Math.random())
                .setHostName(CLIENT_HOSTNAME)
                .setIPAddr(CLIENT_IP)
                .enablePort(InstanceInfo.PortType.SECURE, true)
                .setSecurePort(CLIENT_PORT)
                .setHomePageUrl(null, CLIENT_HOMEPAGE)
                .setHealthCheckUrls(CLIENT_RELATIVE_HEALTH_URL, null, null)
                .setStatusPageUrl(null, CLIENT_STATUS_URL)
                .setMetadata(metadata)
                .build());
    }

    @Nested
    class Multitenancy {

        private static final InstanceInfo PRIMARY_WITH_METADATA = InstanceInfo.Builder.newBuilder()
            .setInstanceId("primary-with-metadata")
            .setAppName("primary-with-metadata")
            .setMetadata(Collections.singletonMap(EurekaMetadataDefinition.REGISTRATION_TYPE, RegistrationType.PRIMARY.getValue()))
            .build();
        private static final InstanceInfo PRIMARY_WITHOUT_METADATA = InstanceInfo.Builder.newBuilder()
            .setInstanceId("primary-without-metadata")
            .setAppName("primary-without-metadata")
            .build();
        private static final InstanceInfo ADDITIONAL = InstanceInfo.Builder.newBuilder()
            .setInstanceId("additional")
            .setAppName("additional")
            .setMetadata(Collections.singletonMap(EurekaMetadataDefinition.REGISTRATION_TYPE, RegistrationType.ADDITIONAL.getValue()))
            .build();

        @Test
        void givenApplication_whenGetPrimaryInstances_thenAdditionalWereRemoved() {
            Application application = new Application("test");
            application.setName("test");
            application.addInstance(PRIMARY_WITH_METADATA);
            application.addInstance(PRIMARY_WITHOUT_METADATA);
            application.addInstance(ADDITIONAL);

            List<InstanceInfo> instances = ServicesInfoService.getPrimaryInstances(application);
            assertEquals(2, instances.size());
            assertSame(PRIMARY_WITH_METADATA, instances.get(0));
            assertSame(PRIMARY_WITHOUT_METADATA, instances.get(1));
        }

    }

}
