/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.zowe.apiml.filter.AttlsFilter;
import org.zowe.apiml.filter.SecureConnectionFilter;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.config.CertificateAuthenticationProvider;
import org.zowe.apiml.security.common.config.HandlerInitializer;
import org.zowe.apiml.security.common.config.SimpleUserDetailService;
import org.zowe.apiml.security.common.content.BasicContentFilter;
import org.zowe.apiml.security.common.content.BearerContentFilter;
import org.zowe.apiml.security.common.content.CookieContentFilter;
import org.zowe.apiml.security.common.error.AuthExceptionHandler;
import org.zowe.apiml.security.common.filter.CategorizeCertsFilter;
import org.zowe.apiml.security.common.filter.StoreAccessTokenInfoFilter;
import org.zowe.apiml.security.common.handler.FailedAccessTokenHandler;
import org.zowe.apiml.security.common.handler.FailedAuthenticationHandler;
import org.zowe.apiml.security.common.handler.SuccessfulAccessTokenHandler;
import org.zowe.apiml.security.common.login.*;
import org.zowe.apiml.security.common.verify.CertificateValidator;
import org.zowe.apiml.zaas.controllers.AuthController;
import org.zowe.apiml.zaas.controllers.SafResourceAccessController;
import org.zowe.apiml.zaas.security.login.x509.X509AuthenticationProvider;
import org.zowe.apiml.zaas.security.query.QueryFilter;
import org.zowe.apiml.zaas.security.query.SuccessfulQueryHandler;
import org.zowe.apiml.zaas.security.query.TokenAuthenticationProvider;
import org.zowe.apiml.zaas.security.refresh.SuccessfulRefreshHandler;
import org.zowe.apiml.zaas.security.service.AuthenticationService;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSourceService;
import org.zowe.apiml.zaas.security.ticket.SuccessfulTicketHandler;
import org.zowe.apiml.zaas.zaas.ExtractAuthSourceFilter;
import org.zowe.apiml.zaas.zaas.ZaasAuthenticationFilter;

import java.util.Collections;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Main configuration place for ZAAS endpoint security
 * <p>
 * Security is configured with separate filterchains per groups of endpoints
 * The main theme is to keep the filterchains independent and isolated
 * Gives more control over the behavior of individual filter chain
 * This makes the security filters simpler as they don't have to be smart
 * Also separates the x509 filters per filterchain for more control
 * <p>
 * Authentication providers are initialized per filterchain's needs. No unused auth providers on chains.
 */

