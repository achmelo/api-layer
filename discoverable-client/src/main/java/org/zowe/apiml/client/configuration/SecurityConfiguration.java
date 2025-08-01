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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.zowe.apiml.filter.AttlsFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Value("${server.attls.enabled:false}")
    private boolean isAttlsEnabled;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSecurity newConf = http.csrf(AbstractHttpConfigurer::disable) // NOSONAR
            .authorizeHttpRequests(requests -> requests
//                .requestMatchers("/api/v3/graphql/**").authenticated()
                .requestMatchers("/ws/**").authenticated()
                .requestMatchers("/**").permitAll())
            .httpBasic(withDefaults());

        if (isAttlsEnabled) {
            newConf.addFilterBefore(new AttlsFilter(), UsernamePasswordAuthenticationFilter.class);
        }
        return newConf.build();
    }


    @Bean
    InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
            .username("user").password("pass").authorities(new SimpleGrantedAuthority("ADMIN"))
            .build();
        return new InMemoryUserDetailsManager(user);
    }


    @Bean
    WebSecurityCustomizer webSecurityCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowUrlEncodedPeriod(true);
        firewall.setAllowSemicolon(true);

        return web -> {
            web.httpFirewall(firewall);
            web.ignoring().requestMatchers("/api/v1/**");
        };

    }

}
