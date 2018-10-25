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

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.ParseException;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

/**
 * static utility functions for managing and interpreting NIST preservation bags by their names.
 * <p>
 * This class embeds conventions for naming PDR "archive preservation package" (AIP) files--i.e.,
 * preservation bags.  There are two conventions supported:
 * <ul>
 *   <li> Original form:  <i>identifier</i>.mbag<i>M</i>_<i>N</i>-<i>S</i> <br>
 *        where <i>M</i>_<i>N</i> is the Multibag profile (MbP) version (e.g. <tt>0_2</tt>, <tt>0_4</tt>), <br>
 *        and <i>S</i> is the sequence number (e.g. 0, 1, ...).  </li>
 *   <li> As of MbP 0.4:  <i>identifier</i>.<i>V</V>_<i>V</V>_<i>V</V>.mbag<i>M</i>_<i>N</i>-<i>S</i> <br>
 *        where <i>V</V>_<i>V</V>_<i>V</V> is the dataset release version (e.g., <tt>1_0_0</tt>, <tt>2_1_10</tt>) 
 * </ul>
 * These conventions are used to determine which bags are associated with a given identifier 
 * (and version) and which are the head bags.  
 */
public class BagUtils {

    static Pattern bagname1re = Pattern.compile("^(\\w+)\\.mbag(\\d+_\\d+)-(\\d+)(\\..*)?$");
    static Pattern bagname2re = Pattern.compile("^(\\w+)\\.(\\d+(_\\d+)*)\\.mbag(\\d+_\\d+)-(\\d+)(\\..*)?$");
    static Pattern dotdelim = Pattern.compile("\\.");
    static Pattern usdelim = Pattern.compile("_");

    /**
     * parse a bag name into its meaningful components: id, version, multibag profile version, 
     * multibag sequence number, and serialization extension (if present).  The version field will 
     * be an empty string.  
     * @param name           the name to parse.  This name should not include any preceeding
     *                       file paths.  
     * @return List<String> - a list containing the components in order of id, version, multibag 
     *                       profile version, multibag sequence number, and serialization 
     *                       extension.  If the name does not contain a serialization extension,
     *                       the fourth element will be an empty string.  That field will not
     *                       include a leading dot.  The version field will be an empty string.
     * @throws ParseException  if the given name does not match the accepted pattern 
     *                         for bag names
     */
    public static List<String> parseBagNameWithoutVersion(String name) throws ParseException {
        Matcher m = bagname1re.matcher(name);
        if (! m.find())
            throw new ParseException("Not a legal bag name: "+name, 0);
        ArrayList<String> out = new ArrayList<String>(4);
        out.add(m.group(1));
        out.add("");
        out.add(m.group(2));
        out.add(m.group(3));

        String ext = m.group(4);
        if (m.group(4) == null) 
          ext = "";
        out.add(ext);

        if (out.get(4).startsWith("."))
            out.set(4, out.get(4).substring(1));
        return out;
    }
    
    /**
     * parse a bag name (according the format that includes the AIP version) into its meaningful 
     * components: id, version, multibag profile version, multibag sequence number, and 
     * serialization extension (if present).  
     * @param name           the name to parse.  This name should not include any preceeding
     *                       file paths.  
     * @return List<String> - a list containing the components in order of id, multibag 
     *                       profile version, multibag sequence number, and serialization 
     *                       extension.  If the name does not contain a serialization extension,
     *                       the fourth element will be an empty string.  That field will not
     *                       include a leading dot.  
     * @throws ParseException  if the given name does not match the accepted pattern 
     *                         for bag names
     */
    public static List<String> parseBagNameWithVersion(String name) throws ParseException {
        Matcher m = bagname2re.matcher(name);
        if (! m.find())
            throw new ParseException("Not a legal bag name: "+name, 0);
        ArrayList<String> out = new ArrayList<String>(5);
        out.add(m.group(1));
        out.add(m.group(2));
        out.add(m.group(4));
        out.add(m.group(5));

        String ext = m.group(6);
        if (m.group(6) == null) 
            ext = "";
        out.add(ext);

        if (out.get(4).startsWith("."))
            out.set(4, out.get(4).substring(1));
        return out;
    }

    /**
     * parse a bag name (according the format that excludes the AIP version) into its meaningful 
     * components: id, version, multibag profile version, multibag sequence number, and 
     * serialization extension (if present).  The version fields will be exactly as they appear 
     * in the name (i.e. with underscore, _, as the delimiter).  
     * @param name           the name to parse.  This name should not include any preceeding
     *                       file paths.  
     * @return List<String> - a list containing the components in order of id, version, multibag 
     *                       profile version, multibag sequence number, and serialization 
     *                       extension.  If the name does not contain a version or serialization 
     *                       extension, the second or fourth element, respectively, will be an 
     *                       empty string.  The extension field will not include a leading dot.  
     * @throws ParseException  if the given name does not match the accepted pattern 
     *                         for bag names
     */
    public static List<String> parseBagName(String name) throws ParseException {
        Matcher m = bagname1re.matcher(name);
        if(m.matches())
            return  parseBagNameWithoutVersion(name);
        
        m = bagname2re.matcher(name);
        if(m.matches())
            return parseBagNameWithVersion(name);

        throw new ParseException("Not a legal bag name: "+name, 0);
    }

