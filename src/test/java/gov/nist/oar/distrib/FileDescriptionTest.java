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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FileDescriptionTest {

    @Test
    public void testCtorNull() {
        FileDescription d = new FileDescription();
        assertNull(d.name);
        assertNull(d.contentType);
        assertNull(d.checksum);
        assertNull(d.aipid);
        assertEquals(-1L, d.contentLength);
        assertEquals(0, d.getProperties().size());

        d = new FileDescription(null, -3L);
        assertNull(d.name);
        assertNull(d.contentType);
        assertNull(d.checksum);
        assertNull(d.aipid);
        assertEquals(-3L, d.contentLength);
        assertEquals(0, d.getProperties().size());
    }

    @Test
    public void testCtorFull() {
        String data = "Hello world";

        FileDescription d = new FileDescription(null, -3, null, (String) null);
        assertNull(d.name);
        assertNull(d.contentType);
        assertNull(d.checksum);
        assertEquals(-3L, d.contentLength);
        assertEquals(0, d.getProperties().size());

        d = new FileDescription("greeting.txt", data.length(), "text/plain", Checksum.sha256("abcdef12345"));
        assertEquals("greeting.txt", d.name);
        assertEquals("text/plain", d.contentType);
        assertEquals("abcdef12345", d.checksum.hash);
        assertEquals("sha256", d.checksum.algorithm);
        assertEquals(11L, d.contentLength);
        assertEquals(0, d.getProperties().size());

        d = new FileDescription("greeting.txt", data.length());
        assertEquals("greeting.txt", d.name);
        assertEquals(11L, d.contentLength);
        assertNull(d.contentType);
        assertNull(d.checksum);
        assertEquals(0, d.getProperties().size());
    }

    @Test
    public void testProps() {
        FileDescription d = new FileDescription("greeting.txt", 11);
        d.setProp("sequence", 14);
        d.setProp("ishead", true);
        d.setProp("size", 5L);

        assertEquals(14, d.getIntProp("sequence"));
        assertEquals(14L, d.getLongProp("sequence"));
        assertEquals(5, d.getIntProp("size"));
        assertEquals(5L, d.getLongProp("size"));
        assertTrue(d.getBooleanProp("ishead"));
        assertNull(d.getProperties().get("goober"));
    }

    @Test
    public void testPropCastExc() {
        FileDescription d = new FileDescription("greeting.txt", 11);
        d.setProp("goober", "gurn");
        assertThrows(ClassCastException.class, () -> d.getIntProp("goober"));
        assertThrows(ClassCastException.class, () -> d.getLongProp("goober"));
        assertThrows(ClassCastException.class, () -> d.getBooleanProp("goober"));
    }

    @Test
    public void testJSON() throws Exception {
        FileDescription d = new FileDescription("greeting.txt", 45098L, "text/plain", Checksum.sha256("abcdef12345"));
        String json = new ObjectMapper().writeValueAsString(d);
        assertThat(json, containsString("\"name\":"));
        assertThat(json, containsString("\"contentLength\":"));
        assertThat(json, containsString("\"checksum\":{\"hash\":"));
        assertThat(json, not(containsString("\"aipid\"")));
        assertThat(json, not(containsString("\"version\"")));
        assertThat(json, not(containsString("\"seq\"")));

        d.aipid = "md30g23ab";
        d.setProp("version", "1.3");
        d.setProp("seq", 3);
        json = new ObjectMapper().writeValueAsString(d);
        assertThat(json, containsString("\"name\":"));
        assertThat(json, containsString("\"contentLength\":45098"));
        assertThat(json, containsString("\"aipid\":"));
        assertThat(json, containsString("\"version\":\"1.3\""));
        assertThat(json, containsString("\"seq\":3"));
    }
}
