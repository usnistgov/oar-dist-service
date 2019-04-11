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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
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
import gov.nist.oar.distrib.web.ServiceSyntaxException;
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
    int fileCount;
    StringBuilder bundlelogfile = new StringBuilder("");
    StringBuilder bundlelogError = new StringBuilder("");
    List<URLStatusLocation> listUrlsStatusSize = new ArrayList<>();
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
     * @throws IOException
     */

    @Override
    public void getData(ZipOutputStream zout) throws DistributionException, IOException {
	HttpURLConnection con = null;
	this.validateBundleRequest();

	logger.info("Forming zip file from the the input fileurls");

	for (int i = 0; i < inputfileList.length; i++) {
	    FileRequest jobject = inputfileList[i];
	    String filepath = jobject.getFilePath();
	    String downloadurl = jobject.getDownloadUrl();
	    URLStatusLocation uLoc = listUrlsStatusSize.get(i);
	    if ((downloadurl.equalsIgnoreCase(uLoc.getRequestedURL())) && this.checkResponse(uLoc)) {
		URL obj = new URL(uLoc.getRequestedURL());
		con = (HttpURLConnection) obj.openConnection();
		try {

		    int len;
		    byte[] buf = new byte[100000];
		    zout.putNextEntry(new ZipEntry(filepath));
		    InputStream fstream = con.getInputStream();
		    while ((len = fstream.read(buf)) != -1) {
			zout.write(buf, 0, len);
		    }
		    zout.closeEntry();
		    fstream.close();
		    fileCount++;
		} catch (IOException ie) {
		    bundlelogError.append("\n Exception in getting data for: " + filepath + " at " + downloadurl);
		    logger.error("There is an error reading this file at location: " + downloadurl + "Exception: "
			    + ie.getMessage());
		}
		if (con != null)
		    con.disconnect();
	    }

	}

	if (fileCount == 0) {
	    logger.info("The package does not contain any data. These errors :" + this.bundlelogError);
	    throw new NoContentInPackageException("No data or files written in Bundle/Package.");
	}
	this.writeLog(zout);

    }

    /**
     * This method/function gets an open validated url connection to check the
     * status of the URL response. Creates appropriate log messages and returns
     * true on successfully accessing data.
     * 
     * @param con
     * @return boolean
     * @throws IOException
     */
    private boolean checkResponse(URLStatusLocation uloc) throws IOException {

	if (uloc.getStatus() == 200) {
	    return true;
	} else if (uloc.getStatus() >= 300) {
	    dealWithErrors(uloc);
	    return false;
	} else if (uloc.getStatus() == 0) {
	    this.bundlelogError.append("\n " + uloc.getRequestedURL());
	    if (!uloc.isValidURL()) {
		this.bundlelogError.append(
			" does not belong to allowed/valid domains, so this file is not downnloaded in the bundle/package.");
	    } else {

		this.bundlelogError.append(" There is an Error accessing this file. Could not connect successfully. ");
	    }
	    return false;
	}
	return false;
    }

    /**
     * If the error code is returned, handle the error and create proper
     * response message.
     * 
     * @param uloc
     * @throws IOException
     */
    private void dealWithErrors(URLStatusLocation uloc) throws IOException {

	String requestedUrl = uloc.getRequestedURL();
	if (uloc.getStatus() >= 400 && uloc.getStatus() <= 500) {

	    logger.info(requestedUrl + " Error accessing this url: " + uloc.getStatus());
	    this.bundlelogError.append("\n " + requestedUrl);
	    this.bundlelogError
		    .append(" There is an Error accessing this file, Server returned status with response code  ");
	    this.bundlelogError
		    .append(uloc.getStatus() + " and message:" + ValidationHelper.getStatusMessage(uloc.getStatus()));

	} else if (uloc.getStatus() >= 300 && uloc.getStatus() <= 400) {

	    this.bundlelogError.append("\n" + requestedUrl + " There are too many redirects for this URL.");

	} else if (uloc.getStatus() >= 500) {
	    this.bundlelogError.append("\n" + requestedUrl + " There is an internal server error accessing this url.");
	    this.bundlelogError.append(" Server returned status with response code :" + uloc.getStatus());

	}

    }

    /**
     * Write a log file in the bundle/package based on the response from
     * accessing requested URLs
     * 
     * @param zout
     */
    private void writeLog(ZipOutputStream zout) {
	try {
	    String filename = "";
	    int l;
	    byte[] buf = new byte[10000];
	    StringBuilder bundleInfo = new StringBuilder(
		    "Information about requested bundle/package is given below.\n");
	    if (bundlelogfile.length() != 0) {
		bundleInfo.append(" Following files are not included in the bundle for the reasons given: \n");
		bundleInfo.append(this.bundlelogfile);
		filename = "/PackagingErrors.txt";
	    }

	    if (bundlelogError.length() != 0) {
		bundleInfo.append(
			" Following files are not included in the bundle because of errors: \n" + bundlelogError);
		filename = "/PackagingErrors.txt";
	    }

	    if ((bundlelogfile.length() == 0 && bundlelogError.length() == 0) && !listUrlsStatusSize.isEmpty()) {
		bundleInfo.append(" All requested files are successfully added to this bundle.");
		filename = "/PackagingSuccessful.txt";
	    }

	    InputStream nStream = new ByteArrayInputStream(bundleInfo.toString().getBytes());
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
     * duplicates, check total file size to compare with allowed size, Checks
     * total number of files allowed This is to validate request and validate
     * input JSON data sent by the client.
     */
    @Override
    public void validateBundleRequest() throws IOException, DistributionException {

	basicValidation();

	if (this.inputfileList.length <= 0)
	    throw new ServiceSyntaxException("Bundle Request has empty list of files and urls.");

	ValidationHelper.removeDuplicates(this.inputfileList);
	long totalFilesSize = this.getTotalSize();

	if (totalFilesSize > this.mxFileSize & this.getFilesCount() > 1) {
	    throw new InputLimitException("Total filesize is beyond allowed limit of " + this.mxFileSize);
	}

	if (this.getFilesCount() > this.mxFilesCount)
	    throw new InputLimitException(
		    "Total number of files requested is beyond allowed limit " + this.mxFilesCount);

	if (!ValidationHelper.areAllUrlsInaccessible(this.listUrlsStatusSize))
	    throw new NoFilesAccesibleInPackageException("None of the URLs returned data requested.");
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
	basicValidation();
	List<FileRequest> list = Arrays.asList(this.inputfileList);

	List<String> downloadurls = list.stream().map(FileRequest::getDownloadUrl).collect(Collectors.toList());
	long totalSize = 0;

	for (int i = 0; i < downloadurls.size(); i++) {
	    URLStatusLocation uLoc = ValidationHelper.getFileURLStatusSize(downloadurls.get(i), this.domains);
	    listUrlsStatusSize.add(uLoc);
	    totalSize += uLoc.getLength();
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
	basicValidation();
	return this.inputfileList.length;
    }

    /**
     * Validate requested URL by checking whether it is from allowed domains.
     */
    @Override
    public boolean validateUrl(String url) throws IOException, DistributionException {
	try {
	    if (!ValidationHelper.isAllowedURL(url, this.domains)) {
		this.bundlelogfile.append("\n Url here:" + url);
		this.bundlelogfile.append(
			" does not belong to allowed domains, so this file is not downnloaded in the bundle/package.");
		return false;
	    }
	    return true;
	} catch (IOException ie) {
	    logger.info("There is an issue accessing this url:" + url + " Excption here" + ie.getMessage());
	    this.bundlelogfile.append("\n There is an issue accessing this url:" + url);
	    return false;
	}
    }

    /*
     * Get the name provided or use default bundle name.
     */
    @Override
    public String getBundleName() throws IOException {
	String bundleName;
	basicValidation();
	return ((bundleName = bundleRequest.getBundleName()) != null) ? bundleName : "download";

    }

    private void basicValidation() throws IOException {
	if (this.inputfileList == null) {
	    JSONUtils.isJSONValid(this.bundleRequest);
	    this.inputfileList = this.bundleRequest.getIncludeFiles();
	}
    }

}
