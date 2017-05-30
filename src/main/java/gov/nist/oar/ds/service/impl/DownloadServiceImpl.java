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


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
  
  @Value("${rmmapi}")
  private String rmmApi;
  

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

  public ResponseEntity<byte[]>  downloadZipFile(String id) throws Exception {
    
    try {    
      HttpServletRequest request = null;
      
      CloseableHttpClient httpClient = HttpClientBuilder
          .create()
          .build();
      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
      factory.setBufferRequestBody(false);
      RestTemplate restTemplate = new RestTemplate(factory);
      ResponseEntity<JSONObject> response = restTemplate.getForEntity(
              rmmApi + "records?@id="+ id + "&include=components",
              JSONObject.class);
      
      String dzId = id;
      logger.info(rmmApi + "records?@id="+ id + "&include=components");
      String fileName = dzId.split("/")[2];
      JSONObject jsonRecord = response.getBody();
      String jsonResultData = new Gson().toJson(jsonRecord.get("ResultData"));
      JSONParser parser = new JSONParser(); 
      
      JSONArray jsonArrayResultData = (JSONArray) parser.parse(jsonResultData);

      JSONObject object = (JSONObject) jsonArrayResultData.get(0);

      String jsonComponents= new Gson().toJson(object.get("components"));

      JSONArray jsonArrayComponents = (JSONArray) parser.parse(jsonComponents);
      
      byte[] myBytes = null;
      myBytes = getCompressed(jsonArrayComponents,fileName);
           
      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      httpHeaders.setContentLength(myBytes.length);
      httpHeaders.setContentDispositionFormData("attachment", fileName + ".zip");
      return new ResponseEntity<>(myBytes, httpHeaders, HttpStatus.OK);
      
  } catch (Exception e) {
      e.printStackTrace();
  }
    return null;
  }
  
  public byte[] getCompressed( JSONArray json, String fileName)
      throws IOException
  {
    
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(bos);
    InputStream in = null;
    BufferedInputStream entryStream = null;

    CloseableHttpClient httpClient = HttpClientBuilder
        .create()
        .build();
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
    factory.setBufferRequestBody(false);
    RestTemplate restTemplate = new RestTemplate(factory);
    restTemplate.getMessageConverters().add(
            new ByteArrayHttpMessageConverter());

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    HttpEntity<String> entity = new HttpEntity<String>(headers);
    for (int i = 0; i < json.size(); i++) {
      JSONObject jsonObjComp = (JSONObject) json.get(i);
      if (jsonObjComp.containsKey("@type"))
        if(jsonObjComp.get("@type").toString().contains("nrdp:DataFile"))
          {
            if (jsonObjComp.containsKey("downloadURL"))
            {
              logger.info("Title -" + jsonObjComp.get("title").toString());
              logger.info("DownloadURL - " + jsonObjComp.get("downloadURL").toString());
              ResponseEntity<byte[]> response = restTemplate.exchange(
                  jsonObjComp.get("downloadURL").toString(),
                      HttpMethod.GET, entity, byte[].class, "1");
              
              ZipEntry entry = new ZipEntry(fileName + "/" + jsonObjComp.get("filepath").toString());
              if (response.getStatusCode() == HttpStatus.OK) 
                {
                  zos.putNextEntry( entry );
                  zos.write(response.getBody());
                  zos.closeEntry();
                }
            }
          }
      }
      zos.close();
      
      return bos.toByteArray();
  }
  
}
