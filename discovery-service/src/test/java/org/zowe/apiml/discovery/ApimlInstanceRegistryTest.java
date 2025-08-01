/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.discovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.resources.ServerCodecs;
import com.netflix.eureka.transport.EurekaServerHttpClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.netflix.eureka.server.InstanceRegistryProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.zowe.apiml.discovery.config.EurekaConfig;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApimlInstanceRegistryTest {

    private ApimlInstanceRegistry apimlInstanceRegistry;

    @Mock private EurekaClientConfig clientConfig;
    @Mock private ServerCodecs serverCodecs;
    @Mock private EurekaClient eurekaClient; // DiscoveryClient?
    @Mock private EurekaServerHttpClientFactory eurekaServerHttpClientFactory;
    @Mock private InstanceRegistryProperties instanceRegistryProperties;
    @Mock private ApplicationContext appCntx;
    private InstanceInfo standardInstance;

    private EurekaServerConfig serverConfig;

    @BeforeEach
    void setUp() {
        standardInstance = getStandardInstance();
        serverConfig = new DefaultEurekaServerConfig();

        apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
            serverConfig,
            clientConfig,
            serverCodecs,
            eurekaClient,
            eurekaServerHttpClientFactory,
            instanceRegistryProperties,
            appCntx,
            new EurekaConfig.Tuple("service*,hello")));

        MethodHandle methodHandle = mock(MethodHandle.class);

        ReflectionTestUtils.setField(apimlInstanceRegistry, "handleRegistrationMethod", methodHandle);
        ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
        ReflectionTestUtils.setField(apimlInstanceRegistry, "register3ArgsMethodHandle", methodHandle);
        ReflectionTestUtils.setField(apimlInstanceRegistry, "handleCancellationMethod", methodHandle);
    }

    @Nested
    class GivenReplacerTuple {
        @Nested
        class WhenChangeServiceId {
            @Test
            void thenChangeServicePrefix() {
                InstanceInfo info = apimlInstanceRegistry.changeServiceId(standardInstance);
                assertEquals("helloclient", info.getInstanceId());
                assertEquals("HELLOCLIENT", info.getAppName());
                assertEquals("helloclient", info.getVIPAddress());
                assertEquals("HELLOCLIENT", info.getAppGroupName());
                assertEquals("192.168.0.1", info.getIPAddr());
                assertEquals("localhost", info.getHostName());
                assertEquals(9090, info.getSecurePort());
                assertEquals("localhost", info.getSecureVipAddress());
            }
        }
    }
    private static Stream<Arguments> tuples() {
       return Stream.of(
           Arguments.of("service*,hello", "helloclient"),
           Arguments.of("service,hello", "helloclient"),
           Arguments.of("service*,hello*", "helloclient"),
           Arguments.of("service*,service", "serviceclient"),
           Arguments.of("service*", "serviceclient"),
           Arguments.of(",service", "serviceclient"),
           Arguments.of("service,", "serviceclient"),
           Arguments.of(null, "serviceclient"),
           Arguments.of("different*,hello", "serviceclient")
       );
    }

    @ParameterizedTest
    @MethodSource("tuples")
    void thenShouldRegister(String tuple, String expectedServiceIdInResult) {
        apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
            serverConfig,
            clientConfig,
            serverCodecs,
            eurekaClient,
            eurekaServerHttpClientFactory,
            instanceRegistryProperties,
            appCntx,
            new EurekaConfig.Tuple(tuple)));
        MethodHandle methodHandle = mock(MethodHandle.class);
        ReflectionTestUtils.setField(apimlInstanceRegistry,"register2ArgsMethodHandle", methodHandle);
        ReflectionTestUtils.setField(apimlInstanceRegistry,"handleRegistrationMethod", methodHandle);
        apimlInstanceRegistry.register(standardInstance, false);
        assertEquals(expectedServiceIdInResult, standardInstance.getInstanceId());
    }

    @ParameterizedTest
    @MethodSource("tuples")
    void thenShouldRegisterWithSecondMethod(String tuple, String expectedServiceIdInResult) {
        apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
            serverConfig,
            clientConfig,
            serverCodecs,
            eurekaClient,
            eurekaServerHttpClientFactory,
            instanceRegistryProperties,
            appCntx,
            new EurekaConfig.Tuple(tuple)));
        MethodHandle methodHandle = mock(MethodHandle.class);
        ReflectionTestUtils.setField(apimlInstanceRegistry,"register3ArgsMethodHandle",methodHandle);
        ReflectionTestUtils.setField(apimlInstanceRegistry,"handleRegistrationMethod",methodHandle);
        apimlInstanceRegistry.register(standardInstance, 1, false);
        assertEquals(expectedServiceIdInResult, standardInstance.getInstanceId());
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenRegistrationMethodsFails {

        @ParameterizedTest
        @MethodSource("exceptions")
        void thenFirstMethodThrowsIllegalException(String tuple, Exception exception) throws Throwable {
            apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
                serverConfig,
                clientConfig,
                serverCodecs,
                eurekaClient,
                eurekaServerHttpClientFactory,
                instanceRegistryProperties,
                appCntx,
                new EurekaConfig.Tuple(tuple)));
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(exception);
            assertThrows(IllegalArgumentException.class, () -> {
                apimlInstanceRegistry.register(standardInstance, false);
            });
        }

        @Test
        void thenFirstMethodThrowRuntimeException() throws Throwable {
            apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
                serverConfig,
                clientConfig,
                serverCodecs,
                eurekaClient,
                eurekaServerHttpClientFactory,
                instanceRegistryProperties,
                appCntx,
                new EurekaConfig.Tuple("service*,hello")));
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "register2ArgsMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(new RuntimeException());
            assertThrows(RuntimeException.class, () -> {
                apimlInstanceRegistry.register(standardInstance, false);
            });
        }

        @ParameterizedTest
        @MethodSource("exceptions")
        void thenSecondMethodThrowsIllegalException(String tuple, Exception exception) throws Throwable {
            apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
                serverConfig,
                clientConfig,
                serverCodecs,
                eurekaClient,
                eurekaServerHttpClientFactory,
                instanceRegistryProperties,
                appCntx,
                new EurekaConfig.Tuple(tuple)));
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "register3ArgsMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(exception);
            assertThrows(IllegalArgumentException.class, () -> {
                apimlInstanceRegistry.register(standardInstance, 1, false);
            });
        }

        @Test
        void thenSecondMethodThrowRuntimeException() throws Throwable {
            apimlInstanceRegistry = spy(new ApimlInstanceRegistry(
                serverConfig,
                clientConfig,
                serverCodecs,
                eurekaClient,
                eurekaServerHttpClientFactory,
                instanceRegistryProperties,
                appCntx,
                new EurekaConfig.Tuple("service*,hello")));
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "register3ArgsMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any())).thenThrow(new RuntimeException());
            assertThrows(RuntimeException.class, () -> {
                apimlInstanceRegistry.register(standardInstance, 1, false);
            });
        }

        private Stream<Arguments> exceptions() {
            return Stream.of(
                Arguments.of("service*,hello", new WrongMethodTypeException()),
                Arguments.of("service*,hello", new Exception(new Throwable()))
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenResolveInstanceRewrittenFails {
        @ParameterizedTest
        @MethodSource("exceptions")
        void thenThrowIllegalArgumentException(Exception exception) throws Throwable {
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "handlerResolveInstanceLeaseDurationMethod", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any())).thenThrow(exception);
            assertThrows(IllegalArgumentException.class, () -> {
                apimlInstanceRegistry.resolveInstanceLeaseDurationRewritten(standardInstance);
            });
        }

        @Test
        void thenThrowRuntimeException() throws Throwable {
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "handlerResolveInstanceLeaseDurationMethod", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any())).thenThrow(new RuntimeException());
            assertThrows(RuntimeException.class, () -> {
                apimlInstanceRegistry.resolveInstanceLeaseDurationRewritten(standardInstance);
            });
        }

        private Stream<Arguments> exceptions() {
            return Stream.of(
                Arguments.of(new WrongMethodTypeException()),
                Arguments.of(new Exception(new Throwable()))
            );
        }
    }

    @Nested
    class WhenStaticallyRegistration {

        @Test
        @SuppressWarnings("unchecked")
        void givenStaticRegistration_thenSuccessful() throws Throwable {
            var methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "replicateToPeersMethodHandle", methodHandle);
            var currentStaticIds = (Set<String>) ReflectionTestUtils.getField(apimlInstanceRegistry, "staticRegistrationIds");
            assertTrue(currentStaticIds.isEmpty());

            var registry = mock(ConcurrentHashMap.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "registry", registry);

            Map<String, Lease<InstanceInfo>> leaseMap = new HashMap<>();
            when(registry.get(anyString())).thenReturn(leaseMap);
            doReturn(new Object()).when(methodHandle).invokeWithArguments(any(), any(), any(), any(), any(), any(), any());

            apimlInstanceRegistry.registerStatically(standardInstance, false, true);

            assertFalse(currentStaticIds.isEmpty());
            assertFalse(leaseMap.isEmpty());
        }

    }

    @Nested
    class HeartbeatPeerReplicate {

        @Test
        void givenPeerReplicaHeartbeat_thenSuccess() throws Throwable {
            var methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "replicateToPeersMethodHandle", methodHandle);
            var instance = mock(InstanceInfo.class);
            doReturn(new Object()).when(methodHandle).invokeWithArguments(any(), any(), any(), any(), any(), any(), any());
            apimlInstanceRegistry.peerAwareHeartbeat(instance);

            verify(methodHandle, times(1)).invokeWithArguments(any(), any(), any(), any(), any(), any(), any());
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenCancelRegistration {

        @Test
        void thenIsSuccessful() throws Throwable {
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenReturn(true);
            apimlInstanceRegistry.register(standardInstance, false);
            verify(apimlInstanceRegistry, times(1)).changeServiceId(any());
            boolean isCancelled = apimlInstanceRegistry.cancel("HELLO", "hello", false);
            assertTrue(isCancelled);
        }

        @ParameterizedTest
        @MethodSource("exceptions")
        void thenThrowIllegalArgumentException(Exception exception) throws Throwable {
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(exception);
            assertThrows(IllegalArgumentException.class, () -> {
                apimlInstanceRegistry.cancel("HELLO", "hello", false);
            });
        }

        @Test
        void thenThrowRuntimeException() throws Throwable {
            MethodHandle methodHandle = mock(MethodHandle.class);
            ReflectionTestUtils.setField(apimlInstanceRegistry, "cancelMethodHandle", methodHandle);
            when(methodHandle.invokeWithArguments(any(), any(), any(), any())).thenThrow(new RuntimeException());
            assertThrows(RuntimeException.class, () -> {
                apimlInstanceRegistry.cancel("HELLO", "hello", false);
            });
        }

        private Stream<Arguments> exceptions() {
            return Stream.of(
                Arguments.of(new WrongMethodTypeException()),
                Arguments.of(new Exception(new Throwable()))
            );
        }
    }


    private InstanceInfo getStandardInstance() {

        return InstanceInfo.Builder.newBuilder()
            .setInstanceId("serviceclient")
            .setAppName("SERVICECLIENT")
            .setAppGroupName("SERVICECLIENT")
            .setIPAddr("192.168.0.1")
            .enablePort(InstanceInfo.PortType.SECURE, true)
            .setSecurePort(9090)
            .setHostName("localhost")
            .setSecureVIPAddress("localhost")
            .setVIPAddress("serviceclient")
            .setStatus(InstanceInfo.InstanceStatus.UP)
            .build();
    }
}
