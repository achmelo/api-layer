/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.util.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

import static java.lang.Integer.parseInt;
import static org.zowe.apiml.util.requests.Endpoints.ROUTED_SERVICE;

@Slf4j
public class ConfigReader {

    public static final boolean IS_MODULITH_ENABLED = Boolean.getBoolean("environment.modulith");

    private static final String PASSWORD = "password";
    private static String configurationFile;

    static {
        configurationFile = "environment-configuration" + System.getProperty("environment.config", "") + ".yml";
    }

    private static volatile EnvironmentConfiguration instance;

    public static EnvironmentConfiguration environmentConfiguration() {
        if (instance == null) {
            log.info("Actual configuration file: {}", configurationFile);
            synchronized (ConfigReader.class) {
                if (instance == null) {
                    final String configFileName = configurationFile;
                    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
                    File configFile = null;
                    try {
                        Path path = Paths.get(Objects.requireNonNull(classLoader.getResource(configFileName)).toURI());
                        configFile = path.toFile();
                    } catch (URISyntaxException e) {
                        log.error("Incorrect environment-configuration.yml location: " + e.getMessage(), e);
                        configFile = new File(Objects.requireNonNull(classLoader.getResource(configFileName)).getFile());
                    }
                    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                    EnvironmentConfiguration configuration;
                    try {
                        configuration = mapper.readValue(configFile, EnvironmentConfiguration.class);
                    } catch (IOException e) {
                        log.warn("Can't read service configuration from resource file, using default: http://localhost:10010", e);
                        Credentials credentials = new Credentials("user", "user");
                        GatewayServiceConfiguration gatewayServiceConfiguration
                            = new GatewayServiceConfiguration("https", "localhost", null, 10010, 10010, 1, "10010", ROUTED_SERVICE, 20);
                        CentralGatewayServiceConfiguration centralGatewayServiceConfiguration = new CentralGatewayServiceConfiguration("https", "localhost", 10010);
                        ZaasConfiguration zaasConfiguration = new ZaasConfiguration("https", "localhost", 10023, 1);
                        DiscoveryServiceConfiguration discoveryServiceConfiguration = new DiscoveryServiceConfiguration("https", "eureka", "password", "localhost","localhost", 10011,10021, 1);
                        DiscoverableClientConfiguration discoverableClientConfiguration = new DiscoverableClientConfiguration("https", "ZOWEAPPL", "localhost", 10012, 1);

                        TlsConfiguration tlsConfiguration = TlsConfiguration.builder()
                            .keyAlias("localhost")
                            .keyPassword(PASSWORD.toCharArray())
                            .keyStoreType("PKCS12")
                            .keyStore("../keystore/localhost/localhost.keystore.p12")
                            .keyStorePassword(PASSWORD.toCharArray())
                            .trustStoreType("PKCS12")
                            .trustStore("../keystore/localhost/localhost.truststore.p12")
                            .trustStorePassword(PASSWORD.toCharArray())
                            .build();

                        AuxiliaryUserList auxiliaryUserList = new AuxiliaryUserList("user,password");

                        ZosmfServiceConfiguration zosmfServiceConfiguration = new ZosmfServiceConfiguration("https", "zosmf.acme.com", 1443, "ibmzosmf", "");
                        IDPConfiguration idpConfiguration = new IDPConfiguration("https://okta-dev.com", "user", "user", "alt_user", "alt_user");
                        SafIdtConfiguration safIdtConfiguration = new SafIdtConfiguration(true);
                        OidcConfiguration oidcConfiguration = new OidcConfiguration("");

                        configuration = new EnvironmentConfiguration(
                            credentials,
                            gatewayServiceConfiguration,
                            centralGatewayServiceConfiguration,
                            zaasConfiguration,
                            discoveryServiceConfiguration,
                            discoverableClientConfiguration,
                            new ApiCatalogServiceConfiguration(),
                            new ApiCatalogServiceConfiguration(),
                            new CachingServiceConfiguration(),
                            tlsConfiguration,
                            zosmfServiceConfiguration,
                            auxiliaryUserList,
                            null,
                            idpConfiguration,
                            safIdtConfiguration,
                            oidcConfiguration
                        );
                    }

                    configuration.getCredentials().setUser(System.getProperty("credentials.user", configuration.getCredentials().getUser()));
                    configuration.getCredentials().setPassword(System.getProperty("credentials.password", StringUtils.isEmpty(configuration.getCredentials().getPassword()) ? "" : new String(configuration.getCredentials().getPassword())));

                    configuration.getGatewayServiceConfiguration().setScheme(System.getProperty("gateway.scheme", configuration.getGatewayServiceConfiguration().getScheme()));
                    configuration.getGatewayServiceConfiguration().setHost(System.getProperty("gateway.host", configuration.getGatewayServiceConfiguration().getHost()));
                    configuration.getGatewayServiceConfiguration().setDvipaHost(System.getProperty("gateway.dvipaHost", configuration.getGatewayServiceConfiguration().getDvipaHost()));
                    configuration.getGatewayServiceConfiguration().setPort(parseInt(System.getProperty("gateway.port", String.valueOf(configuration.getGatewayServiceConfiguration().getPort()))));
                    configuration.getGatewayServiceConfiguration().setExternalPort(parseInt(System.getProperty("gateway.externalPort", String.valueOf(configuration.getGatewayServiceConfiguration().getExternalPort()))));
                    configuration.getGatewayServiceConfiguration().setInstances(parseInt(System.getProperty("gateway.instances", String.valueOf(configuration.getGatewayServiceConfiguration().getInstances()))));
                    configuration.getGatewayServiceConfiguration().setServicesEndpoint(System.getProperty("gateway.servicesEndpoint", configuration.getGatewayServiceConfiguration().getServicesEndpoint()));
                    configuration.getGatewayServiceConfiguration().setBucketCapacity(parseInt(System.getProperty("gateway.bucketCapacity", String.valueOf(configuration.getGatewayServiceConfiguration().getBucketCapacity()))));

                    CentralGatewayServiceConfiguration config = configuration.getCentralGatewayServiceConfiguration();
                    Optional.ofNullable(config).ifPresent(c -> {
                            c.setScheme(System.getProperty("centralgateway.scheme", config.getScheme()));
                            c.setHost(System.getProperty("centralgateway.host", config.getHost()));
                            c.setPort(parseInt(System.getProperty("centralgateway.port", String.valueOf(config.getPort()))));
                        }
                    );

                    if (!IS_MODULITH_ENABLED) {
                        configuration.getZaasConfiguration().setScheme(System.getProperty("zaas.scheme", configuration.getZaasConfiguration().getScheme()));
                        configuration.getZaasConfiguration().setHost(System.getProperty("zaas.host", configuration.getZaasConfiguration().getHost()));
                        configuration.getZaasConfiguration().setPort(parseInt(System.getProperty("zaas.port", String.valueOf(configuration.getZaasConfiguration().getPort()))));
                    }
                    configuration.getDiscoveryServiceConfiguration().setScheme(System.getProperty("discovery.scheme", configuration.getDiscoveryServiceConfiguration().getScheme()));
                    configuration.getDiscoveryServiceConfiguration().setUser(System.getProperty("discovery.user", configuration.getDiscoveryServiceConfiguration().getUser()));
                    configuration.getDiscoveryServiceConfiguration().setPassword(System.getProperty("discovery.password", configuration.getDiscoveryServiceConfiguration().getPassword()));
                    configuration.getDiscoveryServiceConfiguration().setHost(System.getProperty("discovery.host", configuration.getDiscoveryServiceConfiguration().getHost()));
                    configuration.getDiscoveryServiceConfiguration().setAdditionalHost(System.getProperty("discovery.additionalHost", configuration.getDiscoveryServiceConfiguration().getAdditionalHost()));
                    configuration.getDiscoveryServiceConfiguration().setPort(parseInt(System.getProperty("discovery.port", String.valueOf(configuration.getDiscoveryServiceConfiguration().getPort()))));
                    configuration.getDiscoveryServiceConfiguration().setAdditionalPort(parseInt(System.getProperty("discovery.additionalPort", String.valueOf(configuration.getDiscoveryServiceConfiguration().getAdditionalPort()))));
                    configuration.getDiscoveryServiceConfiguration().setInstances(parseInt(System.getProperty("discovery.instances", String.valueOf(configuration.getDiscoveryServiceConfiguration().getInstances()))));

                    configuration.getAuxiliaryUserList().setValue(System.getProperty("auxiliaryUserList.value", String.valueOf(configuration.getAuxiliaryUserList().getValue())));

                    configuration.getApiCatalogServiceConfiguration().setUrl(System.getProperty("apicatalog.url", configuration.getApiCatalogServiceConfiguration().getUrl()));
                    configuration.getApiCatalogServiceConfiguration().setHost(System.getProperty("apicatalog.host", configuration.getApiCatalogServiceConfiguration().getHost()));
                    configuration.getApiCatalogServiceConfiguration().setInstances(parseInt(System.getProperty("apicatalog.instances", String.valueOf(configuration.getApiCatalogServiceConfiguration().getInstances()))));
                    configuration.getApiCatalogServiceConfiguration().setScheme(System.getProperty("apicatalog.scheme", configuration.getApiCatalogServiceConfiguration().getScheme()));
                    configuration.getApiCatalogServiceConfiguration().setPort(parseInt(System.getProperty("apicatalog.port", String.valueOf(configuration.getApiCatalogServiceConfiguration().getPort()))));

                    configuration.getDiscoverableClientConfiguration().setApplId(System.getProperty("discoverableclient.applId", configuration.getDiscoverableClientConfiguration().getApplId()));
                    configuration.getDiscoverableClientConfiguration().setHost(System.getProperty("discoverableclient.host", configuration.getDiscoverableClientConfiguration().getHost()));
                    configuration.getDiscoverableClientConfiguration().setInstances(parseInt(System.getProperty("discoverableclient.instances", String.valueOf(configuration.getDiscoverableClientConfiguration().getInstances()))));
                    configuration.getDiscoverableClientConfiguration().setScheme(System.getProperty("discoverableclient.scheme", configuration.getDiscoverableClientConfiguration().getScheme()));
                    configuration.getDiscoverableClientConfiguration().setPort(parseInt(System.getProperty("discoverableclient.port", String.valueOf(configuration.getDiscoverableClientConfiguration().getPort()))));

                    configuration.getCachingServiceConfiguration().setUrl(System.getProperty("caching.url", configuration.getCachingServiceConfiguration().getUrl()));

                    configuration.getIdpConfiguration().setUser(System.getProperty("oidc.test.user", configuration.getIdpConfiguration().getUser()));
                    configuration.getIdpConfiguration().setPassword(System.getProperty("oidc.test.pass", configuration.getIdpConfiguration().getPassword()));
                    configuration.getIdpConfiguration().setAlternateUser(System.getProperty("oidc.test.alt_user", configuration.getIdpConfiguration().getAlternateUser()));
                    configuration.getIdpConfiguration().setAlternatePassword(System.getProperty("oidc.test.alt_pass", configuration.getIdpConfiguration().getAlternatePassword()));
                    configuration.getIdpConfiguration().setHost(System.getProperty("idpConfiguration.host", configuration.getIdpConfiguration().getHost()));

                    configuration.getSafIdtConfiguration().setEnabled(Boolean.parseBoolean(System.getProperty("safidt.enabled", String.valueOf(configuration.getSafIdtConfiguration().isEnabled()))));

                    configuration.getOidcConfiguration().setClientId(System.getProperty("okta.client.id", String.valueOf(configuration.getOidcConfiguration().getClientId())));

                    setZosmfConfigurationFromSystemProperties(configuration);
                    setTlsConfigurationFromSystemProperties(configuration);

                    instance = configuration;
                }
            }
            log.info("Actual configuration: {}", instance);

            verifyTlsPaths(instance);
        }

        return instance;
    }

