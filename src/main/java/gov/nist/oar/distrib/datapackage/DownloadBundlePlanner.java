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
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.web.InvalidInputException;

/**
 * DownloadBundlePlanner class takes input bundle request with the limits of
 * filesize, filecount and allowed domais for download urls. The class is
 * written to process the input request check for any issues with respect to
 * size and count limit, any unauthorized access, non working urls or any other
 * issues, it sorts and processes input request to create a plan which can be
 * used to send requests to download controller where actual data bundles will
 * be downloaded.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class DownloadBundlePlanner {

	protected static Logger logger = LoggerFactory.getLogger(DownloadBundlePlanner.class);
	List<NotIncludedFile> notIncludedFiles;
	List<FileRequest> filePathUrls;
	List<BundleRequest> bundleFilePathUrls;
	List<String> messages;
	BundleDownloadPlan finalPlan;
	long bundleSize = 0;
	int bundledFilesCount = 0;
	int bundleCount = 0;
	String bundleName = "";
	boolean unsupportedSource;
	String status = "complete";
	private FileRequest[] inputfileList;
	private BundleRequest bundleRequest;
	private long mxFilesBundleSize;
	private int mxBundledFilesCount;
	private String validdomains;
	private int allowedRedirects;
	private long totalRequestedFileSize = 0;
	private String requestId ;
	
	String printSummary = "";
	List<FileRequestLogs> filesLogs = new ArrayList<FileRequestLogs>();
	
	
	public DownloadBundlePlanner() {
		// Default constructor
	}

	/**
	 * This contructor takes number of input parameters which can be used to process request and create bundle.
	 * @param inputjson
	 * @param maxFileSize
	 * @param numOfFiles
	 * @param validdomains
	 * @param bundleName
	 * @param allowedRedirects
	 */
	public DownloadBundlePlanner(BundleRequest inputjson, long maxFileSize, int numOfFiles, String validdomains,
			String bundleName, int allowedRedirects) {
		this.bundleRequest = inputjson;
		this.mxFilesBundleSize = maxFileSize;
		this.mxBundledFilesCount = numOfFiles;
		this.validdomains = validdomains;
		this.bundleName = bundleName;
		this.allowedRedirects = allowedRedirects;
	}

	/**
	 * Get the plan to download all files after checking various limits and criteria
	 * 
	 * @return BundleDownloadPlan -- the recommended plan
	 * @throws DistributionException
	 */
	public BundleDownloadPlan getBundleDownloadPlan() throws DistributionException, InvalidInputException {

	        requestId = UUID.randomUUID().toString();
		notIncludedFiles = new ArrayList<NotIncludedFile>();
		filePathUrls = new ArrayList<FileRequest>();
		bundleFilePathUrls = new ArrayList<BundleRequest>();
		messages = new ArrayList<String>();
		logger.debug("Creating bundle plan..");
		try {
		    
			ObjectMapper mapper = new ObjectMapper();
			String requestString = mapper.writeValueAsString(this.bundleRequest);
			if (ValidationHelper.hasHTMLTags(requestString)) {
				messages.add("Input contains html code, make sure to post proper request.");
				this.status = "Error";
				this.bundleCount = 0;
				return makeBundlePlan();

			}
		} catch (JsonProcessingException ex) {
			// should not happen
			logger.error("There is an issue validating request. unable to create valid JSON.");
			throw new InvalidInputException(
					"Trouble validating request: unable to convert to JSON: " + ex.getMessage());
		}

		try {
			JSONUtils.isJSONValid(this.bundleRequest);
			this.inputfileList = this.bundleRequest.getIncludeFiles();
			ValidationHelper.removeDuplicates(this.inputfileList);
		} catch (IOException ie) {
			logger.error("Error while parsing request, not a valid JSON input." + ie.getMessage());
			messages.add("Error while parsing the request. Check if it is valid JSON.");
			this.status = "Error";
			return makeBundlePlan();

		}

		for (int i = 0; i < inputfileList.length; i++) {
			FileRequest jobject = inputfileList[i];
			String filepath = jobject.getFilePath();
			String downloadurl = jobject.getDownloadUrl();
			
			try {
				if (ValidationHelper.isAllowedURL(downloadurl, validdomains)) {
					this.makeBundles(jobject);
				} else {
					notIncludedFiles.add(new NotIncludedFile(filepath, downloadurl,
							"File not added in package; This URL is from unsupported domain/host."));
				}
			} catch (MalformedURLException ex) {
				notIncludedFiles
						.add(new NotIncludedFile(filepath, downloadurl, "File not added in package; malformed URL"));
			}
		}

		if (!this.filePathUrls.isEmpty()) {
			this.makePlan(this.filePathUrls, bundleSize);
		}

		this.updateMessagesAndStatus();
		printLogsSummary();
		return this.makeBundlePlan();

	}
	
	
	/**
	 * This method combines all the details of current bundle plan request
	 * ** Note to keep the format exactly same for the scripts to run and scrape the logs.
	 * ** No specing after commas. Keep semicolons
	 */
	private void printLogsSummary() {
	    try {

	    String printLog = "";
	    int fileList = 0 ;
	    if(inputfileList != null ) fileList = inputfileList.length;
		
	    logger.info("BundlePlan Summary: RequestId:"+requestId+",Status:"+this.status+",Total Size:"+totalRequestedFileSize+",Bundles:"+ bundleCount
		    +",Files:"+fileList+ ",Number of files not included:"+this.notIncludedFiles.size());

	    printLog = "BundlePlan: ";
	    for(int i=0; i< this.filesLogs.size(); i++) {		
		//logger.info(filesLogs.get(i).toString());
		FileRequestLogs fileLog = filesLogs.get(i);
		printLog += fileLog.getRequestId()+","+ fileLog.getBundleName()+","+fileLog.getRecordId()+","+
			fileLog.getFilePath()+","+fileLog.getFileSize()+","+fileLog.getDownloadUrl()+","+
			fileLog.getTimeStamp()+"\n";
		
	    }
	    logger.info(printLog);

	    }catch(Exception e) {
		logger.error("Error writing logs on console."+e.getMessage());
	    }
	}


	/**
	 * This function helps capture information about individual files to create a log file. 
	 * @param uObj
	 * @param jobject
	 */
	public void createLogs(URLStatusLocation uObj, FileRequest jobject) {
	    try {
		String recordId = "";
	        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
		try {
		 recordId = jobject.getFilePath().split("/")[0];
		}catch(Exception e) {
		   //ignore recordid will be empty.   
		}
		this.filesLogs.add(new FileRequestLogs(this.requestId, this.bundleName+(this.bundleCount+1),recordId,jobject.getFilePath(),
			uObj.getLength(),jobject.getDownloadUrl(), timeStamp));
	    }catch(Exception e) {
		logger.error("Error creating logs for this file.");
	    }
	}
	/**
	 * Add to Bundle of the input requested based on the size and number of files
	 * allowed per bundle request.
	 * 
	 * @param jobject
	 * @throws IOException
	 */
	public void makeBundles(FileRequest jobject) {
		//logger.info("Make bundles: validate urls, check size and accordinlgy create bundle plan.");
	
	    	bundledFilesCount++;
		URLStatusLocation uObj = ValidationHelper.getFileURLStatusSize(jobject.getDownloadUrl(), this.validdomains,
				this.allowedRedirects);
		
		createLogs(uObj, jobject);
		
		String whyNotIncluded = "File not added in package; ";
		String fileUrlWithRequestId= jobject.getDownloadUrl()+"?requestId="+this.requestId;
		if (uObj.getStatus() >= 300 && uObj.getStatus() < 400) {
			whyNotIncluded += "There are too many redirects for this URL.";
			notIncludedFiles.add(new NotIncludedFile(jobject.getFilePath(), fileUrlWithRequestId, whyNotIncluded));
		} else if (uObj.getLength() <= 0) {
			whyNotIncluded += ValidationHelper.getStatusMessage(uObj.getStatus());
			notIncludedFiles.add(new NotIncludedFile(jobject.getFilePath(), fileUrlWithRequestId, whyNotIncluded));
		} else {
		    	long individualFileSize = uObj.getLength();
			totalRequestedFileSize += individualFileSize;
			if (individualFileSize >= this.mxFilesBundleSize) {
				//bundleSize = individualFileSize;
				List<FileRequest> onefilePathUrls = new ArrayList<FileRequest>();
				onefilePathUrls.add(new FileRequest(jobject.getFilePath(), fileUrlWithRequestId, individualFileSize));
				this.makePlan(onefilePathUrls,individualFileSize);
				
			} else {
				bundleSize += individualFileSize;
				if (bundleSize < this.mxFilesBundleSize && bundledFilesCount <= this.mxBundledFilesCount) {
					filePathUrls.add(new FileRequest(jobject.getFilePath(), fileUrlWithRequestId, individualFileSize));

				} else {
					if(!filePathUrls.isEmpty()) {
						bundleSize = bundleSize - individualFileSize;	
					    makePlan(filePathUrls, bundleSize);
					}
					filePathUrls.clear();
					bundledFilesCount = 1;
					filePathUrls.add(new FileRequest(jobject.getFilePath(), fileUrlWithRequestId,individualFileSize));
					bundleSize = individualFileSize;
				}
			}
		}		
	}

	/***
	 * Create Bundle of FileList
	 * @param fPathUrls
	 */
	public void makePlan(List<FileRequest> fPathUrls, long bundleSize) {
		bundleCount++;
		FileRequest[] bundlefilePathUrls = fPathUrls.toArray(new FileRequest[0]);
		bundleFilePathUrls.add(new BundleRequest(bundleName + "-" + bundleCount + ".zip", bundlefilePathUrls, bundleSize,bundlefilePathUrls.length, this.requestId));
	}

	/**
	 * Update Messages and Status
	 */
	private void updateMessagesAndStatus() {
		if (!this.notIncludedFiles.isEmpty() && this.bundleFilePathUrls.isEmpty()) {
			this.messages.add("Files unavailable due to remote server access problem.");
			this.status = "Error";
		}
		if (!this.notIncludedFiles.isEmpty() && !this.bundleFilePathUrls.isEmpty()) {
			messages.add("Some of the selected data files unavailable, due to remote server access problem.");
			this.status = "warnings";
		}

	}
	

	/**
	 * Create final Bundle plan (JSON) to return to client. It creates Java Object
	 * of BundleDownloadPlan after processing input request.
	 */
	public BundleDownloadPlan makeBundlePlan() {
		logger.debug("makeBundlePlan called to return bundleDownloadPlan with urls and sizes.");
		
		int fileList = 0 ;
		if(inputfileList != null ) fileList = inputfileList.length;
			BundleDownloadPlan bPlan = new BundleDownloadPlan();
				
			bPlan.setStatus(status);
			bPlan.setBundleCount(bundleCount);
			bPlan.setBundleNameFilePathUrl(bundleFilePathUrls.toArray(new BundleRequest[0]));
			bPlan.setFilesCount(fileList);
			bPlan.setMessages(messages.toArray(new String[0]));
			bPlan.setNotIncluded(notIncludedFiles.toArray(new NotIncludedFile[0]));
			bPlan.setPostEachTo("_bundle");
			bPlan.setSize(this.totalRequestedFileSize);
			bPlan.setRequestId(requestId);
				
		return bPlan;
				
//		return new BundleDownloadPlan("_bundle", this.status, bundleFilePathUrls.toArray(new BundleRequest[0]),
//				messages.toArray(new String[0]), notIncludedFiles.toArray(new NotIncludedFile[0]),
//				this.totalRequestedFileSize, bundleCount, fileList);
	}

}
