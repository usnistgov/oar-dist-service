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
 *
 * @author Raymond Plante
 */
package gov.nist.oar.distrib.web;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;

/**
 * a simple controller for revealing the version of the deployed service
 *
 * @author Raymond Plante
 */
@RestController
@Api
@RequestMapping(value = "/ds")
public class VersionController {

    /**
     * The service name
     */
    public final static String NAME;

    /**
     * The version of the service
     */
    public final static String VERSION;

    static {
        String name = null;
        String version = null;
        try (InputStream verf = VersionController.class.getResourceAsStream("/VERSION")) {
            if (verf == null) {
                name = "oar-dist-service";
                version = "not set";
            }
            else {
                BufferedReader vrdr = new BufferedReader(new InputStreamReader(verf));
                String line = vrdr.readLine();
                String[] parts = line.split("\\s+");
                name = parts[0];
                version = (parts.length > 1) ? parts[1] : "missing";
            }
        } catch (Exception ex) {
            name = "oar-dist-service";
            version = "unknown";
        }
        NAME = name;
        VERSION = version;
    }

    /**
     * return the version of the service
     */
    @ApiOperation(value = "Return the version data for the service", nickname = "getServiceVersion",
                  notes = "This returns the name and version label for this service")
    @GetMapping(value = "/")
    public VersionInfo getServiceVersion() {
        return new VersionInfo(NAME, VERSION);
    }

    /**
     * redirect "/ds" to "/ds/"
     */
    @ApiOperation(value = "Return the version data for the service", nickname = "getServiceVersion",
                  notes = "This returns the name and version label for this service")
    @GetMapping(value = "")
    public void redirectToServiceVersion(HttpServletResponse resp) throws IOException {
        resp.sendRedirect("ds/");
    }

    public static class VersionInfo {
        public String serviceName = null;
        public String version = null;

        public VersionInfo(String name, String ver) {
            serviceName = name;
            version = ver;
        }
    }
}
