/*
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import gov.nist.oar.bags.preservation.BagUtils;

/**
 * An interface for caching metadata information about datasets as part saving data file to the 
 * data {@link gov.nist.oar.distrib.cachemgr.Cache}.  It is used primarily within the 
 * {@link BagCacher} class.  
 * <p>
 * Note that "caching metadata" means storing this information in the local filesystem separate from 
 * the data {@link gov.nist.oar.distrib.cachemgr.Cache}.  
 */
public class MetadataCache {

    Path basedir = null;
    Map<String, FileLookup> flus = new HashMap<String, FileLookup>();

    /**
     * create the cache.  
     * @param storageDir   a directory below which the metadata information is saved.
     * @throws FileNotFoundException   if the given path is not an existing directory
     * @throws IOException  if it is not possible to write to the directory
     */
    public MetadataCache(Path storageDir) throws IOException {
        basedir = storageDir;
        testWriteAccess();
    }

    void testWriteAccess() throws IOException {
        if (! Files.exists(basedir))
            throw new FileNotFoundException("basedir does not exist: "+basedir.toString());
        if (! Files.isDirectory(basedir))
            throw new FileNotFoundException("basedir is not a directory: "+basedir.toString());
        Path temp = null;
        try {
            temp = Files.createTempFile(basedir, null, null);
        } catch (SecurityException ex) {
            throw new IOException("Unable to create metadata cache files (in "+basedir.toString()+
                                  "): permission denied", ex);
        }
        finally {
            if (temp != null)
                Files.deleteIfExists(temp);
        }
    }

    Path ensureDatasetDir(String aipid, String version) throws IOException {
        if (aipid.length() == 0)
            throw new IllegalArgumentException("aipid: empty string");
        if (version.length() == 0)
            throw new IllegalArgumentException("version: empty string");

        Path dsdir = basedir.resolve(aipid).resolve(version);
        if (! Files.isDirectory(dsdir)) {
            try {
                Files.createDirectories(dsdir);
            } catch (SecurityException ex) {
                throw new IOException("Unable to create metadata cache files ("+dsdir.toString()+
                                      "): permission denied", ex);
            }
        }
        return dsdir;
    }

