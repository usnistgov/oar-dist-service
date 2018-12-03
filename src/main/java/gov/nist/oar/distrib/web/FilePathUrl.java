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

package gov.nist.oar.distrib.web;

/**
 * Jackson json framework to form a request data to a download controller. 
 * This class is used to represent the requested post object to dowload bundled zip of requested files 
 * with given URLs
 */
public class FilePathUrl {

	/*
	 * Name of the file along with path within the bundle
	 */
	private String filePath;
	/**
	 * Url related to filename above, to be used to download data
	 */
	private String downloadUrl;
	
	public FilePathUrl(){
		//DefaultConstructor
	}
	/**
	 * 
	 * @param filepath
	 * @param downloadurl
	 */
	public FilePathUrl(String filepath, String downloadurl){
		this.filePath = filepath;
		this.downloadUrl = downloadurl;
	}
	/**
	 * Set filepath
	 * @param filepath
	 */
	public void setFilePath(String filepath){
		this.filePath = filepath;
	}
	/**
	 * Get filepath
	 * @return
	 */
	public String getFilePath(){
		return this.filePath;
	}
	
	/**
	 * Set downloadurl
	 * @param downloadurl
	 */
	public void setDownloadUrl(String downloadurl){
		this.downloadUrl = downloadurl;
	}
	
	/**
	 * Get the downloaded url
	 * @return
	 */
	public String getDownloadUrl(){
		return this.downloadUrl;
	}
	
	@Override
    public int hashCode() {
        return filePath.hashCode() ^ downloadUrl.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FilePathUrl))
            return false;

        FilePathUrl mdc = (FilePathUrl) obj;
        return mdc.filePath.equals(filePath) && mdc.downloadUrl.equals(downloadUrl);
    }

}
