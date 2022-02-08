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
package gov.nist.oar.distrib.cachemgr.inventory;

import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.IntegrityException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

import java.io.IOException;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import org.json.JSONObject;

public class SizeCheckTest {

    CacheVolume vol = null;
    CacheObject tstco = null;
    SizeCheck chk = null;

    @Before
    public void setUp()
        throws StorageVolumeException, InventoryException, IOException, CacheManagementException
    {
        String ltsdir = System.getProperty("project.test.resourceDirectory");
        assertNotNull(ltsdir);
        vol = new FilesystemCacheVolume(ltsdir);
        chk = new SizeCheck();
    }

    @Test
    public void testSuccess()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        JSONObject md = new JSONObject();
        md.put("size", 9841L);
        
        tstco = new CacheObject("mds1491.mbag0_2-0.zip", md, vol);
        assertTrue(vol.exists(tstco.name));

        chk.check(tstco);
    }

    @Test
    public void testFail()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        JSONObject md = new JSONObject();
        md.put("size", 900L);
        
        tstco = new CacheObject("mds1491.mbag0_2-0.zip", md, vol);
        assertTrue(vol.exists(tstco.name));

        try {
            chk.check(tstco);
            fail("Failed to detect bad size");
        }
        catch (IntegrityException ex) { }
    }

    @Test
    public void testMissing()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        JSONObject md = new JSONObject();
        md.put("size", 900L);
        
        tstco = new CacheObject("goober.zip", md, vol);
        assertFalse(vol.exists(tstco.name));

        try {
            chk.check(tstco);
            fail("Failed to detect bad size");
        }
        catch (ObjectNotFoundException ex) { }
    }

}        
