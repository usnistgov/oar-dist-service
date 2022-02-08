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
 */
package gov.nist.oar.distrib.datapackage;

/**
 * a container for holding a request for set of files to be packaged up into a bundle (or bundles).
 * A request is composed of a list of file requests (as {@link FileRequest} objects) and an optional
 * name for the bundle.  
 *
 * @author Deoyani Nandrekar-Heinis
 */
public class BundleRequest {

    private String requestId;
    /**
     * Name of the bundle to be downloaded
     */
    private String bundleName;
    /**
     * FilePAths and Urls json array
     */
    private FileRequest[] includeFiles;
    
    /**
     * Size of this bundle
     */
    private long bundleSize;
    
    /**
     * total number of files in this bundle
     */
    private long filesInBundle;

    /**
     * Default Constructor
     */
    public BundleRequest() {
	// default constructor
    }

    /**
     * Create an object with bundle name and array of files with URLs.
     * 
     * @param bundleName
     * @param includeFiles
     */
    public BundleRequest(String bundleName, FileRequest[] includeFiles, long bundleSize, long filesInBundle) {
	this.bundleName = bundleName;
	this.includeFiles = includeFiles;
	this.bundleSize = bundleSize;
	this.filesInBundle = filesInBundle;
    }
    
    /**
     * Create an object with bundle name and array of files with URLs.
     * 
     * @param bundleName
     * @param includeFiles
     */
    public BundleRequest(String bundleName, FileRequest[] includeFiles, long bundleSize, long filesInBundle, String requestId) {
	this.bundleName = bundleName;
	this.includeFiles = includeFiles;
	this.bundleSize = bundleSize;
	this.filesInBundle = filesInBundle;
	this.requestId = requestId;
    }

    /**
     * Set  the bundle size
     * 
     * @param size  Sum of files size in this bundle.
     */
    public void setBundleSize(long size) {
	this.bundleSize = size;
    }

    /**
     * Return the total files size of this bundle
     * 
     * @return long
     */
    public long getBundleSize() {
	return this.bundleSize;
    }
    
    /**
     * Set  the bundle size
     * 
     * @param size  Sum of files size in this bundle.
     */
    public void setFilesInBundle(long filesInBundle) {
    	this.filesInBundle = filesInBundle;
    }

   
    /**
     * Return the total number of files in this bundle
     * 
     * @return long
     */
    public long getFilesInBundle() {
	return this.filesInBundle;
    }
    
    /**
     * Set the requested name to give to the bundle
     * 
     * @param bundleName  the name to give to the bundle
     */
    public void setBundleName(String bundleName) {
	this.bundleName = bundleName;
    }

    /**
     * Return the name requested to be assigned to the bundle
     * 
     * @return String -- the desired name for the bundle
     */
    public String getBundleName() {
	return this.bundleName;
    }

    /**
     * Set the list of requested files to include in the bundle
     * 
     * @param includeFiles    the list of requested files
     */
    public void setIncludeFiles(FileRequest[] includeFiles) {
	this.includeFiles = includeFiles;
    }

    /**
     * Return the list of files to requested to be bundled
     * 
     * @return FilePathUrl[] -- the list of requested files
     */
    public FileRequest[] getIncludeFiles() {
	return this.includeFiles;
    }
    
    public void setRequestId(String requestId) {
   	this.requestId = requestId;
    }
       
    public String getRequestId() {
   	return this.requestId;
    }
}
