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
package gov.nist.oar.distrib.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import gov.nist.oar.distrib.StorageVolumeException;

public class WebLongTermStorageTest {

    Logger log = LoggerFactory.getLogger(getClass());
    String aipbase = "https://archive.apache.org/dist/commons/lang/binaries/";
    WebLongTermStorage lts = null;

    public WebLongTermStorageTest() {
        ch.qos.logback.classic.Logger log =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WebLongTermStorage.class);
        log.setLevel(Level.DEBUG);
    }

    @BeforeEach
    public void setUp() throws MalformedURLException {
        try {
            lts = new WebLongTermStorage(aipbase, "md5");
        } catch (MalformedURLException ex) {
            log.error("Bad base URL: " + ex.getMessage());
            throw ex;
        }
    }

    @AfterEach
    public void tearDown() {
        lts = null;
    }

    @Test
    public void testCtor() {
        assertEquals(aipbase, lts.getName());
    }

    @Test
    public void testGetInfo() throws FileNotFoundException, StorageVolumeException {
        assertEquals(3262287, lts.getSize("commons-lang3-3.2.1-bin.tar.gz"));
        assertTrue(lts.exists("commons-lang3-3.2.1-bin.tar.gz"));
        assertEquals("d103c7d4293c7e4a0cf8a0d5bf534f25",
                lts.getChecksum("commons-lang3-3.2.1-bin.tar.gz").hash);
        assertEquals("md5", lts.getChecksum("commons-lang3-3.2.1-bin.tar.gz").algorithm);
    }

    private String getStringContent(InputStream is) throws IOException {
        BufferedReader sr = null;
        try {
            sr = new BufferedReader(new InputStreamReader(is));
            return sr.readLine();
        } finally {
            if (sr != null) sr.close();
        }
    }

    @Test
    public void testOpenFile() throws IOException, StorageVolumeException {
        InputStream s = lts.openFile("commons-lang3-3.2.1-bin.tar.gz.md5");
        try {
            assertEquals("d103c7d4293c7e4a0cf8a0d5bf534f25", getStringContent(s));
        } finally {
            s.close();
        }
    }
}
