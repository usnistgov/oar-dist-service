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
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * static utilities for accessing content from NIST preservation head bags.
 * <p>
 * Head bags are a concept from the Multibag BagIt Profile where a data collection can be split over
 * multiple bags; the head bag contains special metadata for identifying the other bags in the 
 * aggregation and identifying the location of component files amongst the bags.  In the NIST profile,
 * the head bag also contains all of the NIST metadata for the collection.  
 * <p>
 * These functions operate on open InputStreams to files within the bag.  This is because bags are 
 * often in serialized (e.g. zipped) form and in remote storage systems (e.g. AWS S3); in this case,
 * random access to bag contents is not really possible.  See 
 * {@link gov.nist.oar.bags.preservation.ZipBagUtils ZipBagUtils} for accessing streams to particular
 * files in the bag.  
 *
 * @see gov.nist.oar.bags.preservation.ZipBagUtils
 */
public class HeadBagUtils {

    public static final String DEFAULT_MULTIBAG_VERSION = "0.4";

    /**
     * return the name of the bag that contains a desired file.  The bag name will <em>not</em> include 
     * a serialization extension (e.g. {@code .zip}).
     * @param filelookup    an InputStream opened at the start of the  
     *                      {@code multibag/file-lookup.tsv} file 
     * @param filepath      the path to the desired file relative to the base of the bag root.  Thus,
     *                      data files must begin with "data/".
     * @return String - the name of the bag containing the file (without a serialization extension), or
     *                  null if the name is not found.
     */
    public static String lookupFile(InputStream filelookup, String filepath) throws IOException {
        return lookupFile(DEFAULT_MULTIBAG_VERSION, filelookup, filepath);
    }

    /**
     * return the name of the bag that contains a desired file.  The bag name will <em>not</em> include 
     * a serialization extension (e.g. {@code .zip}).
     * @param filelookup    an InputStream opened at the start of the file lookup file
     * @param filepath      the path to the desired file relative to the base of the bag root.  Thus,
     *                      data files must begin with "data/".
     * @return String - the name of the bag containing the file (without a serialization extension), or
     *                  null if the name is not found.
     */
    public static String lookupFile(String mbagver, InputStream filelookup, String filepath)
        throws IOException
    {
        Pattern delim = Pattern.compile("\\t");
        if (mbagver.equals("0.2"))
            delim = Pattern.compile(" +");
        
        BufferedReader cnts = new BufferedReader(new InputStreamReader(filelookup));

        String line = null;
        String[] words = null;
        try {
            while ((line = cnts.readLine()) != null) {
                words = delim.split(line.trim());
                if (words[0].equals(filepath))
                    return words[1];
            }
        }
        catch (ArrayIndexOutOfBoundsException ex) {
            throw new IOException("Error parsing file lookup file: line with too few fields "+
                                  "(Is the multibag version correct?):\n  "+line);
        }
        
        return null;
    }

    /**
     * return a list of the bags that make up this head bag's aggregation
     * <p>
     * The caller of this function is responsible for closing the stream.  
     * @param memberbags    an InputStream opened at the start of the  
     *                      {@code multibag/member-bags.tsv} file 
     */
    public static List<String> listMemberBags(InputStream memberbags) throws IOException {
        return listMemberBags(DEFAULT_MULTIBAG_VERSION, memberbags);
    }

    /**
     * return a list of the bags that make up this head bag's aggregation.  This version of the 
     * method allows one to specify the version of multibag specification to assume.  
     * <p>
     * The caller of this function is responsible for closing the stream.  
     * @param mbagver       the version of the Multibag BagIt Profile that the file contents
     *                      complies with.
     * @param memberbags    an InputStream opened at the start of the member bags file
     */
    public static List<String> listMemberBags(String mbagver, InputStream memberbags)
        throws IOException
    {
        Pattern delim = Pattern.compile("\\t");
        if (mbagver.equals("0.2"))
            delim = Pattern.compile(" +");
        
        List<String> out = new ArrayList<String>(2);
        BufferedReader cnts = new BufferedReader(new InputStreamReader(memberbags));

        String line = null;
        String[] words = null;
        while ((line = cnts.readLine()) != null) {
            words = delim.split(line.trim());
            out.add(words[0]);
        }

        return out;
    }

    /**
     * return a list of the data files are part of the version of the dataset described in the 
     * head bag.  The returned file paths will be relative to the bag's data directory.  The list
     * is generated by extracting all of the file paths from the {@code file-lookup.tsv} that
     * are under the data directory.  
     * <p>
     * The caller of this function is responsible for closing the stream.  
     * <p>
     * Note that this method assumes the latest supportted version of the Multibag BagIt Profile.
     * 
     * @param filelookup   the {@code file-lookup.tsv} opened at its start
     */
    public static List<String> listDataFiles(InputStream filelookup) throws IOException {
        return listDataFiles(DEFAULT_MULTIBAG_VERSION, filelookup);
    }

    /**
     * return a list of the data files are part of the version of the dataset described in the 
     * head bag.  The returned file paths will be relative to the bag's data directory.  The list
     * is generated by extracting all of the file paths from the {@code file-lookup.tsv} that
     * are under the data directory.  
     * <p>
     * The caller of this function is responsible for closing the stream.  
     *
     * @param mbagver       the version of the Multibag BagIt Profile that the file contents
     *                      complies with.
     * @param filelookup    the {@code file-lookup.tsv} opened at its start
     */
    public static List<String> listDataFiles(String mbagver, InputStream filelookup) throws IOException {
        Pattern delim = Pattern.compile("\\t");
        if (mbagver.equals("0.2"))
            delim = Pattern.compile(" +");
        
        BufferedReader cnts = new BufferedReader(new InputStreamReader(filelookup));

        List<String> out = new ArrayList<String>();

        String line = null;
        String[] words = null;
        while ((line = cnts.readLine()) != null) {
            words = delim.split(line.trim());
            if (words[0].startsWith("data/"))
                out.add(words[0].substring(5));
        }

        return out;
    }

    /*
     * TODO:
     *   readJSON()
     */
}
