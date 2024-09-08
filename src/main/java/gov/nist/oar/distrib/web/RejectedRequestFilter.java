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

import org.springframework.web.filter.GenericFilterBean;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.security.web.firewall.RequestRejectedException;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This class is based on code provided by Charles Parker (vallismortis) via StackOverflow 
 * (https://stackoverflow.com/questions/51788764/how-to-intercept-a-requestrejectedexception-in-spring).
 * Note that the author also provided a more complicated alternative solution.
 */

/**
 * A filter to catch Spring's rejection of dangerous input URLs so that a proper response can be 
 * sent. 
 * <p>
 * Spring Security by default inserts a filter that checks incoming web requests for "potentially 
 * malicious" character strings in incoming URLs; when detected, a <code>RequestRejectedException</code>
 * is thrown.  Its internal handlers will catch this exception and response to the client with a 500 
 * status.  
 * <p>
 * this filter catches the <code>RequestRejectedException</code>, and sends the more appropriate response 
 * of 400 (Bad Request).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RejectedRequestFilter extends GenericFilterBean {

    static Logger log = LoggerFactory.getLogger(RejectedRequestFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException
    {
        try {
            chain.doFilter(req, res);
        } catch (RequestRejectedException ex) {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            log.warn("Requested URL rejected: {}: {}", request.getServletPath(), ex.getMessage());

            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
