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
 * @author:Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.ds.controller;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import gov.nist.oar.ds.exception.DistributionException;
import gov.nist.oar.ds.service.BagUtils;
import gov.nist.oar.ds.service.DownloadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import springfox.documentation.annotations.ApiIgnore;


/**
 * This is the download controller class handles downloading files and packages. 
 *
 */
@RestController
@Api(value = "Api endpoints to access/download data", tags = "Data Download API")
@RequestMapping(value = "/download")
public class DownloadController {

  Logger logger = LoggerFactory.getLogger(DownloadController.class);

  @Autowired
  private DownloadService downloadService;

  /**
   * Download zip file
   * @param Id Record identifier
   * @return compressed file of the record data (most recent bag)
   * @throws Exception 
   */
  @RequestMapping(value = "/zip", method = RequestMethod.GET)
  @ApiOperation(value = "package and download files for given id.",nickname = "Download zip",notes = "FileDownload Controller.")
  public ResponseEntity<byte[]> downloadZipFile(String id) throws DistributionException {
    logger.info("Handling zip download for dsid=" + id);
    return downloadService.downloadZipFile(id);
       
  }
  

  @RequestMapping(value = "/{dsId}/**", method = RequestMethod.GET)
  @ApiOperation(value = "Download file from given distribution id package.",nickname = "Get Single file.",notes = "FileDownload Controller.")
  /**
   * Download Single File
   * @param dsid Distribution id/record identifier
   * @param request filename including subpath
   * @return Single file
   * @throws IOException
   */
  public ResponseEntity<byte[]> downloadData(@PathVariable("dsId") String dsid, @ApiIgnore HttpServletRequest request) throws IOException {
    logger.debug("Handling file request from dataset with id=" + dsid);
    
        String restOfTheUrl = (String) request.getAttribute(
              HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
       
        restOfTheUrl = restOfTheUrl.replace("/"+dsid+"/", "");
              logger.info("Handling request for data file: id="+dsid+" path="+restOfTheUrl);
        
            return downloadService.downloadData(dsid, BagUtils.urlDecode(restOfTheUrl));
       
  }


//@RequestMapping(value = "/{dsId}/{filepath:.+}", method = RequestMethod.GET)
//
//public ResponseEntity<byte[]> downloadAlldata(@PathVariable("dsId") String dsid, @PathVariable("filepath") String filepath) throws Exception {
//  logger.info("Loading zip page" + dsid);
//  return downloadService.downloadData(dsid,filepath);
//     
//}
}
