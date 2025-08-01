/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.loadbalancer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Clock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.core.SameInstancePreferenceServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.zowe.apiml.constants.ApimlConstants;
import org.zowe.apiml.gateway.caching.LoadBalancerCache;
import org.zowe.apiml.gateway.caching.LoadBalancerCache.LoadBalancerCacheRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.zowe.apiml.constants.ApimlConstants.X_INSTANCEID;
import static reactor.core.publisher.Flux.just;
import static reactor.core.publisher.Mono.empty;

/**
 * A sticky session load balancer that ensures requests from the same user are routed to the same service instance.
 */
@Slf4j
public class DeterministicLoadBalancer extends SameInstancePreferenceServiceInstanceListSupplier {

    private static final String HEADER_NONE_SIGNATURE = Base64.getEncoder().encodeToString("{\"typ\":\"JWT\",\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

    private final LoadBalancerCache cache;
    private final Clock clock;
    private final int expirationTime;

    public DeterministicLoadBalancer(ServiceInstanceListSupplier delegate,
                                     ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerClientFactory,
                                     LoadBalancerCache cache,
                                     Clock clock,
                                     int expirationTime) {
        super(delegate, loadBalancerClientFactory);
        this.cache = cache;
        this.clock = clock;
        this.expirationTime = expirationTime;
        log.debug("StickySessionLoadBalancer instantiated");
    }

    /**
     * Gets a list of service instances based on the request. This method ensures that requests from the same user are
     * routed to the same service instance, leveraging the cache to maintain sticky sessions.
     *
     * @param request the load balancer request
     * @return a flux of service instance lists
     */
    @Override
    public Flux<List<ServiceInstance>> get(Request request) {
        String serviceId = getServiceId();
        if (serviceId == null) {
            return Flux.empty();
        }
        AtomicReference<String> principal = new AtomicReference<>();
        return delegate.get(request)
            .flatMap(serviceInstances -> getSub(request.getContext())
                .switchIfEmpty(Mono.just(""))
                .flatMap(user -> {
                    if (user == null || user.isEmpty()) {
                        log.debug("No authentication present on request, not filtering the service: {}", serviceId);
                        return empty();
                    } else {
                        principal.set(user);
                        return cache.retrieve(user, serviceId).onErrorResume(t -> Mono.empty());
                    }
                })
                .switchIfEmpty(Mono.just(LoadBalancerCacheRecord.NONE))
                .flatMapMany(cacheRecord -> filterInstances(principal.get(), serviceId, cacheRecord, serviceInstances, request.getContext()))
            )
            .doOnError(e -> log.debug("Error in determining service instances", e));
    }

    /**
     * Checks if the cached date is too old based on the expiration time.
     *
     * @param cachedDate the cached date
     * @return true if the cached date is too old, false otherwise
     */
    private boolean isTooOld(LocalDateTime cachedDate) {
        LocalDateTime now = LocalDateTime.now().minusHours(expirationTime);
        return now.isAfter(cachedDate);
    }

    private Mono<String> getSub(Object requestContext) {
        if (requestContext instanceof RequestDataContext ctx) {
            var token = Optional.ofNullable(getTokenFromCookie(ctx))
                                .orElseGet(() -> getTokenFromHeader(ctx));
            return Mono.just(extractSubFromToken(token));
        }
        return Mono.just("");
    }

    private String getTokenFromCookie(RequestDataContext ctx) {
        var tokens = ctx.getClientRequest().getCookies().get("apimlAuthenticationToken");
        return tokens == null || tokens.isEmpty() ? null : tokens.get(0);
    }

    private String getTokenFromHeader(RequestDataContext ctx) {
        var authHeaderValues = ctx.getClientRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        var token = authHeaderValues == null || authHeaderValues.isEmpty() ? null : authHeaderValues.get(0);
        if (token != null && token.startsWith(ApimlConstants.BEARER_AUTHENTICATION_PREFIX)) {
            token = token.replaceFirst(ApimlConstants.BEARER_AUTHENTICATION_PREFIX, "").trim();
            if (token.isEmpty()) {
                return null;
            }

            return token;
        }
        return null;
    }

    /**
     * Filters the list of service instances to include only those with the specified instance ID.
     * Optional operation, it verifies if the conditions are met to actually filter the list of instances.
     *
     * @param user             The user
     * @param serviceId        The serviceId
     * @param cacheRecord           the cache record
     * @param serviceInstances the list of service instances to filter
     * @return the filtered list of service instances
     */
    private Flux<List<ServiceInstance>> filterInstances(
        String user,
        String serviceId,
        LoadBalancerCacheRecord cacheRecord,
        List<ServiceInstance> serviceInstances,
        Object requestContext) {

        Flux<List<ServiceInstance>> result;
        if (shouldIgnore(serviceInstances, user)) {
            var instanceId = getInstanceId(requestContext);
            try {
                return just(checkInstanceIdHeader(instanceId, serviceInstances));
            } catch (ResponseStatusException ex) {
                return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Service instance not found for the provided instance ID"));
            }
        }
        if (isNotBlank(cacheRecord.getInstanceId()) && isTooOld(cacheRecord.getCreationTime())) {
            result = cache.delete(user, serviceId)
                .thenMany(chooseOne(user, serviceInstances));
        } else if (isNotBlank(cacheRecord.getInstanceId())) {
            result = chooseOne(cacheRecord.getInstanceId(), user, serviceInstances);
        } else {
            result = chooseOne(user, serviceInstances);
        }
        return result;
    }

    /**
     * Retrieves the 'X-InstanceId' attribute from the request context.
     *
     * @param requestContext the request context
     * @return the instance ID, or null if not found
     */
    private String getInstanceId(Object requestContext) {
        if (requestContext instanceof RequestDataContext ctx) {
            return getInstanceFromHeader(ctx);
        }
        return null;
    }

    private String getInstanceFromHeader(RequestDataContext context) {
        if (context != null && context.getClientRequest() != null) {
            HttpHeaders headers = context.getClientRequest().getHeaders();
            if (headers != null) {
                return headers.getFirst(X_INSTANCEID);
            }
        }
        return null;
    }

    /**
     * Filters the list of service instances to include only those with the specified instance ID.
     *
     * @param instanceId       ID of the service instance
     * @param serviceInstances the list of service instances to filter
     * @return the filtered list of service instances
     */
    private List<ServiceInstance> checkInstanceIdHeader(String instanceId, List<ServiceInstance> serviceInstances) {
        if (instanceId != null) {
            List<ServiceInstance> filteredInstances = serviceInstances.stream()
                .filter(instance -> instanceId.equals(instance.getInstanceId()))
                .toList();
            if (!filteredInstances.isEmpty()) {
                return filteredInstances;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service instance not found for the provided instance ID");
        }
        return serviceInstances;
    }


    /**
     * Selected the preferred instance if not null, if the preferred instance is not found or is null a new preference is created
     *
     * @param instanceId       The preferred instanceId
     * @param user             The user
     * @param serviceInstances The default serviceInstances available
     * @return Flux with a list containing only one selected instance
     */
    private Flux<List<ServiceInstance>> chooseOne(String instanceId, String user, List<ServiceInstance> serviceInstances) {
        Stream<ServiceInstance> stream = serviceInstances.stream();
        if (instanceId != null) {
            stream = stream.filter(instance -> instanceId.equals(instance.getInstanceId()));
        }
        ServiceInstance chosenInstance = stream.findAny().orElse(serviceInstances.get(0));
        return cache.store(user, chosenInstance.getServiceId(), new LoadBalancerCacheRecord(chosenInstance.getInstanceId()))
            .thenMany(just(Collections.singletonList(chosenInstance)));
    }

    /**
     * Shortcut to create a new instance preference
     *
     * @param user
     * @param serviceInstances
     * @return
     */
    private Flux<List<ServiceInstance>> chooseOne(String user, List<ServiceInstance> serviceInstances) {
        return chooseOne(null, user, serviceInstances);
    }

    boolean shouldIgnore(List<ServiceInstance> instances, String user) {
        return StringUtils.isEmpty(user) || instances.isEmpty() || !lbTypeIsAuthentication(instances.get(0));
    }

    private boolean lbTypeIsAuthentication(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata != null) {
            String lbType = metadata.get("apiml.lb.type");
            return lbType != null && lbType.equals("authentication");
        }
        return false;
    }

    private String removeJwtSign(String jwtToken) {
        if (jwtToken == null) return null;

        int firstDot = jwtToken.indexOf('.');
        int lastDot = jwtToken.lastIndexOf('.');
        if ((firstDot < 0) || (firstDot >= lastDot)) throw new MalformedJwtException("Invalid JWT format");

        return HEADER_NONE_SIGNATURE + jwtToken.substring(firstDot, lastDot + 1);
    }

    private Claims getJwtClaims(String jwt) {
        /*
         * Removes signature, because we don't have key to verify z/OS tokens, and we just need to read claim.
         * Verification is done by SAF itself. JWT library doesn't parse signed key without verification.
         */
        try {
            String withoutSign = removeJwtSign(jwt);
            return Jwts.parser()
                .unsecured()
                .clock(clock)
                .build()
                .parseUnsecuredClaims(withoutSign)
                .getPayload();
        } catch (RuntimeException exception) {
            log.debug("Exception when trying to parse the JWT token {}", jwt);
            return null; // NOSONAR
        }
    }

    private String extractSubFromToken(String token) {
        if (StringUtils.isNotEmpty(token)) {
            Claims claims = getJwtClaims(token);
            if (claims != null) {
                return claims.getSubject();
            }
        }
        return "";
    }
}
