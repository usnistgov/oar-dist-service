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
	public BundleDownloadPlan(){}
	
	
	public BundleDownloadPlan(String postEachTo, String status, BundleNameFilePathUrl[] requests, String[] messages, NotIncludedFiles[] notIncluded ){
		this.postEachTo = postEachTo;
		this.status = status;
		this.requests = requests;
		this.messages = messages;
		this.notIncluded = notIncluded;
	}
	/**
	 * 
	 * @param postEachTo
	 */
	public void setPostEachTo(String postEachTo){
		this.postEachTo = postEachTo;
	}
	
	public void setBundleNameFilePathUrl(BundleNameFilePathUrl[] requests){
		this.requests = requests;
	}
	
	public void setStatus(String status){
		this.status = status;
	}
	
	public void setMessages(String[] msgs){
		this.messages = msgs;
	}
	
	public void setNotIncluded(NotIncludedFiles[] notIncluded){
		this.notIncluded = notIncluded;
	}
	
	public String getPostEach(){
		return this.postEachTo;
	}
	
	public BundleNameFilePathUrl[] getBundleNameFilePathUrl(){
		return this.requests;
	}
	
	public String getStatus(){
		return this.status;
	}
	
	public String[] getMessages(){
		return this.messages;
	}
	
	public NotIncludedFiles[] getNotIncluded(){
		return this.notIncluded;
	}

}
