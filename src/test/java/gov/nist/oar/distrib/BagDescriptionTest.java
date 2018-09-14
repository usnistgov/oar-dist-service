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
import static org.hamcrest.CoreMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BagDescriptionTest {

    @Test
    public void testCtorNull() {
        BagDescription d = new BagDescription();
        assertEquals(null, d.name          );
        assertEquals(null, d.contentType   );
        assertEquals(null, d.checksum      );
        assertEquals(null, d.aipid         );
        assertEquals( -1L, d.contentLength );
        assertEquals(   0, d.getProperties().size());

        d = new BagDescription(null, -3L);
        assertEquals(null, d.name          );
        assertEquals(null, d.contentType   );
        assertEquals(null, d.checksum      );
        assertEquals(null, d.aipid         );
        assertEquals( -3L, d.contentLength );
        assertEquals(   0, d.getProperties().size());
    }

    @Test
    public void testCtorFull() {

        String data = "Hello world";

        BagDescription d = new BagDescription(null, -3, null, (String) null);
        assertEquals(null, d.name         );
        assertEquals(null, d.contentType  );
        assertEquals(null, d.checksum     );
        assertEquals(null, d.aipid        );
        assertEquals( -3L, d.contentLength);
        assertEquals(   0, d.getProperties().size());

        d = new BagDescription("greeting.txt", data.length(), "text/plain",
                               Checksum.sha256("abcdef12345"));
        assertEquals("greeting.txt", d.name);
        assertEquals("text/plain",   d.contentType);
        assertEquals("abcdef12345",  d.checksum.hash);
        assertEquals("sha256", d.checksum.algorithm);
        assertEquals(11L, d.contentLength);
        assertEquals(null, d.aipid        );
        assertEquals(   0, d.getProperties().size());

        d = new BagDescription("mds25gd1k.2_1_1.mbag0_4-12.zip", data.length(), "text/plain",
                               Checksum.sha256("abcdef12345"));
        assertEquals("mds25gd1k.2_1_1.mbag0_4-12.zip", d.name);
        assertEquals("text/plain",   d.contentType);
        assertEquals("abcdef12345",  d.checksum.hash);
        assertEquals("sha256", d.checksum.algorithm);
        assertEquals(11L, d.contentLength);
        assertNotEquals(   0, d.getProperties().size());
        assertEquals("mds25gd1k",    d.aipid);
        assertEquals("2.1.1", d.getStringProp("sinceVersion"));
        assertEquals("0.4",   d.getStringProp("multibagProfileVersion"));
        assertEquals(12, d.getIntProp("multibagSequence"));
        assertEquals("zip",   d.getStringProp("serialization"));

        d = new BagDescription("mds25gd1k.2_1_51.mbag1_0-34.7z", data.length());
        assertEquals("mds25gd1k.2_1_51.mbag1_0-34.7z", d.name);
        assertEquals( 11L, d.contentLength);
        assertEquals("application/x-7z-compressed", d.contentType );
        assertEquals(null, d.checksum    );
        assertNotEquals(   0, d.getProperties().size());
        assertEquals("mds25gd1k",    d.aipid);
        assertEquals("2.1.51", d.getStringProp("sinceVersion"));
        assertEquals("1.0",   d.getStringProp("multibagProfileVersion"));
        assertEquals(34, d.getIntProp("multibagSequence"));
        assertEquals("7z",   d.getStringProp("serialization"));
    }

    @Test
    public void testProps() {
        BagDescription d = new BagDescription("greeting.txt", 11);
        d.setProp("sequence", 14);
        d.setProp("ishead", true);
        d.setProp("size", 5L);

        assertEquals(14, d.getIntProp("sequence"));
        assertEquals(14L, d.getLongProp("sequence"));
        assertEquals(5, d.getIntProp("size"));
        assertEquals(5L, d.getLongProp("size"));
        assertTrue(d.getBooleanProp("ishead"));
        assertEquals(null, d.getProperties().get("goober"));
    }

    @Test
    public void testPropCastExc() {
        BagDescription d = new BagDescription("greeting.txt", 11);
        d.setProp("goober", "gurn");
        try {
            d.getIntProp("goober");
            fail("ClassCastException not thrown");
        } catch(ClassCastException ex) { }
        try {
            d.getLongProp("goober");
            fail("ClassCastException not thrown");
        } catch(ClassCastException ex) { }
        try {
            d.getBooleanProp("goober");
            fail("ClassCastException not thrown");
        } catch(ClassCastException ex) { }
    }

    @Test
    public void testJSON() throws Exception {
        BagDescription d = new BagDescription("mds25gd1k.2_1_51.mbag1_0-34.7z", 45098391472L, 
                                              "text/plain", Checksum.sha256("abcdef12345"));
        String json = new ObjectMapper().writeValueAsString(d);
        assertThat(json, containsString("\"name\":"));
        assertThat(json, containsString("\"contentLength\":"));
        assertThat(json, containsString("\"checksum\":{\"hash\":"));
        assertThat(json, containsString("\"aipid\":\"mds25gd1k\""));
        assertThat(json, containsString("\"sinceVersion\":\"2.1.51\""));
        assertThat(json, containsString("\"multibagProfileVersion\":\"1.0\""));
        assertThat(json, containsString("\"serialization\":\"7z\""));
        assertThat(json, containsString("\"multibagSequence\":34"));
    }

    @Test
    public void testJSONfallback() throws Exception {
        BagDescription d = new BagDescription("greeting.txt", 45098L, "text/plain",
                                              Checksum.sha256("abcdef12345"));
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
