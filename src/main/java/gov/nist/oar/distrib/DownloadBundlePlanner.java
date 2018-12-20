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
package gov.nist.oar.distrib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.oar.distrib.datapackage.JSONUtils;
import gov.nist.oar.distrib.datapackage.ObjectUtils;
import gov.nist.oar.distrib.web.BundleDownloadPlan;
import gov.nist.oar.distrib.web.BundleNameFilePathUrl;
import gov.nist.oar.distrib.web.FilePathUrl;
import gov.nist.oar.distrib.web.NotIncludedFiles;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DownloadBundlePlanner {

    protected static Logger logger = LoggerFactory.getLogger(DownloadBundlePlanner.class);
    List<NotIncludedFiles> notIncludedFiles;
    List<FilePathUrl> filePathUrls;
    List<BundleNameFilePathUrl> bundleFilePathUrls;
    List<String> messages;
    BundleDownloadPlan finalPlan;
    long bundlePlanSize = 0;
    int bundlePlanCount = 0;
    int bundleCount = 0;
    String bundleName = "";
    boolean unsupportedSource;
    String status = "complete";
    private FilePathUrl[] inputfileList;
    private BundleNameFilePathUrl bundleRequest;
    private long mxFileSize;
    private int mxFilesCount;
    private String validdomains;

    public DownloadBundlePlanner() {

    }

    public DownloadBundlePlanner(BundleNameFilePathUrl inputjson, long maxFileSize, int numOfFiles,
	    String validdomains) {
	this.bundleRequest = inputjson;
	this.mxFileSize = maxFileSize;
	this.mxFilesCount = numOfFiles;
	this.validdomains = validdomains;
    }
    /*
     * Get the plan to download all files after checking various limits and
     * criteria.
     */

    public BundleDownloadPlan getBundleDownloadPlan() {

	notIncludedFiles = new ArrayList<NotIncludedFiles>();
	filePathUrls = new ArrayList<FilePathUrl>();
	bundleFilePathUrls = new ArrayList<BundleNameFilePathUrl>();
	messages = new ArrayList<String>();

	try {
	    JSONUtils.isJSONValid(this.bundleRequest);
	    this.inputfileList = this.bundleRequest.getIncludeFiles();
	    ObjectUtils.removeDuplicates(this.inputfileList);
	    for (int i = 0; i < inputfileList.length; i++) {
		FilePathUrl jobject = inputfileList[i];
		String filepath = jobject.getFilePath();
		String downloadurl = jobject.getDownloadUrl();
		if (ObjectUtils.validateUrlDomain(downloadurl, validdomains)) {
		    this.makeBundles(jobject);
		} else {
		    notIncludedFiles.add(new NotIncludedFiles(filepath, downloadurl, "Not valid Url."));
		}
	    }

	    if (!this.filePathUrls.isEmpty()) {
		this.makePlan();
	    }
	    if (this.unsupportedSource) {
		messages.add("Some urls are not added due to unsupported host.");
		this.status = "warnings";
	    }
	    this.makeBundlePlan();

	} catch (IOException ie) {
	    messages.add("Error while accessing some url.");
	    this.status = "failure";
	    logger.info("Error while accessing url :" + ie.getMessage());
	}
	return finalPlan;
    }

    public void makeBundles(FilePathUrl jobject) throws IOException {
	bundlePlanSize += ObjectUtils.getFileSize(jobject.getFilePath());
	bundlePlanCount++;
	if (bundlePlanSize < this.mxFileSize && bundlePlanCount < this.mxFilesCount) {
	    filePathUrls.add(new FilePathUrl(jobject.getFilePath(), jobject.getDownloadUrl()));
	} else {
	    makePlan();
	}
    }

    public void makePlan() {
	if (!filePathUrls.isEmpty()) {
	    bundleCount++;
	    FilePathUrl[] fpathUrls = filePathUrls.toArray(new FilePathUrl[0]);
	    bundleFilePathUrls.add(new BundleNameFilePathUrl(bundleName + "-" + bundleCount + ".zip", fpathUrls));
	    filePathUrls.clear();
	}
    }

    // String postEachTo, String status, BundleNameFilePathUrl[] requests,
    // String[] messages, NotIncludedFiles[] notIncluded
    public void makeBundlePlan() {
	this.finalPlan = new BundleDownloadPlan("_bundle", this.status,
		bundleFilePathUrls.toArray(new BundleNameFilePathUrl[0]), messages.toArray(new String[0]),
		notIncludedFiles.toArray(new NotIncludedFiles[0]));
    }

}
