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
 */

package gov.nist.oar.distrib.datapackage;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;

/**
 * a container for holding a request for a file to be part of a data package (or bundle).  A
 * request is composed of a URL, where the file can be downloaded from, and a file path, the 
 * desired locatin for the file inside the data bundle.  
 */
public class FileRequest {

    /*
     * Name of the file along with path within the bundle
     */
    @NotNull
    private String filePath;

    /*
     * Url related to filename above, to be used to download data
     */
    @NotNull
    private String downloadUrl;

    private long fileSize;
    
    public FileRequest() {
	// DefaultConstructor
    }

    /**
     * Create the request.
     * 
     * @param filepath     the desired file path within a data bundle for the requested file
     * @param downloadurl  the URL where the file can be retrieeved from
     */
    public FileRequest(String filepath, String downloadurl) {
	this.filePath = filepath;
	this.downloadUrl = downloadurl;
    }

    /**
     * 
     * @param filepath
     * @param downloadurl
     * @param fileSize
     */
    public FileRequest(String filepath, String downloadurl, long fileSize) {
  	this.filePath = filepath;
  	this.downloadUrl = downloadurl;
  	this.fileSize = fileSize;
    }
    /**
     * Set the desired file path within a data bundle for the requested file
     * 
     * @param filepath     the desired file path
     */
    public void setFilePath(String filepath) {
	this.filePath = filepath;
    }

    /**
     * return the desired file path for the requested file
     * 
     * @return String -- the forward-slash (/) delimited path desired for the requested file
     */
    public String getFilePath() {
	return this.filePath;
    }


    /**
     * Set the URL where the requested file can be retrieved from 
     * 
     * @param downloadurl   the download URL for the requested file
     */
    public void setDownloadUrl(String downloadurl) {
	this.downloadUrl = downloadurl;
    }

    /**
     * Get the URL where the requested file can be retrieved from 
     * 
     * @return String -- the URL
     */
    public String getDownloadUrl() {
	return this.downloadUrl;
    }

    /**
     * Return file Size
     * @return long
     */
    public long getFileSize() {
	return this.fileSize;
    }
    
    /**
     * Set File Size
     */
    public void setFileSize(long fileSize) {
	this.fileSize = fileSize;
    }
    
    @Override
    public int hashCode() {
	return filePath.hashCode() ^ downloadUrl.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
	if (!(obj instanceof FileRequest))
	    return false;

	FileRequest mdc = (FileRequest) obj;
	return mdc.filePath.equals(filePath) && mdc.downloadUrl.equals(downloadUrl);
    }

}
