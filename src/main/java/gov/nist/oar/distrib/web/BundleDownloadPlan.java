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
public class BundleDownloadPlan {

	/**
	 * Post to the end point mentioned
	 */
	private String postEachTo;
	/**
	 * FilePAths and Urls json array 
	 */
	private  BundleNameFilePathUrl[] requests;
	/**
	 * Status of the service/request
	 */
	private String status;
	/**
	 * Any Messages associated with the bundle
	 */
	private String[] messages;
	/**
	 * List of files not included in the bundle
	 */
	private NotIncludedFiles[] notIncluded;
	
	/**
	 * Default Constructor
	 */
	public BundleDownloadPlan(){
	  //Default constructor
	}
	
	/**
	 * Creates java object representing plan to download requested data.
	 * @param postEachTo method to post request to.
	 * @param status status of the service.
	 * @param requests requested files organized in bundles. 
	 * @param messages If there are any specific messages available with given request.
	 * @param notIncluded If files are not included in the bundle.
	 */
	public BundleDownloadPlan(String postEachTo, String status, BundleNameFilePathUrl[] requests, String[] messages, NotIncludedFiles[] notIncluded ){
		this.postEachTo = postEachTo;
		this.status = status;
		this.requests = requests;
		this.messages = messages;
		this.notIncluded = notIncluded;
	}
	/**
	 * Set the api endpoint to post the request to.
	 * @param postEachTo
	 */
	public void setPostEachTo(String postEachTo){
		this.postEachTo = postEachTo;
	}
	/**
	 * Set the requests in the form of bundlename and urls.
	 * @param requests
	 */
	public void setBundleNameFilePathUrl(BundleNameFilePathUrl[] requests){
		this.requests = requests;
	}
	/**
	 * Set the status of the service.
	 * @param status
	 */
	public void setStatus(String status){
		this.status = status;
	}
	/**
	 * Set messages related to requested bundle.
	 * @param msgs
	 */
	public void setMessages(String[] msgs){
		this.messages = msgs;
	}
	/**
	 * Set list of files not included in the plan for downloading bundles.
	 * @param notIncluded
	 */
	public void setNotIncluded(NotIncludedFiles[] notIncluded){
		this.notIncluded = notIncluded;
	}
	/**
	 * Set the post method api endpoint name.
	 * @return
	 */
	public String getPostEach(){
		return this.postEachTo;
	}
	
	/**
	 * Sets bundles for requested list of files.
	 * @return
	 */
	public BundleNameFilePathUrl[] getBundleNameFilePathUrl(){
		return this.requests;
	}
	/**
	 * Get the service/api endpoint status.
	 * @return
	 */
	public String getStatus(){
		return this.status;
	}
	/**
	 * Get the messages if any, associated with the request.
	 * @return
	 */
	public String[] getMessages(){
		return this.messages;
	}
	/**
	 * Get number of files not included in the proposed bundle plan.
	 * @return
	 */
	public NotIncludedFiles[] getNotIncluded(){
		return this.notIncluded;
	}

}
