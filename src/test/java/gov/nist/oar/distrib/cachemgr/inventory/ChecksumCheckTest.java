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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;

import org.json.JSONObject;
import org.json.JSONException;

public class ChecksumCheckTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    FilesystemCacheVolume vol = null;
    CacheObjectCheck chk = null;

    FilesystemCacheVolume makevol(String root) throws IOException {
        File rootdir = tempf.newFolder(root);
        return new FilesystemCacheVolume(rootdir.toString(), root);
    }

    File _makefile(File target, String contents) throws IOException {
        PrintWriter w = new PrintWriter(target);
        try {
            w.println(contents);
        }
        finally {
            w.close();
        }
        return target;
    }

    CacheObject makeobj(FilesystemCacheVolume v, String name, String contents) throws IOException {
        File out = _makefile(new File(v.getRootDir(), name), contents);
        return new CacheObject(name, v);
    }

    @Before
    public void setUp() throws IOException {
        vol = makevol("goob");
        chk = new ChecksumCheck();
    }        

    @Test
    public void testCheck() throws IOException, CacheManagementException, StorageVolumeException {
        CacheObject co = makeobj(vol, "hello.txt", "hello world");
        try {
            chk.check(co);
            fail("Failed to report missing metadata in CacheObject");
        } catch (CacheManagementException ex) {  }
        
        String hash = "a948904f2f0f479b8f8197694b30184b0d2ed1c1cd2a1ec0fb85d299a192a447";
        JSONObject md = new JSONObject();
        md.put("size", 12L);
        md.put("checksum", hash);
        md.put("checksumAlgorithm", "sha256");
        co = new CacheObject(co.name, md, vol);

        chk.check(co);

        md.put("checksum", "XXXXXXX");
        co = new CacheObject(co.name, md, vol);
        try {
            chk.check(co);
            fail("Failed to detect different checksum");
        } catch (ChecksumMismatchException ex) {
            assertEquals(hash, ex.calculatedHash);
            assertEquals(12L, ex.size);
        }

        md.put("size", 14L);
        co = new CacheObject(co.name, md, vol);
        try {
            chk.check(co);
            fail("Failed to detect different checksum");
        } catch (ChecksumMismatchException ex) {
            assertNull(ex.calculatedHash);
            assertEquals(12L, ex.size);
        }

        md.put("checksumAlgorithm", "md5");
        co = new CacheObject(co.name, md, vol);
        try {
            chk.check(co);
            fail("Failed to complain about unsupported checksum algorithm");
        } catch (CacheManagementException ex) {  }

        co.name = "gurn.txt";
        try {
            chk.check(co);
            fail("Failed to report missing cache object");
        } catch (ObjectNotFoundException ex) {  }
    }
}
