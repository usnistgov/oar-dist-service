package gov.nist.mml.oar.ds.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PreservationS3Config {

	@Value("${cloud.aws.preservation.credentials.accessKey}")
	private String accessKey;

	@Value("${cloud.aws.preservation.credentials.secretKey}")
	private String secretKey;

	@Value("${cloud.aws.preservation.region}")
	private String region; 

	@Bean(name="preservationAWSCredentials")
	public BasicAWSCredentials basicAWSCredentials() {
		return new BasicAWSCredentials(accessKey, secretKey);
	}

	@Bean
	public AmazonS3Client amazonS3Client(@Qualifier("preservationAWSCredentials") AWSCredentials awsCredentials) {
		AmazonS3Client amazonS3Client = new AmazonS3Client(awsCredentials);
		amazonS3Client.setRegion(Region.getRegion(Regions.fromName(region)));
		return amazonS3Client;
	}
}
