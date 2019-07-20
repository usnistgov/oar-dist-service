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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.datapackage;

/**
 * A container class that identifies a requested remote data file that will be included
 * in a data bundle.  
 * <p>
 * A client uses {@link DataPackager} and {@link DownloadBundlePlanner} to make requests to bundle
 * many data files (each accessible via a URL) into a single data bundle (currently a zip file).  
 * These classes check the validity of the origin and the URL contents according to policy; those
 * files with URLs that do not conform the policies are left out of the bundle.  This class is
 * used to help communicate this decision to the client: it identifies a file that is being left
 * (by its URL) and provides the reason why.  
 *
 * @author Deoyani Nandrekar-Heinis 
 */
public class NotIncludedFile {

    /**
     * The file path that was requested to be given to the data file within the data bundle
     */
    private String filePath = null;

    /**
     * The download URL for the data file that cannot be included in the data bundle
     */
    private String downloadUrl = null;

    /**
     * An explanation as to why the file is not being included in the data bundle
     */
    private String message = null;

    /**
     * a default constructor.  All internal information will be initialized to nulls; 
     * the caller is responsible for using set methods to set the information.  
     */
    public NotIncludedFile() {
	// Default
    }

    /**
     * a parameterized constructor 
     * 
     * @param fPath
     *            String filename with path
     * @param dUrl
     *            String download URL for given file
     * @param msg
     *            String message why file not included
     */
    public NotIncludedFile(String fPath, String dUrl, String msg) {
	this.filePath = fPath;
	this.downloadUrl = dUrl;
	this.message = msg;
    }

    /**
     * set the file path that was requested to be given to the data file within the data bundle
     * 
     * @param fPath  the requested file path
     */
    public void setFilePath(String fPath) {
	this.filePath = fPath;
    }

    /**
     * set the download URL for the data file that will not be included in the data bundle
     * 
     * @param dUrl   the requested download URL
     */
    public void setDownloadUrl(String dUrl) {
	this.downloadUrl = dUrl;
    }

    /**
     * set the explanation as to why the file is not being included in the data bundle
     * 
     * @param msg   the reason for exclusion
     */
    public void setMessage(String msg) {
	this.message = msg;
    }

    /***
     * return the file path that was requested to be given to the data file within the data bundle
     * 
     * @return String   the requested file path
     */
    public String getFilePath() {
	return this.filePath;
    }

    /**
     * return the download URL for the data file that will not be included in the data bundle
     * 
     * @return String   the requested download URL
     */
    public String getDownloadUrl() {
	return this.downloadUrl;
    }

    /**
     * return the explanation as to why the file is not being included in the data bundle
     * 
     * @return String   the reason for the exclusion
     */
    public String getMessage() {
	return this.message;
    }

}
