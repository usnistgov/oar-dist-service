package gov.nist.mml.oar.ds.service;

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
	 * Return a summary list of bags of a data set
	 * @param dsId: id of the data set
	 * @return the list of keys of the bags
	 * @throws IOException
	 */
	ResponseEntity<List<String>> findDataSetBags(String dsId) throws IOException;

	/**
	 * 
	 * @param dsId
	 * @param distId
	 * @return 
	 * @throws IOException
	 */
	ResponseEntity<byte[]> downloadDistributionFile(String dsId, String distId) throws IOException;

	/**
	 * Find the head bag of a data set by its id
	 * @param dsId: id of the data set
	 * @return the head bag key 
	 * @throws IOException
	 */
	ResponseEntity<String> findDataSetHeadBag(String dsId) throws IOException;
	
	

}
