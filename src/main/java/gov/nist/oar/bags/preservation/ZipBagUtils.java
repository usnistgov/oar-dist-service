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
 * @author: Raymond Plante (raymond.plante@nist.gov)
 */
package gov.nist.oar.bags.preservation;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * static utilities for opening files inside zipped bags.  
 * <p>
 * These functions operate on InputStreams opened to the start of the bag.  This is because bags are
 * sometimes accessed from remote storage systems (e.g. AWS S3); in this case, random access to the 
 * bag is not practical.
 * 
 * @see gov.nist.oar.bags.preservation.HeadBagUtils
 */
public class ZipBagUtils {

    public static final String DEFAULT_MULTIBAG_VERSION = "0.4";

    /**
     * a container that provides access to a component file within a zip file.  The input stream
     * is set at the location of the component file, and the ZipEntry provides the metadata for 
     * the component file.
     */
    public static class OpenEntry {
        /**
         * the name of the zip file entry that this entry represents
         */
        public String name = null;

        /**
         * file metadata for the entry
         */
        public ZipEntry info = null;
        
        /**
         * an open InputStream for reading the named file
         */
        public ZipInputStream stream = null;

        /**
         * instantiate the container
         */
        public OpenEntry(String filename, ZipEntry metadata, ZipInputStream filestream) {
            name = filename;
            info = metadata;
            stream = filestream;
        }

        /**
         * instantiate the container with nulls
         */
        public OpenEntry() { }
    }
        
