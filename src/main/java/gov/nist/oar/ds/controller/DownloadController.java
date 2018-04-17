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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.http.HttpStatus;

import gov.nist.oar.ds.exception.DistributionException;
import gov.nist.oar.ds.service.DownloadService;
import gov.nist.oar.ds.service.BagUtils;
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
  

//  /**
//   * 
//   * @return
// * @throws Exception 
//   */
//  @ApiOperation(value = "Returns distriubution rest api info.",nickname = "get content",
//  notes = "Index Controller.")
//  @RequestMapping(value = "/{dsId}/**", method = RequestMethod.GET)
//  public ResponseEntity<byte[]> index(@PathVariable("dsId") String dsId, HttpServletRequest request)  {
//   logger.info("Loading test page");
////	  String path = (String) request.getAttribute(
////    HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
////String bestMatchPattern = (String ) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
////
////AntPathMatcher apm = new AntPathMatcher();
////String finalPath = apm.extractPathWithinPattern(bestMatchPattern, path);
//
//    return downloadService.downloadData("test","test2.text");
//  }
  
  
//  
//
//  /**
//   * Download a distribution file by its id
//   * 
//   * @param dsId
//   * @param distId
//   * @return
//   * @throws IOException
//   */
//  @RequestMapping(value = "/{dsId}/{distId}", method = RequestMethod.GET)
//  @ApiOperation(value = "Get data for given distribution with distribution id.",nickname = "distById",
//  notes = "distID is data collection id and distId is actual data id.")
//
//  public ResponseEntity<byte[]> download(@PathVariable("dsId") String dsId,
//      @PathVariable("distId") String distId) throws IOException {
//    logger.info("Downloading distribution file with distId=" + distId + " dsId=" + dsId);
//    return downloadService.downloadDistributionFile(dsId, distId);
//  }
//
//
//  /**
//   * Return the list of bags of a data set
//   * 
//   * @param dsId
//   * @return
//   * @throws IOException
//   */
//  @RequestMapping(value = "/{dsId}/bags", method = RequestMethod.GET)
//  @ApiOperation(value = "Get bags for given distribution",nickname = "get bags",
//  notes = "Find Data Set bags for given disId")
//  public ResponseEntity<List<String>> listDataSetBags(@PathVariable("dsId") String dsId)
//      throws IOException {
//    return downloadService.findDataSetBags(dsId);
//  }
//
//  /**
//   * Return the head bag key of a data set
//   * 
//   * @param dsId: id of the data set
//   * @return the head bag key of a data set
//   * @throws IOException
//   */
//  @RequestMapping(value = "/{dsId}/headBag", method = RequestMethod.GET)
//  @ApiOperation(value = "Get HeadBag for given distribution Id.",nickname = "get headbag",
//  notes = "Get Headbag for given distribution with distID")
//
//  public ResponseEntity<String> headBag(@PathVariable("dsId") String dsId) throws IOException {
//    return downloadService.findDataSetHeadBag(dsId);
//  }
//
//
//  /**
//   * Cache a data set
//   * 
//   * @param dsId
//   * @return
//   * @throws IOException
//   */
//  @RequestMapping(value = "/{dsId}/cache", method = RequestMethod.POST)
//  @ApiOperation(value = "Get Cache for given distribution",nickname = "get cache",
//  notes = "Get data cache for given distribution by distribution ID.")
//
//  public ResponseEntity<byte[]> cacheDataSet(@PathVariable("dsId") String dsId) throws IOException {
//    return null;
//  }
//
  /**
   * Download zip file
   * 
   * @param Id
   * @return 
   * @return
   * @throws Exception 
   */
  @RequestMapping(value = "/zip", method = RequestMethod.GET)

  public ResponseEntity<byte[]> downloadZipFile(String id) throws DistributionException {
    logger.info("Handling zip download for dsid=" + id);
    return downloadService.downloadZipFile(id);
       
  }
  
//@RequestMapping(value = "/{dsId}", method = RequestMethod.GET)
//
//public ResponseEntity<byte[]> downloadAlldata(@PathVariable("dsId") String dsid) throws Exception {
//  logger.info("Loading zip page" + dsid);
//  return downloadService.downloadAllData(dsid);
//     
//}

//@RequestMapping(value = "/{dsId}/{filepath:.+}", method = RequestMethod.GET)
//
//public ResponseEntity<byte[]> downloadAlldata(@PathVariable("dsId") String dsid, @PathVariable("filepath") String filepath) throws Exception {
//  logger.info("Loading zip page" + dsid);
//  return downloadService.downloadData(dsid,filepath);
//     
//}

@RequestMapping(value = "/{dsId}/**", method = RequestMethod.GET)
public ResponseEntity<byte[]> downloadData(@PathVariable("dsId") String dsid, HttpServletRequest request) throws IOException {
  logger.debug("Handling file request from dataset with id=" + dsid);
  
	  String restOfTheUrl = (String) request.getAttribute(
	        HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
	 
	  restOfTheUrl = restOfTheUrl.replace("/"+dsid+"/", "");
            logger.info("Handling request for data file: id="+dsid+" path="+restOfTheUrl);
      
          return downloadService.downloadData(dsid, BagUtils.urlDecode(restOfTheUrl));
     
}

//@RequestMapping(value="/{id}/**", method = RequestMethod.GET)
//public void foo(@PathVariable("id") int id, HttpServletRequest request) {
//    String restOfTheUrl = (String) request.getAttribute(
//        HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
//    logger.info(restOfTheUrl);
//   
//}


}
