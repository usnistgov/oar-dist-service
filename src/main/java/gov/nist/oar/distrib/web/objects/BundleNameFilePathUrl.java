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
 *
 */

/**
 * Jackson json framework to form a request data to a download controller. This
 * class is used to represent the requested post object to dowload bundled zip
 * of requested files with given URLs. It also holds name of the bundle given by
 * client.
 */
public class BundleNameFilePathUrl {

    /**
     * Name of the bundle to be downloaded
     */
    private String bundleName;
    /**
     * FilePAths and Urls json array
     */
    private FilePathUrl[] includeFiles;

    /**
     * Default Constructor
     */
    public BundleNameFilePathUrl() {
	// default constructor
    }

    /**
     * Create an object with bundle name and array of files with urls.
     * 
     * @param bundleName
     * @param includeFiles
     */
    public BundleNameFilePathUrl(String bundleName, FilePathUrl[] includeFiles) {
	this.bundleName = bundleName;
	this.includeFiles = includeFiles;
    }

    /**
     * Set the name of the bundle to be downloaded
     * 
     * @param bundleName
     */
    public void setBundleName(String bundleName) {
	this.bundleName = bundleName;
    }

    /**
     * Get the name to be downloaded
     * 
     * @return String
     */
    public String getBundleName() {
	return this.bundleName;
    }

    /**
     * Set the url associated with filename to download data from
     * 
     * @param includeFiles
     */
    public void setIncludeFiles(FilePathUrl[] includeFiles) {
	this.includeFiles = includeFiles;
    }

    /**
     * get the url associated with filename to download data from
     * 
     * @return FilePathUrl[]
     */
    public FilePathUrl[] getIncludeFiles() {
	return this.includeFiles;
    }
}
