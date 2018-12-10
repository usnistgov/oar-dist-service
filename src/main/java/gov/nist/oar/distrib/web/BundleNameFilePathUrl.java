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
package gov.nist.oar.distrib.web;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */

/**
 * Jackson json framework to form a request data to a download controller. 
 * This class is used to represent the requested post object to dowload bundled zip of requested files 
 * with given URLs. It also holds name of the bundle given by client.
 */
public class BundleNameFilePathUrl {
	
	/**
	 * Name of the bundle to be downloaded
	 */
	private String bundleName;
	/**
	 * FilePAths and Urls json array 
	 */
	private FilePathUrl[] fPathUrl;
	
	/**
	 * Default Constructor
	 */
	public BundleNameFilePathUrl(){
		//default constructor
	}
	/**
	 * Create an object with bundle name and array of files with urls.
	 * @param bundleName
	 * @param fPathUrl
	 */
	public BundleNameFilePathUrl(String bundleName,FilePathUrl[] fPathUrl ){
		this.bundleName = bundleName;
		this.fPathUrl = fPathUrl;
	}
	
	/**
	 * Set the name of the bundle to be downloaded
	 * @param bundleName
	 */
	public void setBundleName(String bundleName){
		this.bundleName = bundleName;
	}
	
	/**
	 * Get the name to be downloaded
	 * @return
	 */
	public String getBundleName(){
		return this.bundleName;
	}
	
	/**
	 * Set the url associated with filename  to download data from
	 * @param fPathUrl
	 */
	public void setFilePathUrl(FilePathUrl[] fPathUrl){
		this.fPathUrl = fPathUrl;
	}

	/**
	 * get the url associated with filename to download data from
	 * @return
	 */
	public FilePathUrl[] getFilePathUrl(){
		return this.fPathUrl;
	}
}
