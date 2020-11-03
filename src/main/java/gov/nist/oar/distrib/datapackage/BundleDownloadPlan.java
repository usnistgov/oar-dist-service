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
 * 
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.datapackage;

import java.util.UUID;

/**
 * A representation of a plan for bundling a requested list of data files into one or more data 
 * bundles (zip files).  
 * <p>
 * The {@link DataPackager} class will write out a data bundle containing files accessible via 
 * a list of URLs.  The packager implementation, however, will enforce various policies regarding
 * the origin of the URLs and how much data can go into a bundle.  To avoid violating these 
 * policies, a client can use the {@link DownloadBundlePlanner} to learn how to segregate the URLs
 * into separate requests to the {@link DataPackager} that are guaranteed to meet restrictions.  
 * The {@link DownloadBundlePlanner} gives the client a plan in the form of an instance of this 
 * {@link BundleDownloadPlan}.  
 * <p>
 * A plan is made up of a list of requests that should be given to a {@link DataPackager}.  The 
 * plan can also include a list of requested URLs that should <i>not</i> be included in a bundling 
 * request, because a problem was detected either with the origin or the content of the URL.  
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class BundleDownloadPlan {

    /**
     * Request Id
     */
    private String requestId;
    /**
     * Post to the end point mentioned
     */
    private String postEachTo;
    /**
     * FilePAths and Urls json array
     */
    private BundleRequest[] requests;
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
    private NotIncludedFile[] notIncluded;
    /**
     * 
     */
    private long size;
    /**
     * 
     */
    private long bundleCount;
    /**
     * 
     */
    private long filesCount;

    /**
     * Default Constructor
     */
    public BundleDownloadPlan() {
	// Default constructor
    }

    /**
     * Creates java object representing plan to download requested data.
     * 
     * @param postEachTo
     *            method to post request to.
     * @param status
     *            status of the service.
     * @param requests
     *            requested files organized in bundles.
     * @param messages
     *            If there are any specific messages available with given
     *            request.
     * @param notIncluded
     *            If files are not included in the bundle.
     */
    public BundleDownloadPlan(String postEachTo, String status, BundleRequest[] requests, 
                              String[] messages, NotIncludedFile[] notIncluded, long size, long bundleCount, long filesCount, String requestId)
    {
	this.postEachTo = postEachTo;
	this.status = status;
	this.requests = requests;
	this.messages = messages;
	this.notIncluded = notIncluded;
	this.size = size;
	this.bundleCount = bundleCount;
	this.filesCount = filesCount;
	this.requestId = requestId;

    }

    /**
     * Set the API endpoint to POST each request in the plan to
     * 
     * @param postEachTo -- the URL endpoint
     */
    public void setPostEachTo(String postEachTo) {
	this.postEachTo = postEachTo;
    }

    /**
     * Set the series of bundling requests that constitutes this plan
     * 
     * @param requests   the list of requests that should be made to the bundling endpoint (given 
     *                   by {@link getPostEachTo})
     */
    public void setBundleNameFilePathUrl(BundleRequest[] requests) {
	this.requests = requests;
    }

    /**
     * Set the status of the plan.  See {@link getStatus}.
     * 
     * @param status    a label that indicates the success of this plan
     */
    public void setStatus(String status) {
	this.status = status;
    }

    /**
     * Set messages related to requested bundle.
     * 
     * @param msgs    a list of messages
     */
    public void setMessages(String[] msgs) {
	this.messages = msgs;
    }

    /**
     * Set list of files not included in the plan for downloading bundles.
     * 
     * @param notIncluded   a list of the files not included in the plan, each wrapped in a 
     *                      NotIncludedFile instance.
     */
    public void setNotIncluded(NotIncludedFile[] notIncluded) {
	this.notIncluded = notIncluded;
    }
    
    public void setSize(long size) {
    	this.size = size;
    }

    public long getSize() {
    	return this.size;
    }
    
    
    public void setBundleCount(long bCount) {
    	this.bundleCount = bCount;
    }

    public long getBundleCount() {
    	return this.bundleCount;
    }
    
    public void setFilesCount(long fCount) {
    	this.filesCount = fCount;
    }

    public long getFilesCount() {
    	return this.filesCount;
    }
    /**
     * return the API endpoint to POST each request in the plan to. 
     * 
     * @return String -- the endpoint URL
     */
    public String getPostEachTo() {
	return this.postEachTo;
    }

    /**
     * Return the list of bundling requests that should be made to the bundling API endpoint 
     * (given by {@link getPostEachTo}) in order to carry out this plan.  
     * 
     * @return BundleRequest[] -- the list of requests
     */
    public BundleRequest[] getBundleNameFilePathUrl() {
	return this.requests;
    }

    /**
     * Return a label indicating how successful this plan is.  The status is a String label that 
     * indicates that how successful the plan is at meeting the initial request that this plan is 
     * derived from.  For example, if not all of the originally requested data files could not be 
     * included in the plan, the status might be set to "warnings".
     * 
     * @return String -- the status label
     */
    public String getStatus() {
	return this.status;
    }

    /**
     * Get the messages associated with the request.
     * 
     * @return String[] -- the list of messages.  If there are no messages, the returned array will 
     *                     be empty.
     */
    public String[] getMessages() {
	return this.messages;
    }

    /**
     * return the list of the originally-requested files that are <i>not</i> included in this 
     * bundle plan.
     * 
     * @return NotIncludedFile[] -- the list of files, each wrapped in a NotIncludedFile.
     */
    public NotIncludedFile[] getNotIncluded() {
	return this.notIncluded;
    }
    
    public void setRequestId(String requestId) {
	this.requestId = requestId;
    }
    
    public String getRequestId() {
	return this.requestId;
    }

}
