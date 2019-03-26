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
import gov.nist.oar.distrib.InputLimitException;
import gov.nist.oar.distrib.web.objects.BundleRequest;
import gov.nist.oar.distrib.web.objects.FileRequest;

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
    private FileRequest[] inputfileList;
    private BundleRequest bundleRequest;
    private String domains;
    String bundlelogfile = "";
    String bundlelogError = "";
    int filecount = 0;
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
    public DefaultDataPackager(FileRequest[] inputjson, long maxFileSize, int numOfFiles) {
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
    public DefaultDataPackager(BundleRequest inputjson, long maxFileSize, int numOfFiles) {
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
    public DefaultDataPackager(BundleRequest inputjson, long maxFileSize, int numOfFiles, String domains) {
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
    int tryAccessUrl;

    @Override
    public void writeData(ZipOutputStream zout) throws DistributionException {
	logger.info("Forming zip file from the the input fileurls");
	int len;
	byte[] buf = new byte[100000];
	for (int i = 0; i < inputfileList.length; i++) {
	    FileRequest jobject = inputfileList[i];
	    String filepath = jobject.getFilePath();
	    String downloadurl = jobject.getDownloadUrl();
	    tryAccessUrl = 1;
	    try {
		if (this.validateUrl(downloadurl) && this.checkFileURLResponse(downloadurl)) {
		    this.writeDataFile(zout, filepath, downloadurl);
		    filecount++;
		}
	    } catch (IOException ie) {
		bundlelogError += "\n Exception in getting data for: " + filepath + " at " + downloadurl;
		logger.error("There is an error reading this file at location: " + downloadurl + "Exception: "
			+ ie.getMessage());
	    }
	}
	this.writeLog(zout);

    }

    /**
     * 
     * @param downloadurl
     * @param filepath
     * @param zout
     * @throws IOException
     */
    private boolean checkFileURLResponse(String downloadurl) {
	UrlStatusLocation uloc = ObjectUtils.getURLStatus(downloadurl);
	if (uloc.getStatus() >= 400 && uloc.getStatus() <= 500) {

	    logger.info(downloadurl + " Error accessing this url: " + uloc.getStatus());
	    this.bundlelogError = "\n " + downloadurl
		    + " There is an Error accessing this file, Server returned status " + uloc.getStatus()
		    + " with message:" + ObjectUtils.getStatusMessage(uloc.getStatus());
	    return false;

	} else if (uloc.getStatus() >= 300 && uloc.getStatus() <= 400) {

	    tryAccessUrl++;
	    if (tryAccessUrl > 4)
		return false;

	    checkFileURLResponse(uloc.getLocation());
	} else if (uloc.getStatus() >= 500) {
	    this.bundlelogError = "\n" + downloadurl + " There is an internal server error accessing this url."
		    + " Server returned code :" + uloc.getStatus();
	    return false;
	} else if (uloc.getStatus() == 200) {

	    return true;
	}

	return false;
    }

    /**
     * Write data files in the output bundle/package.
     * 
     * @param zout
     *            OutputStream
     * @param filepath
     *            File with the path information to create similar structure in
     *            package
     * @param downloadurl
     *            URL to download data
     * @throws IOException
     */
    private void writeDataFile(ZipOutputStream zout, String filepath, String downloadurl) throws IOException {
	int len;
	byte[] buf = new byte[100000];
	zout.putNextEntry(new ZipEntry(filepath));
	InputStream fstream = new URL(downloadurl).openStream();
	while ((len = fstream.read(buf)) != -1) {
	    zout.write(buf, 0, len);
	}
	zout.closeEntry();
	fstream.close();
    }

    private void writeLog(ZipOutputStream zout) {
	try {
	    String filename = "";
	    int l;
	    byte[] buf = new byte[10000];
	    String bundleInfo = "Information about status of this bundle and contents is as below:\n";
	    if (!bundlelogfile.isEmpty()) {
		bundleInfo += " Following files are not included in the bundle : \n" + this.bundlelogfile;
		filename = "PackagingErrors.txt";
	    }

	    if (!bundlelogError.isEmpty()) {
		bundleInfo += " There is an Error accessing some of the data files : \n" + bundlelogError;
		filename = "PackagingErrors.txt";
	    }

	    if ((bundlelogfile.isEmpty() && bundlelogError.isEmpty()) && filecount > 0) {
		bundleInfo += " All requested files are successsfully added to this bundle.";
		filename = "PackagingSuccessful.txt";
	    }

	    InputStream nStream = new ByteArrayInputStream(bundleInfo.getBytes());
	    zout.putNextEntry(new ZipEntry(filename));
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

	    if (this.getTotalSize() > this.mxFileSize & this.getFilesCount() > 1) {
		throw new InputLimitException("Total filesize is beyond allowed limit of." + this.mxFileSize);
	    }
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
	List<FileRequest> list = Arrays.asList(this.inputfileList);

	List<String> downloadurls = list.stream().map(FileRequest::getDownloadUrl).collect(Collectors.toList());
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
	    if (!ObjectUtils.isAllowedURL(url, this.domains)) {
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