    /**
     * return true if the file is a legal bag name.  The name may or may not contain a 
     * serialization extension (e.g. ".zip").  
     * @param name   the bag name to judge
     */
    public static boolean isLegalBagName(String name) {
        return  (bagname1re.matcher(name).matches() || bagname2re.matcher(name).matches());
    }

    /**
     * return the version of the multibag profile a preservation bag claims to conform to
     * based on its name.
     * @param name        the bag's name
     * @return String - the dot-delimited version string, or an empty string if the version 
     *                  cannot be determined. 
     */
    public static String multibagVersionOf(String name) {
        try {
            return usdelim.matcher(parseBagName(name).get(2))
                          .replaceAll(".");
        }
        catch (ParseException ex) {
            return "";
        }
    }

    static class VersionComparator implements Comparator<String> {
        public VersionComparator() {
          //Default Constructor
        }
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof VersionComparator);
        }
        @Override
        public int compare(String v1, String v2) {
            String[] f1 = v1.split("[_\\.]");
            String[] f2 = v2.split("[_\\.]");
            int c = 0;
            int i = 0;
            for (; i < f1.length && i < f2.length; i++) {
                c = toInt(f1[i]).compareTo(toInt(f2[i]));
                if (c != 0) 
                  return c;
            }
            for (; i < f1.length; i++) 
                if (toInt(f1[i]) > 0) return +1;
            for (; i < f2.length; i++) 
                if (toInt(f2[i]) > 0) return -1;
            return c;
        }
        Integer zero = new Integer(0);
        private Integer toInt(String is) {
            try {
                return new Integer(is);
            } catch (NumberFormatException ex) {
                return zero;
            }
        }
    }
    
    static class NameComparator implements Comparator<String> {
        public NameComparator() {
          //Default Constructor
        }
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof NameComparator);
        }
        @Override
        public int compare(String n1, String n2) {
            // After parsing the bag names into their components, we sort the
            // names based on the ordering with the following precendence:
            //  1. the AIP identifier (lexically)
            //  2. the bag sequence number
            //  3. the publication version
            //  4. the multibag profile version
            //  5. the serialization extension (lexically)
            
            List<String> p1 = null, p2 = null;

            try {
                p1 = BagUtils.parseBagName(n1);
                p2 = BagUtils.parseBagName(n2);
            } catch (ParseException ex) {
                throw new IllegalArgumentException(ex.getMessage(), ex);
            }

            // 1. compare AIP id
            int c = p1.get(0).compareTo(p2.get(0));
            if (c != 0) 
              return c;

            // 2. compare the multibag sequence number.
            //
            // It is possible for a NumberFormatException to be thrown, but
            // it normally should not.
            try {
                c = (new Integer(p1.get(3))).compareTo(new Integer(p2.get(3)));
                if (c != 0)
                    return c;
            } catch (NumberFormatException ex) {
                // can't compare non-integers; treat them as equal
                c = 0;
            }
            
            // 3. compare the publication version.  When not present, the version
            // is taken as zero.  
            c = version_cmp.compare(p1.get(1), p2.get(1));
            if (c != 0) 
              return c;

            // 4. compare the multibag profile version
            c = version_cmp.compare(p1.get(2), p2.get(2));
            if (c != 0) 
              return c;

            // 5. finally, compare the extensions, alphabetically
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
     * @param bagnames   the list of bag names.  
     * @return String  - the name of the latest head bag in the list
     * @throws IllegalArgumentException  if the list is empty or contains an illegal 
     *                                   bag name
     */
    public static String findLatestHeadBag(List<String> bagnames) {
        if (bagnames.size() == 0)
            throw new IllegalArgumentException("Empty bag name list provided");
        ArrayList<String> sortable = new ArrayList<>(bagnames);
        sortable.sort(bagNameComparator());
        return sortable.get(sortable.size()-1);
    }

    /**
     * select the bags from a list of bagnames that match a desired version.  Under
     * certain circumstances, this will look for certain varients.  If none of the 
     * names match the version, an empty list is returned.
     * @param bagnames   the list of bag names to filter
     * @param version    the desired version in dot-delimited form
     * @return List<String>  - the list of matching names (in the same order as they
     *                   appeared in the original list)
     * @throws IllegalArgumentException  if the list is empty or contains an illegal 
     *                                   bag name
     */
    public static List<String> selectVersion(List<String> bagnames, String version) {
        // in bag names, the version appears in N_N_N format; swap . for _
        version = dotdelim.matcher(version)
                          .replaceAll("_");
        
        // Most likely given current NIST practice, if version is simply "0" or "1",
        // we're refering to bags following the original naming convention.
        if (version.equals("0") || version.equals("1")) {
            List<String> out = BagUtils.selectVersion(bagnames, "");
            if (out.size() > 0) return out;
        }

        ArrayList<String> out = new ArrayList<String>();
        Pattern vernamere = null;
        while (true) {
            // loop through possible matching version strings
            vernamere = (version.length() == 0)
                           ? Pattern.compile("^(\\w+)\\.mbag")
                           : Pattern.compile("^(\\w+)\\."+version+"\\.");
            for (String name : bagnames) {
                if (vernamere.matcher(name).find())
                    out.add(name);
            }
            if (out.size() > 0 || ! version.endsWith("_0"))
                break;

            // try lopping off trailing zeros
            version = version.substring(0, version.length()-2);
        }

        return out;
    }
}

