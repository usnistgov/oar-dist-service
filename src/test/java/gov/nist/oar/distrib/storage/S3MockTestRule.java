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
package gov.nist.oar.distrib.storage;

import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.junit.runner.Description;
import org.junit.AssumptionViolatedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.File;
import java.lang.ProcessBuilder.Redirect;

/**
 * a JUnit ClassRule that starts a S3Mock server in a separate process for testing purposes
 */
public class S3MockTestRule implements TestRule {

    static final String _testurl = "http://localhost:9090/";
    private File serverdir = null;
    private File script = null;
    private Process server = null;
    private Logger log = LoggerFactory.getLogger(getClass());

    public S3MockTestRule() {
        File cwd = new File(System.getProperty("user.dir", "."));
        serverdir = new File(cwd, "s3mock");
        script = new File(serverdir, "runS3MockServer.sh");
    }

    public void startServer() throws IOException {
        String scrp = script.getAbsolutePath();
        log.info("Starting server in dir="+serverdir.toString());
        log.info("  See server log in s3mock-server.log");
        server = new ProcessBuilder(scrp).directory(serverdir)
                                         .redirectErrorStream(true)
                                         .redirectOutput(Redirect.to(new File("s3mock-server.log")))
                                         .start();
        if (! server.isAlive())
            throw new IllegalStateException("Server exited prematurely; status="+
                                            Integer.toString(server.exitValue()));
    }

    public void stopServer() {
        log.info("Shutting down S3Mock server");
        if (server != null && server.isAlive()) {
            server.destroy();
            try {
                server.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for server shutdown.");
            }
        }
    }

    public boolean checkAvailable() throws MalformedURLException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) (new URL(_testurl)).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            int status = conn.getResponseCode();
            conn.getContent();

            if (status >= 200 && status < 300)
                return true;
            return false;
        }
        catch (IOException ex) {
            log.warn("S3Mock Service is not up yet ("+ex.getMessage()+")");
            return false;
        }
        finally {
            if (conn != null) conn.disconnect();
        }
    }

    public boolean waitForServer(long timeoutms) throws InterruptedException, MalformedURLException {
        long startTime = System.currentTimeMillis();
        long remainingTime = timeoutms;
    
        
        while (remainingTime > 0) {
            if (!server.isAlive()) {
                throw new IllegalStateException("S3Mock server exited prematurely; status=" +
                                                server.exitValue());
            }
            if (checkAvailable()) {
                log.info("S3Mock server is available.");
                return true;
            }
            Thread.sleep(3000); // Delay before checking server status
            remainingTime = timeoutms - (System.currentTimeMillis() - startTime);
            log.warn("Waiting for S3Mock server, remaining time: " + remainingTime + "ms");
        }
        return false;
    }

    @Override
    public Statement apply(Statement base, Description desc) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (! script.exists())
                    throw new AssumptionViolatedException(script.toString() +
                                                   ": S3Mock server script not found (skipping AWS tests)");
                startServer();
                try {
                    if (! waitForServer(30000))
                        throw new IllegalStateException("S3Mock server not responding after 30s.");
                    base.evaluate();
                }
                finally {
                    stopServer();
                }
            }
        };
    }
}    
