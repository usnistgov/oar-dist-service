package gov.nist.mml.oar.distservice.service.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.mml.oar.distservice.s3.S3Wrapper;
import gov.nist.mml.oar.distservice.service.CacheManager;
import gov.nist.mml.oar.distservice.service.DownloadService;

@Service
public class DownloadServiceImpl implements DownloadService{
	

	@Autowired
	private S3Wrapper s3Wrapper;
	
	@Value("${cloud.aws.preservation.s3.bucket}")
	private String preservationBucket; 
	
	@Value("${cloud.aws.cache.s3.bucket}")
	private String cacheBucket; 
	
	@Autowired
	private CacheManager cacheManager;
	
 
	public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles) {
		return s3Wrapper.upload(cacheBucket, multipartFiles);
	}

	/**
	 * TODO: Check if file exists in the cache before going to the preservation package.
	 */
	@Override
	public ResponseEntity<byte[]> downloadFile(String key) throws IOException {
		return s3Wrapper.download(cacheBucket, key);
	}

	@Override
	public List<S3ObjectSummary> listCached() {
		// TODO Auto-generated method stub
		return s3Wrapper.list(cacheBucket);
	}

	@Override
	public ResponseEntity<byte[]> downloadDistFile(String dsId, String distId) throws IOException {
		if(!cacheManager.isCached(dsId, distId)){
			List<S3ObjectSummary> bagSummaries = s3Wrapper.list(preservationBucket, dsId+"_");
			if(bagSummaries !=null && !bagSummaries.isEmpty()) {
				Collections.sort(bagSummaries, (bag1, bag2) -> bag1.getKey().compareTo(bag2.getKey()));
				S3ObjectSummary headBag = bagSummaries.get(0);
				ResponseEntity<byte[]> headBagContent = downloadFile(headBag.getKey());
				return  headBagContent;		
			}
 		}
		return null;
	}

	@Override
	public List<S3ObjectSummary> listPreserved() {
		// TODO Auto-generated method stub
		return s3Wrapper.list(preservationBucket);
	}
	
	
	
}
