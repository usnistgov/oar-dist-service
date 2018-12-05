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
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.oar.distrib.DataPackager;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.web.FilePathUrl;


/**
 * DefaultDataPackager  implements DataPackager interface and gives a default functionality of downloading data from provided
 * list of data urls. This class processes request and validate the requested information. It checks if there are any duplicates in the requested list of files.
 * The requested list of files is in JSON[] format. Class also checks the allowed size and allowed number of files.  
 * @author Deoyani Nandrekar-Heinis
 */
public class DefaultDataPackager implements DataPackager {
	
	private long mxFileSize;
	private int numberofFiles;
	private FilePathUrl[] inputfileList;
	protected static Logger logger = LoggerFactory.getLogger(DefaultDataPackager.class);
	
	public DefaultDataPackager(){}
	
	/**
	 * Construct input parameters to be used within the class
	 * @param inputjson
	 * @param maxFileSize total file size allowed to download
	 * @param numOfFiles total number of files allowed to download
	 */
	public DefaultDataPackager(FilePathUrl[] inputjson, long maxFileSize, int numOfFiles ){
		this.inputfileList = inputjson;
		this.mxFileSize = maxFileSize;
		this.numberofFiles = numOfFiles;
	}

	/***
	 * Read inputstream from valid urls and stream it to outputstream provided by response handler.
	 * @param zout ZipOutputStream
	 * @throws DistributionException 
	 */
	@Override
	public void writeData(ZipOutputStream zout) throws DistributionException {
		logger.info("Forming zip file from the the input fileurls");
		
			int len,l;
			byte[] buf = new byte[100000];
			for(int i = 0 ; i < inputfileList.length ; i++){
				FilePathUrl jobject = inputfileList[i];
				String filepath = jobject.getFilePath();
				String downloadurl = jobject.getDownloadUrl();
				String zipinfofile = "There was an error getting data from url listed below:";
				try{
					zout.putNextEntry(new ZipEntry(filepath));
				try{
					InputStream fstream = new URL(downloadurl).openStream();
					while ((len = fstream.read(buf)) != -1) {
						zout.write(buf, 0, len);
					}
					zout.closeEntry(); 
					fstream.close();}
				catch(IOException ie){
					zipinfofile += "\n"+downloadurl;
					InputStream nStream = new ByteArrayInputStream(zipinfofile.getBytes());
					zout.putNextEntry(new ZipEntry("ZipInfo.txt"));
					while ((l = nStream.read(buf)) != -1) {
						zout.write(new byte[1000], 0, l);
					}
					zout.closeEntry();
					logger.info("There is an error reading this file at location: "+downloadurl+ 
							"Exception: "+ie.getMessage());
					
				}}catch(IOException ie){
					logger.info("There is an error createing zip entry for "+downloadurl+ 
							"Exception: "+ie.getMessage());
				}
			}
		
   	
	}

	/**
	 * Validate the request sent by user.
	 * Function checks and eliminates duplicates, check total filesize to comapre with allowed size, 
	 * Checks total number of files allowed 
	 * @throws DistributionException
	 */
	@Override
	public void validateRequest() throws DistributionException {
		if(this.inputfileList.length > 0){
			JSONUtils jUtils = new JSONUtils();
			if(!jUtils.isJSONValid(inputfileList))
				throw new DistributionException("Input values are not valid. Check the json represntation.");;
			
			// Remove duplicates	
			List<FilePathUrl> list =  Arrays.asList(inputfileList);
			List<FilePathUrl> newfilelist  = list.stream()
                    .distinct()               // it will remove duplicate object, It will check duplicate using equals method
                    .collect(Collectors.toList());
			
			this.inputfileList =  newfilelist.toArray(new FilePathUrl[0]) ;
					
			if(this.getSize() > this.mxFileSize) 
				throw new DistributionException("Total filesize is beyond allowed limit.");
			if(this.getFilesCount() > this.numberofFiles)
				throw new DistributionException("Total number of files requested is beyond allowed limit.");
				
			
		}else{
			throw new DistributionException("Requested files jsonobject is empty.");
		}
		
	}
	
	

	/**
	 * Function to calculate total files size requested by user
	 * @return long, total size.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	@Override
	public long getSize() {
		
		List<FilePathUrl> list = Arrays.asList(this.inputfileList);
		
		List<String> downloadurls = list.stream()
				.map(FilePathUrl::getDownloadUrl)
                .collect(Collectors.toList());
		long totalSize = 0;
		for (int i = 0; i < downloadurls.size(); i++) {
			try{
			URL obj = new URL(downloadurls.get(i));
			URLConnection conn = obj.openConnection();
			totalSize += conn.getContentLength();
			}catch(IOException ie){
				logger.info("There is error reading this url:"+downloadurls.get(i));
			}
		}
		return totalSize;	
		
	}

	/**
	 * Checks whether total number of files requested are within allowed limit. 
	 * @return boolean based on comparison
	 */
	@Override
	public int getFilesCount() {
		return this.inputfileList.length;
		
	}

    /**
     * Writedata function taking other form of outputstream
     * @param out
     * @throws DistributionException
     */
	@Override
	public void writeData(OutputStream out) throws DistributionException {
		// TODO Auto-generated method stub
		
	}



}
