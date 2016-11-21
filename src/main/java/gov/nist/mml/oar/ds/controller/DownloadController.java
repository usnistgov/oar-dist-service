package gov.nist.mml.oar.ds.controller;

import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.mml.oar.ds.s3.S3Wrapper;
import gov.nist.mml.oar.ds.service.DownloadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/od/ds")
public class DownloadController {
 
	Logger logger = LoggerFactory.getLogger(DownloadController.class);

	
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
	 * @param dsId
	 * @param distId
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/{dsId}/dist/{distId}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> download(@PathVariable("dsId") String dsId,@PathVariable("distId") String distId) throws IOException {
	    logger.info("Downloading distribution file with distId=" + distId + " dsId=" + dsId);
		return downloadService.downloadDistFile(dsId, distId);
	} 

	
	/**
	 * Return the list of bags of a data sets
	 * @param dsId
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/{dsId}/bags", method = RequestMethod.GET)
	public ResponseEntity<List<String>> listDataSetBags(@PathVariable("dsId") String dsId) throws IOException {
		return downloadService.findDataSetBags(dsId);
	} 
	
	/**
	 * Cache a data set file located in the preservation package  
	 * @param dsId: the id of the data set
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/{dsId}/cache", method = RequestMethod.POST)
	public ResponseEntity<byte[]> cacheDataSet(@PathVariable("dsId") String dsId) throws IOException {
		return null;
	} 
	
	

//	@RequestMapping(value = "/cacheFiles", method = RequestMethod.POST)
//	public List<PutObjectResult> uploadToCache(@RequestParam("file") MultipartFile[] multipartFiles) {
//		return downloadService.uploadToCache(multipartFiles);
//	}

 
 
	
	

}
 