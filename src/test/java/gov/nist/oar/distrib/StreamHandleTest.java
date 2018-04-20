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
import java.io.StringBufferInputStream;

public class StreamHandleTest {

    @Test
    public void testCtorNull() {

        StreamHandle sh = new StreamHandle();
        assertEquals(sh.dataStream, null);
        assertEquals(sh.name, null);
        assertEquals(sh.contentType, null);
        assertEquals(sh.checksum, null);
        assertEquals(sh.size, -1L);

        sh = new StreamHandle(null);
        assertEquals(sh.dataStream, null);
        assertEquals(sh.name, null);
        assertEquals(sh.contentType, null);
        assertEquals(sh.checksum, null);
        assertEquals(sh.size, -1L);
    }

    @Test
    public void testCtorFull() {

        String data = "Hello world";
        InputStream strm = new StringBufferInputStream(data);

        StreamHandle sh = new StreamHandle(null, -3, null, null, (String) null);
        assertEquals(null, sh.dataStream  );
        assertEquals(null, sh.name        );
        assertEquals(null, sh.contentType );
        assertEquals(null, sh.checksum    );
        assertEquals( -3L, sh.size        );

        sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                              Checksum.sha256("abcdef12345"));
        assertSame(sh.dataStream, strm);
        assertEquals("greeting.txt", sh.name);
        assertEquals("text/plain",   sh.contentType);
        assertEquals("abcdef12345",  sh.checksum.hash);
        assertEquals("sha256", sh.checksum.algorithm);
        assertEquals(11L, sh.size);
    }

    @Test
    public void testCtorOther() {
        String data = "Hello world";
        InputStream strm = new StringBufferInputStream(data);

        StreamHandle sh = new StreamHandle(strm, data.length(), "greeting.txt", "text/plain",
                                           "abcdef12345");
        assertSame(sh.dataStream,    strm);
        assertEquals(sh.name,        "greeting.txt");
        assertEquals(sh.contentType, "text/plain");
        assertEquals(sh.checksum.hash, "abcdef12345");
        assertEquals(sh.checksum.algorithm, Checksum.SHA256);
        assertEquals(sh.size,        11L);

        sh = new StreamHandle(strm, data.length());
        assertSame(sh.dataStream,    strm);
        assertEquals(sh.name,        null);
        assertEquals(sh.contentType, null);
        assertEquals(sh.checksum, null);
        assertEquals(sh.size,        11L);

        sh = new StreamHandle(strm);
        assertSame(sh.dataStream,    strm);
        assertEquals(sh.name,        null);
        assertEquals(sh.contentType, null);
        assertEquals(sh.checksum,    null);
        assertEquals(sh.size,        -1L);
    }

}
