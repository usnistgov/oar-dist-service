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
package gov.nist.oar.distrib.cachemgr.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;

public class NullCacheVolumeTest {

    @Test
    public void testGetName() throws IOException {
        CacheVolume v = new NullCacheVolume("goob");
        assertEquals("goob", v.getName());
    }

    @Test
    public void testExists() throws StorageVolumeException, IOException {
        NullCacheVolume v = new NullCacheVolume("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        v.addObjectName("goob");
        assertTrue(v.exists("goob"), "Failed to find object in store");
    }

    @Test
    public void testInputStreamSaveAs() throws StorageVolumeException, IOException {
        String stuff = "All work and no play makes Jack a dull boy.  ";
        StringBuilder sb = new StringBuilder(stuff);
        for (int i = 0; i < 200; i++) {
            sb.append(stuff);
        }

        NullCacheVolume v = new NullCacheVolume("root");
        InputStream data = new ByteArrayInputStream(sb.toString().getBytes());
        try {
            v.saveAs(data, "goob", null);
        } finally {
            data.close();
        }
        assertTrue(v.exists("goob"), "Failed to find object in store");
    }

    @Test
    public void testCacheObjectSaveAs() throws StorageVolumeException, IOException {
        NullCacheVolume v = new NullCacheVolume("root");
        v.addObjectName("goob");
        CacheObject co = new CacheObject("goob", v);

        // now test transfer
        assertFalse(v.exists("hank"), "Mistakenly believes non-existent object exists");
        v.saveAs(co, "hank");
        assertTrue(v.exists("hank"), "Failed to find object in store");
    }

    @Test
    public void testGetStream() throws StorageVolumeException, IOException {
        NullCacheVolume v = new NullCacheVolume("root");
        v.addObjectName("goob");

        InputStream is = v.getStream("goob");
        byte[] buf = new byte[20];
        try {
            int n = is.read(buf);
            assertTrue(n < 1, "Read unexpected bytes");
        } finally {
            is.close();
        }
    }

    @Test
    public void testGet() throws StorageVolumeException, IOException {
        NullCacheVolume v = new NullCacheVolume("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        v.addObjectName("goob");

        CacheObject co = v.get("goob");
        assertEquals("root", co.volname);
        assertEquals("root", co.volume.getName());
        assertEquals("goob", co.name);
    }

    @Test
    public void testRemove() throws StorageVolumeException, IOException {
        NullCacheVolume v = new NullCacheVolume("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        assertFalse(v.remove("goob"));
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");

        v.addObjectName("goob");
        assertTrue(v.exists("goob"), "Failed to find object in store");

        assertTrue(v.remove("goob"));
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
    }
}
