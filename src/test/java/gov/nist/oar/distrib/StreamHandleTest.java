/*
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
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

public class StreamHandleTest {

    @Test
    public void testCtorNull() throws IOException {
        try (StreamHandle sh = new StreamHandle()) {
            assertNull(sh.dataStream);
            assertNull(sh.getInfo().name);
            assertNull(sh.getInfo().contentType);
            assertNull(sh.getInfo().checksum);
            assertEquals(-1L, sh.getInfo().contentLength);
        }

        try (StreamHandle sh = new StreamHandle(null)) {
            assertNull(sh.dataStream);
            assertNull(sh.getInfo().name);
            assertNull(sh.getInfo().contentType);
            assertNull(sh.getInfo().checksum);
            assertEquals(-1L, sh.getInfo().contentLength);
        }
    }

    @Test
    public void testCtorFull() throws IOException {
        String data = "Hello world";
        InputStream strm = new ByteArrayInputStream(data.getBytes());

        try (StreamHandle sh = new StreamHandle(null, -3, null, null, (String) null)) {
            assertNull(sh.dataStream);
            assertNull(sh.getInfo().name);
            assertNull(sh.getInfo().contentType);
            assertNull(sh.getInfo().checksum);
            assertEquals(-3L, sh.getInfo().contentLength);
        }

        try (StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                Checksum.sha256("abcdef12345"))) {
            assertSame(strm, sh.dataStream);
            assertEquals("greeting.txt", sh.getInfo().name);
            assertEquals("text/plain", sh.getInfo().contentType);
            assertEquals("abcdef12345", sh.getInfo().checksum.hash);
            assertEquals("sha256", sh.getInfo().checksum.algorithm);
            assertEquals(11L, sh.getInfo().contentLength);
        }
    }

    @Test
    public void testCtorOther() throws IOException {
        String data = "Hello world";
        InputStream strm = new ByteArrayInputStream(data.getBytes());

        try (StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                "abcdef12345")) {
            assertSame(strm, sh.dataStream);
            assertEquals("greeting.txt", sh.getInfo().name);
            assertEquals("text/plain", sh.getInfo().contentType);
            assertEquals("abcdef12345", sh.getInfo().checksum.hash);
            assertEquals(Checksum.SHA256, sh.getInfo().checksum.algorithm);
            assertEquals(11L, sh.getInfo().contentLength);
        }

        try (StreamHandle sh = new StreamHandle(strm, data.length())) {
            assertSame(strm, sh.dataStream);
            assertNull(sh.getInfo().name);
            assertNull(sh.getInfo().contentType);
            assertNull(sh.getInfo().checksum);
            assertEquals(11L, sh.getInfo().contentLength);
        }

        try (StreamHandle sh = new StreamHandle(strm)) {
            assertSame(strm, sh.dataStream);
            assertNull(sh.getInfo().name);
            assertNull(sh.getInfo().contentType);
            assertNull(sh.getInfo().checksum);
            assertEquals(-1L, sh.getInfo().contentLength);
        }
    }

    @Test
    public void testClose() throws IOException {
        String data = "Hello world";
        InputStream strm = new ByteArrayInputStream(data.getBytes());

        try (StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                "abcdef12345")) {
            sh.close();
        }

        try (StreamHandle sh = new StreamHandle(null, 55L, "greeting.txt", "text/plain", "abcdef12345")) {
            sh.close();
        }
    }

    @Test
    public void testAutoClose() throws IOException {
        String data = "Hello world";
        InputStream strm = new ByteArrayInputStream(data.getBytes());

        try (StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                "abcdef12345")) {
            sh.dataStream.read();
        }
    }
}
