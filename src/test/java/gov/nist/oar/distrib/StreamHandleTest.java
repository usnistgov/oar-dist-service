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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;

public class StreamHandleTest {

    @Test
    public void testCtorNull() {

        StreamHandle sh = new StreamHandle();
        assertEquals(sh.dataStream, null);
        assertEquals(sh.getInfo().name, null);
        assertEquals(sh.getInfo().contentType, null);
        assertEquals(sh.getInfo().checksum, null);
        assertEquals(sh.getInfo().contentLength, -1L);

        sh = new StreamHandle(null);
        assertEquals(sh.dataStream, null);
        assertEquals(sh.getInfo().name, null);
        assertEquals(sh.getInfo().contentType, null);
        assertEquals(sh.getInfo().checksum, null);
        assertEquals(sh.getInfo().contentLength, -1L);
    }

    @Test
    public void testCtorFull() {

        String data = "Hello world";
        InputStream strm = new StringBufferInputStream(data);

        StreamHandle sh = new StreamHandle(null, -3, null, null, (String) null);
        assertEquals(null, sh.dataStream  );
        assertEquals(null, sh.getInfo().name        );
        assertEquals(null, sh.getInfo().contentType );
        assertEquals(null, sh.getInfo().checksum    );
        assertEquals( -3L, sh.getInfo().contentLength        );

        sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                              Checksum.sha256("abcdef12345"));
        assertSame(sh.dataStream, strm);
        assertEquals("greeting.txt", sh.getInfo().name);
        assertEquals("text/plain",   sh.getInfo().contentType);
        assertEquals("abcdef12345",  sh.getInfo().checksum.hash);
        assertEquals("sha256", sh.getInfo().checksum.algorithm);
        assertEquals(11L, sh.getInfo().contentLength);
    }

    @Test
    public void testCtorOther() {
        String data = "Hello world";
        InputStream strm = new StringBufferInputStream(data);

        StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                                           "abcdef12345");
        assertSame(sh.dataStream,    strm);
        assertEquals(sh.getInfo().name,        "greeting.txt");
        assertEquals(sh.getInfo().contentType, "text/plain");
        assertEquals(sh.getInfo().checksum.hash, "abcdef12345");
        assertEquals(sh.getInfo().checksum.algorithm, Checksum.SHA256);
        assertEquals(sh.getInfo().contentLength,        11L);

        sh = new StreamHandle(strm, data.length());
        assertSame(sh.dataStream,    strm);
        assertEquals(sh.getInfo().name,        null);
        assertEquals(sh.getInfo().contentType, null);
        assertEquals(sh.getInfo().checksum, null);
        assertEquals(sh.getInfo().contentLength,        11L);

        sh = new StreamHandle(strm);
        assertSame(sh.dataStream,    strm);
        assertEquals(sh.getInfo().name,        null);
        assertEquals(sh.getInfo().contentType, null);
        assertEquals(sh.getInfo().checksum,    null);
        assertEquals(sh.getInfo().contentLength,        -1L);
    }

    @Test
    public void testClose() throws IOException {
        String data = "Hello world";
        InputStream strm = new StringBufferInputStream(data);

        StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                                           "abcdef12345");
        sh.close();
        sh.close();

        sh = new StreamHandle(null, 55L, "greeting.txt", "text/plain", "abcdef12345");
        sh.close();
        sh.close();
    }

    @Test
    public void testAutoClose() throws IOException {
        String data = "Hello world";
        InputStream strm = new StringBufferInputStream(data);

        try (StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                                                "abcdef12345")) 
        {
            sh.dataStream.read();
        }
    }
}
