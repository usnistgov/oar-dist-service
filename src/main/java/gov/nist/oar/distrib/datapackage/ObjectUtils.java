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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.datapackage;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import gov.nist.oar.distrib.web.objects.FilePathUrl;

/***
 * ObjectUtils class provides the functionality to validate objects and text.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class ObjectUtils {
    private ObjectUtils() {
    }

    /**
     * Read the url header to get the size of file.
     * 
     * @param url
     * @return long
     * @throws IOException
     */
    public static long getFileSize(String url) throws IOException {
	URL obj = new URL(url);
	URLConnection conn = obj.openConnection();
	return conn.getContentLength();
    }

    /***
     * Accepts given url and allowed domains. And checks whether url requested
     * from valid domain.
     * 
     * @param url
     * @param domains
     * @return boolean
     * @throws IOException
     */
    public static boolean validateUrlDomain(String url, String domains) throws IOException {
	URL obj = new URL(url);
	if (!domains.toLowerCase().contains(obj.getHost().toLowerCase()))
	    return false;
	return true;
    }

    /**
     * This method helps remove duplicate object, It will check duplicate using
     * equals method
     * 
     * @param inputfileList
     *            input list of filepathurls provided by user.
     * @return Updated list.
     */
    public static FilePathUrl[] removeDuplicates(FilePathUrl[] inputfileList) {
	List<FilePathUrl> list = Arrays.asList(inputfileList);
	List<FilePathUrl> newfilelist = list.stream().distinct().collect(Collectors.toList());
	return newfilelist.toArray(new FilePathUrl[0]);
    }

    private static final String HTML_PATTERN = "<(\"[^\"]*\"|'[^']*'|[^'\">])*>";
    private static Pattern pattern = Pattern.compile(HTML_PATTERN);

    /**
     * Is input requested contains any html tags.
     * 
     * @param text
     * @return boolean
     */
    public static boolean hasHTMLTags(String text) {
	Matcher matcher = pattern.matcher(text);
	return matcher.matches();
    }
}
