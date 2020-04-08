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
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.io.IOException;

import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.junit.runner.Description;
import org.junit.AssumptionViolatedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a JUnit ClassRule that checks for access to a web site (e.g. data.nist.gov) as a guard for tests 
 * that require that.
 */
public class RequireWebSite implements TestRule {

    private String _testurl = null;
    private Logger log = LoggerFactory.getLogger(getClass());

    public RequireWebSite(String testurl) {
        _testurl = testurl;
    }

    public boolean checkSite() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) (new URL(_testurl)).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(20000);
            int status = conn.getResponseCode();
            conn.getContent();

            if (status >= 200 && status < 300)
                return true;
            return false;
        }
        catch (MalformedURLException ex) {
            log.error("Bad required web site URL, "+_testurl+": "+ex.getMessage());
            return false;
        }
        catch (IOException ex) {
            log.warn("IOException while accessing "+_testurl+"; assuming not available");
            log.warn(ex.getMessage());
            return false;
        }
        finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public Statement apply(Statement base, Description desc) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (! checkSite()) 
                    throw new AssumptionViolatedException("Site appears unavailable: Skipping test(s)");
                else
                    base.evaluate();
            }
        };
    }
}

