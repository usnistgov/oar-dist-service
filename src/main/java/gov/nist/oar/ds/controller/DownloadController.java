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
package gov.nist.oar.ds.controller;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.http.HttpStatus;
import gov.nist.oar.ds.service.DownloadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * This is the download controller class responsible of handling the download restful http requests
 *
 */

@RestController
@Api(value = "Api endpoints to access/download data", tags = "Data Distribution API")
public class DownloadController {

  Logger logger = LoggerFactory.getLogger(DownloadController.class);

  public static final String CONTENT = "Welcome to the OAR distribution service api";

  @Autowired
  private DownloadService downloadService;

  public DownloadService getDownloadService() {
    return downloadService;
  }

  public void setDownloadService(DownloadService downloadService) {
    this.downloadService = downloadService;
  }
  

  /**
   * 
   * @return
   */
  @ApiOperation(value = "Returns distriubution rest api info.",nickname = "get content",
  notes = "Index Controller.")
  public ResponseEntity<String> index() {
    logger.info("Loading index page");
    return new ResponseEntity<>(CONTENT, HttpStatus.OK);
  }


  /**
   * Return the list of bags of a data set
   * 
   * @param dsId
   * @return
   * @throws IOException
   */
  @RequestMapping(value = "/{bucket}/{dsId}/listbags", method = RequestMethod.GET)
  @ApiOperation(value = "Get bags for given distribution",nickname = "get bags",
  notes = "Find Data Set bags for given disId")
  public ResponseEntity<List<String>> listDataSetBags(@PathVariable("bucket") String bucket, @PathVariable("dsId") String dsId)
      throws IOException {
    return downloadService.findDataSetBags(bucket,dsId);
  }

  /**
   * Return the head bag key of a data set
   * 
   * @param dsId: id of the data set
   * @return the head bag key of a data set
   * @throws IOException
   */
  @RequestMapping(value = "/{bucket}/{dsId}/headBag", method = RequestMethod.GET)
  @ApiOperation(value = "Get HeadBag for given distribution Id.",nickname = "get headbag",
  notes = "Get Headbag for given distribution with distID")

  public ResponseEntity<String> headBag(@PathVariable("bucket") String bucket,@PathVariable("dsId") String dsId) throws IOException {
    return downloadService.findDataSetHeadBag(bucket,dsId);
  }
  
  
  /**
   * Download a Zip file.
   * @param dsid
   * @param format
   * @return
   * @throws Exception
   */
  @RequestMapping(value = "/{dsid}", method = RequestMethod.GET)
  public ResponseEntity<byte[]> downloadZipFile(@PathVariable("dsid") String dsid, @RequestParam(value = "format", required = true) String format) throws Exception {
    logger.info("Loading zip page" + dsid);
    return downloadService.downloadZipFile(dsid);    
  }
  
  /**
   * Download file.
   */
@RequestMapping(value = "/{dsId}/**", method = RequestMethod.GET)
public ResponseEntity<byte[]> downloadData(@PathVariable("dsId") String dsid, HttpServletRequest request) throws Exception {
  logger.info("Loading zip page" + dsid);
  try{
	    String restOfTheUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
	    restOfTheUrl = restOfTheUrl.replace("/"+dsid+"/", "");
	    logger.info(restOfTheUrl);
	  return downloadService.downloadData(dsid,restOfTheUrl);
	  
  }catch(Exception e){
	  throw e;
  }
	     
}
/**
 * LOad and extract data in the cache.
 * @param dsid
 * @throws Exception
 */
@RequestMapping(value="/loadcache/{dsid}", method = RequestMethod.GET)
public void loadcache (@PathVariable("dsid") String dsid) throws Exception {
	downloadService.extractAlltoCache(dsid); 
}

/**
 * Just for testing
 * @return
 * @throws Exception
 */
@RequestMapping(value="/testdownload", method = RequestMethod.GET)
public ResponseEntity<byte[]> testdownlaod () throws Exception {
	return downloadService.getdownloadtest(); 
}
//@RequestMapping(value = "/{dsId}", method = RequestMethod.GET)
//public ResponseEntity<byte[]> downloadAlldata(@PathVariable("dsId") String dsid) throws Exception {
//  logger.info("Loading zip page" + dsid);
//  return downloadService.downloadAllData(dsid);
//     
//}
//To let the user add filepath with . and extensions
//@RequestMapping(value = "/{dsId}/{filepath:.+}", method = RequestMethod.GET)


}
