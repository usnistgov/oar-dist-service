///**
// * This software was developed at the National Institute of Standards and Technology by employees of
// * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
// * of the United States Code this software is not subject to copyright protection and is in the
// * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
// * use by other parties, and makes no guarantees, expressed or implied, about its quality,
// * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
// * used. This software can be redistributed and/or modified freely provided that any derivative
// * works bear some notice that they are derived from it, and any modified versions bear some notice
// * that they have been modified.
// * 
// * @author:Harold Affo
// */
//package gov.nist.oar.ds.unit.controller;
//
//
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.context.embedded.LocalServerPort;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ContextConfiguration;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
//import org.springframework.test.context.web.WebAppConfiguration;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.MvcResult;
//import org.springframework.test.web.servlet.setup.MockMvcBuilders;
//
//import gov.nist.oar.ds.config.ApplicationConfig;
//import gov.nist.oar.ds.controller.DownloadController;
//import gov.nist.oar.ds.service.DownloadService;
//
//
//import java.nio.charset.Charset;
//import java.util.Arrays;
//
//import javax.inject.Inject;
//
//import static org.hamcrest.Matchers.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
// 
//@RunWith(SpringJUnit4ClassRunner.class)
////@ContextConfiguration(classes = {ApplicationConfig.class})
//@ContextConfiguration
//@SpringBootTest(classes = ApplicationConfig.class, webEnvironment  = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@TestPropertySource(properties = { "cloud.aws.preservation.s3.bucket: nist-oar-dev-pres", "cloud.aws.region: us-east-1",
//		"cloud.aws.cache.s3.bucket: nist-oar-dev-cache","rmmapi: https://oardev.nist.gov/rmm/ ",
//		"distservice.ec2storage: '/tmp/distdata/'",
//		"distservice.compressed.format: zip" })
////@WebAppConfiguration
//
//public class DownloadControllerTests {
// 
//    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(
//			MediaType.APPLICATION_JSON.getType(),
//			MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));
//	
//	private  MockMvc mockMvc;
//
////	@LocalServerPort
////    int port;
////	
//	@Autowired
//	private DownloadService download;
//
//	@Autowired
//	private DownloadController downloadController;
//    
//    @Inject
//    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;
//    
//	@Before
//	public void setup() {
//		
//		this.mockMvc = MockMvcBuilders.standaloneSetup(downloadController)
//				.setCustomArgumentResolvers(pageableArgumentResolver).build();
//	}
//    
////	@Test
////	public void shouldDowloadAll() throws Exception {
////		mockMvc.perform(get("/3A1EE2F169DD3B8CE0531A570681DB5D1491"))
////		.andExpect(status().isOk());
////	}
//	
//	@Test
//	public void loadtest() throws Exception {
//		MvcResult result = mockMvc.perform(get("/test")).andReturn();
//		//.andExpect(status().isNotFound());	
//		String content = result.getResponse().getContentAsString();
//		System.out.println("TEST ::"+content);
//	}
//	
//	@Test
//	public void shouldDowloadFile() throws Exception {
//		MvcResult result = mockMvc.perform(get("/1234/1234")).andReturn();
//		//.andExpect(status().isNotFound());	
//		String content = result.getResponse().getContentAsString();
//		System.out.println("TEST ::"+content);
//	}
//}