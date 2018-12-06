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
 */
package gov.nist.oar.distrib.web;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.builders.ResponseMessageBuilder;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.VendorExtension;
import springfox.documentation.service.ResponseMessage;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
@ComponentScan({"gov.nist.oar.distrib.web"})
/**
 * Configuration for Swagger-generated API documentation.
 * <p>
 * This class enables Swagger to generate API documentation drawn from REST controller class 
 * markup.  An HTML rendering of that document is made available from {@code /swagger-ui.html}.
 * 
 * @author dsn1 Deoyani Nandrekar-Heinis
 */
public class DistribSwaggerConfig {
        
    private static List<ResponseMessage> responseMessageList = new ArrayList<>();

    static {
        responseMessageList.add(new ResponseMessageBuilder()
                                   .code(500)
                                   .message("500 - Internal Server Error")
                                   .responseModel(new ModelRef("Error")).build()
        );
        responseMessageList.add(new ResponseMessageBuilder()
                                   .code(403)
                                   .message("403 - Forbidden")
                                   .build()
        );
    }
    
    @Bean
    /**
     * Create the Swagger documentation builder object
     * @return Docket
     */
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                  .select()
                  .apis(RequestHandlerSelectors.basePackage("gov.nist.oar.distrib.web"))
                  .paths(PathSelectors.any())
                  .build()
                  .apiInfo(apiInfo());
    }

    /**
     * Return the basic 
     * @return return ApiInfo
     */
    private ApiInfo apiInfo() {

        ApiInfo apiInfo = 
            new ApiInfo("Data Distribution Service API (" + VersionController.NAME + ")", 
                        "An API for accessing data products from the NIST Public Data Repository " +
                           "(along with information about them)",
                        VersionController.VERSION,
                        "https://www.nist.gov/director/licensing", 
                        new Contact("OAR Data Support Team", "https://www.nist.gov/data",
                                    "datasupport@nist.gov"),
                        "NIST Public licence", 
                        "https://www.nist.gov/director/licensing",
                        new HashSet<VendorExtension>());
        return apiInfo;
    }
}
