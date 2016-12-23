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
 * @author:Harold Affo (Prometheus Computing, LLC)
 */
package gov.nist.oar.ds.service.impl;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.ds.s3.S3Wrapper;
import gov.nist.oar.ds.service.DownloadService;

/**
 * This is the default implementation of the download service class responsible of handling download
 * requests
 * 
 */
@Service
public class DownloadServiceImpl implements DownloadService {

  Logger logger = LoggerFactory.getLogger(DownloadServiceImpl.class);

  @Autowired
  private S3Wrapper s3Wrapper;

  @Value("${cloud.aws.preservation.s3.bucket}")
  private String preservationBucket;

  @Value("${cloud.aws.cache.s3.bucket}")
  private String cacheBucket;

  private static final String MAPPING_FILE_PREFIX = "ore.json";


  @Override
  public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles) {
    return s3Wrapper.upload(cacheBucket, multipartFiles);
  }


  @Override
  public ResponseEntity<byte[]> downloadDistributionFile(String dsId, String distId)
      throws IOException {
    logger.info("Downloading dsId=" + dsId + ",distId=" + distId + " from " + cacheBucket);
    String fileKey = getDistributionFileKey(dsId, distId);
    if (fileKey != null) {
      return s3Wrapper.download(cacheBucket, fileKey);
    }
    return null;
  }

  /**
   * 
   * @param dsId
   * @param distId
   * @return
   * @throws IOException
   */
  private String getDistributionFileKey(String dsId, String distId) throws IOException {
    String prefix = dsId + "-" + distId;
    List<S3ObjectSummary> files = s3Wrapper.list(cacheBucket, prefix);
    if (files != null && !files.isEmpty()) {
      return files.get(0).getKey();
    }

    return null;
  }

  /**
   * 
   * @param dsId
   * @return
   */
  private List<String> findBagsById(String dsId) {
    List<S3ObjectSummary> bagSummaries = s3Wrapper.list(cacheBucket, dsId + ".bag.");
    Collections.sort(bagSummaries, (bag1, bag2) -> bag2.getKey().compareTo(bag1.getKey()));
    List<String> results = new ArrayList<>();
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
    if (results != null && !results.isEmpty()) {
      return new ResponseEntity<>(results.get(0), HttpStatus.OK);
    }
    return new ResponseEntity<>(null, HttpStatus.OK);
  }

  /**
   * 
   * @param dsId
   * @return
   * @throws IOException
   */
  @SuppressWarnings("unused")
  private String getMappingFile(String dsId) throws IOException {
    ResponseEntity<byte[]> mappingFile =
        s3Wrapper.download(cacheBucket, dsId + "-" + MAPPING_FILE_PREFIX);
    byte[] result = mappingFile.getBody();
    return IOUtils.toString(result, "UTF-8");
  }



}