    private static void verifyTlsPaths(EnvironmentConfiguration env) {
        TlsConfiguration tlsConfig = env.getTlsConfiguration();
        if (!new File(tlsConfig.getKeyStore()).exists()) throw new RuntimeException(String.format("%s does not exist", tlsConfig.getKeyStore()));
        if (!new File(tlsConfig.getClientKeystore()).exists()) throw new RuntimeException(String.format("%s does not exist", tlsConfig.getClientKeystore()));
        if (!new File(tlsConfig.getTrustStore()).exists()) throw new RuntimeException(String.format("%s does not exist", tlsConfig.getTrustStore()));
    }

    private static void setZosmfConfigurationFromSystemProperties(EnvironmentConfiguration configuration) {
        ZosmfServiceConfiguration zosmfConfiguration = configuration.getZosmfServiceConfiguration();
        zosmfConfiguration.setHost(System.getProperty("zosmf.host", zosmfConfiguration.getHost()));
        String port = System.getProperty("zosmf.port", String.valueOf(zosmfConfiguration.getPort()));
        zosmfConfiguration.setPort(parseInt(port));
        zosmfConfiguration.setScheme(System.getProperty("zosmf.scheme", zosmfConfiguration.getScheme()));
        zosmfConfiguration.setServiceId(System.getProperty("zosmf.serviceId", zosmfConfiguration.getServiceId()));
    }

