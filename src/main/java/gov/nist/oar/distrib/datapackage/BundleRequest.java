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

    /**
     * Name of the bundle to be downloaded
     */
    private String bundleName;
    /**
     * FilePAths and Urls json array
     */
    private FileRequest[] includeFiles;

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
    public BundleRequest(String bundleName, FileRequest[] includeFiles) {
	this.bundleName = bundleName;
	this.includeFiles = includeFiles;
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
}
