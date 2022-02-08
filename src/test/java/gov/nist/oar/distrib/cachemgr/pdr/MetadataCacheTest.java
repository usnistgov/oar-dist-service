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

import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MetadataCacheTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    MetadataCache mdcache = null;

    @Before
    public void setUp() throws IOException {
        mdcache = new MetadataCache(tempf.getRoot().toPath());
    }

    @After
    public void tearDown() {
        tempf.delete();
    }

    @Test
    public void testCtor() {
        try {
            mdcache = new MetadataCache(tempf.getRoot().toPath().resolve("goober"));
            fail("Failed to raise FileNotFoundException");
        }
        catch (FileNotFoundException ex) { }
        catch (IOException ex) {
            fail("Unexpected IOException thrown: "+ex.getMessage());
        }
    }

    @Test
    public void testEnsureDatasetDir() throws IOException {
        try {
            mdcache.ensureDatasetDir("", "1.0.0");
            fail("Failed to catch empty id string");
        } catch (IllegalArgumentException ex) { }
        try {
            mdcache.ensureDatasetDir("goobber", "");
            fail("Failed to catch empty id string");
        } catch (IllegalArgumentException ex) { }
        try {
            mdcache.ensureDatasetDir("goobber", null);
            fail("Failed to catch empty id string");
        } catch (NullPointerException ex) { }
        try {
            mdcache.ensureDatasetDir(null, "1.0.0");
            fail("Failed to catch empty id string");
        } catch (NullPointerException ex) { }

        Path dir = tempf.getRoot().toPath().resolve("goober").resolve("1.0.0");
        assertEquals(dir, mdcache.ensureDatasetDir("goober", "1.0.0"));
        assertTrue("Missing AIP dir", Files.isDirectory(dir.getParent()));
        assertTrue("Missing version dir", Files.isDirectory(dir));
    }

    @Test
    public void testForgetEmpty() throws IOException {
        Path dir = tempf.getRoot().toPath().resolve("goober").resolve("1.0.0");
        assertEquals(dir, mdcache.ensureDatasetDir("goober", "1.0.0"));
        assertTrue("Missing AIP dir", Files.isDirectory(dir.getParent()));
        assertTrue("Missing version dir", Files.isDirectory(dir));

        mdcache.forget("goober", "2.0");
        assertTrue("Missing AIP dir", Files.isDirectory(dir.getParent()));
        assertTrue("Missing version dir", Files.isDirectory(dir));

        mdcache.forget("goober", "1.0.0");
        assertTrue("Version dir not removed", ! Files.exists(dir));
        assertTrue("AIP dir not removed", ! Files.exists(dir.getParent()));
    }

    @Test
    public void testLatestVersion() throws IOException {
        assertNull(mdcache.getLatestVersion("gurn"));
        Path dsdir = tempf.getRoot().toPath().resolve("gurn");
        assertTrue("AIP dir unexpectedly exists", ! Files.exists(dsdir));
        
        mdcache.setLatestVersion("gurn", "1.0");
        assertEquals("1.0", mdcache.getLatestVersion("gurn"));
        assertTrue("Missing AIP dir", Files.isDirectory(dsdir));
        assertTrue("Missing version dir", Files.isDirectory(dsdir.resolve("1.0")));
        assertTrue("Missing latest file", Files.isRegularFile(dsdir.resolve("latest_version")));
        
        mdcache.setLatestVersion("gurn", "1.0.3");
        assertEquals("1.0.3", mdcache.getLatestVersion("gurn"));
        assertTrue("Missing AIP dir", Files.isDirectory(dsdir));
        assertTrue("Missing version dir", Files.isDirectory(dsdir.resolve("1.0.3")));
        assertTrue("Missing latest file", Files.isRegularFile(dsdir.resolve("latest_version")));

        mdcache.forget("gurn", "1.0.3");
        assertTrue("Version dir not removed", ! Files.exists(dsdir.resolve("1.0.3")));
        assertTrue("Missing version dir", Files.isDirectory(dsdir.resolve("1.0")));
        assertTrue("Missing AIP dir", Files.isDirectory(dsdir));
        assertTrue("Missing latest file", Files.isRegularFile(dsdir.resolve("latest_version")));
        assertEquals("1.0.3", mdcache.getLatestVersion("gurn"));

        mdcache.forget("gurn", "1.0");
        assertTrue("Version dir not removed", ! Files.exists(dsdir.resolve("1.0.3")));
        assertTrue("Version dir not removed", ! Files.exists(dsdir.resolve("1.0")));
        assertTrue("Missing AIP dir", Files.isDirectory(dsdir));
        assertTrue("Missing latest file", Files.isRegularFile(dsdir.resolve("latest_version")));
        assertEquals("1.0.3", mdcache.getLatestVersion("gurn"));
    }

    @Test
    public void testWriteMetadata() throws IOException {
        JSONObject md = new JSONObject();
        md.put("count", 3);
        Path dest = tempf.getRoot().toPath().resolve("gurn.json");
        assertTrue(! Files.exists(dest));

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
        
        Path dest = tempf.getRoot().toPath().resolve("gurn").resolve("1.0rc1").resolve("a:b.txt.json");
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
        
        Path dsdir = tempf.getRoot().toPath().resolve("gary");
        assertTrue("Missing AIP dir", Files.isDirectory(dsdir));
        assertNull(JSONObject.getNames(mdcache.getMetadataForCache("gary", "a/b.txt", "1")));

        fl.map("a/b.txt", "gary.1.mbag0_4-0");
        assertEquals("gary.1.mbag0_4-0", fl.getMemberBagFor("a/b.txt"));
        assertTrue("AIP dir missing", Files.isDirectory(dsdir));
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
        assertTrue("Missing bag name: gary.1.mbag0_4-0", bags.contains("gary.1.mbag0_4-0"));
        assertTrue("Missing bag name: gary.1.mbag0_4-1", bags.contains("gary.1.mbag0_4-1"));
        bags = null;
        bags = mdcache.getMemberBags("gary", "1");
        assertEquals(2, bags.size());
        assertTrue("Missing bag name: gary.1.mbag0_4-0", bags.contains("gary.1.mbag0_4-0"));
        assertTrue("Missing bag name: gary.1.mbag0_4-1", bags.contains("gary.1.mbag0_4-1"));

        // FileLookup.getDataFilesInBag()
        Collection<String> files = fl.getDataFilesInBag("gary.1.mbag0_4-1");
        assertEquals(2, files.size());
        assertTrue("Missing file name: c.json", files.contains("c.json"));
        assertTrue("Missing file name: d.json", files.contains("d.json"));
        files = fl.getDataFilesInBag("gary.1.mbag0_4-0");
        assertEquals(1, files.size());
        assertTrue("Missing file name: a/b.txt", files.contains("a/b.txt"));

        files = mdcache.getDataFilesInBag("gary", "1", "gary.1.mbag0_4-1");
        assertEquals(2, files.size());
        assertTrue("Missing file name: c.json", files.contains("c.json"));
        assertTrue("Missing file name: d.json", files.contains("d.json"));

        // test reloading from disk
        mdcache = new MetadataCache(tempf.getRoot().toPath());
        files = mdcache.getDataFilesInBag("gary", "1", "gary.1.mbag0_4-1");
        assertEquals(2, files.size());
        assertTrue("Missing file name: c.json", files.contains("c.json"));
        assertTrue("Missing file name: d.json", files.contains("d.json"));
    }
}
