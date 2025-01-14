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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;

/**
 * a servlet filter that checks to determine if the web request has a "Bearer" token that matches a 
 * configured value.
 */
public class BearerTokenAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    final private String reqtoken;

    /**
     * configure the filter to require the given value as the bearer token
     * @param secret     the bearer token value that HTTP requests must provide.  If null or empty
     *                      (or equal to "@null"), this filter will short-circuit with a 404 (not found)
     *                      response.
     * @param forendpts  the endpoints that will require this authentication
     */
    public BearerTokenAuthenticationFilter(String secret, RequestMatcher forendpts) {
        super(forendpts);
        if ("@null".equals(secret) || "".equals(secret))
            secret = null;
        reqtoken = secret;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException, IOException
    {
        if (reqtoken == null) {
            response.sendError(response.SC_NOT_FOUND, "Cache API not avalable");
            return null;
        }

        final String BEARERTAG = "Bearer ";
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (token == null) token = "";
        if (! token.startsWith(BEARERTAG))
            throw new BadCredentialsException("Non-Bearer credentional provided");
        token = token.substring(BEARERTAG.length()).trim();
        if (! reqtoken.equals(token))
            throw new BadCredentialsException("Unrecognized/Invalid token provided");

        // Authentication success!
        Authentication out = new UsernamePasswordAuthenticationToken(token, token, null);
        // out.setAuthenticated(true);
        return out;
    }

    @Override
    protected void successfulAuthentication(final HttpServletRequest request, final HttpServletResponse response,
                                            final FilterChain chain, final Authentication authResult)
        throws IOException, ServletException
    {
        SecurityContextHolder.getContext().setAuthentication(authResult);
        chain.doFilter(request, response);
    }
}
