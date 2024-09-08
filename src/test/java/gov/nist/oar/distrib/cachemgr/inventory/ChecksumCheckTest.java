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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;

public class ChecksumCheckTest {

    @TempDir
    public File tempDir;

    FilesystemCacheVolume vol = null;
    CacheObjectCheck chk = null;

    FilesystemCacheVolume makevol(String root) throws IOException {
        File rootdir = new File(tempDir, root);
        return new FilesystemCacheVolume(rootdir.toString(), root);
    }

    File _makefile(File target, String contents) throws IOException {
        try (PrintWriter w = new PrintWriter(target)) {
            w.println(contents);
        }
        return target;
    }

    CacheObject makeobj(FilesystemCacheVolume v, String name, String contents) throws IOException {
        File out = _makefile(new File(v.getRootDir(), name), contents);
        JSONObject md = new JSONObject();
        md.put("modified", FilesystemCacheVolume.getLastModifiedTimeOf(out));
        return new CacheObject(name, md, v);
    }

    @BeforeEach
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
        md.put("modified", co.getMetadatumLong("modified", -1L));
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
            fail("Failed to detect different size");
        } catch (ChecksumMismatchException ex) {
            assertEquals("size 12", ex.calculatedHash);
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

    @Test
    public void testModifiedCheck() throws IOException, CacheManagementException, StorageVolumeException {
        CacheObject co = makeobj(vol, "hello.txt", "hello world");
        String hash = "a948904f2f0f479b8f8197694b30184b0d2ed1c1cd2a1ec0fb85d299a192a447";
        long mod = co.getMetadatumLong("modified", -1L);
        assertTrue(mod > 0L, "Bad modified time: " + mod);

        JSONObject md = co.exportMetadata();
        md.put("size", 12L);
        md.put("checksum", hash);
        md.put("checksumAlgorithm", "sha256");
        co = new CacheObject(co.name, md, vol);

        // all good
        chk = new ChecksumCheck(false);
        chk.check(co);

        // now require matching dates; still good
        chk = new ChecksumCheck(true);
        chk.check(co);

        // now muck with the modified date
        md.put("modified", co.getMetadatumLong("modified", -1L)+2L);
        co = new CacheObject(co.name, md, vol);
        try {
            chk.check(co);
            fail("Failed to detect difference in mod time");
        }
        catch (ChecksumMismatchException ex) {
            assertEquals("modified "+Long.toString(mod), ex.calculatedHash);
            assertEquals(12L, ex.size);
        }
    }

    class HackVolume extends FilesystemCacheVolume {
        public HackVolume(File root) throws IOException {
            super(root);
        }

        @Override
        public CacheObject get(String name) throws StorageVolumeException {
            CacheObject out = super.get(name);
            JSONObject md = out.exportMetadata();
            md.put("volumeChecksum", "etag goober");
            return new CacheObject(out.name, md, this);
        }
    }

    @Test
    public void testVolChecksumCheck() throws IOException, CacheManagementException, StorageVolumeException {
        CacheObject co = makeobj(vol, "hello.txt", "hello world");
        FilesystemCacheVolume hv = new HackVolume(vol.getRootDir());
        String hash = "a948904f2f0f479b8f8197694b30184b0d2ed1c1cd2a1ec0fb85d299a192a447";
        co = hv.get("hello.txt");
        String vcs = co.getMetadatumString("volumeChecksum", "");
        assertEquals("etag goober", vcs);

        JSONObject md = co.exportMetadata();
        md.put("size", 12L);
        md.put("checksum", hash);
        md.put("checksumAlgorithm", "sha256");
        co = new CacheObject(co.name, md, hv);

        // all good
        chk = new ChecksumCheck(true, false);
        chk.check(co);

        // now leverage volume checksum; still good
        chk = new ChecksumCheck(false, true);
        chk.check(co);

        // now muck with the volume checksum
        md.put("volumeChecksum", "etag XXXXX");
        co = new CacheObject(co.name, md, hv);
        try {
            chk.check(co);
            fail("Failed to detect difference in volume checksum");
        }
        catch (ChecksumMismatchException ex) {
            assertEquals("etag goober", ex.calculatedHash);
            assertEquals(12L, ex.size);
        }

        // now muck with the modified time
        md.put("modified", co.getMetadatumLong("modified", -1L)+2L);
        co = new CacheObject(co.name, md, hv);
        try {
            chk.check(co);
            fail("Failed to detect difference in modified time");
        }
        catch (ChecksumMismatchException ex) {
            long mod = hv.get("hello.txt").getLastModified();
            assertEquals("modified "+Long.toString(mod), ex.calculatedHash);
            assertEquals(12L, ex.size);
        }
    }
}
