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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

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
     * @param mbagver       the version of the Multibag BagIt Profile that the file contents
     *                      complies with.
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
}