@ConditionalOnProperty(name = "apiml.security.filterChainConfiguration", havingValue = "new", matchIfMissing = false)
@ConditionalOnMissingBean(name = "modulithConfig")
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class NewSecurityConfiguration {

    private final ObjectMapper securityObjectMapper;
    private final AuthenticationService authenticationService;
    private final AuthConfigurationProperties authConfigurationProperties;
    private final HandlerInitializer handlerInitializer;

    private final SuccessfulAccessTokenHandler successfulAuthAccessTokenHandler;
    private final SuccessfulQueryHandler successfulQueryHandler;
    private final SuccessfulTicketHandler successfulTicketHandler;
    private final SuccessfulRefreshHandler successfulRefreshHandler;
    private final FailedAccessTokenHandler failedAccessTokenHandler;
    @Qualifier("publicKeyCertificatesBase64")
    private final Set<String> publicKeyCertificatesBase64;
    private final CertificateValidator certificateValidator;
    private final X509AuthenticationProvider x509AuthenticationProvider;
    private final AuthSourceService authSourceService;
    private final AuthExceptionHandler authExceptionHandler;

    @Value("${server.attls.enabled:false}")
    private boolean isAttlsEnabled;

    @Value("${apiml.health.protected:true}")
    private boolean isHealthEndpointProtected;

    /**
     * Login and Logout endpoints
     * <p>
     * logout filter matches for logout request and handles logout
     * apimlX509filter sifts through certs for authentication
     * login filter authenticates credentials
     * x509AuthenticationFilter authenticates certificate from apimlX509Filter
     * shouldAlreadyBeAuthenticated stops processing of request and ends the chain
     */
    @Configuration
    @RequiredArgsConstructor
    @Order(1)
    class AuthenticationFunctionality {

        private final CompoundAuthProvider compoundAuthProvider;

        @Bean
        public SecurityFilterChain authenticationFunctionalityFilterChain(HttpSecurity http) throws Exception {
            baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers( // no http method to catch all attempts to login and handle them here. Otherwise it falls to default filterchain and tries to route the calls, which doesnt make sense
                authConfigurationProperties.getZaasLoginEndpoint(),
                authConfigurationProperties.getZaasLogoutEndpoint()
            )))
                .authorizeHttpRequests(requests -> requests
                        .anyRequest().permitAll())

                .logout(logout -> logout
                    .logoutRequestMatcher(new AntPathRequestMatcher(
                        authConfigurationProperties.getZaasLogoutEndpoint(), HttpMethod.POST.name()
                    ))
                    .addLogoutHandler(logoutHandler())
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))

                .authenticationProvider(compoundAuthProvider) // for authenticating credentials
                .authenticationProvider(new CertificateAuthenticationProvider()) // this is a dummy auth provider so the x509 prefiltering doesn't fail with nullpointer (no auth provider) or No AuthenticationProvider found for org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
                .with(new CustomSecurityFilters(), Customizer.withDefaults());

            return http.build();
        }

        private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
            @Override
            public void configure(HttpSecurity http) {
                AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                //drive filter order this way
                http.addFilterBefore(new CategorizeCertsFilter(publicKeyCertificatesBase64, certificateValidator), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterBefore(loginFilter("/**", authenticationManager), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterAfter(x509ForwardingAwareAuthenticationFilter("/**"), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class) // this filter consumes certificates from custom attribute and maps them to credentials and authenticates them
                    .addFilterAfter(new ShouldBeAlreadyAuthenticatedFilter("/**", handlerInitializer.getAuthenticationFailureHandler()), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class); // this filter stops processing of filter chain because there is nothing on /auth/login endpoint
            }

            private LoginFilter loginFilter(String loginEndpoint, AuthenticationManager authenticationManager) {
                return new LoginFilter(
                    loginEndpoint,
                    handlerInitializer.getSuccessfulLoginHandler(),
                    handlerInitializer.getAuthenticationFailureHandler(),
                    securityObjectMapper,
                    authenticationManager,
                    handlerInitializer.getResourceAccessExceptionHandler());
            }

            private X509ForwardingAwareAuthenticationFilter x509ForwardingAwareAuthenticationFilter(String loginEndpoint) {
                return new X509ForwardingAwareAuthenticationFilter(loginEndpoint,
                    handlerInitializer.getSuccessfulLoginHandler(),
                    x509AuthenticationProvider);
            }
        }

        private LogoutHandler logoutHandler() {
            FailedAuthenticationHandler failure = handlerInitializer.getAuthenticationFailureHandler();
            return new JWTLogoutHandler(authenticationService, failure);
        }
    }

    /**
     * Secures endpoints:
     *   - /auth/access-token/generate
     * <p>
     * Requires authentication by a client certificate forwarded form Gateway or basic authentication, supports credentials in header and body.
     * The request is fulfilled by the filter chain only, there is no controller to handle it.
     * Order of custom filters:
     *   - CategorizeCertsFilter - checks for forwarded client certificate and put it into a custom request attribute
     *   - StoreAccessTokenInfoFilter - extracts access token filter from request to a custom attribute
     *   - LoginFilter - attempts to log in a user using basic authentication credentials, generates access token and stops the chain on success, reply with the token
     *   - X509ForwardingAwareAuthenticationFilter - attempts to log in a user using forwarded client certificate, generates access token and stops the chain on success, reply with the token
     *   - ShouldBeAlreadyAuthenticatedFilter - stops filter chain if none of the authentications was successful
     *
     */
    @Configuration
    @RequiredArgsConstructor
    @Order(7)
    class AccessToken {
        private final CompoundAuthProvider compoundAuthProvider;
        private final AuthenticationProvider tokenAuthenticationProvider;

        @Bean
        public SecurityFilterChain accessTokenFilterChain(HttpSecurity http) throws Exception {
            baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers( // no http method to catch all attempts to login and handle them here. Otherwise it falls to default filterchain and tries to route the calls, which doesnt make sense
                authConfigurationProperties.getZaasAccessTokenEndpoint()
            )))
                .authorizeHttpRequests(requests -> requests
                    .anyRequest().permitAll())
                .authenticationProvider(compoundAuthProvider) // for authenticating credentials
                .authenticationProvider(tokenAuthenticationProvider)
                .authenticationProvider(new CertificateAuthenticationProvider()) // this is a dummy auth provider so the x509 prefiltering doesn't fail with nullpointer (no auth provider) or No AuthenticationProvider found for org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
                .with(new CustomSecurityFilters(), Customizer.withDefaults());

            return http.build();
        }

        private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
            @Override
            public void configure(HttpSecurity http) {
                AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                //drive filter order this way
                http.addFilterBefore(new CategorizeCertsFilter(publicKeyCertificatesBase64, certificateValidator), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterBefore(new StoreAccessTokenInfoFilter(handlerInitializer.getUnAuthorizedHandler().getHandler()), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterBefore(accessTokenFilter("/**", authenticationManager), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterAfter(x509ForwardingAwareAuthenticationFilter("/**"), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class) // this filter consumes certificates from custom attribute and maps them to credentials and authenticates them
                    .addFilterAfter(new ShouldBeAlreadyAuthenticatedFilter("/**", handlerInitializer.getAuthenticationFailureHandler()), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class); // this filter stops processing of filter chain because there is nothing on /auth/access-token/generate endpoint
            }

            private LoginFilter accessTokenFilter(String endpoint, AuthenticationManager authenticationManager) {
                return new LoginFilter(
                    endpoint,
                    successfulAuthAccessTokenHandler,
                    failedAccessTokenHandler,
                    securityObjectMapper,
                    authenticationManager,
                    handlerInitializer.getResourceAccessExceptionHandler());
            }

            private X509ForwardingAwareAuthenticationFilter x509ForwardingAwareAuthenticationFilter(String loginEndpoint) {
                return new X509ForwardingAwareAuthenticationFilter(loginEndpoint,
                    successfulAuthAccessTokenHandler,
                    x509AuthenticationProvider);
            }
        }

        /**
         * Secures endpoints:
         *  - /auth/access-token/revoke/tokens/**
         *  - /auth/access-token/evict
         *
         * Requires authentication by a client certificate forwarded form Gateway or basic authentication, supports only credentials in header.
         * Order of custom filters:
         *  - CategorizeCertsFilter - checks for forwarded client certificate and put it into a custom request attribute
         *  - X509AuthAwareFilter - attempts to log in using a user using forwarded client certificate, replaces pre-authentication in security context by the authentication result
         *  - BasicAuthFilter - attempts to log in a user using credentials from basic authentication header
         */
        @Configuration
        @RequiredArgsConstructor
        @Order(8)
        class AuthenticationProtectedEndpoints {

            private final CompoundAuthProvider compoundAuthProvider;

            @Bean
            public SecurityFilterChain authProtectedEndpointsFilterChain(HttpSecurity http) throws Exception {
                baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers( // no http method to catch all attempts to login and handle them here. Otherwise it falls to default filterchain and tries to route the calls, which doesnt make sense
                        authConfigurationProperties.getZaasRevokeMultipleAccessTokens() + "/**",
                        authConfigurationProperties.getZaasEvictAccessTokensAndRules()
                )))
                    .authorizeHttpRequests(requests -> requests
                        .anyRequest().authenticated())
                    .authenticationProvider(compoundAuthProvider) // for authenticating credentials
                    .with(new CustomSecurityFilters(), Customizer.withDefaults());
                return http.build();
            }

            private class CustomSecurityFilters extends AbstractHttpConfigurer<AccessToken.CustomSecurityFilters, HttpSecurity> {
                @Override
                public void configure(HttpSecurity http) {
                    http.addFilterAfter(new CategorizeCertsFilter(publicKeyCertificatesBase64, certificateValidator), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                        .addFilterAfter(x509ForwardingAwareAuthenticationFilter(), CategorizeCertsFilter.class)
                        .addFilterAfter(loginFilter(http), X509AuthAwareFilter.class);
                }

                private NonCompulsoryAuthenticationProcessingFilter loginFilter(HttpSecurity http) {
                    AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                    return new BasicAuthFilter("/**",
                        handlerInitializer.getAuthenticationFailureHandler(),
                        securityObjectMapper,
                        authenticationManager,
                        handlerInitializer.getResourceAccessExceptionHandler());
                }

                private X509ForwardingAwareAuthenticationFilter x509ForwardingAwareAuthenticationFilter() {
                    return new X509AuthAwareFilter("/**",
                        handlerInitializer.getAuthenticationFailureHandler(),
                        x509AuthenticationProvider);
                }

            }

        }

        @Configuration
        @RequiredArgsConstructor
        @Order(9)
        class ZaasEndpoints {

            @Bean
            public SecurityFilterChain authZaasEndpointsFilterChain(HttpSecurity http) throws Exception {
                baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers( // no http method to catch all attempts to login and handle them here. Otherwise it falls to default filterchain and tries to route the calls, which doesnt make sense
                        "/zaas/scheme/**"
                )))
                    .authorizeHttpRequests(requests -> requests
                        .anyRequest().authenticated())
                    .x509(x509 -> x509.userDetailsService(x509UserDetailsService()))
                    .addFilterAfter(new CategorizeCertsFilter(publicKeyCertificatesBase64, certificateValidator), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterAfter(new ExtractAuthSourceFilter(authSourceService, authExceptionHandler), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                    .addFilterAfter(new ZaasAuthenticationFilter(authSourceService, authExceptionHandler), CategorizeCertsFilter.class);

                return http.build();
            }

        }


        /**
         * Query and Ticket and Refresh endpoints share single filter that handles auth with and without certificate. This logic is encapsulated in the queryFilter or ticketFilter.
         * Query endpoint does not require certificate to be present in RequestContext. It verifies JWT token.
         */
        @Configuration
        @RequiredArgsConstructor
        @Order(2)
        class Query {

            private final TokenAuthenticationProvider tokenAuthenticationProvider;

            @Bean
            public SecurityFilterChain queryFilterChain(HttpSecurity http) throws Exception {
                return baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers(
                        authConfigurationProperties.getZaasQueryEndpoint()
                    )))
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .authenticationProvider(tokenAuthenticationProvider)
                    .logout(AbstractHttpConfigurer::disable) // logout filter in this chain not needed
                    .with(new CustomSecurityFilters(), Customizer.withDefaults())
                    .build();
            }

            private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
                @Override
                public void configure(HttpSecurity http) {
                    AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                    http.addFilterBefore(queryFilter("/**", authenticationManager), UsernamePasswordAuthenticationFilter.class);
                }

                private QueryFilter queryFilter(String queryEndpoint, AuthenticationManager authenticationManager) {
                    return new QueryFilter(
                        queryEndpoint,
                        successfulQueryHandler,
                        handlerInitializer.getAuthenticationFailureHandler(),
                        authenticationService,
                        HttpMethod.GET,
                        false,
                        authenticationManager);
                }
            }
        }

        /**
         * Query and Ticket and Refresh endpoints share single filter that handles auth with and without certificate. This logic is encapsulated in the queryFilter or ticketFilter.
         * Ticket endpoint does require certificate to be present in RequestContext. It verifies the JWT token.
         */

        @Configuration
        @RequiredArgsConstructor
        @Order(3)
        class Ticket {

            private final AuthenticationProvider tokenAuthenticationProvider;

            @Bean
            public SecurityFilterChain ticketFilterChain(HttpSecurity http) throws Exception {
                return baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers(
                    authConfigurationProperties.getZaasTicketEndpoint()
                ))).authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .authenticationProvider(tokenAuthenticationProvider)
                    .logout(AbstractHttpConfigurer::disable) // logout filter in this chain not needed
                    .x509(x509 -> x509 //default x509 filter, authenticates trusted cert, ticketFilter(..) depends on this
                        .userDetailsService(new SimpleUserDetailService())
                    ).with(new CustomSecurityFilters(), withDefaults())
                    .build();
            }

            private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
                @Override
                public void configure(HttpSecurity http) {
                    AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                    http.addFilterBefore(ticketFilter("/**", authenticationManager), UsernamePasswordAuthenticationFilter.class);
                }

                private QueryFilter ticketFilter(String ticketEndpoint, AuthenticationManager authenticationManager) {
                    return new QueryFilter(
                        ticketEndpoint,
                        successfulTicketHandler,
                        handlerInitializer.getAuthenticationFailureHandler(),
                        authenticationService,
                        HttpMethod.POST,
                        true,
                        authenticationManager);
                }
            }
        }

        /**
         * Query and Ticket and Refresh endpoints share single filter that handles auth with and without certificate.
         * Refresh endpoint does require certificate to be present in RequestContext. It verifies the JWT token and based
         * on valid token and client certificate issues a new valid token
         */
        @Configuration
        @RequiredArgsConstructor
        @ConditionalOnProperty(name = "apiml.security.allowTokenRefresh", havingValue = "true")
        @Order(6)
        class Refresh {

            private final AuthenticationProvider tokenAuthenticationProvider;

            @Bean
            public SecurityFilterChain refreshFilterChain(HttpSecurity http) throws Exception {
                baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers(
                        authConfigurationProperties.getZaasRefreshEndpoint()
                ))).authorizeHttpRequests(requests -> requests
                        .anyRequest().authenticated())
                    .authenticationProvider(tokenAuthenticationProvider)
                    .logout(AbstractHttpConfigurer::disable) // logout filter in this chain not needed
                    .x509(x509 -> x509 //default x509 filter, authenticates trusted cert, refreshFilter(..) depends on this
                        .userDetailsService(new SimpleUserDetailService()))
                    .with(new CustomSecurityFilters(), Customizer.withDefaults());

                return http.build();
            }

            private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
                @Override
                public void configure(HttpSecurity http) {
                    AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                    http.addFilterBefore(refreshFilter("/**", authenticationManager), UsernamePasswordAuthenticationFilter.class);
                }

                private QueryFilter refreshFilter(String ticketEndpoint, AuthenticationManager authenticationManager) {
                    return new QueryFilter(
                        ticketEndpoint,
                        successfulRefreshHandler,
                        handlerInitializer.getAuthenticationFailureHandler(),
                        authenticationService,
                        HttpMethod.POST,
                        true,
                        authenticationManager);
                }
            }
        }

        /**
         * Endpoints which are protected by client certificate
         * Default Spring security x509 filter authenticates any trusted certificate
         */
        @Configuration
        @RequiredArgsConstructor
        @Order(4)
        class CertificateProtectedEndpoints {
            @Bean
            public SecurityFilterChain certificateEndpointsFilterChain(HttpSecurity http) throws Exception {
                return baseConfigure(http.securityMatchers(matchers -> matchers
                    .requestMatchers(AuthController.CONTROLLER_PATH + AuthController.INVALIDATE_PATH, AuthController.CONTROLLER_PATH + AuthController.DISTRIBUTE_PATH))
                ).authorizeHttpRequests(requests -> requests
                        .anyRequest().authenticated())
                    .logout(AbstractHttpConfigurer::disable) // logout filter in this chain not needed
                    .x509(x509 -> x509 // default x509 filter, authenticates trusted cert
                        .userDetailsService(new SimpleUserDetailService())).build();
            }
        }

        /**
         * Endpoints which require either authentication or client certificate
         * Filters for tokens and credentials are placed in front of certificate filter for precedence
         * Default Spring security x509 filter authenticates any trusted certificate
         */
        @Configuration
        @RequiredArgsConstructor
        @Order(5)
        class CertificateOrAuthProtectedEndpoints {

            private final CompoundAuthProvider compoundAuthProvider;
            private final AuthenticationProvider tokenAuthenticationProvider;

            @Bean
            public SecurityFilterChain certificateOrAuthEndpointsFilterChain(HttpSecurity http) throws Exception {
                baseConfigure(
                    http.securityMatchers(matchers -> matchers
                        .requestMatchers("/application/**")
                        .requestMatchers(HttpMethod.POST, SafResourceAccessController.FULL_CONTEXT_PATH)
                    )
                ).authorizeHttpRequests(requests -> requests
                        .anyRequest()
                        .authenticated()
                    )
                    .logout(AbstractHttpConfigurer::disable);  // logout filter in this chain not needed

                if (isAttlsEnabled) {
                    http
                        // filter out API ML certificate
                        .addFilterBefore(reversedCategorizeCertFilter(), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class);
                }

                return http.authenticationProvider(compoundAuthProvider) // for authenticating credentials
                    .authenticationProvider(tokenAuthenticationProvider) // for authenticating Tokens
                    .authenticationProvider(new CertificateAuthenticationProvider())
                    .with(new CustomSecurityFilters(), Customizer.withDefaults())
                    .build();
            }

            private class CustomSecurityFilters extends AbstractHttpConfigurer<CustomSecurityFilters, HttpSecurity> {
                @Override
                public void configure(HttpSecurity http) {
                    AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
                    // place the following filters before the x509 filter
                    http
                        .addFilterAfter(new CategorizeCertsFilter(publicKeyCertificatesBase64, certificateValidator), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                        .addFilterAfter(x509ForwardingAwareAuthenticationFilter(),  CategorizeCertsFilter.class) // this filter consumes certificates from custom attribute and maps them to credentials and authenticates them
                        .addFilterBefore(basicFilter(authenticationManager), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                        .addFilterBefore(cookieFilter(authenticationManager), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class)
                        .addFilterBefore(bearerContentFilter(authenticationManager), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class);
                }

                /**
                 * Processes basic authentication credentials and authenticates them
                 */
                private BasicContentFilter basicFilter(AuthenticationManager authenticationManager) {
                    return new BasicContentFilter(
                        authenticationManager,
                        handlerInitializer.getAuthenticationFailureHandler(),
                        handlerInitializer.getResourceAccessExceptionHandler(),
                        new String[] {"/"});
                }

                /**
                 * Processes token credentials stored in cookie and authenticates them
                 */
                private CookieContentFilter cookieFilter(AuthenticationManager authenticationManager) {
                    return new CookieContentFilter(
                        authenticationManager,
                        handlerInitializer.getAuthenticationFailureHandler(),
                        handlerInitializer.getResourceAccessExceptionHandler(),
                        authConfigurationProperties,
                        new String[] {"/"});
                }

                /**
                 * Secures content with a Bearer token
                 */
                private BearerContentFilter bearerContentFilter(AuthenticationManager authenticationManager) {
                    return new BearerContentFilter(
                        authenticationManager,
                        handlerInitializer.getAuthenticationFailureHandler(),
                        handlerInitializer.getResourceAccessExceptionHandler(),
                        new String[] {"/"});
                }

                private X509ForwardingAwareAuthenticationFilter x509ForwardingAwareAuthenticationFilter() {
                    return new X509AuthAwareFilter("/**",
                        handlerInitializer.getAuthenticationFailureHandler(),
                        x509AuthenticationProvider);
                }
            }

            private CategorizeCertsFilter reversedCategorizeCertFilter() {
                CategorizeCertsFilter out = new CategorizeCertsFilter(publicKeyCertificatesBase64, certificateValidator);
                out.setCertificateForClientAuth(crt -> out.getPublicKeyCertificatesBase64().contains(CategorizeCertsFilter.base64EncodePublicKey(crt)));
                out.setApimlCertificate(crt -> !out.getPublicKeyCertificatesBase64().contains(CategorizeCertsFilter.base64EncodePublicKey(crt)));
                return out;
            }
        }

        /**
         * Fallback filterchain for all other requests
         * All Routing goes through here
         * The filterchain does not require authentication
         * Web security is configured here and only here
         */
        @Configuration
        @RequiredArgsConstructor
        @Order(100)
        class DefaultSecurity {

            // Web security only needs to be configured once, putting it to multiple filter chains causes multiple evaluations of the same rules
            @Bean
            public WebSecurityCustomizer webSecurityCustomizer() {
                return web -> {
                    if (!isHealthEndpointProtected) {
                        web.ignoring().requestMatchers("/application/health");
                    }
                    // Endpoints that skip Spring Security completely
                    // There is no CORS filter on these endpoints. If you require CORS processing, use a defined filter chain
                    web.ignoring()
                        .requestMatchers("/error",
                            "/application/info", "/application/version",
                            AuthController.CONTROLLER_PATH + AuthController.ALL_PUBLIC_KEYS_PATH,
                            AuthController.CONTROLLER_PATH + AuthController.CURRENT_PUBLIC_KEYS_PATH);
                };
            }
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return baseConfigure(http.securityMatchers(matchers -> matchers.requestMatchers("/**", "/gateway/version")))
                .authorizeHttpRequests(requests -> requests
                    .anyRequest()
                    .permitAll()).logout(AbstractHttpConfigurer::disable)
                .build();
        }
    }

    /**
     * Common configuration for all filterchains
     */
    protected HttpSecurity baseConfigure(HttpSecurity http) throws Exception {
        if (isAttlsEnabled) {
            http.addFilterBefore(new AttlsFilter(), org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter.class);
            http.addFilterBefore(new SecureConnectionFilter(), AttlsFilter.class);
        }

        /*
         * Parent authentication manager is used as a fallback when no authentication provider is able to authenticate the request.
         * Spring provides the default provider manager as the parent for fallback. This can lead to unexpected behavior as we never use
         * spring defaults for authentication.
         */
        http.setSharedObject(AuthenticationManagerBuilder.class,
            http.getSharedObject(AuthenticationManagerBuilder.class).parentAuthenticationManager(null));

        return http
                .cors(withDefaults()).csrf(AbstractHttpConfigurer::disable)    // NOSONAR we are using SAMESITE cookie to mitigate CSRF
                .headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> {})
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                )
                .exceptionHandling(handling -> handling.authenticationEntryPoint(handlerInitializer.getBasicAuthUnauthorizedHandler()))
                    .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(handlerInitializer.getBasicAuthUnauthorizedHandler()));
    }

    private UserDetailsService x509UserDetailsService() {
        return username -> new User(username, "", Collections.emptyList());
    }

}
