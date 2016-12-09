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
 * 
 * @author:Harold Affo
 */
package gov.nist.mml.oar.ds.service.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.mml.oar.ds.s3.S3Wrapper;
import gov.nist.mml.oar.ds.service.CacheManager;
import gov.nist.mml.oar.ds.service.DownloadService;

@Service
public class DownloadServiceImpl implements DownloadService {


  @Autowired
  private S3Wrapper s3Wrapper;

  @Value("${cloud.aws.preservation.s3.bucket}")
  private String preservationBucket;

  @Value("${cloud.aws.cache.s3.bucket}")
  private String cacheBucket;

  @Autowired
  private CacheManager cacheManager;


  private static final String MAPPING_FILE_PREFIX = "ore.json";


  public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles) {
    return s3Wrapper.upload(cacheBucket, multipartFiles);
  }


  @Override
  public ResponseEntity<byte[]> downloadDistributionFile(String dsId, String distId)
      throws IOException {
    String fileKey = getDistributionFileKey(dsId, distId);
    if (fileKey != null) {
      return s3Wrapper.download(cacheBucket, fileKey);
    }
    return null;
  }

  private String getDistributionFileKey(String dsId, String distId) throws IOException {
    String mappingFile = getMappingFile(dsId);
    if (!StringUtils.isEmpty(mappingFile)) {
      return "Dist001-1.png";// TODO
    }
    return null;
  }

  private List<String> findBagsById(String dsId) {
    List<S3ObjectSummary> bagSummaries = s3Wrapper.list(cacheBucket, dsId + ".bag.");
    Collections.sort(bagSummaries, (bag1, bag2) -> bag2.getKey().compareTo(bag1.getKey()));
    List<String> results = new ArrayList<String>();
    for (S3ObjectSummary sum : bagSummaries) {
      results.add(sum.getKey());
    }

    return results;
  }


  @Override
  public ResponseEntity<List<String>> findDataSetBags(String dsId) throws IOException {
    return new ResponseEntity<>(findBagsById(dsId), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<String> findDataSetHeadBag(String dsId) throws IOException {
    List<String> results = findBagsById(dsId);
    String headBag = null;
    if (results != null && !results.isEmpty()) {
      new ResponseEntity<>(results.get(0), HttpStatus.OK);
    }
    return new ResponseEntity<>(null, HttpStatus.OK);
  }


  private String getMappingFile(String dsId) throws IOException {
    ResponseEntity<byte[]> mappingFile =
        s3Wrapper.download(cacheBucket, dsId + "-" + MAPPING_FILE_PREFIX);
    byte[] result = mappingFile.getBody();
    return IOUtils.toString(result, "UTF-8");
  }



}
