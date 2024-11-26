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
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.DistributionException;

/**
 * DefaultDataPackager implements DataPackager interface and gives a default
 * functionality of downloading data from provided list of data urls. This class
 * processes request and validate the requested information. It checks if there
 * are any duplicates in the requested list of files and if the requested list
 * of files is in JSON[] format. Class also checks the allowed size and allowed
 * number of files per package.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class DefaultDataPackager implements DataPackager {


	private long mxFileSize;
	private int mxFilesCount;
	private FileRequest[] inputfileList;
	private BundleRequest bundleRequest;
	private String domains;
	private int allowedRedirects;
	private int fileCount;
	private StringBuilder bundlelogfile = new StringBuilder("");
	private StringBuilder bundlelogError = new StringBuilder("");
	private List<URLStatusLocation> listUrlsStatusSize = new ArrayList<>();
	protected static Logger logger = LoggerFactory.getLogger(DefaultDataPackager.class);
	private long totalRequestedPackageSize = -1;
	private int requestValidity = 0;	
	private String writeLog = "";
	private String requestedFrom = "";
	
	public DefaultDataPackager() {
		// Default Constructor
	}

	/**
	 * Construct input parameters to be used within the class
	 * 
	 * @param inputjson   requested Bundle
	 * @param maxFileSize total file size allowed to download
	 * @param numOfFiles  total number of files allowed to download
	 */
	public DefaultDataPackager(BundleRequest inputjson, long maxFileSize, int numOfFiles, String domains,
			int allowedRedirects) {
		this.bundleRequest = inputjson;
		this.mxFileSize = maxFileSize;
		this.mxFilesCount = numOfFiles;
		this.domains = domains;
		this.allowedRedirects = allowedRedirects;
	}

	/***
	 * Read inputstream from valid urls and stream it to outputstream provided by
	 * response handler.
	 * 
	 * @param zout ZipOutputStream
	 * @throws DistributionException
	 * @throws IOException
	 */

	@Override
	public void getData(ZipOutputStream zout) throws IOException, DistributionException {
		HttpURLConnection con = null;
		this.validateBundleRequest();
		
		logger.info("Forming zip file from the the input fileurls");

		for (int i = 0; i < inputfileList.length; i++) {
		    writeLog += bundleRequest.getRequestId()+ ","+bundleRequest.getBundleName()+",";
			FileRequest jobject = inputfileList[i];
			String filepath = jobject.getFilePath();
			String downloadurl = jobject.getDownloadUrl();
			
			
			/**
			 * This section is added to improve logs for each dowload request through bundles.
			 */
			String recordid ="", filedir ="";
			if (filepath.contains("/") && filepath.contains("ark")) {
				// Process `ark/...` format
				int in = filepath.indexOf('/', 1 + filepath.indexOf('/', 1 + filepath.indexOf('/')));
				recordid = filepath.substring(filepath.indexOf('/', filepath.indexOf('/') + 1) + 1, in);
				filedir = filepath.substring(in + 1);
			} else if (filepath.contains("/")) {
				// Process 32/34-character `resId` format
				int in = filepath.indexOf('/');
				recordid = filepath.substring(0, in);
				filedir = filepath.substring(in + 1);

				if (recordid.matches("[a-zA-Z0-9]{32,34}")) {
					String lastPart = recordid.substring(30); // Strip of the first 30 chars and keep the last part
					recordid = "mds-" + lastPart; // Prepend "mds-"
				}
			}

			writeLog +=recordid+","+filepath+","+jobject.getFileSize()+","+downloadurl+",";
			
			/*
			 *End of part for logs
			*/

			// The modified file path
			String modifiedFilePath = recordid + "/" + filedir;
			
			if (this.validateUrl(downloadurl)) {
				
				URLStatusLocation uLoc = listUrlsStatusSize.get(i);
				
				
				if ((downloadurl.equalsIgnoreCase(uLoc.getRequestedURL())) && this.checkResponse(uLoc)) {
					InputStream fstream = null;
					try {
						URL obj = new URL(this.updateURL(uLoc.getRequestedURL()));
						con = (HttpURLConnection) obj.openConnection();
                                                con.setInstanceFollowRedirects(true);
						fstream = con.getInputStream();
						int len;
						byte[] buf = new byte[100000];
						zout.putNextEntry(new ZipEntry(modifiedFilePath));
						while ((len = fstream.read(buf)) != -1) {
							zout.write(buf, 0, len);
						}
						zout.closeEntry();
						fstream.close();
						fileCount++;
						writeLog += "success \n";
						
					} catch (IOException ie) {
					        writeLog += "failed \n";
						bundlelogError.append("\n Exception in getting data for: " + filepath + " at " + downloadurl
								+ "\n" + "This file might be corrupt.");
						logger.error("There is an error reading this file at location: " + downloadurl + "Exception: "
								+ ie.getMessage());
						zout.closeEntry();
					} finally {
					       
						logger.info("Bundle Request Details:"+ writeLog);
						if (fstream != null)
							fstream.close();
						if (con != null)
							con.disconnect();
					}

				}
			}
			
		}

		if (fileCount == 0) {
			logger.info("The package does not contain any data. These errors :" + this.bundlelogError);
			throw new NoContentInPackageException("No data or files written in Bundle/Package.");
		}

		 this.writeLogMessages(zout);
	}

	/**
	 * This method/function accepts URLStatusLocation to check the status of the URL
	 * response. Creates appropriate log messages and returns true on successfully
	 * accessing data.
	 * 
	 * @param URLStatusLocation
	 * @return boolean
	 * @throws IOException
	 */
	private boolean checkResponse(URLStatusLocation uloc) {

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
	 * If the error code is returned, handle the error and create proper response
	 * message , write in log string builder.
	 * 
	 * @param uloc
	 * @throws IOException
	 */
	private void dealWithErrors(URLStatusLocation uloc) {

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
	 * Write a log file in the bundle/package based on the response from accessing
	 * requested URLs
	 * 
	 * @param zout
	 * @throws IOException
	 */
	private void writeLogMessages(ZipOutputStream zout) throws IOException {
		InputStream nStream = null;
		try {
		    	String bundlerequestStatus = "Bundle Request Summary : "+ this.bundleRequest.getRequestId();
//		    	logger.info("Bundle Request :"+this.bundleRequest.getRequestId());
			String filename = "";
			int l;
			byte[] buf = new byte[10000];
			StringBuilder bundleInfo = new StringBuilder(
					"Information about requested bundle/package is given below.\n");
			if (bundlelogfile.length() != 0) {
				bundleInfo.append("\n Following files are not included in the bundle for the reasons given: \n");
				bundleInfo.append(this.bundlelogfile);
				filename = "/DownloadErrors.txt";
				bundlerequestStatus += ": partial";
//				logger.info(bundlerequestStatus + "This bundle request is not completely successful. ");
			}

			if (bundlelogError.length() != 0) {
				bundleInfo.append(
						"\n Following files are not included in the bundle because of errors: \n" + bundlelogError);
				filename = "/DownloadErrors.txt";
				bundlerequestStatus += ": errors";
//				logger.info(bundlerequestStatus + "There are errors getting data in this bundle request. ");
			}

			if ((bundlelogfile.length() == 0 && bundlelogError.length() == 0) && !listUrlsStatusSize.isEmpty()) {
				bundleInfo.append("\n All requested files are successfully added to this bundle.");
				filename = "/DownloadSuccessful.txt";
				bundlerequestStatus += ": complete";
//				logger.info(bundlerequestStatus +" This bundle request is successful.");
			}

			logger.info(bundlerequestStatus);
			nStream = new ByteArrayInputStream(bundleInfo.toString().getBytes());
			zout.putNextEntry(new ZipEntry(filename));
			while ((l = nStream.read(buf)) != -1) {
				zout.write(buf, 0, l);
			}
			zout.closeEntry();
		} catch (IOException ie) {
			logger.info("Exception while creating Ziplogfile" + ie.getMessage());
			if (zout != null)
				zout.closeEntry();
		} finally {
			if (nStream != null)
				nStream.close();

		}
	}


	/**
	 * If called for the first time within the Datapackager life cycle, this
	 * function/method validates the request. It checks and removes duplicates,
	 * checks total file size to compare with allowed size, Checks total number of
	 * files allowed. This is to validate request and validate input JSON data sent
	 * by the client. If this function is already called by a DataPackager, code
	 * uses existing non-zero status to return appropriate error message, to avoid
	 * redoing HEAD calls to get required information for validation.
	 */
	@Override
	public void validateBundleRequest() throws IOException, DistributionException {
		if (requestValidity == 0) {

			try {
				basicValidation();

				if (this.inputfileList.length <= 0) {
					requestValidity = 3;

				} else {
					ValidationHelper.removeDuplicates(this.inputfileList);
					long totalFilesSize = this.getTotalSize();

					if (totalFilesSize > this.mxFileSize && this.getFilesCount() > 1) {
						requestValidity = 4;

					} else if (this.getFilesCount() > this.mxFilesCount) {
						requestValidity = 5;
					} else {
						int countNotAccessible = ValidationHelper.noOfNotAcceccibleURLs(this.listUrlsStatusSize);
						if (countNotAccessible == this.getFilesCount()) {
							requestValidity = 6;

						} else if (requestValidity == 0)
							requestValidity = 1;
					}
				}

			} catch (IOException ie) {
				requestValidity = 2;
			}

		}

		getServiceErrorStatus(requestValidity);

	}

	/**
	 * This method, helps check validation status internally to avoid doing it
	 * multiple times. As validation status is cached for the DataPackager life
	 * cycle this helps return appropriate exception if any without redoing HEAD/Get
	 * calls.
	 * 
	 * @param status takes requestValidity variable input
	 * @throws IOException
	 * @throws DistributionException
	 */
	private void getServiceErrorStatus(int status) throws IOException, DistributionException {
		switch (status) {
		case 1:
			requestValidity = 1;
			return;
		case 2:
			throw new IOException("IOException while validating request and parsing input.");
		case 3:
			throw new EmptyBundleRequestException();
		case 4:
			throw new InputLimitException("Total filesize is beyond allowed limit of " + this.mxFileSize);
		case 5:
			throw new InputLimitException(
					"Total number of files requested is beyond allowed limit " + this.mxFilesCount);
		case 6:
			throw new NoFilesAccesibleInPackageException("None of the URLs returned data requested.");
		default:
			return;
		}
	}

	/**
	 * Function to get total files size requested by client throws IOException if
	 * there is an issue accessing url.
	 * 
	 * @return long, total size.
	 * @throws MalformedURLException
	 * @throws IOException
	 */

	public long getTotalSize() throws IOException {

		if (this.totalRequestedPackageSize == -1) {
			basicValidation();
			List<FileRequest> list = Arrays.asList(this.inputfileList);

			List<String> downloadurls = list.stream().map(FileRequest::getDownloadUrl).collect(Collectors.toList());
			long totalSize = 0;

			for (int i = 0; i < downloadurls.size(); i++) {
				URLStatusLocation uLoc = ValidationHelper.getFileURLStatusSize(downloadurls.get(i), this.domains,
						this.allowedRedirects);
				listUrlsStatusSize.add(uLoc);
				totalSize += uLoc.getLength();
			}
			this.totalRequestedPackageSize = totalSize;
		}
		return totalRequestedPackageSize;
	}

	/**
	 * Checks whether total number of files requested are within allowed limit.
	 * 
	 * @return boolean based on comparison
	 * @throws IOException
	 */

	private int getFilesCount() throws IOException {
		basicValidation();
		return this.inputfileList.length;
	}

	/**
	 * Validate requested URL by checking whether it is from allowed domains.
	 */
	@Override
	public boolean validateUrl(String url) {
		try {
			if (!ValidationHelper.isAllowedURL(url, this.domains)) {
				this.bundlelogfile.append("\n Url here:" + url);
				this.bundlelogfile.append(" does not belong to allowed domains, so this file is "
						+ "not downnloaded in the bundle/package.");
				return false;
			}
			return true;
		} catch (MalformedURLException ex) {
			this.bundlelogfile.append("\n Url here:" + url);
			this.bundlelogfile
					.append(", is not a legal URL, so this file is " + "not downnloaded in the bundle/package.");
			return false;
		}
	}

	/**
	 * Read the name from Bundle Request, if no name is provided, default name
	 * prefix 'download' is returned
	 */
	@Override
	public String getBundleName() throws IOException {
		String bundleName;
		basicValidation();
		return ((bundleName = bundleRequest.getBundleName()) != null) ? bundleName : "download";

	}

	/**
	 * This checks whether file list is populated if not parse input JSON, validate
	 * and get files.
	 * 
	 * @throws IOException
	 */
	private void basicValidation() throws IOException {
		if (this.inputfileList == null) {
			JSONUtils.isJSONValid(this.bundleRequest);
			this.inputfileList = this.bundleRequest.getIncludeFiles();
		}
	}
	/*
     * provide a compact printing of the stack trace that focuses on the frame(s) associated 
     * with a particular class and method
     */
    String formatLocation(Throwable ex, String methodname) {
        return formatLocation(ex, methodname, this);
    }
    String formatLocation(Throwable ex, String methodname, Object catcher) {
        String cls = catcher.getClass().getName();
        StringBuilder sb = new StringBuilder();
        StringBuilder ellipses = null;
        for (StackTraceElement frame : ex.getStackTrace()) {
            if (frame.getClassName().equals(cls) && frame.getMethodName().equals(methodname)) {
                if (ellipses != null) {
                    sb.append(ellipses.toString());
                    ellipses = null;
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append("  at ").append(frame.getClassName()).append(".").append(frame.getMethodName())
                    .append(":").append(frame.getLineNumber());
            }
            else if (sb.length() > 0) {
                if (ellipses == null) 
                    ellipses = new StringBuilder("\n");
                ellipses.append('.');
            }
        }
        if (sb.length() == 0) sb.append("  at undetected code location");

        return sb.toString();
    }

    private void quietClose(Closeable c) { quietClose(c, null); }
    private void quietClose(Closeable c, String name) {
        try {
            if (c != null) c.close();
        } catch (IOException ex) {
            StringBuffer sb = new StringBuffer();
            if (name != null) sb.append(name).append(": ");
            sb.append("Trouble closing open stream: ").append(ex.getMessage());
            logger.warn(sb.toString());
        }
    }

	@Override
	public void setRequestedAddr(String requestedFrom) {
		this.requestedFrom = requestedFrom.replace(".", "-");
	}

    
    /**
     * Check whether the file is hosted at the given list of servers and then only add the requested IP
     * This is to avoid sending IPs to external hosts which are not managed by our group
     */
    public String updateURL(String url) throws MalformedURLException {
	
    	URL obj = new URL(url);
	    String host = obj.getHost();
	 
	    String[] domainList = this.domains.split("\\|");
	    for (int i = 0; i < domainList.length; i++) {
	       if(host.contains(domainList[i])) {
		    	String urlReq ="";	
		    	if(url.contains("?requestId=") || url.contains("?"))
		    		urlReq= url+"&requestedFrom="+this.requestedFrom;
		    	else
		    		urlReq= url+"?requestedFrom="+this.requestedFrom;
		    	return urlReq;
	       }
	    }
	    return url;
    }
    

}
