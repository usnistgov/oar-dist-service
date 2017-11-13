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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
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
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.Gson;

import gov.nist.oar.ds.s3.S3Wrapper;
import gov.nist.oar.ds.service.DownloadService;
import gov.nist.oar.ds.util.Helpers;

import java.util.stream.Stream;
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
  
  @Value("${distservice.ec2storage}")
  private String ec2TempStorage;
  
  String contentDispositionFormat = "attachment";
  Helpers help;
  public DownloadServiceImpl(){
	  help = new Helpers();
  }

  /**
   * 
   * @param dsId
   * @return
   */
  private List<String> findBagsById(String bucket, String dsId) {
    List<S3ObjectSummary> bagsList = s3Wrapper.list(bucket, dsId);
    List<String> bagnames =  new ArrayList<>();
    for(int i=0; i<bagsList.size(); i++ ){
    	  if(bucket.equals(cacheBucket) || (bucket.equals(preservationBucket) && bagsList.get(i).getKey().endsWith(".zip")))
    		  bagnames.add(bagsList.get(i).getKey());
    	  
	  }
    return bagnames;
  } 


  @Override
  public ResponseEntity<List<String>> findDataSetBags(String bucket, String dsId) throws IOException {
	 
    return new ResponseEntity<>(findBagsById(bucket,dsId), HttpStatus.OK);
  }


  @Override
  public ResponseEntity<String> findDataSetHeadBag(String bucket, String dsId) throws IOException {
    List<String> results = findBagsById(bucket,dsId);
    if (results != null && !results.isEmpty()) {
      return new ResponseEntity<>(results.get(0), HttpStatus.OK);
    }
    return new ResponseEntity<>(null, HttpStatus.OK);
  }


    
  /**
   * 
   * @param Id
   * @return
   * @throws Exception
   */
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
      httpHeaders.setContentDispositionFormData(contentDispositionFormat, fileName + ".zip");
      return new ResponseEntity<>(myBytes, httpHeaders, HttpStatus.OK);
      
  } catch (Exception e) {
     logger.info(e.getMessage());
  }
    return null;
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
                    } 
                     catch(Exception e){
                    	 throw e;
                     }
                      finally {
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
  	
 
  
  
 //@Deoyani updated code
  
  
 /**
  * extract in the cache 
  */
@Override
public void extractAlltoCache(String recordid) throws IOException{

//// If code works no need to copy file locally	
//	  List<S3ObjectSummary> files = s3Wrapper.list(preservationBucket, recordid);
//	  String recordBagKey = files.get(files.size()-1).getKey();
//		 ResponseEntity<byte[]> zipdata = s3Wrapper.download(preservationBucket,recordBagKey);
//		 ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipdata.getBody()));
//		 ZipEntry entry; 
//		  while((entry = zis.getNextEntry()) != null)  {
//			 
//			  ByteArrayOutputStream bout = new ByteArrayOutputStream();
//			  byte[] outdata = new byte[ 9000];
//			  	  int len;
//				  while ((len = zis.read(outdata)) != -1) {
//					  bout.write(outdata, 0, len);
//				  }
//		       int l = bout.toByteArray().length;
//			   s3Wrapper.putObject(cacheBucket, entry.getName(), new ByteArrayInputStream(bout.toByteArray()),l);
//			  
//		       bout.close();      
//		       zis.closeEntry();
//		  }
//		  
//		  zis.close();
	
	extractAlltoCacheLocal(recordid);
		  
}  


/**
 * Download single file with given path
 */

@Override
public ResponseEntity<byte[]> downloadData(String recordid, String filename) throws Exception {
	
	try{
		 if(!existHeadInCache(recordid)) {
			 this.extractAlltoCache(recordid);
		 }
		 
		 String groupDirlocation = "/multibag/group-directory.txt";
		 String filePath = help.getFilepath(filename,s3Wrapper.getS3Object(cacheBucket, help.getBagId()+groupDirlocation));
		 
		 ///Download object with given id and filepath
		 return s3Wrapper.download(cacheBucket, filePath);
		  
	}catch(Exception e){
			 logger.info(e.getMessage());
			  throw e;
	}
}

  

  

/* 
 * Test This.
 */
@Override
public ResponseEntity<byte[]> getdownloadtest() throws Exception {

	  try{
		  String testrecord = "5D7B2D35E24C3DD2E053B357068137DB1879";
		  
		  if(!existHeadInCache(testrecord)) {
			  this.extractAlltoCache(testrecord);
		  }
		  String objectPath = "";
		// assuming that you have an InputStream named inputStream
		  try (BufferedReader br = new BufferedReader(new InputStreamReader(s3Wrapper.getS3Object(cacheBucket, testrecord+".mbag0_2-0/multibag/group-directory.txt")))) {
		      String line = null;
		      while((line = br.readLine()) != null) {
		          if(line.contains("testdata/MIDAS Review Workflow.pdf")){
		        	  System.out.println(" TEST :: "+line);
		        	  String[] bucketPath = line.split(testrecord);
		              objectPath = testrecord + bucketPath[1]+"/" + bucketPath[0];
		              objectPath = objectPath.trim();
		          }
		      }
		  } catch (IOException e) {
		      e.printStackTrace();
		  }
		  
		  
		
//		  //try (Stream<String> stream = Files.lines(Paths.get(""))) {
//		  try (Stream<String> stream = (Stream<String>) s3Wrapper.getS3Object(cacheBucket, testrecord+".mbag0_2-0/multibag/group-directory.txt")) {		
//			  stream.filter(s->s.contains("testdata/MIDAS Review Workflow.pdf")).forEach(System.out::println);
//		  }
	  
		  ///Download object 
		  return s3Wrapper.download(cacheBucket, objectPath);
	  }
	  	catch (IOException e) {
	  			e.printStackTrace();
	  			throw new Exception(e.getMessage());
	  	}  
	  
	  catch(Exception e){
			  System.out.println(e.getMessage());
			  throw e;
		  }
}




//@Override
//public ResponseEntity<byte[]> downloadData(String recordid, String filepath) throws Exception{
//  try{
//   
//   List<S3ObjectSummary> files = s3Wrapper.list(preservationBucket, recordid);
//   
//   String recordBagKey = files.get(files.size()-1).getKey();  
//   byte[] outdata = new byte[ 9000];
//	  
//	  String filename = recordBagKey.substring(0, recordBagKey.length()-4)+"/data/"+filepath;
//	  ByteArrayOutputStream out = new ByteArrayOutputStream();
//	  if(!s3Wrapper.doesObjectExistInCache(cacheBucket, recordBagKey))
//		  s3Wrapper.copytocache(preservationBucket, recordBagKey, cacheBucket,recordBagKey);
//	  
//	  ResponseEntity<byte[]> zipdata = s3Wrapper.download(cacheBucket,recordBagKey);
//	  ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipdata.getBody()));
//	  ZipEntry entry; 
//	  while((entry = zis.getNextEntry()) != null)  {
//		  if (entry.getName().equals(filename)) {
//			  int len;
//			  while ((len = zis.read(outdata)) != -1) {
//				  out.write(outdata, 0, len);
//			  }
//			  out.close();
//		  }
//		}
//	  zis.close();
//	  
//	  HttpHeaders httpHeaders = new HttpHeaders();
//	    httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//	    httpHeaders.setContentLength(out.toByteArray().length);
//	    httpHeaders.setContentDispositionFormData(contentDispositionFormat, filepath);
//	    return new ResponseEntity<>(out.toByteArray(), httpHeaders, HttpStatus.OK);
//
//  }catch(NullPointerException ne){
//	  throw ne;
//  }
//  catch(Exception e){
//    throw e;
//  }
// 
//}


public void extractAlltoCacheLocal(String recordid) throws IOException{
  List<S3ObjectSummary> files = s3Wrapper.list(preservationBucket, recordid);
   
     String recordBagKey = files.get(files.size()-1).getKey();

	 ResponseEntity<byte[]> zipdata = s3Wrapper.download(preservationBucket,recordBagKey);
	 ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipdata.getBody()));
	 ZipEntry entry; 
	  while((entry = zis.getNextEntry()) != null)  {
		  
		  String fileName = entry.getName();
		   if(!fileName.contains(".DS_Store") && !fileName.contains("__MACOSX")) {

				String entryPath = ec2TempStorage + fileName;
			   if (!entry.isDirectory()) {
	                // if the entry is a file, extracts it
	                help.extractFile(zis, entryPath, fileName);
	                s3Wrapper.putObject(cacheBucket, fileName, new File(entryPath));
	            } else {
	                // if the entry is a directory, make the directory
	                File dir = new File(entryPath);
	                dir.mkdir();
	            }
           }
		  
		}
	  zis.close();	  
	  Helpers.deleteDirectory(new File(ec2TempStorage+help.getBagId()));
}

/***
 * Check the latest sequence number in cache. Return true if exists
 * @param dsId
 * @return
 */
public boolean existHeadInCache(String dsId){
	try{
	  	String preservationHead = help.findHeadBag(this.findBagsById(preservationBucket, dsId));
	  	//As preservation bucket has zipped files.
	  	help.setBagId(preservationHead.substring(0,preservationHead.length()-4));
	  	String cacheHead = help.findHeadBag(this.findBagsById(cacheBucket, help.getBagId()));
	  	return !(("").equals(cacheHead) ||  null == cacheHead ); 
	  	
	}catch(NullPointerException e){
		logger.info("Null pointer exception as there are bags with given recordid."+e.getMessage());
		return false;
	}catch(Exception e){
		logger.info("Exception as there are bags with given recordid."+e.getMessage());
		throw e;
	}
}

}


