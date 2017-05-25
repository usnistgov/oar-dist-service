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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
   * Download a distribution file by its id
   * 
   * @param dsId
   * @param distId
   * @return
   * @throws IOException
   */
  @RequestMapping(value = "/{dsId}/{distId}", method = RequestMethod.GET)
  @ApiOperation(value = "Get data for given distribution with distribution id.",nickname = "distById",
  notes = "distID is data collection id and distId is actual data id.")

  public ResponseEntity<byte[]> download(@PathVariable("dsId") String dsId,
      @PathVariable("distId") String distId) throws IOException {
    logger.info("Downloading distribution file with distId=" + distId + " dsId=" + dsId);
    return downloadService.downloadDistributionFile(dsId, distId);
  }


  /**
   * Return the list of bags of a data set
   * 
   * @param dsId
   * @return
   * @throws IOException
   */
  @RequestMapping(value = "/{dsId}/bags", method = RequestMethod.GET)
  @ApiOperation(value = "Get bags for given distribution",nickname = "get bags",
  notes = "Find Data Set bags for given disId")
  public ResponseEntity<List<String>> listDataSetBags(@PathVariable("dsId") String dsId)
      throws IOException {
    return downloadService.findDataSetBags(dsId);
  }

  /**
   * Return the head bag key of a data set
   * 
   * @param dsId: id of the data set
   * @return the head bag key of a data set
   * @throws IOException
   */
  @RequestMapping(value = "/{dsId}/headBag", method = RequestMethod.GET)
  @ApiOperation(value = "Get HeadBag for given distribution Id.",nickname = "get headbag",
  notes = "Get Headbag for given distribution with distID")

  public ResponseEntity<String> headBag(@PathVariable("dsId") String dsId) throws IOException {
    return downloadService.findDataSetHeadBag(dsId);
  }


  /**
   * Cache a data set
   * 
   * @param dsId
   * @return
   * @throws IOException
   */
  @RequestMapping(value = "/{dsId}/cache", method = RequestMethod.POST)
  @ApiOperation(value = "Get Cache for given distribution",nickname = "get cache",
  notes = "Get data cache for given distribution by distribution ID.")

  public ResponseEntity<byte[]> cacheDataSet(@PathVariable("dsId") String dsId) throws IOException {
    return null;
  }

  /**
   * Download zip file
   * 
   * @param Id
   * @return 
   * @return
   * @throws Exception 
   */
  @RequestMapping(value = "/zip", method = RequestMethod.GET)

  public ResponseEntity<byte[]> downloadZipFile(String id) throws Exception {
    logger.info("Loading zip page" + id);
    return downloadService.downloadZipFile(id);
       
  }


}
