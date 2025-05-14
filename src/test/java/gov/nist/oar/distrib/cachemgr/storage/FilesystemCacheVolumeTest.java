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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;

public class FilesystemCacheVolumeTest {

    @TempDir
    File tempf;

    FilesystemCacheVolume makevol(String root) throws IOException {
        File rootdir = new File(tempf, root);
        rootdir.mkdir();
        return new FilesystemCacheVolume(rootdir.toString(), root);
    }

    File makefile(String contents) throws IOException {
        return _makefile(new File(tempf, "tempfile"), contents);
    }

    File _makefile(File target, String contents) throws IOException {
        try (PrintWriter w = new PrintWriter(target)) {
            w.println(contents);
        }
        return target;
    }

    CacheObject makeobj(FilesystemCacheVolume v, String name, String contents) throws IOException {
        _makefile(new File(v.getRootDir(), name), contents);
        return new CacheObject(name, v);
    }

    String getFileContents(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (FileReader r = new FileReader(f)) {
            char[] buf = new char[20];
            int n;
            while ((n = r.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    @Test
    public void testGetName() throws IOException {
        CacheVolume v = makevol("goob");
        assertEquals("goob", v.getName());
    }

    @Test
    public void testExists() throws StorageVolumeException, IOException {
        CacheVolume v = makevol("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        File obj = new File(tempf, "root/goob");
        obj.createNewFile();
        assertTrue(v.exists("goob"), "Failed to find object in store");
    }

    @Test
    public void testInputStreamSaveAs() throws StorageVolumeException, IOException {
        File obj = makefile("hello world");

        FilesystemCacheVolume v = makevol("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        JSONObject md = new JSONObject();
        try (FileInputStream fs = new FileInputStream(obj)) {
            v.saveAs(fs, "goob", md);
        }
        File out = new File(v.getRootDir(), "goob");
        assertTrue(out.exists());
        assertTrue(v.exists("goob"), "Failed to find object in store");
        assertEquals("hello world\n", getFileContents(out));
        assertTrue(md.has("modified"), "Metadata not updated with 'modified'");
        long mod = md.getLong("modified");
        assertTrue(mod > 0L, "Mod date not set: " + Long.toString(mod));
    }

    @Test
    public void testNoMetadataSaveAs() throws StorageVolumeException, IOException {
        File obj = makefile("hello world");

        FilesystemCacheVolume v = makevol("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        try (FileInputStream fs = new FileInputStream(obj)) {
            v.saveAs(fs, "goob", null);
        }
        File out = new File(v.getRootDir(), "goob");
        assertTrue(out.exists());
        assertTrue(v.exists("goob"), "Failed to find object in store");
        assertEquals("hello world\n", getFileContents(out));
    }

    @Test
    public void testCacheObjectSaveAs() throws StorageVolumeException, IOException {
        FilesystemCacheVolume v = makevol("root");
        CacheObject co = makeobj(v, "goob", "hello world");

        assertFalse(v.exists("hank"), "Mistakenly believes non-existent object exists");
        v.saveAs(co, "hank");
        assertTrue(v.exists("hank"), "Failed to find object in store");
        assertEquals("hello world\n", getFileContents(new File(v.getRootDir(), "hank")));
    }

    @Test
    public void testGetStream() throws StorageVolumeException, IOException {
        FilesystemCacheVolume v = makevol("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        makeobj(v, "goob", "hello world");
        assertTrue(v.exists("goob"), "Failed to find object in store");

        StringBuilder sb = new StringBuilder();
        try (InputStream is = v.getStream("goob");
             InputStreamReader r = new InputStreamReader(is)) {
            char[] buf = new char[20];
            int n;
            while ((n = r.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
        }
        assertEquals("hello world\n", sb.toString());
    }

    @Test
    public void testGet() throws StorageVolumeException, IOException {
        Instant nowi = Instant.now();
        long now = nowi.getEpochSecond() * 1000;
        try { Thread.sleep(1000); } catch (InterruptedException ex) { fail("Interrupted!"); }
        FilesystemCacheVolume v = makevol("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        CacheObject co = makeobj(v, "goob", "hello world");
        assertTrue(v.exists("goob"), "Failed to find object in store");

        co = v.get("goob");
        assertEquals("root", co.volname);
        assertEquals("root", co.volume.getName());
        assertEquals("goob", co.name);
        assertEquals(12, co.getSize());
        long mod = co.getLastModified();
        assertTrue(mod > now, "Bad mod time: " + Long.toString(mod) + " !> " + Long.toString(now));
    }

    @Test
    public void testRemove() throws StorageVolumeException, IOException {
        FilesystemCacheVolume v = makevol("root");
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
        assertFalse(v.remove("goob"));
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");

        makeobj(v, "goob", "hello world");
        assertTrue(v.exists("goob"), "Failed to find object in store");

        assertTrue(v.remove("goob"));
        assertFalse(v.exists("goob"), "Mistakenly believes non-existent object exists");
    }

    @Test
    public void testRedirectForUnsupported() throws StorageVolumeException, IOException {
        FilesystemCacheVolume v = makevol("root");
        assertThrows(UnsupportedOperationException.class, () -> v.getRedirectFor("goober"));
    }

    @Test
    public void testRedirectFor() throws StorageVolumeException, IOException, MalformedURLException {
        String root = "root";
        File rootdir = new File(tempf, root);
        rootdir.mkdir();
        FilesystemCacheVolume v = new FilesystemCacheVolume(rootdir.toString(), root, "https://ex.org/");
        try {
            // Use URI instead of URL
            assertEquals(new URI("https://ex.org/goober"), v.getRedirectFor("goober").toURI());
            assertEquals(new URI("https://ex.org/i%20a/m%20groot"), v.getRedirectFor("i a/m groot").toURI());

            // New test case with Unicode and special characters like α,β,γ
            String name = "folder/EDS map αβγ #file.tiff";
            String expected = "https://ex.org/folder/EDS%20map%20%CE%B1%CE%B2%CE%B3%20%23file.tiff";
            assertEquals(new URI(expected), v.getRedirectFor(name).toURI());
        } catch (URISyntaxException e) {
            fail("URI syntax is incorrect: " + e.getMessage());
        }
    }
}
