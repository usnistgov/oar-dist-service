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
package gov.nist.oar.distrib.web.objects;

/**
 * @author Deoyani Nandrekar-Heinis 
 * This class is a java object representing,
 * files and urls which has some issues so they should not get included
 * in data bundle plan which is posted to download data.
 */

public class NotIncludedFiles {

    /**
     * Name and path of the file not included in the bundle
     */
    private String filePath;
    /**
     * Download Url for the given file
     */
    private String downloadUrl;
    /**
     * Message for why the file is not included
     */
    private String message;

    /**
     * Default Constructor NotIncludedFiles
     */
    public NotIncludedFiles() {
	// Default
    }

    /**
     * Parameterized constructor for NotIncludedFiles
     * 
     * @param fPath
     *            String filename with path
     * @param dUrl
     *            String download URL for given file
     * @param msg
     *            String message why file not included
     */
    public NotIncludedFiles(String fPath, String dUrl, String msg) {
	this.filePath = fPath;
	this.downloadUrl = dUrl;
	this.message = msg;
    }

    /**
     * Set String filePath for file not included in the bundle plan
     * 
     * @param fPath
     */
    public void setFilePath(String fPath) {
	this.filePath = fPath;
    }

    /**
     * String Download URL for given file
     * 
     * @param dUrl
     */
    public void setDownloadUrl(String dUrl) {
	this.downloadUrl = dUrl;
    }

    /**
     * String message why file in not included in this bundle
     * 
     * @param msg
     */
    public void setMessage(String msg) {
	this.message = msg;
    }

    /***
     * Get the name of the file (along with path)
     * 
     * @return String filepath
     */
    public String getFilePath() {
	return this.filePath;
    }

    /**
     * Get DownloadURL associated with the file
     * 
     * @return String downloadUrl
     */
    public String getDownloadUrl() {
	return this.downloadUrl;
    }

    /**
     * Get message why file is not included
     * 
     * @return String message
     */
    public String getMessage() {
	return this.message;
    }

}
