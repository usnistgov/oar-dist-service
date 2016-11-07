package gov.nist.mml.oar.distservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
public class IndexController {

	Logger logger = LoggerFactory.getLogger(IndexController.class);

	@RequestMapping("/")
	public String index() {
	    logger.info("Loading index page");
		return "OAR Distribution Service API";
	}
}
