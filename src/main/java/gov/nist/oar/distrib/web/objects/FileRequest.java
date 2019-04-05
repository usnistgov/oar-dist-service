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

package gov.nist.oar.distrib.web.objects;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;

/**
 * Jackson json framework to form a request data to a download controller. This
 * class is used to represent the requested post object to dowload bundled zip
 * of requested files with given URLs
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

    public FileRequest() {
	// DefaultConstructor
    }

    /**
     * Set the requested filename along including hierarchy expected and Url to
     * get file from.
     * 
     * @param filepath
     * @param downloadurl
     */
    public FileRequest(String filepath, String downloadurl) {
	this.filePath = filepath;
	this.downloadUrl = downloadurl;
    }

    /**
     * Set filepath
     * 
     * @param filepath
     */
    public void setFilePath(String filepath) {
	this.filePath = filepath;
    }

    /**
     * Get filepath
     * 
     * @return
     */
    public String getFilePath() {
	return this.filePath;
    }

    /**
     * Set downloadurl
     * 
     * @param downloadurl
     */
    public void setDownloadUrl(String downloadurl) {
	this.downloadUrl = downloadurl;
    }

    /**
     * Get the downloaded url
     * 
     * @return
     */
    public String getDownloadUrl() {
	return this.downloadUrl;
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