    private static char[] getSystemPropertyCharArray(String name, char[] defaultValue) {
        String value = System.getProperty(name);
        if (StringUtils.isEmpty(value)) return defaultValue;
        return value.toCharArray();
    }

    private static void setTlsConfigurationFromSystemProperties(EnvironmentConfiguration configuration) {
        TlsConfiguration tlsConfiguration = configuration.getTlsConfiguration();
        tlsConfiguration.setKeyAlias(System.getProperty("tlsConfiguration.keyAlias", tlsConfiguration.getKeyAlias()));
        tlsConfiguration.setKeyPassword(getSystemPropertyCharArray("tlsConfiguration.keyPassword", tlsConfiguration.getKeyPassword()));
        tlsConfiguration.setClientKeystore(System.getProperty("tlsConfiguration.clientKeyStore", tlsConfiguration.getClientKeystore()));
        tlsConfiguration.setClientCN(System.getProperty("tlsConfiguration.clientCN", tlsConfiguration.getClientCN()));
        tlsConfiguration.setKeyStore(System.getProperty("tlsConfiguration.keyStore", tlsConfiguration.getKeyStore()));
        tlsConfiguration.setKeyStoreType(System.getProperty("tlsConfiguration.keyStoreType", tlsConfiguration.getKeyStoreType()));
        tlsConfiguration.setKeyPassword(getSystemPropertyCharArray("tlsConfiguration.keyStorePassword", tlsConfiguration.getKeyStorePassword()));
        tlsConfiguration.setTrustStoreType(System.getProperty("tlsConfiguration.trustStoreType", tlsConfiguration.getTrustStoreType()));
        tlsConfiguration.setTrustStore(System.getProperty("tlsConfiguration.trustStore", tlsConfiguration.getTrustStore()));
        tlsConfiguration.setTrustStorePassword(getSystemPropertyCharArray("tlsConfiguration.trustStorePassword", tlsConfiguration.getTrustStorePassword()));
    }
}
