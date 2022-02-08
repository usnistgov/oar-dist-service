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
package gov.nist.oar.distrib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import gov.nist.oar.distrib.Checksum;

public class ChecksumTest {

    @Test
    public void testCtor() {
        Checksum cs = new Checksum("abcdef12345", "md5");
        assertEquals("abcdef12345", cs.hash);
        assertEquals("md5", cs.algorithm);
    }

    @Test
    public void testSha256() {
        Checksum cs = Checksum.sha256("abcdef12345");
        assertEquals("abcdef12345", cs.hash);
        assertEquals(Checksum.SHA256, cs.algorithm);
        assertEquals("sha256", cs.algorithm);
    }

    @Test
    public void testCrc32() {
        Checksum cs = Checksum.crc32("abcdef12345");
        assertEquals("abcdef12345", cs.hash);
        assertEquals(Checksum.CRC32, cs.algorithm);
        assertEquals("crc32", cs.algorithm);
    }

    @Test
    public void testCalcSHA256() throws IOException {
        String data = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n";
        InputStream is = new ByteArrayInputStream(data.getBytes());
        Checksum cs = Checksum.calcSHA256(is);
        assertEquals("sha256", cs.algorithm);

        // truth determined via sha256sum Unix command
        String truth = "38acb15d02d5ac0f2a2789602e9df950c380d2799b4bdb59394e4eeabdd3a662";
        assertEquals(truth, cs.hash);
    }

    @Test
    public void testCalcSHA256bigger() throws IOException {
        String line = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n";
        StringBuilder data = new StringBuilder();
        for(int i=0; i < 800; i++) {  // need more than buffer size of 50k
            data.append(line);
        }
        InputStream is = new ByteArrayInputStream(data.toString().getBytes());
        Checksum cs = Checksum.calcSHA256(is);
        assertEquals("sha256", cs.algorithm);

        // truth determined via sha256sum Unix command
        String truth = "e635ba02a6ce85e013b6962797d47431c24051d8e8517ca33ac53ac594fa2dc6";
        assertEquals(truth, cs.hash);
    }
}
