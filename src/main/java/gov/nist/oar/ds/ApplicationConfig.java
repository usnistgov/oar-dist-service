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

package gov.nist.oar.ds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;


import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * This is the configuration class responsible of starting the spring-boot application
 *
 */
@SpringBootApplication
@RefreshScope
@ComponentScan(basePackages = {"gov.nist.oar"})
public class ApplicationConfig {

  private static Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

  /**
   * Main runner of the spring-boot class
   * 
   * @param args
   */	
  @SuppressWarnings("squid:S2095")
  public static void main(String... args) {
    log.info("########## Starting oar distribution service ########");
    SpringApplication.run(ApplicationConfig.class, args);
  }
  
  /**
   *Add CORS 
   * @return
   */
  @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**");
            }
        };
    }
}
