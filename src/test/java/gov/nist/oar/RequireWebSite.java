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
package gov.nist.oar;

import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.io.IOException;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JUnit 5 extension that checks for access to a website (e.g., data.nist.gov) 
 * before all tests are run.
 */
public class RequireWebSite implements BeforeAllCallback {

    private String testUrl;
    private Logger log = LoggerFactory.getLogger(getClass());

    // Default constructor required by JUnit 5
    public RequireWebSite() {
        this.testUrl = "https://oardev3.nist.gov"; // default URL
    }

    public RequireWebSite(String testUrl) {
        this.testUrl = testUrl;
    }

    /**
     * Method to check if the site is available by sending an HTTP request.
     * 
     * @return true if the site is available, false otherwise
     */
    public boolean checkSite() {
        HttpURLConnection conn = null;
        try {
            // Use URI to create a URL
            URI uri = new URI(testUrl);
            URL url = uri.toURL();

            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(20000);
            int status = conn.getResponseCode();
            conn.getContent();

            return status >= 200 && status < 300;
        } catch (URISyntaxException ex) {
            log.error("Bad required web site URL, " + testUrl + ": " + ex.getMessage());
            return false;
        } catch (IOException ex) {
            log.warn("IOException while accessing " + testUrl + "; assuming not available");
            log.warn(ex.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // If the site is not available, skip the test(s)
        Assumptions.assumeTrue(checkSite(), 
            "Site appears unavailable: Skipping test(s)");
    }
}