package gov.nist.mml.oar.distservice.service;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public interface DownloadService {
	
	/**
	 * 
	 * @param multipartFiles
	 * @return
	 */
	public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles);
	
	/**
	 * Download a file. If the file exist in the cache then download it from the cache. If the file does not exist in the cache then  download it from the preservation bucket
	 * @param key
	 * @return
	 * @throws IOException
	 */
	public ResponseEntity<byte[]> downloadFile(String key) throws IOException; 
	
	
	/**
	 * Download a distribution file
	 * @param key
	 * @return
	 * @throws IOException
	 */
	public ResponseEntity<byte[]> downloadDistFile(String dsId, String distId) throws IOException; 
	
	
	/**
	 * 
	 * @return
	 */
	public List<S3ObjectSummary> listCached();
	
	/**
	 * 
	 * @return
	 */
	public List<S3ObjectSummary> listPreserved();
	
	

}
