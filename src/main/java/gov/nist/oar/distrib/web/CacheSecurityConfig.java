/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 */
package gov.nist.oar.distrib.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import jakarta.servlet.Filter;

@Configuration
@EnableWebSecurity
public class CacheSecurityConfig {

    private static final RequestMatcher SECURED_ENDPOINTS =
        new OrRequestMatcher(new AntPathRequestMatcher("/cache/**"));

    @Value("${distrib.cachemgr.restapi.accesstoken:@null}")
    String accessToken;

    public CacheSecurityConfig() {
        super();
    }

    /**
     * Main method to configure security for the application.
     * 
     * @param http the {@link HttpSecurity} instance used to configure security for the application
     * @return the {@link SecurityFilterChain} defining the security rules for the application
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return configureStrictSecurity(http);
    }

    /**
     * Configures the security filter chain for the application, specifying the security rules
     * for different endpoints, session management, authentication, and filter chain setup.
     * 
     * - Stateless session management.
     * - BearerTokenAuthenticationFilter for token-based authentication.
     * - Only `/cache/**` requires authentication.
     * - CSRF protection is disabled.
     * - Form login, basic authentication, and logout are disabled.
     * 
     * @param http the {@link HttpSecurity} instance used to configure security for the application
     * @return the {@link SecurityFilterChain} defining the security rules for the application
     * @throws Exception if an error occurs during configuration
     */
    public SecurityFilterChain configureStrictSecurity(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(Customizer.withDefaults()) 
            .addFilterBefore(authenticationFilter(), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                // auth.requestMatchers("/ds/**").permitAll();
                auth.requestMatchers("/**").permitAll();
                auth.requestMatchers(SECURED_ENDPOINTS).authenticated(); 
            })
            .csrf(csrf -> csrf.disable()) 
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable()) 
            .logout(logout -> logout.disable()); 

        return http.build();
    }

    /**
     * Configures security rules where all requests are permitted without any authentication.
     * 
     * @param http the {@link HttpSecurity} instance used to configure security for the application
     * @return the {@link SecurityFilterChain} defining permissive security rules
     * @throws Exception if an error occurs during configuration
     */
    public SecurityFilterChain configurePermissiveSecurity(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Instantiate and configure the authentication filter that will be installed.
     * This returns an instance of {@link BearerTokenAuthenticationFilter}.
     */
    protected Filter authenticationFilter() throws Exception {
        final BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(accessToken, SECURED_ENDPOINTS);
        filter.setAuthenticationManager(authenticationManager(null));  // Inject the AuthenticationManager
        return filter;
    }
}