    /**
     * clear cached information for the specified dataset
     */
    public synchronized void forget(String aipid, String version) throws IOException {
        Path aipdir = basedir.resolve(aipid);
        if (! Files.exists(aipdir))
            return;
        Path dsdir = aipdir.resolve(version);
        
        if (Files.exists(dsdir)) {
            // delete the directory and its contents
            try (Stream<Path> walk = Files.walk(dsdir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }

            try {
                // try getting rid of the parent directory
                Files.delete(aipdir);
            } catch (DirectoryNotEmptyException ex) { }
        }
    }

    /**
     * record what has been determined to be the latest available version of a dataset.
     * @param aipid    the ID of the dataset being tagged
     * @param version  the version determined to be the latest
     */
    public synchronized void setLatestVersion(String aipid, String version) throws IOException {
        ensureDatasetDir(aipid, version);
        Path lvfile = basedir.resolve(aipid).resolve("latest_version");
        BufferedWriter wrtr = new BufferedWriter(new FileWriter(lvfile.toFile()));
        try {
            wrtr.append(version);
            wrtr.newLine();
        }
        finally {
            try { wrtr.close(); } catch (IOException ex) { }
        }
    }

    /**
     * return what has been registered as the label for the latest version of the dataset, or 
     * null if one has not bee registered. 
     */
    public synchronized String getLatestVersion(String aipid) throws IOException {
        Path lvfile = basedir.resolve(aipid).resolve("latest_version");
        if (! Files.exists(lvfile))
            return null;

        BufferedReader rdr = new BufferedReader(new FileReader(lvfile.toFile()));
        try {
            String out = rdr.readLine();
            if (out != null) out = out.trim();
            return out;
        }
        finally {
            try { rdr.close(); } catch (IOException ex) { }
        }
    }

    /**
     * return a list of the archive bags that contain a particular version of a dataset
     */
    public Deque<String> getMemberBags(String aipid, String version) throws IOException {
        return getFileLookup(aipid, version).getMemberBags();
    }

    /**
     * return the set of file paths for all data files that should be available from the given member bag
     */
    public Collection<String> getDataFilesInBag(String aipid, String version, String memberbag)
        throws IOException
    {
        return getFileLookup(aipid, version).getDataFilesInBag(memberbag);
    }

    /**
     * save the file metadata found in a given NERDm record.  The given JSON object must include 
     * a "filepath" property.
     */
    public synchronized void cacheFileMetadata(String aipid, String version, JSONObject md)
        throws IOException, JSONException
    {
        String filepath = md.optString("filepath");
        if (filepath == null)
            throw new IllegalArgumentException("JSONObject md: missing 'filepath' property");
        if (filepath.length() == 0)
            throw new IllegalArgumentException("JSONObject md: empty 'filepath' property");
        JSONObject cached = getMetadataForCache(aipid, filepath, version);

        for(String key : md.keySet())
            cached.put(key, md.get(key));

        Path dsdir = ensureDatasetDir(aipid, version);
        Path mdfile = dsdir.resolve(filepath.replace("/", ":") + ".json");
        writeMetadata(cached, mdfile);
    }

    void writeMetadata(JSONObject md, Path dest) throws IOException, JSONException {
        Path temp = Files.createTempFile(dest.getParent(), "datafile", ".json");
        try {
            FileWriter wrtr = new FileWriter(temp.toFile());
            try {
                md.write(wrtr, 2, 0);
            }
            finally {
                try { wrtr.close(); } catch (IOException ex) { }
            }
            Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * return the key distribution metadata to be stored in the cache's inventory for a particular 
     * data file from a dataset.  The returned object will be empty if no metadata has been saved yet. 
     */
    public synchronized JSONObject getMetadataForCache(String aipid, String filepath, String version)
        throws IOException, JSONException
    {
        if (filepath.length() == 0)
            throw new IllegalArgumentException("filepath: empty string");
        
        Path dsdir = ensureDatasetDir(aipid, version);
        Path mdfile = dsdir.resolve(filepath.replace("/", ":") + ".json");
        if (! Files.exists(mdfile))
            return new JSONObject();

        FileReader rdr = new FileReader(mdfile.toFile());
        JSONObject out = null;
        try {
            // define out instead of return
            out = new JSONObject(new JSONTokener(rdr));
        }
        finally {
            try { rdr.close(); } catch (IOException ex) { }
        }

        // Read file with name "_" if it exists
        Path topLevelFile = dsdir.resolve("_");
        if (Files.exists(topLevelFile)) {
            FileReader fileReader = new FileReader(topLevelFile.toFile());
            try {
                JSONObject topLevelFileJson = new JSONObject(new JSONTokener(fileReader));
                if (topLevelFileJson.has("title")) {
                    String title = topLevelFileJson.getString("title");
                    // Add the title as a resTitle field to the out object
                    out.put("resTitle", title);
                }
            } finally {
                try { fileReader.close(); } catch (IOException ex) { }
            }
        }

        return out;
    }

    /**
     * return an interface for loading (and examining) file-lookup mappings
     */
    public synchronized FileLookup getFileLookup(String aipid, String version) throws IOException {
        ensureDatasetDir(aipid, version);
        String id = idFor(aipid, version);
        if (! flus.containsKey(id))
            flus.put(id, new FileLookupImpl(aipid, version));
        return flus.get(id);
    }

    private String idFor(String aipid, String version) { return aipid+"#"+version; }

    /**
     * an interface for loading and examining file-lookup mappings
     */
    public interface FileLookup {

        /**
         * save a file mapping
         */
        public void map(String filepath, String memberbag) throws IOException;

        /**
         * return the name of the member bag that contains the data file with the given file path, or 
         * null if the filepath is not recognized (or has not been mapped yet).
         */
        public String getMemberBagFor(String filepath) throws IOException;

        /**
         * return an ordered list of the member bags that have been mapped so far
         */
        public Deque<String> getMemberBags() throws IOException;

        /**
         * return a collection of filepaths that map to a given member bagfile.  The set will be empty
         * if the given bag has not been registered yet.  
         */
        public Collection<String> getDataFilesInBag(String bagname) throws IOException;

        /**
         * flush all newly added mappings to disk
         */
        public void close() throws IOException;
    }

    private class FileLookupImpl implements FileLookup {

        Map<String, String> lu = null;
        String aipid = null;
        String version = null;
        Path dsdir = null;

        FileLookupImpl(String aipid, String version) {
            this.aipid = aipid;
            this.version = version;
            dsdir = basedir.resolve(aipid).resolve(version);
            if (! Files.exists(dsdir))
                throw new RuntimeException("unexpected state: directory does not exist: "+dsdir.toString());
        }

        Map<String, String> loadMappings() throws IOException {
            Map<String, String> out = new HashMap<String, String>(10);

            for (Path mdfile : Files.list(dsdir).filter(p -> p.getFileName().toString().endsWith(".json"))
                                                .map(p -> dsdir.resolve(p))
                                                .collect(Collectors.toList()))
            {
                JSONObject md = null;
                FileReader rdr = new FileReader(mdfile.toFile());
                try {
                    md = new JSONObject(new JSONTokener(rdr));
                }
                catch (JSONException ex) {
                    continue;
                }
                finally {
                    try { rdr.close(); } catch (IOException ex) { }
                }

                if (md.has("bagfile") && md.optString("filepath") != null)
                    out.put(md.getString("filepath"), md.getString("bagfile"));
            }

            return out;
        }
        
        @Override
        public void map(String filepath, String memberbag) throws IOException {
            if (lu == null)
                lu = loadMappings();
            
            JSONObject md = null;
            try {
                md = getMetadataForCache(aipid, filepath, version);
            } catch (JSONException ex) {
                throw new IOException("Trouble reading JSON data for "+aipid+"/"+filepath+"#"+version+
                                      ": "+ ex.getMessage());
            }
            md.put("bagfile", memberbag);
            md.put("filepath", filepath);
            cacheFileMetadata(aipid, version, md);

            lu.put(filepath, memberbag);
        }

        @Override
        public String getMemberBagFor(String filepath) throws IOException {
            if (lu == null) 
                lu = loadMappings();
            return lu.get(filepath);
        }

        @Override
        public Deque<String> getMemberBags() throws IOException {
            if (lu == null) 
                lu = loadMappings();

            Set<String> mems = new TreeSet<String>(BagUtils.bagNameComparator());
            mems.addAll(lu.values());

            return new ArrayDeque<String>(mems);
        }

        @Override
        public Collection<String> getDataFilesInBag(String bagname) throws IOException {
            if (lu == null)
                lu = loadMappings();

            Set<String> out = new TreeSet<String>();
            for(Map.Entry<String, String> entry : lu.entrySet()) {
                if (bagname.equals(entry.getValue()))
                    out.add(entry.getKey());
            }

            return out;
        }
            
        public void close() { }
    }
}
