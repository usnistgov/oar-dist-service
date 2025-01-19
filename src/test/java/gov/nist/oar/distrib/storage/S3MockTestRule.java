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

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JUnit 5 Extension that starts an S3Mock server in a separate process for testing purposes.
 */
public class S3MockTestRule implements BeforeAllCallback, AfterAllCallback {

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
        log.info("Starting server in dir=" + serverdir.toString());
        log.info("  See server log in s3mock-server.log");
        server = new ProcessBuilder(scrp).directory(serverdir)
                .redirectErrorStream(true)
                .redirectOutput(Redirect.to(new File("s3mock-server.log")))
                .start();
        if (!server.isAlive())
            throw new IllegalStateException("Server exited prematurely; status=" +
                    Integer.toString(server.exitValue()));
    }

    public void stopServer() {
        log.info("Shutting down S3Mock server");
        server.destroy();
    }

    public boolean checkAvailable() throws MalformedURLException {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(_testurl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            int status = conn.getResponseCode();
            conn.getContent();

            return status >= 200 && status < 300;
        } catch (IOException ex) {
            log.warn("S3Mock Service is not up yet (" + ex.getMessage() + ")");
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public boolean waitForServer(long timeoutms) throws InterruptedException, MalformedURLException {
        long starttime = System.currentTimeMillis();

        while (System.currentTimeMillis() - starttime < timeoutms) {
            if (!server.isAlive())
                throw new IllegalStateException("Server exited prematurely; status=" +
                        Integer.toString(server.exitValue()));
            if (checkAvailable())
                return true;
            Thread.sleep(2000);
        }

        return false;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!script.exists())
            throw new IllegalStateException(script.toString() + ": S3Mock server script not found (skipping AWS tests)");
        startServer();
        if (!waitForServer(30000))
            throw new IllegalStateException("S3Mock server not responding after 30s.");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stopServer();
    }
}
