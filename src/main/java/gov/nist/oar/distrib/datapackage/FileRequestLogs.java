package gov.nist.oar.distrib.datapackage;

import jakarta.validation.constraints.NotNull;

public class FileRequestLogs {

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

    private String timeStamp;

    private String requestId;

    private String bundleName;

    private String recordId;

    public FileRequestLogs() {
	// DefaultConstructor
    }

    /**
     * Create the request.
     * 
     * @param filepath    the desired file path within a data bundle for the
     *                    requested file
     * @param downloadurl the URL where the file can be retrieeved from
     */
    public FileRequestLogs(String filepath, String downloadurl) {
	this.filePath = filepath;
	this.downloadUrl = downloadurl;
    }

    /**
     * Constructor with  filepath, downloadurl and filesize
     * @param String filepath
     * @param String downloadurl
     * @param long fileSize
     */
    public FileRequestLogs(String filepath, String downloadurl, long fileSize) {
	this.filePath = filepath;
	this.downloadUrl = downloadurl;
	this.fileSize = fileSize;
    }

    /**
     * Constructor 
     * @param String filepath
     * @param String downloadurl
     * @param long fileSize
     * @param String timeStamp
     */
    public FileRequestLogs(String filepath, String downloadurl, long fileSize, String timeStamp) {
	this.filePath = filepath;
	this.downloadUrl = downloadurl;
	this.fileSize = fileSize;
	this.timeStamp = timeStamp;
    }

    /**
     * Constructor
     * @param String requestId
     * @param String bundleName
     * @param String recordId
     * @param String filepath
     * @param long fileSize
     * @param String downloadurl
     * @param String timeStamp
     */
    public FileRequestLogs(String requestId, String bundleName, String recordId, String filepath, long fileSize,
	    String downloadurl, String timeStamp) {
	this.filePath = filepath;
	this.downloadUrl = downloadurl;
	this.fileSize = fileSize;
	this.timeStamp = timeStamp;
	this.recordId = recordId;
	this.requestId = requestId;
	this.bundleName = bundleName;

    }

    /**
     * Set the unique request id
     * @param  String requestId
     */
    public void setRequestId(String requestId) {
	this.requestId = requestId;
    }

    /**
     * Get the unique request Id
     * @return String requestId
     */
    public String getRequestId() {
	return this.requestId;
    }

    /**
     * Set the record id for requested data
     * @param RecordId
     */
    public void setRecordId(String recordId) {
	this.recordId = recordId;
    }

    /**
     * Get the record id for given record
     * @return
     */
    public String getRecordId() {
	return this.recordId;
    }

    /**
     * Set name for the bundle request
     * @param String BundleName
     */
    public void setBundleName(String bundleName) {
	this.bundleName = bundleName;
    }

    /**
     * Get bundle name
     * @return String BundleName
     */
    public String getBundleName() {
	return this.bundleName;
    }

    /**
     * Set the timestamp
     * @param String timeStamp
     */
    public void setTimeStamp(String timeStamp) {
	this.timeStamp = timeStamp;
    }

    /**
     * get timestamp
     * @return String timeStamp
     */
    public String getTimeStamp() {
	return this.timeStamp;
    }

    /**
     * Set the desired file path within a data bundle for the requested file
     * 
     * @param filepath the desired file path
     */
    public void setFilePath(String filepath) {
	this.filePath = filepath;
    }

    /**
     * return the desired file path for the requested file
     * 
     * @return String -- the forward-slash (/) delimited path desired for the
     *         requested file
     */
    public String getFilePath() {
	return this.filePath;
    }

    /**
     * Set the URL where the requested file can be retrieved from
     * 
     * @param downloadurl the download URL for the requested file
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
     * 
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

	FileRequestLogs mdc = (FileRequestLogs) obj;
	return mdc.filePath.equals(filePath) && mdc.downloadUrl.equals(downloadUrl);
    }

}
