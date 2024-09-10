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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.pdr;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CacheOptsTest {

    @Test
    public void testParse() {
        CacheOpts opts = new CacheOpts();
        assertFalse(opts.recache);
        assertNull(opts.seq);
        assertEquals(0, opts.prefs);

        opts = CacheOpts.parse("recache=1");
        assertTrue(opts.recache);
        assertNull(opts.seq);
        assertEquals(0, opts.prefs);

        opts = CacheOpts.parse("r=1,seq=14");
        assertTrue(opts.recache);
        assertEquals(opts.seq, "14");
        assertEquals(0, opts.prefs);

        opts = CacheOpts.parse("r=1,seq=goob,pref=3,rec=0");
        assertFalse(opts.recache);
        assertEquals(opts.seq, "goob");
        assertEquals(3, opts.prefs);
    }

    @Test
    public void testSerialize() {
        CacheOpts opts = new CacheOpts();
        assertEquals("re=0", opts.serialize());

        opts.seq = "2112";
        assertEquals("re=0,seq=2112", opts.serialize());

        opts.recache = true;
        assertEquals("re=1,seq=2112", opts.serialize());

        opts.prefs = 3;
        assertEquals("re=1,seq=2112,pr=3", opts.serialize());

        opts.seq = null;
        assertEquals("re=1,pr=3", opts.serialize());
    }
}
