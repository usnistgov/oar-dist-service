package gov.nist.mml.oar.distservice.controller;

import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.mml.oar.distservice.s3.S3Wrapper;
import gov.nist.mml.oar.distservice.service.DownloadService;

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
 
	
	@Autowired
	private DownloadService downloadService;
	
	public DownloadService getDownloadService() {
		return downloadService;
	}

	public void setDownloadService(DownloadService downloadService) {
		this.downloadService = downloadService;
	}
	
	
	@RequestMapping(value = "/{dsId}/{distId}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> download(@PathVariable("dsId") String dsId,@PathVariable("distId") String distId) throws IOException {
		return downloadService.downloadDistFile(dsId, distId);
	} 
	

	@RequestMapping(value = "/cacheFiles", method = RequestMethod.POST)
	public List<PutObjectResult> uploadToCache(@RequestParam("file") MultipartFile[] multipartFiles) {
		return downloadService.uploadToCache(multipartFiles);
	}

	@RequestMapping(value = "/downloadFiles", method = RequestMethod.GET)
	public ResponseEntity<byte[]> downloadFile(@RequestParam String key) throws IOException {
		return downloadService.downloadFile(key);
	} 
	
	/**
	 * TODO: remove.
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/listCached", method = RequestMethod.GET)
	public List<S3ObjectSummary> listCached() throws IOException {
		return downloadService.listCached();
	}
	 
	/**
	 * TODO: remove.
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/listPreserved", method = RequestMethod.GET)
	public List<S3ObjectSummary> listPreserved() throws IOException {
		return downloadService.listCached();
	}
	
	
	

}
 