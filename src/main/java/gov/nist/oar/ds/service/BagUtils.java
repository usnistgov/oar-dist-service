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
 * @author: Raymond Plante (raymond.plante@nist.gov)
 */
package gov.nist.oar.ds.service;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.ParseException;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

/**
 * static utility functions for managing and interpreting bags in long term storage
 */
public class BagUtils {

    static Pattern bagnamere = Pattern.compile("^(\\w+).mbag(\\d+_\\d+)-(\\d+)(\\..*)?$");

    /**
     * parse a bag name into its meaningful components: id, multibag profile version, 
     * multibag sequence number, and serialization extension (if present).  
     * @param String name    the name to parse.  This name should not include any preceeding
     *                       file paths.  
     * @return List<String>  a list containing the components in order of id, multibag 
     *                       profile version, multibag sequence number, and serialization 
     *                       extension.  If the name does not contain a serialization extension,
     *                       the fourth element will be an empty string.  That field will not
     *                       include a leading dot.  
     * @throws ParseException  if the given name does not match the accepted pattern 
     *                         for bag names
     */
    public static List<String> parseBagName(String name) throws ParseException {
        Matcher m = bagnamere.matcher(name);
        if (! m.find())
            throw new ParseException("Not a legal bag name: "+name, 0);
        ArrayList<String> out = new ArrayList<String>(4);
        out.add(m.group(1));
        out.add(m.group(2));
        out.add(m.group(3));

        String ext = m.group(4);
        if (m.group(4) == null)  ext = "";
        out.add(ext);

        if (out.get(3).startsWith("."))
            out.set(3, out.get(3).substring(1));
        return out;
    }

    /**
     * return true if the file is a legal bag name.  The name may or may not contain a 
     * serialization extension (e.g. ".zip").  
     */
    public static boolean isLegalBagName(String name) {
        return bagnamere.matcher(name).matches();
    }

    static class VersionComparator implements Comparator<String> {
        public VersionComparator() {}
        public boolean equals(Object obj) {
            return (obj instanceof VersionComparator);
        }
        public int compare(String v1, String v2) {
            String[] f1 = v1.split("[_\\.]");
            String[] f2 = v2.split("[_\\.]");
            int c = 0;
            for (int i=0; i < f1.length && i < f2.length; i++) {
                c = (new Integer(f1[i])).compareTo(new Integer(f2[i]));
                if (c != 0) return c;
            }
            return c;
        }
    }

    static class NameComparator implements Comparator<String> {
        public NameComparator() {}
        public boolean equals(Object obj) {
            return (obj instanceof NameComparator);
        }

        public int compare(String n1, String n2) {
            List<String> p1 = null, p2 = null;

            try {
                p1 = BagUtils.parseBagName(n1);
                p2 = BagUtils.parseBagName(n2);
            } catch (ParseException ex) {
                throw new ClassCastException(ex.getMessage());
            }

            // compare bag id
            int c = p1.get(0).compareTo(p2.get(0));
            if (c != 0) return c;

            // compare the multibag sequence number.
            //
            // It is possible for a NumberFormatException to be thrown, but
            // it normally should not. 
            c = (new Integer(p1.get(2))).compareTo(new Integer(p2.get(2)));
            if (c != 0) return c;

            // compare the multibag profile version
            c = version_cmp.compare(p1.get(1), p2.get(1));
            if (c != 0) return c;

            // this effectively sorts alphabetically by the serialization extension
            return n1.compareTo(n2);
        }
    }

    static Comparator<String> version_cmp = new VersionComparator();
    static Comparator<String> name_cmp    = new NameComparator();
    
    /**
     * return a Comparator for sorting bag names
     */
    public static Comparator<String> bagNameComparator() {
        return name_cmp;
    }

    /**
     * return a Comparator for sorting version strings.  In this implementation, a version 
     * is a String containing a sequence of integers delimited either by periods ('.') or 
     * underscores ('_').  If the fields are anything else but integers, a NumberFormatException
     * is thrown.
     */
    public static Comparator<String> versionComparator() {
        return version_cmp;
    }

    /**
     * return the latest head bag from the List of bag names.  Each item in the list 
     * must be a legal bag name (see isLegalBagName()) and have the same identifier.
     * If the former requirement is not satisfied, a runtime exception will be thrown; 
     * if the second is not, the result is undefined. 
     * @param List<String> bagnames   the list of bag names.  
     * @return String    the name of the latest head bag in the list
     */
    public static String findLatestHeadBag(List<String> bagnames) {
        ArrayList<String> sortable = new ArrayList<String>(bagnames);
        sortable.sort(bagNameComparator());
        return sortable.get(sortable.size()-1);
    }

    /**
     * URL-decode the given string.  That is, return the input string with %-codes 
     * replaced with their character values and pluses replaced with spaces.
     * @param String urlstr   the string to decode
     * @return String   the decoded string
     */
    public static String urlDecode(String urlstr) {
        try {
            return URLDecoder.decode(urlstr, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            // should not happen
            throw new RuntimeException("Unexpected encoding error", ex);
        }
    }
}

