package gov.nist.mml.oar.distservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {

	  @RequestMapping("/")
	public String index() {
		return "OAR Distribution Service API";
	}
}
 