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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.FileRequest;
import gov.nist.oar.distrib.datapackage.NotIncludedFile;
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
	private List<String> logsForFiles;
	private List recordId;
	private List logsFilesSizes;
	private HashMap<String, List<String>> logsRecords = new HashMap<>();
	private String requestId ;
	
	String printSummary = "";
	List<FileRequestLogs> filesLogs = new ArrayList<FileRequestLogs>();
	
	//@Value("${logging.path}")
	private String logFile;
	
	public DownloadBundlePlanner() {
		// Default constructor
	}

	/**
	 * 
	 * @param inputjson
	 * @param maxFileSize
	 * @param numOfFiles
	 * @param validdomains
	 * @param bundleName
	 * @param allowedRedirects
	 */
	public DownloadBundlePlanner(BundleRequest inputjson, long maxFileSize, int numOfFiles, String validdomains,
			String bundleName, int allowedRedirects, String logFile) {
		this.bundleRequest = inputjson;
		this.mxFilesBundleSize = maxFileSize;
		this.mxBundledFilesCount = numOfFiles;
		this.validdomains = validdomains;
		this.bundleName = bundleName;
		this.allowedRedirects = allowedRedirects;
		this.logFile = logFile;
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
		logger.info("Creating bundle plan..");
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
	
//	public void forLogs(String filePath, String fileURL, long fileSize, String timeStamp) {
//	    try {
//		String[] recordPath = filePath.split("/");
//		if(!logsRecords.containsKey(recordPath[0]) || logsForFiles == null) 
//		    logsForFiles = new ArrayList<String>();
//		logsForFiles.add(" FilePath "+filePath+" FileURL : "+fileURL+" FileSize: "+fileSize);
//		
//		 printLog += requestId+","+bundleFilePathUrls.get(i).getBundleName()
//		this.logsRecords.put(recordPath[0],logsForFiles); 
//	    }catch(Exception exp) {
//		logger.error("Exception creating Logs for List of the files and sizes.");
//	    }
//	}
	
	/**
	 * 
	 */
	private void printLogsSummary() {
	    try {
	    //Print logs about files
	    String fileName = "RequestSummary.csv";
	    String printLog = "";
	    int fileList = 0 ;
	    if(inputfileList != null ) fileList = inputfileList.length;
		
	    logger.info("Request Id:"+requestId+", Status :"+this.status+", Total Size:"+totalRequestedFileSize+" , Bundles:"+ bundleCount
		    +", Files:"+fileList+ ", Number of files not included:"+this.notIncludedFiles.size());
	    
	    String requestSummary = requestId+","+this.status+","+totalRequestedFileSize+" ,"+ bundleCount
		    +","+fileList+ ","+this.notIncludedFiles.size()+" \n";
	    this.writeFile(fileName, requestSummary);
	    
	    logger.info("Requested files size info ::");
	    for(int i=0; i<bundleFilePathUrls.size(); i++) {
		logger.info("BundleName:"+bundleFilePathUrls.get(i).getBundleName()+
			", Bundle Size:"+bundleFilePathUrls.get(i).getBundleSize()+","+"No of Files:"+bundleFilePathUrls.get(i).getFilesInBundle());
		FileRequest[] fRequest = bundleFilePathUrls.get(i).getIncludeFiles();
		logger.info("List of Files in bundle:");
		for(int j=0; j<fRequest.length ; j++) {
		    logger.info(fRequest[j].getFilePath()+","+fRequest[j].getFileSize()+","+fRequest[j].getDownloadUrl());
		
		}
	    }
	    
	    //Print logs about files
	     fileName = "RequestedFilesLogs.csv";
	     printLog = "";
	    for(int i=0; i< this.filesLogs.size(); i++) {		
		//logger.info(filesLogs.get(i).toString());
		FileRequestLogs fileLog = filesLogs.get(i);
		printLog += fileLog.getRequestId()+","+ fileLog.getBundleName()+","+fileLog.getRecordId()+","+
			fileLog.getFilePath()+","+fileLog.getFileSize()+","+fileLog.getDownloadUrl()+","+
			fileLog.getTimeStamp()+"\n";
		
	    }
	    writeFile(fileName, printLog);
	    }catch(Exception e) {
		logger.error("Error writing logs on console."+e.getMessage());
	    }
	}
	
	/**
	 * 
	 * @param fileName
	 * @param filecontent
	 */
	public void writeFile(String fileName, String filecontent) {
	    try {
		fileName = logFile+"/"+fileName;
		File loggingFile = new File(fileName);
		loggingFile.createNewFile();
		FileOutputStream outputStream = new FileOutputStream(loggingFile, true);
		byte[] strToBytes = filecontent.getBytes();
		outputStream.write(strToBytes);
		outputStream.close();
	    } catch(IOException e) {
		logger.error("Error Writing Logs File. "+e.getMessage());
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
		if (uObj.getStatus() >= 300 && uObj.getStatus() < 400) {
			whyNotIncluded += "There are too many redirects for this URL.";
			notIncludedFiles.add(new NotIncludedFile(jobject.getFilePath(), jobject.getDownloadUrl(), whyNotIncluded));
		} else if (uObj.getLength() <= 0) {
			whyNotIncluded += ValidationHelper.getStatusMessage(uObj.getStatus());
			notIncludedFiles.add(new NotIncludedFile(jobject.getFilePath(), jobject.getDownloadUrl(), whyNotIncluded));
		} else {
		    	long individualFileSize = uObj.getLength();
			totalRequestedFileSize += individualFileSize;
			if (individualFileSize >= this.mxFilesBundleSize) {
				//bundleSize = individualFileSize;
				List<FileRequest> onefilePathUrls = new ArrayList<FileRequest>();
				onefilePathUrls.add(new FileRequest(jobject.getFilePath(), jobject.getDownloadUrl(), individualFileSize));
				this.makePlan(onefilePathUrls,individualFileSize);
				
			} else {
				bundleSize += individualFileSize;
				if (bundleSize < this.mxFilesBundleSize && bundledFilesCount <= this.mxBundledFilesCount) {
					filePathUrls.add(new FileRequest(jobject.getFilePath(), jobject.getDownloadUrl(), individualFileSize));

				} else {
					if(!filePathUrls.isEmpty()) {
						bundleSize = bundleSize - individualFileSize;	
					    makePlan(filePathUrls, bundleSize);
					}
					filePathUrls.clear();
					bundledFilesCount = 1;
					filePathUrls.add(new FileRequest(jobject.getFilePath(), jobject.getDownloadUrl(),individualFileSize));
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
		logger.info("makeBundlePlan called to return bundleDownloadPlan with urls and sizes.");
		
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
