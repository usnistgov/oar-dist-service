package gov.nist.mml.oar.ds.config;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@RefreshScope
@ComponentScan(basePackages = {"gov.nist.mml.oar.ds"} )
@EnableEurekaClient
public class Application {

	private static Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String... args) {
    	log.info("########## Starting oar distribution service ########");
		SpringApplication.run(Application.class, args);
	}
}
  