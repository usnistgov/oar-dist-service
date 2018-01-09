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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
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
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.Gson;

import gov.nist.oar.ds.s3.S3Wrapper;
import gov.nist.oar.ds.service.DownloadService;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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


//  @Override
//  public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles) {
//    return s3Wrapper.upload(cacheBucket, multipartFiles);
//  }
//
//
//  @Override
//  public ResponseEntity<byte[]> downloadDistributionFile(String dsId, String distId)
//      throws IOException {
//    logger.info("Downloading dsId=" + dsId + ",distId=" + distId + " from " + cacheBucket);
//    String fileKey = getDistributionFileKey(dsId, distId);
//    if (fileKey != null) {
//      return s3Wrapper.download(cacheBucket, fileKey);
//    }
//    return null;
//  }

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

//
//  @Override
//  public ResponseEntity<List<String>> findDataSetBags(String dsId) throws IOException {
//    return new ResponseEntity<>(findBagsById(dsId), HttpStatus.OK);
//  }

//
//  @Override
//  public ResponseEntity<String> findDataSetHeadBag(String dsId) throws IOException {
//    List<String> results = findBagsById(dsId);
//    if (results != null && !results.isEmpty()) {
//      return new ResponseEntity<>(results.get(0), HttpStatus.OK);
//    }
//    return new ResponseEntity<>(null, HttpStatus.OK);
//  }

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
  
  /**
   * 
   * @param Id
   * @return
   * @throws Exception
   */
  @Override
  public ResponseEntity<byte[]>  downloadZipFile(String id) throws Exception {
    
    try {    

      CloseableHttpClient httpClient = createAcceptSelfSignedCertificateClient();
      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
      factory.setBufferRequestBody(false);
      RestTemplate restTemplate = new RestTemplate(factory);
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
      HttpEntity<String> entity = new HttpEntity<String>(headers);
      ResponseEntity<JSONObject> response = restTemplate.exchange(
          rmmApi + "records?@id="+ id + "&include=components",
              HttpMethod.GET, entity, JSONObject.class, "1");
      logger.info("response" + response);
      JSONObject jsonRecord = response.getBody();
      int recordCount = (int) jsonRecord.get("ResultCount");
      if (recordCount == 0) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      } else {
        String dzId = id;
        logger.info(rmmApi + "records?@id="+ id + "&include=components");
        String fileName = dzId.split("/")[2];
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
      }
      
  } catch (Exception e) {
    logger.error("Error occured while downloading zip file" + e.toString());
    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
  
  /**
   * 
   * @param json array, file name
   * @return
   * @throws IOException, URISyntaxException
   * @throws KeyStoreException 
   * @throws NoSuchAlgorithmException 
   * @throws KeyManagementException 
   */
  public byte[] getCompressed( JSONArray json, String fileName)
      throws IOException, URISyntaxException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException
  {
    
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(bos);

    CloseableHttpClient httpClient = createAcceptSelfSignedCertificateClient();

    try{
      for (int i = 0; i < json.size(); i++) {
        JSONObject jsonObjComp = (JSONObject) json.get(i);
        if (jsonObjComp.containsKey("@type"))
          if(jsonObjComp.get("@type").toString().contains("nrdp:DataFile"))
            {
              if (jsonObjComp.containsKey("downloadURL"))
              {
                HttpGet request = new HttpGet(jsonObjComp.get("downloadURL").toString());
                CloseableHttpResponse response = httpClient.execute(request);
                logger.info("return code" + response.getStatusLine().getStatusCode());
                  try 
                    {
                      BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent());
                      ZipEntry entry = new ZipEntry(fileName + "/" + jsonObjComp.get("filepath").toString());
                      zos.putNextEntry( entry );
                      int inByte;
                      while((inByte = bis.read()) != -1) 
                      {
                        zos.write(inByte);
                      }
                      bis.close();
                    } finally {
                      response.close();
                      zos.closeEntry();
                    }
                }
            }
        }
    } finally {
      zos.close();
      httpClient.close();
    }
    return bos.toByteArray() ;
  }
  /**
   * 
   * @return httpclient
   * @throws IOException, URISyntaxException
   * @throws KeyStoreException 
   * @throws NoSuchAlgorithmException 
   * @throws KeyManagementException 
   */
  private static CloseableHttpClient createAcceptSelfSignedCertificateClient()
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

  // use the TrustSelfSignedStrategy to allow Self Signed Certificates
  SSLContext sslContext = SSLContextBuilder
          .create()
          .loadTrustMaterial(new TrustSelfSignedStrategy())
          .build();

  // we can optionally disable hostname verification. 
  // if you don't want to further weaken the security, you don't have to include this.
  HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
  
  // create an SSL Socket Factory to use the SSLContext with the trust self signed certificate strategy
  // and allow all hosts verifier.
  SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, allowAllHosts);
  
  // finally create the HttpClient using HttpClient factory methods and assign the ssl socket factory
  return HttpClients
          .custom()
          .setSSLSocketFactory(connectionFactory)
          .build();
  }
  	
  
  
  public ResponseEntity<byte[]> getResponsEntity(String filename, byte[] outdata){
		
	  HttpHeaders httpHeaders = new HttpHeaders();
	    httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
	    httpHeaders.setContentLength(outdata.length); 
	    httpHeaders.setContentDispositionFormData("attachment", filename);
	    return new ResponseEntity<>(outdata, httpHeaders, HttpStatus.OK);
  }

  public ResponseEntity<byte[]> downloadAllZiponlyData(String bucket, String key) throws Exception{
	  
	  try{
		  byte[] outdata = new byte[ 9000];
		  ByteArrayOutputStream bos = new ByteArrayOutputStream();
		  ZipOutputStream zos = new ZipOutputStream(bos);
		  ResponseEntity<byte[]> zipdata = s3Wrapper.download(bucket,key);
		  ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipdata.getBody()));
		  ZipEntry entry; 
		  while((entry = zis.getNextEntry()) != null)  {
			  if (entry.getName().contains("/data/")) {
				  zos.putNextEntry(entry);
				  int len;
				  while ((len = zis.read(outdata)) != -1) {
					  zos.write(outdata, 0, len);
				  }
				  zos.closeEntry();
			  }
			    
			}
		  zis.close();
		  zos.close();
		  return getResponsEntity(key.substring(0, key.length()-14)+".zip", bos.toByteArray());
		  
		 
	  }
	  catch(Exception e){
		  throw e;
	  }
  }
  
  ////Deoyani Adding new methods:
  public ResponseEntity<byte[]> downloadAllData(String recordid) throws Exception{
	  try{
	   
		  
	   List<S3ObjectSummary> files = s3Wrapper.list(preservationBucket, recordid);
	   
	   
	   if (files != null && !files.isEmpty()) {
		  
		  if(!s3Wrapper.doesObjectExistInCache(cacheBucket, files.get(files.size()-1).getKey()))
			  s3Wrapper.copytocache(preservationBucket, files.get(files.size()-1).getKey(), cacheBucket,files.get(files.size()-1).getKey());
		  //return s3Wrapper.download(cacheBucket, files.get(files.size()-1).getKey());
		  return downloadAllZiponlyData(cacheBucket,files.get(files.size()-1).getKey());
	   }
	   else return null;
	  

	  }catch(Exception e){
	    throw e;
	  }
	 
  }
  

  
  
  @Override
  public ResponseEntity<byte[]> downloadData(String recordid, String filepath) throws Exception{
	  try{
	   
	  
	   List<S3ObjectSummary> files = s3Wrapper.list(preservationBucket, recordid);
	   
//	    
//	  if (files != null && !files.isEmpty())
//		  return null ;
	  
	  
	     String recordBagKey = files.get(files.size()-1).getKey();
		  
	     byte[] outdata = new byte[ 9000];
		  
		  String filename = recordBagKey.substring(0, recordBagKey.length()-4)+"/data/"+filepath;
		  ByteArrayOutputStream out = new ByteArrayOutputStream();
//		  if(!s3Wrapper.doesObjectExistInCache(cacheBucket, recordBagKey))
//			  s3Wrapper.copytocache(preservationBucket, recordBagKey, cacheBucket,recordBagKey);

                  logger.debug("Pulling data from bucket="+preservationBucket);
		  ResponseEntity<byte[]> zipdata = s3Wrapper.download(preservationBucket,recordBagKey);
		  ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipdata.getBody()));
		  ZipEntry entry; 
		  while((entry = zis.getNextEntry()) != null)  {
			  if (entry.getName().equals(filename)) {
				  int len;
				  while ((len = zis.read(outdata)) != -1) {
					  out.write(outdata, 0, len);
				  }
				  out.close();
			  }
			    
			}
		  zis.close();
		  HttpHeaders httpHeaders = new HttpHeaders();
		    httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		    httpHeaders.setContentLength(out.toByteArray().length);
		    httpHeaders.setContentDispositionFormData("attachment", filepath);
		    return new ResponseEntity<>(out.toByteArray(), httpHeaders, HttpStatus.OK);
	
	  

	  }catch(NullPointerException ne){
		  throw ne;
	  }
	  catch(Exception e){
	    throw e;
	  }
	 
  }
  
  
}
