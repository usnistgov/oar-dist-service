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
 * 
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
	URLConnection conn = obj.openConnection();
	return conn.getContentLength();
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
    
    public static UrlStatusLocation  getURLStatus(String url){
	
	try{
	    URL obj = new URL(url);
	    HttpURLConnection con = (HttpURLConnection)obj.openConnection();
	    con.setConnectTimeout(5000);
	    con.setReadTimeout(5000);
	    con.connect();

//	    System.out.println( "connected url: "+status+" :: " + con.getURL() 
//	    +" ::"+con.getHeaderField( "Location" ) + "::"+con.getResponseCode());

	    UrlStatusLocation uLoc = new UrlStatusLocation(con.getResponseCode(),con.getHeaderField( "Location" ));
	    con.disconnect();
	    
	    return uLoc;
	}catch(IOException iexp){
	    System.out.println("IOException:"+iexp.getMessage());
	    logger.error(iexp.getMessage());
	    return new UrlStatusLocation(-1, "");
	    
	}catch(Exception exp){
	    System.out.println("Exception:"+exp.getMessage());
	    logger.error(exp.getMessage());
	    return new UrlStatusLocation(-1, "");
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
    
}

class UrlStatusLocation {
    private  int status;
    private  String location;

    public UrlStatusLocation(int status, String location) {
        this.status = status;
        this.location = location;
    }

    public int getStatus() {
        return status;
    }

    public String getLocation() {
        return location;
    }
}
