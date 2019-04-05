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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.web.objects.FileRequest;

/***
 * ObjectUtils class provides the functionality to validate objects and text.
 * It also use to work with server responses and response codes
 * @author Deoyani Nandrekar-Heinis
 */
public class ObjectUtils {
    protected static Logger logger = LoggerFactory.getLogger(ObjectUtils.class);

    /**
     * Read the url header to get the size of file.
     * 
     * @param url
     * @return long
     * @throws IOException
     */
    public static long getFileSize(String url) throws IOException {
	URL obj = new URL(url);
	HttpURLConnection conn = (HttpURLConnection)obj.openConnection();
	conn.setRequestMethod("HEAD");
	long length = conn.getContentLength();
	conn.disconnect();
	return length;
    }

    /**
     * Return true if a given URL matches one of the set of allowed URL
     * patterns.
     * 
     * @param url
     *            the URL to test
     * @param allowedUrls
     *            a regular expression string for allowed URLs. Multiple URL
     *            patterns can be concatonated with a pipe (|) character.
     * @return boolean True if the URL matches one of the given patterns; false,
     *         otherwise
     * @throws IOException
     */
    // private
    // private
    public static boolean isAllowedURL(String url, String allowedUrls) throws IOException {
	URL obj = new URL(url);
	String[] domainList = allowedUrls.split("\\|");
	List<Boolean> list = new ArrayList<Boolean>();
	// Looping over all the whitelist domains
	for (int i = 0; i < domainList.length; i++) {
	    String host = "";
	    String context = "";
	    // If domains have path included in the config list check host and path.
	    if (domainList[i].contains("/")) {
		String[] parts = domainList[i].split("/", 2);
		host = parts[0];
		if (getPatternMatch(host, obj.getHost())) {
		    context = parts[1];
		    String[] paths = obj.getPath().split("/");
		    list.add(getPatternMatch(context, obj.getPath().split("/")[1]));

		} else {
		    list.add(false);
		}
	    } // else check only the host
	    else {
		host = ".*" + domainList[i];
		list.add(getPatternMatch(host.trim(), obj.getHost()));
	    }

	}
	// if all the values in list are false, return false.
	// It means none of the domain and host matches return false.
	return !(list.stream().allMatch(val -> val == false));

    }

    /**
     * Using Url connection get the response code from server
     * @param con
     * @return UrlStatusLocation Object
     */
    public static UrlStatusLocation getURLStatus(HttpURLConnection con) {

	try {

	    HttpURLConnection.setFollowRedirects(false);
	    con.setFollowRedirects(false);
	    con.setConnectTimeout(10000);
	    con.setReadTimeout(10000);
	    con.connect();
	    return new UrlStatusLocation(con.getResponseCode(), con.getHeaderField("Location"), con.getURL().toString());

	} catch (IOException iexp) {
	    logger.error(iexp.getMessage());
	    return new UrlStatusLocation(-1, "",con.getURL().toString());

	} catch (Exception exp) {
	    logger.error(exp.getMessage());
	    return new UrlStatusLocation(-1, "",con.getURL().toString());
	}

    }

    // Pattern matching for give pattern in requested string
    private static boolean getPatternMatch(String pattern, String requestString) {
	pattern = pattern.trim();
	Pattern dpattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
	Matcher matcher = dpattern.matcher(requestString);
	return matcher.matches();
    }

    /**
     * This method helps remove duplicate object, It will check duplicate using
     * equals method
     * 
     * @param inputfileList
     *            input list of filepathurls provided by user.
     * @return Updated list.
     */
    public static FileRequest[] removeDuplicates(FileRequest[] inputfileList) {
	List<FileRequest> list = Arrays.asList(inputfileList);
	List<FileRequest> newfilelist = list.stream().distinct().collect(Collectors.toList());
	return newfilelist.toArray(new FileRequest[0]);
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
	return matcher.find();
    }

    /***
     * Status messages in user readable format for response with 400* errors
     * @param statuscode
     * @return String Readable error message
     */
    public static String getStatusMessage(int statuscode) {

	switch (statuscode) {
	case 400:
	    return "The given URL is malformed.";
	case 401:
	    return "This URL request needs authorized user access.";
	case 403:
	    return "Access to this URL is forbidden";
	case 404:
	    return "The requested file by given URL is not found on server.";
	case 405:
	    return "Requested file/URL can not be accessed because method is not allowed.";
	case 409:
	    return "There is some conflict accessing this URL.";
	case 410:
	    return "This file represented by given URL is no longer available on the server.";
	default:
	    return "There is an error accessing this file/URL.";
	}

    }

}

/**
 * Class is a POJO to return server response code with the url location
 * @author Deoyani Nandrekar-Heinis
 *
 */
class UrlStatusLocation {
    private int status;
    private String location;
    private String requestedURL;

    public UrlStatusLocation(int status, String location, String requestedURL) {
	this.status = status;
	this.location = location;
	this.requestedURL = requestedURL;
    }

    public int getStatus() {
	return status;
    }

    public String getLocation() {
	return location;
    }
    
    public String getRequestedURL(){
	return this.requestedURL;
    }
}
