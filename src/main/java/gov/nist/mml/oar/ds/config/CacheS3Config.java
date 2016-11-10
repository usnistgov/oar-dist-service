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
public class CacheS3Config {

	@Value("${cloud.aws.cache.credentials.accessKey}")
	private String accessKey;

	@Value("${cloud.aws.cache.credentials.secretKey}")
	private String secretKey;

	@Value("${cloud.aws.cache.region}")
	private String region; 

	@Bean(name="cacheAWSCredentials")
	public BasicAWSCredentials cacheAWSCredentials() {
		return new BasicAWSCredentials(accessKey, secretKey);
	}

	@Bean
	public AmazonS3Client cacheClient(@Qualifier("cacheAWSCredentials") AWSCredentials awsCredentials) {
		AmazonS3Client amazonS3Client = new AmazonS3Client(awsCredentials);
		amazonS3Client.setRegion(Region.getRegion(Regions.fromName(region)));
		return amazonS3Client;
	}
}
