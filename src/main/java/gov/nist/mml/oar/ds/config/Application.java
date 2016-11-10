package gov.nist.mml.oar.ds.config;
 
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@RefreshScope
@ComponentScan(basePackages = {"gov.nist.mml.oar.ds"} )
public class Application {

	public static void main(String... args) {
		SpringApplication.run(Application.class, args);
	}
}
  