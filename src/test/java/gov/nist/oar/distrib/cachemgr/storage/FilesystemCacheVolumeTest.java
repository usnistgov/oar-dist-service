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

import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.net.URL;
import java.net.MalformedURLException;

import org.json.JSONObject;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;

public class FilesystemCacheVolumeTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    FilesystemCacheVolume makevol(String root) throws IOException {
        File rootdir = tempf.newFolder(root);
        return new FilesystemCacheVolume(rootdir.toString(), root);
    }

    File makefile(String contents) throws IOException {
        return _makefile(tempf.newFile(), contents);
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

    String getFileContents(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        FileReader r = new FileReader(f);
        char[] buf = new char[20];
        int n = 0;
        try {
            while ((n = r.read(buf)) >= 0) 
                sb.append(buf, 0, n);
        }
        finally {
            r.close();
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
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
        tempf.newFile("root/goob");
        assertTrue("Failed to find object in store", v.exists("goob"));
    }

    @Test
    public void testInputStreamSaveAs() throws StorageVolumeException, IOException {
        // create the file to stream
        File obj = makefile("hello world");

        // now stream it to the store
        FilesystemCacheVolume v = makevol("root");
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
        FileInputStream fs = new FileInputStream(obj);
        JSONObject md = new JSONObject();
        try {
            v.saveAs(fs, "goob", md);
        } finally { fs.close(); }
        File out = new File(v.getRootDir(), "goob");
        assertTrue(out.exists());
        assertTrue("Failed to find object in store", v.exists("goob"));
        assertEquals("hello world\n", getFileContents(out));
        long mod = md.getLong("modified");
        assertTrue("metadata not updated with 'modified'", md.has("modified"));
        assertTrue("Mod date not set: "+Long.toString(mod), mod > 0L);
    }

    @Test
    public void testNoMetadataSaveAs() throws StorageVolumeException, IOException {
        // create the file to stream
        File obj = makefile("hello world");

        // now stream it to the store
        FilesystemCacheVolume v = makevol("root");
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
        FileInputStream fs = new FileInputStream(obj);
        try {
            v.saveAs(fs, "goob", null);
        } finally { fs.close(); }
        File out = new File(v.getRootDir(), "goob");
        assertTrue(out.exists());
        assertTrue("Failed to find object in store", v.exists("goob"));
        assertEquals("hello world\n", getFileContents(out));
    }

    @Test
    public void testCacheObjectSaveAs() throws StorageVolumeException, IOException {
        // create the file to stream
        FilesystemCacheVolume v = makevol("root");
        CacheObject co = makeobj(v, "goob", "hello world");

        // now test transfer
        assertFalse("Mistakenly believes non-existent object exists", v.exists("hank"));
        v.saveAs(co, "hank");
        assertTrue("Failed to find object in store", v.exists("hank"));
        assertEquals("hello world\n", getFileContents(new File(v.getRootDir(), "hank")));
    }

    @Test
    public void testGetStream() throws StorageVolumeException, IOException {
        FilesystemCacheVolume v = makevol("root");
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
        CacheObject co = makeobj(v, "goob", "hello world");
        assertTrue("Failed to find object in store", v.exists("goob"));

        StringBuilder sb = new StringBuilder();
        InputStream is = v.getStream("goob");
        InputStreamReader r = new InputStreamReader(is);
        char[] buf = new char[20];
        int n = 0;
        try {
            while ((n = r.read(buf)) >= 0) 
                sb.append(buf, 0, n);
        }
        finally {
            r.close();
        }
        assertEquals("hello world\n", sb.toString());
    }

    @Test
    public void testGet() throws StorageVolumeException, IOException {
        Instant nowi = Instant.now();
        long now = nowi.getEpochSecond() * 1000;
        try { Thread.sleep(1000); } catch (InterruptedException ex) { fail("Interrupted!"); }
        FilesystemCacheVolume v = makevol("root");
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
        CacheObject co = makeobj(v, "goob", "hello world");
        assertTrue("Failed to find object in store", v.exists("goob"));

        co = v.get("goob");
        assertEquals("root", co.volname);
        assertEquals("root", co.volume.getName());
        assertEquals("goob", co.name);
        assertEquals(12, co.getSize());
        long mod = co.getLastModified();
        assertTrue("Bad mod time: "+Long.toString(mod)+" !> "+Long.toString(now), mod > now);
    }

    @Test
    public void testRemove() throws StorageVolumeException, IOException {
        FilesystemCacheVolume v = makevol("root");
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
        assertFalse(v.remove("goob"));
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));

        CacheObject co = makeobj(v, "goob", "hello world");
        assertTrue("Failed to find object in store", v.exists("goob"));

        assertTrue(v.remove("goob"));
        assertFalse("Mistakenly believes non-existent object exists", v.exists("goob"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRedirectForUnsupported()
        throws StorageVolumeException, UnsupportedOperationException, IOException
    {
        FilesystemCacheVolume v = makevol("root");
        v.getRedirectFor("goober");
    }

    @Test
    public void testRedirectFor()
        throws StorageVolumeException, UnsupportedOperationException, IOException, MalformedURLException
    {
        String root = "root";
        File rootdir = tempf.newFolder(root);
        FilesystemCacheVolume v = new FilesystemCacheVolume(rootdir.toString(), root, "https://ex.org/");
        assertEquals(new URL("https://ex.org/goober"), v.getRedirectFor("goober"));
        assertEquals(new URL("https://ex.org/i%20a/m%20groot"), v.getRedirectFor("i a/m groot"));
    }
}
