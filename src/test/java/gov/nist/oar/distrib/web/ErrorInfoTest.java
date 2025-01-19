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
package gov.nist.oar.distrib.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ErrorInfoTest {

    @Test
    public void testCtor() {
        ErrorInfo e = new ErrorInfo("/goober", 403, "Rude!", "HEAD");
        assertEquals("/goober", e.requestURL);
        assertEquals(403, e.status);
        assertEquals("Rude!", e.message);
        assertEquals("HEAD", e.method);

        e = new ErrorInfo("/goober", 410, "Rude!");
        assertEquals("/goober", e.requestURL);
        assertEquals(410, e.status);
        assertEquals("Rude!", e.message);
        assertEquals("GET", e.method);

        e = new ErrorInfo(410, "Rude!");
        assertNull(e.requestURL);
        assertEquals(410, e.status);
        assertEquals("Rude!", e.message);
        assertNull(e.method);
    }

    @Test
    public void testJSON() throws Exception {
        ErrorInfo e = new ErrorInfo("/goober", 403, "Rude!", "HEAD");
        String json = new ObjectMapper().writeValueAsString(e);
        JSONAssert.assertEquals("{requestURL:\"/goober\", status:403, message:Rude!, method:HEAD}",
                                json, true);

        e.method = null;
        json = new ObjectMapper().writeValueAsString(e);
        JSONAssert.assertEquals("{requestURL:\"/goober\", status:403, message:Rude!}",
                                json, true);

        e.requestURL = null;
        json = new ObjectMapper().writeValueAsString(e);
        JSONAssert.assertEquals("{status:403, message:Rude!}", json, true);

        e.message = null;
        json = new ObjectMapper().writeValueAsString(e);
        JSONAssert.assertEquals("{status:403}", json, true);
    }
}
