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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MetadataCacheTest {

    @TempDir
    Path tempf;

    MetadataCache mdcache = null;

    @BeforeEach
    public void setUp() throws IOException {
        mdcache = new MetadataCache(tempf);
    }

    @AfterEach
    public void tearDown() {
        // Cleanup not needed as @TempDir manages file deletion
    }

    @Test
    public void testCtor() {
        try {
            mdcache = new MetadataCache(tempf.resolve("goober"));
            fail("Failed to raise FileNotFoundException");
        } catch (FileNotFoundException ex) {
            // Expected
        } catch (IOException ex) {
            fail("Unexpected IOException thrown: " + ex.getMessage());
        }
    }

    @Test
    public void testEnsureDatasetDir() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> mdcache.ensureDatasetDir("", "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> mdcache.ensureDatasetDir("goobber", ""));
        assertThrows(NullPointerException.class, () -> mdcache.ensureDatasetDir("goobber", null));
        assertThrows(NullPointerException.class, () -> mdcache.ensureDatasetDir(null, "1.0.0"));

        Path dir = tempf.resolve("goober").resolve("1.0.0");
        assertEquals(dir, mdcache.ensureDatasetDir("goober", "1.0.0"));
        assertTrue(Files.isDirectory(dir.getParent()), "Missing AIP dir");
        assertTrue(Files.isDirectory(dir), "Missing version dir");
    }

    @Test
    public void testForgetEmpty() throws IOException {
        Path dir = tempf.resolve("goober").resolve("1.0.0");
        assertEquals(dir, mdcache.ensureDatasetDir("goober", "1.0.0"));
        assertTrue(Files.isDirectory(dir.getParent()), "Missing AIP dir");
        assertTrue(Files.isDirectory(dir), "Missing version dir");

        mdcache.forget("goober", "2.0");
        assertTrue(Files.isDirectory(dir.getParent()), "Missing AIP dir");
        assertTrue(Files.isDirectory(dir), "Missing version dir");

        mdcache.forget("goober", "1.0.0");
        assertFalse(Files.exists(dir), "Version dir not removed");
        assertFalse(Files.exists(dir.getParent()), "AIP dir not removed");
    }

    @Test
    public void testLatestVersion() throws IOException {
        assertNull(mdcache.getLatestVersion("gurn"));
        Path dsdir = tempf.resolve("gurn");
        assertFalse(Files.exists(dsdir), "AIP dir unexpectedly exists");

        mdcache.setLatestVersion("gurn", "1.0");
        assertEquals("1.0", mdcache.getLatestVersion("gurn"));
        assertTrue(Files.isDirectory(dsdir), "Missing AIP dir");
        assertTrue(Files.isDirectory(dsdir.resolve("1.0")), "Missing version dir");
        assertTrue(Files.isRegularFile(dsdir.resolve("latest_version")), "Missing latest file");

        mdcache.setLatestVersion("gurn", "1.0.3");
        assertEquals("1.0.3", mdcache.getLatestVersion("gurn"));
        assertTrue(Files.isDirectory(dsdir), "Missing AIP dir");
        assertTrue(Files.isDirectory(dsdir.resolve("1.0.3")), "Missing version dir");
        assertTrue(Files.isRegularFile(dsdir.resolve("latest_version")), "Missing latest file");

        mdcache.forget("gurn", "1.0.3");
        assertFalse(Files.exists(dsdir.resolve("1.0.3")), "Version dir not removed");
        assertTrue(Files.isDirectory(dsdir.resolve("1.0")), "Missing version dir");
        assertTrue(Files.isDirectory(dsdir), "Missing AIP dir");
        assertTrue(Files.isRegularFile(dsdir.resolve("latest_version")), "Missing latest file");
        assertEquals("1.0.3", mdcache.getLatestVersion("gurn"));

        mdcache.forget("gurn", "1.0");
        assertFalse(Files.exists(dsdir.resolve("1.0.3")), "Version dir not removed");
        assertFalse(Files.exists(dsdir.resolve("1.0")), "Version dir not removed");
        assertTrue(Files.isDirectory(dsdir), "Missing AIP dir");
        assertTrue(Files.isRegularFile(dsdir.resolve("latest_version")), "Missing latest file");
        assertEquals("1.0.3", mdcache.getLatestVersion("gurn"));
    }

    @Test
    public void testWriteMetadata() throws IOException {
        JSONObject md = new JSONObject();
        md.put("count", 3);
        Path dest = tempf.resolve("gurn.json");
        assertFalse(Files.exists(dest));

        mdcache.writeMetadata(md, dest);
        assertTrue(Files.isRegularFile(dest));

        md = new JSONObject(new JSONTokener(new FileReader(dest.toFile())));
        String[] names = JSONObject.getNames(md);
        assertEquals(1, names.length);
        assertEquals("count", names[0]);
    }

    @Test
    public void testFileMetadata() throws IOException {
        JSONObject md = mdcache.getMetadataForCache("gurn", "a/b.txt", "1.0rc1");
        assertNull(JSONObject.getNames(md));

        md = new JSONObject();
        md.put("count", 3);
        try {
            mdcache.cacheFileMetadata("gurn", "1.0rc1", md);
            fail("Failed to catch missing filepath property");
        } catch (IllegalArgumentException ex) { }
        
        md.put("filepath", "a/b.txt");
        mdcache.cacheFileMetadata("gurn", "1.0rc1", md);

        Path dest = tempf.resolve("gurn").resolve("1.0rc1").resolve("a:b.txt.json");
        assertTrue(Files.isRegularFile(dest));

        md = mdcache.getMetadataForCache("gurn", "a/b.txt", "1.0");
        assertNull(JSONObject.getNames(md));

        md = new JSONObject(new JSONTokener(new FileReader(dest.toFile())));
        assertEquals(2, JSONObject.getNames(md).length);
        assertEquals("a/b.txt", md.getString("filepath"));
        assertTrue(md.similar(md));

        md = mdcache.getMetadataForCache("gurn", "b.txt", "1.0rc1");
        assertNull(JSONObject.getNames(md));

        md = mdcache.getMetadataForCache("gurn", "a/b.txt", "1.0rc1");
        assertEquals(2, JSONObject.getNames(md).length);
        assertEquals("a/b.txt", md.getString("filepath"));
        assertTrue(md.similar(md));
    }

    @Test
    public void testFileLookup() throws IOException {
        MetadataCache.FileLookup fl = mdcache.getFileLookup("gary", "1");

        Path dsdir = tempf.resolve("gary");
        assertTrue(Files.isDirectory(dsdir), "Missing AIP dir");
        assertNull(JSONObject.getNames(mdcache.getMetadataForCache("gary", "a/b.txt", "1")));

        fl.map("a/b.txt", "gary.1.mbag0_4-0");
        assertEquals("gary.1.mbag0_4-0", fl.getMemberBagFor("a/b.txt"));
        assertTrue(Files.isDirectory(dsdir), "AIP dir missing");
        JSONObject md = mdcache.getMetadataForCache("gary", "a/b.txt", "1");
        assertEquals(2, JSONObject.getNames(md).length);
        assertEquals("gary.1.mbag0_4-0", md.getString("bagfile"));
        assertEquals("a/b.txt", md.getString("filepath"));

        md = new JSONObject();
        md.put("count", 3);
        md.put("filepath", "a/b.txt");
        mdcache.cacheFileMetadata("gary", "1", md);

        md = null;
        md = mdcache.getMetadataForCache("gary", "a/b.txt", "1");
        assertEquals(3, JSONObject.getNames(md).length);
        assertEquals("gary.1.mbag0_4-0", md.getString("bagfile"));
        assertEquals("a/b.txt", md.getString("filepath"));
        assertEquals(3, md.getInt("count"));

        fl.map("c.json", "gary.1.mbag0_4-1");
        assertEquals("gary.1.mbag0_4-1", fl.getMemberBagFor("c.json"));
        assertEquals("gary.1.mbag0_4-0", fl.getMemberBagFor("a/b.txt"));
        md = mdcache.getMetadataForCache("gary", "c.json", "1");
        assertEquals(2, JSONObject.getNames(md).length);
        assertEquals("gary.1.mbag0_4-1", md.getString("bagfile"));
        assertEquals("c.json", md.getString("filepath"));

        fl.map("d.json", "gary.1.mbag0_4-1");
        assertEquals("gary.1.mbag0_4-1", fl.getMemberBagFor("d.json"));
        assertEquals("gary.1.mbag0_4-1", fl.getMemberBagFor("c.json"));
        assertEquals("gary.1.mbag0_4-0", fl.getMemberBagFor("a/b.txt"));
        md = mdcache.getMetadataForCache("gary", "d.json", "1");
        assertEquals(2, JSONObject.getNames(md).length);
        assertEquals("gary.1.mbag0_4-1", md.getString("bagfile"));
        assertEquals("d.json", md.getString("filepath"));

        // FileLookup.getMemberBags()
        Collection<String> bags = fl.getMemberBags();
        assertEquals(2, bags.size());
        assertTrue(bags.contains("gary.1.mbag0_4-0"), "Missing bag name: gary.1.mbag0_4-0");
        assertTrue(bags.contains("gary.1.mbag0_4-1"), "Missing bag name: gary.1.mbag0_4-1");
        bags = null;
        bags = mdcache.getMemberBags("gary", "1");
        assertEquals(2, bags.size());
        assertTrue(bags.contains("gary.1.mbag0_4-0"), "Missing bag name: gary.1.mbag0_4-0");
        assertTrue(bags.contains("gary.1.mbag0_4-1"), "Missing bag name: gary.1.mbag0_4-1");

        // FileLookup.getDataFilesInBag()
        Collection<String> files = fl.getDataFilesInBag("gary.1.mbag0_4-1");
        assertEquals(2, files.size());
        assertTrue(files.contains("c.json"), "Missing file name: c.json");
        assertTrue(files.contains("d.json"), "Missing file name: d.json");
        files = fl.getDataFilesInBag("gary.1.mbag0_4-0");
        assertEquals(1, files.size());
        assertTrue(files.contains("a/b.txt"), "Missing file name: a/b.txt");

        files = mdcache.getDataFilesInBag("gary", "1", "gary.1.mbag0_4-1");
        assertEquals(2, files.size());
        assertTrue(files.contains("c.json"), "Missing file name: c.json");
        assertTrue(files.contains("d.json"), "Missing file name: d.json");

        // test reloading from disk
        mdcache = new MetadataCache(tempf);
        files = mdcache.getDataFilesInBag("gary", "1", "gary.1.mbag0_4-1");
        assertEquals(2, files.size());
        assertTrue(files.contains("c.json"), "Missing file name: c.json");
        assertTrue(files.contains("d.json"), "Missing file name: d.json");
    }
}
