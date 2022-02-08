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

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.IntegrityException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AlwaysFailsCheckTest {

    CacheObject tstco = null;
    AlwaysFailsCheck chk = null;

    @Before
    public void setUp() {
        tstco = new CacheObject("hank");
    }

    @Test
    public void testDefaultFail()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        chk = new AlwaysFailsCheck();
        try {
            chk.check(tstco);
            fail("IntegrityException not raised");
        }
        catch (IntegrityException ex) {
            assertEquals("Compulsory integrity failure", ex.getMessage());
        }
    }

    @Test
    public void testFailWithMessage()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        chk = new AlwaysFailsCheck("hello world");
        try {
            chk.check(tstco);
            fail("IntegrityException not raised");
        }
        catch (IntegrityException ex) {
            assertEquals("hello world", ex.getMessage());
        }
    }

    @Test
    public void testStorageVolumeFail()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        chk = new AlwaysFailsCheck(StorageVolumeException.class, "hello world");
        try {
            chk.check(tstco);
            fail("StorageVolumeException not raised");
        }
        catch (StorageVolumeException ex) {
            assertEquals("hello world", ex.getMessage());
        }
    }

    @Test
    public void testCacheManagementFail()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        chk = new AlwaysFailsCheck(CacheManagementException.class, "hello world");
        try {
            chk.check(tstco);
            fail("CacheManagementException not raised");
        }
        catch (CacheManagementException ex) {
            assertEquals("hello world", ex.getMessage());
        }
    }

    @Test
    public void testIntegrityFail()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        chk = new AlwaysFailsCheck(IntegrityException.class, "hello world");
        try {
            chk.check(tstco);
            fail("IntegrityException not raised");
        }
        catch (IntegrityException ex) {
            assertEquals("hello world", ex.getMessage());
        }
    }

    @Test
    public void testRuntimeFail()
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        chk = new AlwaysFailsCheck(NullPointerException.class, "hello world");
        try {
            chk.check(tstco);
            fail("NullPointerException not raised");
        }
        catch (NullPointerException ex) {
            assertEquals("hello world", ex.getMessage());
        }
    }

    @Test
    public void testBadException() {
        try {
            chk = new AlwaysFailsCheck(Exception.class, "hello world");
            fail("Failed to detect illegal exception");
        }
        catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().startsWith("Illegal argument: throwthis class"));
        }
    }
}