    /**
     * advance the given input stream to the start of the requested file.  
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param filepath   the path to the desired file.  This path must include the bag's base
     *                   directory and must be delimited with forward slashes ('/').
     * @return OpenEntry - the input stream and associated ZipEntry information bundled together
     * @throws FileNotFoundException   if the requested file is not found in the zip file.  Note that 
     *                   the zipfile input stream will now be set at the end of the file.
     * @throws IOException   if an (unexpected) error occurs while reading the input stream
     */
    public static OpenEntry openFile(InputStream zipfile, String filepath)
        throws IOException, FileNotFoundException
    {
        ZipInputStream zis = new ZipInputStream(zipfile);

        ZipEntry entry = null;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().equals(filepath))
                return new OpenEntry(filepath, entry, zis);
        }

        throw new FileNotFoundException(filepath + ": filepath not found in zip file");
    }

    /**
     * return the open file from a bag's payload (i.e. below the <code>data</code> directory.
     * <p>
     * This is just like {@link #openFile(InputStream,String) openFile()} except that the 
     * the file path is relative to the bag's <code>data</code>.  
     *
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @return OpenEntry - the input stream and associated ZipEntry information bundled together
     */
    public static OpenEntry openDataFile(InputStream zipfile, String bagname, String filepath)
        throws IOException, FileNotFoundException
    {
        return openFile(zipfile, bagname + "/data/" + filepath);
    }

    /**
     * return the given zip file input stream set to the start of the {@code member-bags.tsv}
     * file.  
     *
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @return OpenEntry - the input stream and associated ZipEntry information bundled together
     * @throws FileNotFoundException   if the {@code member-bags.tsv} file is not found in the 
     *                   zip file.  Note that the zipfile input stream will now be set at the end 
     *                   of the file.
     * @throws IOException   if an (unexpected) error occurs while reading the input stream
     */
    public static OpenEntry openMemberBags(InputStream zipfile, String bagname)
        throws IOException, FileNotFoundException
    {
        return openMemberBags(DEFAULT_MULTIBAG_VERSION, zipfile, bagname);
    }

    /**
     * return the given zip file input stream set to the start of the {@code member-bags.tsv}
     * file.  
     *
     * @param mbagver    the version of the Multibag BagIt Profile that the file contents
     *                   complies with.
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @return OpenEntry - the input stream and associated ZipEntry information bundled together
     * @throws FileNotFoundException   if the {@code member-bags.tsv} file is not found in the 
     *                   zip file.  Note that the zipfile input stream will now be set at the end 
     *                   of the file.
     * @throws IOException   if an (unexpected) error occurs while reading the input stream
     */
    public static OpenEntry openMemberBags(String mbagver, InputStream zipfile, String bagname)
        throws IOException, FileNotFoundException
    {
        String mb = bagname + "/multibag/member-bags.tsv";
        if (mbagver.equals("0.2"))
            mb = bagname + "/multibag/group-members.txt";
        return openFile(zipfile, mb);
    }

    /**
     * return the given zip file input stream set to the start of the {@code file-lookup.tsv}
     * file.  
     * <p>
     * Note that this method assumes the latest supportted version of the Multibag BagIt Profile.
     *
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @return OpenEntry - the input stream and associated ZipEntry information bundled together
     * @throws FileNotFoundException   if the {@code file-lookup.tsv} file is not found in the 
     *                   zip file.  Note that the zipfile input stream will now be set at the end 
     *                   of the file.
     * @throws IOException   if an (unexpected) error occurs while reading the input stream
     */
    public static OpenEntry openFileLookup(InputStream zipfile, String bagname)
        throws IOException, FileNotFoundException
    {
        return openFileLookup(DEFAULT_MULTIBAG_VERSION, zipfile, bagname);
    }

    /**
     * return the given zip file input stream set to the start of the file lookup
     * file.  
     * <p>
     * This version adapts to the version of the Multibag BagIt Profile the bag is supposed 
     * to comply with, via the {@code mbagvers} parameter.  
     *
     * @param mbagvers   the version of the Multibag BagIt Profile that the file contents
     *                   complies with.
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @return OpenEntry - the input stream and associated ZipEntry information bundled together
     * @throws FileNotFoundException   if the file lookup file is not found in the 
     *                   zip file.  Note that the zipfile input stream will now be set at the end 
     *                   of the file.
     * @throws IOException   if an (unexpected) error occurs while reading the input stream
     */
    public static OpenEntry openFileLookup(String mbagvers, InputStream zipfile, String bagname)
        throws IOException, FileNotFoundException
    {
        String flu = bagname + "/multibag/file-lookup.tsv";
        if (mbagvers.equals("0.2"))
            flu = bagname + "/multibag/group-directory.txt";
        return openFile(zipfile, flu);
    }

    /**
     * extract the NERDm metadata oject for a particular file component in the collection a head
     * bag describes and return it as a JSONObject.  
     * <p>
     * Note that, unlike {@link #getResourceMetadata(String,InputStream,String) getResourceMetadata()},
     * the metadata will <i>not</i> be annotated with a <code>_location</code> property.
     * 
     * @param filepath   the path to the data file to look for (relative to the bag's "data" directory
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @throws FileNotFoundException  if a file with the given path is not described in the given 
     *                           zip file stream.
     * @throws IOException    if there is an error while reading the metadata from the bag
     * @throws JSONException  if there is a JSON parsing error while reading the metadata from the bag.
     */
    public static JSONObject getFileMetadata(String filepath, InputStream zipfile, String bagname)
        throws FileNotFoundException, IOException, JSONException
    {
        String lookFor = bagname+"/metadata/"+filepath+"/nerdm.json";
        OpenEntry nerdmfile = ZipBagUtils.openFile(zipfile, lookFor);  // may throw FileNotFoundException
        return HeadBagUtils.readJSON(nerdmfile.stream);
    }

    /**
     * extract the complete NERDm metadata object for the collection a head bag describes and return
     * it as a JSONObject.  The DataFile components in the record will have a special boolean annotation 
     * property, <code>_location</code>, indicating the name of the bag file that contains the file, or 
     * null if the file is not available from a bag (i.e. its <code>downloadURL</code> points to an 
     * externally archived file).  
     * @param mbagvers   the version of the Multibag BagIt Profile that the file contents
     *                     complies with.
     * @param zipfile    an InputStream set at the start of the zip file.  
     * @param bagname    the bag name (which must be the root directory for the bag)
     * @throws IOException    if there is an error while reading the metadata from the bag
     * @throws JSONException  if there is a JSON parsing error while reading the metadata from the bag.
     */
    public static JSONObject getResourceMetadata(String mbagvers, InputStream zipfile, String bagname)
        throws IOException, JSONException
    {
        NerdmExtractor xtractr = new NerdmExtractor(mbagvers, zipfile, bagname);
        return xtractr.extract();
    }

    static class NerdmExtractor {
        String mbagvers = null;
        String bagname = null;
        ZipInputStream zipstrm = null;

        String fluname = null;
        String mdatadir = null;
        String resmdatafile = null;

        /**
         * create the extractor
         * @param mbagver    the version of the Multibag BagIt Profile that the file contents
         *                     complies with.
         * @param zipfile    an InputStream set at the start of the zip file.  
         * @param bagname    the bag name (which must be the root directory for the bag)
         */
        NerdmExtractor(String mbagvers, InputStream zipfile, String bagname) {
            this.mbagvers = mbagvers;
            this.bagname = bagname;
            zipstrm = new ZipInputStream(zipfile);

            fluname = bagname + "/multibag/file-lookup.tsv";
            if (mbagvers.equals("0.2"))
                fluname = bagname + "/multibag/group-directory.txt";
            mdatadir = bagname + "/metadata/";
            resmdatafile = mdatadir + "nerdm.json";
        }

        /**
         * load the file locations into the given Map, assuming that the input stream is located
         * at the start of the file lookup file.  This will only load data file locations.
         */
        Map<String, String> loadFileLocations(Map<String, String> filelookup) throws IOException {
            Pattern delim = Pattern.compile("\\t");
            if (mbagvers.equals("0.2"))
                delim = Pattern.compile(" +");
            
            BufferedReader cnts = new BufferedReader(new InputStreamReader(zipstrm));
                
            String line = null;
            String[] words = null;
            try {
                while ((line = cnts.readLine()) != null) {
                    words = delim.split(line.trim());
                    if (words[0].startsWith("data/") && words[0].length() > "data/".length())
                        filelookup.put(words[0], words[1]);
                }
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                throw new IOException("Error parsing file lookup file: line with too few fields "+
                                      "(Is the multibag version correct?):\n  "+line);
            }

            return filelookup;
        }
        
        boolean isNerdmFile(String entryName) {
            return (entryName.startsWith(mdatadir) && entryName.endsWith("/nerdm.json"));
        }

        boolean isLookupFile(String entryName) {
            return fluname.equals(entryName);
        }

        boolean isResourceNerdmFile(String entryName) {
            return resmdatafile.equals(entryName);
        }

        /**
         * add a <code>_location</code> property indicating the bag file containing the data file 
         * described by the given component JSON data.
         */
        void annotateLocation(JSONObject comp, Map<String, String> lu) throws JSONException {
            String path = comp.optString("filepath", null);
            if (path != null) path = "data/" + path;
            
            if (path != null && lu.containsKey(path))
                comp.put("_location", lu.get(path));
            else
                comp.put("_location", JSONObject.NULL);
        }

        public JSONObject extract() throws IOException, JSONException {
            TreeMap<String, JSONObject> comps = new TreeMap<String, JSONObject>();
            JSONObject res = null;
            Map<String, String> locs = null;

            // iterate through each file in the zip file
            ZipEntry zfile = null;
            String filename = null;
            while((zfile = zipstrm.getNextEntry()) != null) {
                filename = zfile.getName();

                if (isResourceNerdmFile(filename)) {
                    res = HeadBagUtils.readJSON(zipstrm);
                }
                else if (isNerdmFile(filename)) {
                    JSONObject cmp = HeadBagUtils.readJSON(zipstrm);
                    String filepath = cmp.optString("filepath", null);
                    if (filepath == null)   // should not happen
                        filepath = filename.substring(mdatadir.length(),
                                                      filename.length()-"/nerdm.json".length());
                    comps.put(filepath, cmp);
                }
                else if (isLookupFile(filename)) {
                    locs = loadFileLocations(new HashMap<String,String>(4));
                }
            }

            // annotate the extracted component metadata and add them to the root resource record
            if (locs == null) locs = new HashMap<String, String>(1);
            if (! res.has("components"))
                res.put("components", new JSONArray());
            
            JSONArray complist = res.getJSONArray("components");
            for(JSONObject cmp : comps.values()) {
                if (cmp.has("filepath"))
                    annotateLocation(cmp, locs);
                complist.put(cmp);
            }

            // we're done
            return res;
        }
    }
}
