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

}
