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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.ds.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;

import gov.nist.oar.ds.util.ErrorInfo;
import io.swagger.annotations.Api;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
@ApiIgnore
@RestController
public class CustomErrorController implements ErrorController {

    private static final String PATH = "/error";

    @Autowired
    private ErrorAttributes errorAttributes;

    @RequestMapping(value = PATH)
    ErrorInfo error(HttpServletRequest request, HttpServletResponse response) {
       
    	Map<String, Object> errorAttributes = getErrorAttributes(request, true);
        return new ErrorInfo(request.getContextPath(),errorAttributes.get("message").toString(),Integer.toString(response.getStatus())); 
    }

    @Override
    public String getErrorPath() {
        return PATH;
    }

    private Map<String, Object> getErrorAttributes(HttpServletRequest request, boolean includeStackTrace) {
        RequestAttributes requestAttributes = new ServletRequestAttributes(request);
        return errorAttributes.getErrorAttributes((WebRequest) requestAttributes, includeStackTrace);
    }

}
