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
 * ValidationHelper class provides different methods and functions to validate
 * input request. Check against standards, connect the given URLs and get
 * information from the remote servers about individual files. It provides a
 * POJO representing information related each requested file. It also provides
 * additional functions to return appropriate user readable messages for
 * different HttpStatus representing different types of errors.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class ValidationHelper {
    protected static Logger logger = LoggerFactory.getLogger(ValidationHelper.class);

    static int countTryUrl = 0;

    public ValidationHelper() {
	// Default Consrtuctor
    }

    /**
     * This method takes input URL which is validated against valid domains, If
     * it is valid URL, HEAD request is created to get the response code and
     * content length of the file. If URL server redirects, methods attempts 4
     * times to connect and get value otherwise set the contentLength to zero.
     * If there is any other IO exception while connecting to URL it is caught
     * and appropriate response code and information is sent back.
     * 
     * @param url
     *            URL to be validated
     * @param domains
     *            valida domains
     * @return UrlStatusLocation
     */
    public static URLStatusLocation getFileURLStatusSize(String url, String domains) {
	boolean validURL = false;
	long length = 0;
	int responseCode = 0;
	String location = "";
	HttpURLConnection conn = null;
	try {
	    validURL = ValidationHelper.isAllowedURL(url, domains);
	    if (validURL) {
		URL obj = new URL(url);
		conn = (HttpURLConnection) obj.openConnection();
		HttpURLConnection.setFollowRedirects(false);
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("HEAD");
		length = conn.getContentLength();
		responseCode = conn.getResponseCode();
		if ((responseCode >= 300 && responseCode < 400) && countTryUrl < 4) {
		    location = conn.getHeaderField("Location");
		    countTryUrl++;
		    conn.disconnect();
		    length = 0;
		    getFileURLStatusSize(location, domains);
		}
		if ((responseCode >= 300 && responseCode < 400) && countTryUrl == 4) {
		    length = 0;
		    countTryUrl = 0;
		}

		conn.disconnect();
	    }
	} catch (IOException exp) {
	    logger.error(exp.getMessage());
	    logger.info("There is error reading this url:" + url + "\n" + exp.getMessage());
	    countTryUrl = 0;

	} finally {
	    if (conn != null)
		conn.disconnect();
	}

	return new URLStatusLocation(responseCode, location, url, length, validURL);

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
    public static boolean isAllowedURL(String url, String allowedUrls) throws IOException {
	URL obj = new URL(url);
	String[] domainList = allowedUrls.split("\\|");
	List<Boolean> list = new ArrayList<Boolean>();
	// Looping over all the whitelist domains
	for (int i = 0; i < domainList.length; i++) {
	    String host = "";
	    String context = "";
	    // If domains have path included in the config list check host and
	    // path.
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
     * 
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

    public static boolean areAllUrlsInaccessible(List<URLStatusLocation> lisULoc) {
	for (int k = 0; k < lisULoc.size(); k++) {
	    if (lisULoc.get(k).getStatus() >= 400)
		return false;
	}
	return true;
    }

}

/**
 * Class is a POJO to collect URL, status, content length, validation and
 * redirect if any
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
class URLStatusLocation {
    private int status;
    private String location;
    private String requestedURL;
    private long length;
    private boolean validURL;

    /**
     * POJO constructor
     * 
     * @param status
     *            HttpStatus code return by URL in terms of response
     * @param location
     *            Redirect location returned by header
     * @param requestedURL
     *            URL to be validated and connected
     * @param length
     *            ContentLegth of file at the given URL location
     * @param validURL
     *            boolean representing URL from valid domain
     */
    public URLStatusLocation(int status, String location, String requestedURL, long length, boolean validURL) {
	this.status = status;
	this.location = location;
	this.requestedURL = requestedURL;
	this.length = length;
	this.validURL = validURL;
    }

    public int getStatus() {
	return status;
    }

    public String getLocation() {
	return location;
    }

    public String getRequestedURL() {
	return this.requestedURL;
    }

    public long getLength() {
	return this.length;
    }

    public boolean isValidURL() {
	return this.validURL;
    }
}
