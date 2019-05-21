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
import java.util.ArrayList;
import java.util.List;
import java.net.MalformedURLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.FileRequest;
import gov.nist.oar.distrib.datapackage.NotIncludedFile;

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

    public DownloadBundlePlanner() {
	// Default constructor
    }

    public DownloadBundlePlanner(BundleRequest inputjson, long maxFileSize, int numOfFiles, String validdomains,
	    String bundleName) {
	this.bundleRequest = inputjson;
	this.mxFilesBundleSize = maxFileSize;
	this.mxBundledFilesCount = numOfFiles;
	this.validdomains = validdomains;
	this.bundleName = bundleName;
    }

    /**
     * Get the plan to download all files after checking various limits and
     * criteria
     * 
     * @return BundleDownloadPlan -- the recommended plan
     * @throws DistributionException 
     */
    public BundleDownloadPlan getBundleDownloadPlan() throws DistributionException {

	notIncludedFiles = new ArrayList<NotIncludedFile>();
	filePathUrls = new ArrayList<FileRequest>();
	bundleFilePathUrls = new ArrayList<BundleRequest>();
	messages = new ArrayList<String>();

        try {
	    ObjectMapper mapper = new ObjectMapper();
	    String requestString = mapper.writeValueAsString(this.bundleRequest);
	    if (ValidationHelper.hasHTMLTags(requestString)) {
	        messages.add("Input contains html code, make sure to post proper request.");
	        this.status = "Error";
	        return makeBundlePlan();
	     
	    }
        } catch (JsonProcessingException ex) {
            // should not happen
            throw new DistributionException("Trouble validating request: unable to conver to JSON: " +
                                            ex.getMessage());
        }

	try {
	    JSONUtils.isJSONValid(this.bundleRequest);
	    this.inputfileList = this.bundleRequest.getIncludeFiles();
	    ValidationHelper.removeDuplicates(this.inputfileList);
	} catch (IOException ie) {
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
                notIncludedFiles.add(new NotIncludedFile(filepath, downloadurl,
                                                         "File not added in package; malformed URL"));
            }
	}

	if (!this.filePathUrls.isEmpty()) {
	    this.makePlan(this.filePathUrls);
	}

	this.updateMessagesAndStatus();
	return this.makeBundlePlan();
	
    }

    /**
     * Add to Bundle of the input requested based on the size and number of
     * files allowed per bundle request.
     * 
     * @param jobject
     * @throws IOException
     */
    public void makeBundles(FileRequest jobject) {
	bundledFilesCount++;
	URLStatusLocation uObj = ValidationHelper.getFileURLStatusSize(jobject.getDownloadUrl(), this.validdomains);
	long individualFileSize = uObj.getLength();
	if (individualFileSize <= 0) {
		String whyNotIncluded =  "File not added in package; ";
		if(uObj.getStatus() >=300 && uObj.getStatus() <400)
			whyNotIncluded += "There are multiple redirects for this URL.";
		else
			whyNotIncluded += ValidationHelper.getStatusMessage(uObj.getStatus());
	    notIncludedFiles.add(new NotIncludedFile(jobject.getFilePath(), jobject.getDownloadUrl(),
		     whyNotIncluded));
	} else {
            if (individualFileSize >= this.mxFilesBundleSize) {
                List<FileRequest> onefilePathUrls = new ArrayList<FileRequest>();
                onefilePathUrls.add(new FileRequest(jobject.getFilePath(), jobject.getDownloadUrl()));
                this.makePlan(onefilePathUrls);
	    } else {
		bundleSize += individualFileSize;
		if (bundleSize < this.mxFilesBundleSize && bundledFilesCount <= this.mxBundledFilesCount) {
		    filePathUrls.add(new FileRequest(jobject.getFilePath(), jobject.getDownloadUrl()));
		} else {
		    makePlan(filePathUrls);
		    filePathUrls.clear();
		    bundledFilesCount = 1;
		    filePathUrls.add(new FileRequest(jobject.getFilePath(), jobject.getDownloadUrl()));
		    bundleSize = individualFileSize;
		}
	    }
	}
    }

    /***
     * Create Bundle of FileList
     * 
     * @param fPathUrls
     */
    public void makePlan(List<FileRequest> fPathUrls) {
	bundleCount++;
	FileRequest[] bundlefilePathUrls = fPathUrls.toArray(new FileRequest[0]);
	bundleFilePathUrls.add(new BundleRequest(bundleName + "-" + bundleCount + ".zip", bundlefilePathUrls));
    }

    private void updateMessagesAndStatus() {
	if (!this.notIncludedFiles.isEmpty() && this.bundleFilePathUrls.isEmpty()) {
	    this.messages.add("No Files added in the Bundle, there are problems accessing URLs.");
	    this.status = "Error";
	}
	if (!this.notIncludedFiles.isEmpty() && !this.bundleFilePathUrls.isEmpty()) {
	    messages.add("Some URLs have problem accessing contents.");
	    this.status = "warnings";
	}

    }

    /**
     * Create final Bundle plan (JSON) to return to client. It creates Java
     * Object of BundleDownloadPlan after processing input request.
     */
    public BundleDownloadPlan makeBundlePlan() {
	return new BundleDownloadPlan("_bundle", this.status,
		bundleFilePathUrls.toArray(new BundleRequest[0]), messages.toArray(new String[0]),
		notIncludedFiles.toArray(new NotIncludedFile[0]));
    }

}
