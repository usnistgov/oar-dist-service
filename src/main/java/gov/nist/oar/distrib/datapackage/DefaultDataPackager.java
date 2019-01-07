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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.DataPackager;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.DownloadBundlePlanner;
import gov.nist.oar.distrib.InputLimitException;
import gov.nist.oar.distrib.web.objects.BundleDownloadPlan;
import gov.nist.oar.distrib.web.objects.BundleNameFilePathUrl;
import gov.nist.oar.distrib.web.objects.FilePathUrl;

/**
 * DefaultDataPackager implements DataPackager interface and gives a default
 * functionality of downloading data from provided list of data urls. This class
 * processes request and validate the requested information. It checks if there
 * are any duplicates in the requested list of files. The requested list of
 * files is in JSON[] format. Class also checks the allowed size and allowed
 * number of files.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class DefaultDataPackager implements DataPackager {

    private long mxFileSize;
    private int mxFilesCount;
    private FilePathUrl[] inputfileList;
    private BundleNameFilePathUrl bundleRequest;
    private String domains;
    String bundlelogfile = " Information about this bundle and contents as below:\n";
    protected static Logger logger = LoggerFactory.getLogger(DefaultDataPackager.class);

    public DefaultDataPackager() {
	// Default Constructor
    }

    /**
     * Construct input parameters to be used within the class
     * 
     * @param inputjson
     * @param maxFileSize
     *            total file size allowed to download
     * @param numOfFiles
     *            total number of files allowed to download
     */
    public DefaultDataPackager(FilePathUrl[] inputjson, long maxFileSize, int numOfFiles) {
	this.inputfileList = inputjson;
	this.mxFileSize = maxFileSize;
	this.mxFilesCount = numOfFiles;
    }

    /**
     * Construct input parameters to be used within the class
     * 
     * @param inputjson
     * @param maxFileSize
     *            total file size allowed to download
     * @param numOfFiles
     *            total number of files allowed to download
     */
    public DefaultDataPackager(BundleNameFilePathUrl inputjson, long maxFileSize, int numOfFiles) {
	this.bundleRequest = inputjson;
	this.mxFileSize = maxFileSize;
	this.mxFilesCount = numOfFiles;
    }

    /**
     * Construct input parameters to be used within the class
     * 
     * @param inputjson
     * @param maxFileSize
     *            total file size allowed to download
     * @param numOfFiles
     *            total number of files allowed to download
     */
    public DefaultDataPackager(BundleNameFilePathUrl inputjson, long maxFileSize, int numOfFiles, String domains) {
	this.bundleRequest = inputjson;
	this.mxFileSize = maxFileSize;
	this.mxFilesCount = numOfFiles;
	this.domains = domains;
    }

    /***
     * Read inputstream from valid urls and stream it to outputstream provided
     * by response handler.
     * 
     * @param zout
     *            ZipOutputStream
     * @throws DistributionException
     */
    @Override
    public void writeData(ZipOutputStream zout) throws DistributionException {
	logger.info("Forming zip file from the the input fileurls");

	int len, l;
	byte[] buf = new byte[100000];
	for (int i = 0; i < inputfileList.length; i++) {
	    FilePathUrl jobject = inputfileList[i];
	    String filepath = jobject.getFilePath();
	    String downloadurl = jobject.getDownloadUrl();
	    bundlelogfile += "If there are any issues with any files/urls it will be listed below.";

	    try {

		if (this.validateUrl(downloadurl)) {
		    zout.putNextEntry(new ZipEntry(filepath));
		    InputStream fstream = new URL(downloadurl).openStream();
		    while ((len = fstream.read(buf)) != -1) {
			zout.write(buf, 0, len);
		    }
		    zout.closeEntry();
		    fstream.close();
		}
	    } catch (IOException ie) {

		bundlelogfile += "\n Exception in getting data for: " + filepath + " at " + downloadurl;

		logger.info("There is an error reading this file at location: " + downloadurl + "Exception: "
			+ ie.getMessage());

	    }

	}
	this.writeLog(zout);

    }

    private void writeLog(ZipOutputStream zout) {
	try {
	    int len, l;
	    byte[] buf = new byte[10000];
	    InputStream nStream = new ByteArrayInputStream(bundlelogfile.getBytes());
	    zout.putNextEntry(new ZipEntry("BundleInfo.txt"));
	    while ((l = nStream.read(buf)) != -1) {
		zout.write(buf, 0, l);
	    }
	    zout.closeEntry();
	} catch (IOException ie) {
	    logger.info("Exception while creating Ziplogfile" + ie.getMessage());
	}
    }

    /**
     * Validate the request sent by client. Function checks and eliminates
     * duplicates, check total filesize to compare with allowed size, Checks
     * total number of files allowed
     * 
     * @throws DistributionException
     * @throws IOException
     */
    @Override
    public void validateRequest() throws DistributionException, IOException, InputLimitException {
	if (this.inputfileList.length > 0) {

	    ObjectUtils.removeDuplicates(this.inputfileList);

	    if (this.getTotalSize() > this.mxFileSize)
		throw new InputLimitException("Total filesize is beyond allowed limit of." + this.mxFileSize);

	    if (this.getFilesCount() > this.mxFilesCount)
		throw new InputLimitException(
			"Total number of files requested is beyond allowed limit." + this.mxFilesCount);

	} else {
	    throw new DistributionException("Requested files jsonobject is empty.");
	}

    }

    /**
     * This is to validate request and validate input json data sent.
     */
    @Override
    public void validateBundleRequest() throws DistributionException, IOException, InputLimitException {

	JSONUtils.isJSONValid(this.bundleRequest);

	this.inputfileList = this.bundleRequest.getIncludeFiles();

	this.validateRequest();
    }

    /*
     * Check whether input request is a valid json data.
     */
    @Override
    public void validateInput() throws IOException {

	JSONUtils.isJSONValid(this.bundleRequest);
    }

    /**
     * Function to get total files size requested by client throws IOException
     * if there is an issue accessing url.
     * 
     * @return long, total size.
     * @throws MalformedURLException
     * @throws IOException
     */
    @Override
    public long getTotalSize() throws IOException {
	if (this.inputfileList == null) {
	    this.validateInput();
	    this.inputfileList = this.bundleRequest.getIncludeFiles();
	}
	List<FilePathUrl> list = Arrays.asList(this.inputfileList);

	List<String> downloadurls = list.stream().map(FilePathUrl::getDownloadUrl).collect(Collectors.toList());
	long totalSize = 0;
	for (int i = 0; i < downloadurls.size(); i++) {
	    try {
		totalSize += ObjectUtils.getFileSize(downloadurls.get(i));
	    } catch (IOException ie) {
		logger.info("There is error reading this url:" + downloadurls.get(i));
	    }
	}
	return totalSize;

    }

    /**
     * Checks whether total number of files requested are within allowed limit.
     * 
     * @return boolean based on comparison
     * @throws IOException
     */
    @Override
    public int getFilesCount() throws IOException {
	if (this.inputfileList == null) {
	    this.validateInput();
	    this.inputfileList = this.bundleRequest.getIncludeFiles();
	}
	return this.inputfileList.length;
    }

    /**
     * Validate input url.
     */
    @Override
    public boolean validateUrl(String url) throws IOException, DistributionException {
	try {
	    if (!ObjectUtils.validateUrlDomain(url, this.domains)) {
		this.bundlelogfile += "\n Url here:" + url
			+ " does not belong to allowed domains, so no file is downnloaded for this";
		return false;
	    }
	    return true;
	} catch (IOException ie) {
	    logger.info("There is an issue accessing this url:" + url + " Excption here" + ie.getMessage());
	    this.bundlelogfile += "\n There is an issue accessing this url:" + url;
	    return false;
	}
    }

}
