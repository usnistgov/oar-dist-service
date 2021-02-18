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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import org.apache.commons.lang3.StringUtils;


import javax.servlet.Filter;

/**
 * Security configuration for the Cache management service endpoints.
 * <p>
 * This engages an AuthenticationFilter for accesses to the endpoints for the {@link CacheManagementController}.
 */
@Configuration
@EnableWebSecurity
public class CacheSecurityConfig extends WebSecurityConfigurerAdapter {

    private static final RequestMatcher SECURED_ENDPOINTS =
        new OrRequestMatcher(new AntPathRequestMatcher("/cache/**"));

    @Value("${distrib.cachemgr.restapi.accesstoken:@null}")
    String accessToken;

    public CacheSecurityConfig() {
        super();
    }

    /**
     * configure the web service security used in the application.
     * <p>
     * This configures the following conditions:
     * <ul>
     *   <li> installs our {@link BearerTokenAuthenticationFilter} to do the actual authentication test, </li>
     *   <li> establishes which endpoints should use this authentication filter, </li>
     *   <li> sets stateless (as in, no) session management, </li>
     *   <li> use default exception handling, particularly when authentication fails (sending 401), and </li>
     *   <li> disables use of login forms, logout forms, and CSRF guarding. <li>
     * </ul>
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .exceptionHandling().and()
            .addFilterBefore(authenticationFilter(), AnonymousAuthenticationFilter.class)
            .authorizeRequests().requestMatchers(SECURED_ENDPOINTS).authenticated().and()
            .csrf().disable()
            .formLogin().disable()
            .httpBasic().disable()
            .logout().disable();
    }

    /**
     * instantiate and configure the authentication filter that will get installed.  This returns an instance
     * of the {@link BearerTokenAuthenticationFilter}.  
     */
    protected Filter authenticationFilter() throws Exception {
        final BearerTokenAuthenticationFilter out =
            new BearerTokenAuthenticationFilter(accessToken, SECURED_ENDPOINTS);
        out.setAuthenticationManager(authenticationManager());
        return out;
    }
}
