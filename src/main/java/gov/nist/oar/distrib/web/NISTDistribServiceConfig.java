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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.DataPackagingService;
import gov.nist.oar.distrib.service.DefaultDataPackagingService;
import gov.nist.oar.distrib.service.DefaultPreservationBagService;
import gov.nist.oar.distrib.service.FileDownloadService;
import gov.nist.oar.distrib.service.NerdmDownloadService;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.RPARequestHandler;
import gov.nist.oar.distrib.storage.AWSS3LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The configuration for the Distribution Service used in the NIST Public Data Repository.
 * <p>
 * The class defines how the components are put together to form the distribution web service
 * using the Spring Boot framework.  The following configuration properties are supported for 
 * driving the configuration:
 * <dl>
 *   <dt> {@code distrib.bagstore.mode} </dt>
 *   <dd> a label that indicates how preservation bags are stored; the following values are 
 *        supported:
 *        <dl>
 *          <dd> <strong>local</strong> -- the bags are stored under a local directory (specified 
 *               by {@code distrib.bagstore.location}); the {@code FilesystemLongTermStorage} 
 *               class will be used to access the bags.  </dd>
 *          <dd> <strong>aws</strong> -- the bags are stored in an AWS S3 bucket (whose name
 *               is specified by {@code distrib.bagstore.location}), assuming the distribution
 *               service itself is running on an AWS EC2 machine.  The {@code AWSS3LongTermStorage} 
 *               class will be used to access the bags, and implicit credentials through the 
 *               EC2 machine will be used.  </dd>
 *          <dd> <strong>remote</strong> -- the bags are stored in an AWS S3 bucket (whose name
 *               is specified by {@code distrib.bagstore.location}), assuming the distribution
 *               service itself is running on an AWS EC2 machine.  The {@code AWSS3LongTermStorage} 
 *               class will be used to access the bags; however, credentials must be explicitly 
 *               provided. <em>Not yet implemented; will throw exception.</em> </dd>
 *        </dl> 
 *        All other values will throw an exception.  </dd>
 *   <dt> {@code distrib.bagstore.location} </dt>
 *   <dd> The location where the preservation bags are stored.  If {@code distrib.bagstore.mode}
 *        is set to {@code local}, this value is the path to a directory on the local filesystem.
 *        If {@code distrib.bagstore.mode} is set to {@code aws} or {@code remote}, this value is 
 *        the name of an S3 bucket. </dd>
 *   <dt> {@code distrib.baseurl} </dt>
 *   <dd> The base URL for the service (as seen by external, public users); this should include 
 *        everything up to but not including the "/ds" part of the path. </dd>
 *   <dt> {@code cloud.aws.region} </dt>
 *   <dd> The AWS service region (e.g. "us-east-1") to use.  This is ignored if 
 *        {@code distrib.bagstore.mode}=local. </dd>
 *   <dt> {@code distrib.packaging.maxfilecount} </dt>
 *   <dd> The maximum number of files that can be included within a single data package (see 
 *        {@link gov.nist.oar.distrib.service.DataPackagingService}) </dd>
 *   <dt> {@code distrib.packaging.maxpackagesize} </dt>
 *   <dd> The maximum file size for a single data package
 *        {@link gov.nist.oar.distrib.service.DataPackagingService}) </dd>
 *   <dt> {@code distrib.packaging.allowedurls} </dt>
 *   <dd> a pipe-delimited list of regular expressions for URLs that are allowed to appear in a data 
 *        packaging request (see
 *        {@link gov.nist.oar.distrib.service.DataPackagingService}) </dd>
 * </dl>
 * <p>
 * See also 
 * {@see gov.nist.oar.distrib.web.LocalstackDistribServiceConfig LocalstackDistribServiceConfig}
 */
@SpringBootApplication
@EnableAsync
public class NISTDistribServiceConfig {

    private static Logger logger = LoggerFactory.getLogger(NISTDistribServiceConfig.class);

    /**
     * the location where preservation bags can be found.  If <code>distrib.bagstore.mode</code> 
     * is "local",  this value is a directory path.  If the mode is "aws" or "remote", then this is
     * an S3 bucket name.  
     */
    @Value("${distrib.bagstore.location}")
    String bagstore;

    /**
     * the storage mode of which three are recognized:
     * <ul>
     *   <li> {@code aws} - Bags are stored in an S3 bucket and accessed from an EC2 machine 
     *                      (production mode); in this case, bagstore holds the S3 bucket name. </li>
     *   <li> {@code remote} - Bags are stored in an S3 bucket and accessed from a remote (non-EC2) 
     *                       machine (a development mode); in this case, bagstore holds the S3 bucket 
     *                       name. </li>
     *   <li> {@code local}  - Bags are stored under a directory on the local filesystem
     *                       (testing or development mode); in this case, bagstore identifies the 
     *                       bags' parent directory.  </li>
     */
    @Value("${distrib.bagstore.mode}")
    String mode;

    /**
     * the AWS region the service should operate in; this is ignored if mode=local.
     */
    @Value("${cloud.aws.region:us-east-1}")
    String region;

    /**
     * a local directory where the cache manager can store its databases and head bag cache
     */
    @Value("${distrib.cachemgr.admindir:@null}")
    String cacheadmin;

    /**
     * the maximum allowed size of a data package.  A package will only exceed this size if it contains
     * a single file (that would not fit otherwise).
     */
    @Value("${distrib.packaging.maxpackagesize:500000000}")
    long maxPkgSize;
       
    /**
     * the maximum number of files to allow in a single data package
     */
    @Value("${distrib.packaging.maxfilecount:200}")
    int maxFileCount;

    /**
     * a white list of allowed URL patterns from which to retrieve data to include in data packages.
     * This value is given as a regular expression Strings delimited by pipe (|) characters; each 
     * expression represents a pattern for a URL that is allowed to appear in a data packaging request.
     * A URL can match any of the patterns to be considered allowed.  A pattern is matched against a 
     * URL starting with the authority field (i.e. just the "://") and may include parts of the URL 
     * path.  
     */
    @Value("${distrib.packaging.allowedurls:}")
    String allowedUrls;

    @Value("${distrib.packaging.allowedRedirects:1}")
    int allowedRedirects;
    
    @Value("${distrib.baseurl}")
    String baseUrl;

    S3Client             s3client;    // set via getter below
    BagStorage                lts;    // set via getter below
    MimetypesFileTypeMap  mimemap;    // set via getter below
    CacheManagerProvider cmgrprov;    // set via getter below

    /**
     * the storage service to use to access the bags
     */
    @Bean
    public BagStorage getLongTermStorage(S3Client s3client) throws ConfigurationException {
        logger.info("Bagstore mode: " + mode);
        logger.info("Bagstore location: " + bagstore);
        try {
            if (mode.equals("aws") || mode.equals("remote")) {
                return new AWSS3LongTermStorage(bagstore, s3client);
            } else if (mode.equals("local")) {
                return new FilesystemLongTermStorage(bagstore);
            } else {
                throw new ConfigurationException("distrib.bagstore.mode",
                        "Unsupported storage mode: " + mode);
            }
        } catch (FileNotFoundException ex) {
            throw new ConfigurationException("distrib.bagstore.location",
                    "Storage Location not found: " + ex.getMessage(), ex);
        } catch (StorageVolumeException ex) {
            throw new ConfigurationException("distrib.bagstore.aws",
                    "Storage volume exception: " + ex.getMessage(), ex);
        }
    }


    /**
     * the client for access S3 storage
     */
    @Bean
    public S3Client getAmazonS3() throws ConfigurationException {
        logger.info("Creating S3 client");

        // Check if "remote" mode is supported
        if ("remote".equalsIgnoreCase(mode)) {
            throw new ConfigurationException("Remote credentials not supported yet");
        }

        try {
            // Use default credential provider chain (supports EC2 instance profiles)
            S3Client client = S3Client.builder()
                    .credentialsProvider(InstanceProfileCredentialsProvider.create())
                    .region(Region.of(region))
                    .build();

            logger.info("S3 client created successfully for region: {}", region);
            return client;
        } catch (Exception e) {
            logger.error("Failed to create S3 client: {}", e.getMessage(), e);
            throw new ConfigurationException("Error creating S3 client: " + e.getMessage(), e);
        }
    }

    /**
     * the MIME type assignments to use when setting content types
     */
    @Bean
    public MimetypesFileTypeMap getMimetypesFileTypeMap() {
        InputStream mis = getClass().getResourceAsStream("/mime.types");
        if (mis == null) {
            logger.warn("No mime.type resource found; content type support will be limited!");
            return new MimetypesFileTypeMap();
        }
        return new MimetypesFileTypeMap(mis);
    }

    /**
     * the service implementation to use to download data products.
     */
    @Bean
    public FileDownloadService getFileDownloadService(PreservationBagService bagsvc, MimetypesFileTypeMap mimemap,
                                                      CacheManagerProvider cmprovider)
        throws ConfigurationException
    {
        return cmprovider.getFileDownloadService(bagsvc, mimemap);
    }
        
    /**
     * the service implementation to use to download data products.
     */
    @Bean
    public PreservationBagService getPreservationBagService(BagStorage lts, MimetypesFileTypeMap mimemap) {
        return new DefaultPreservationBagService(lts, mimemap);
    }

    /**
     * the service implementation to use to package data into bundles
     */
    @Bean
    public DataPackagingService getDataPackagingService(){
        return new DefaultDataPackagingService(allowedUrls, maxPkgSize, maxFileCount, allowedRedirects);
    }

    @Bean
    public NerdmDownloadService getNerdmDownloadService() {
        return new NerdmDownloadService(baseUrl);
    }

    /**
     * create a configuration object for the cache manager
     */
    @Bean
    @ConfigurationProperties("distrib.cachemgr")
    public NISTCacheManagerConfig getCacheManagerConfig() throws ConfigurationException {
        // this will have config properties injected into it
        return new NISTCacheManagerConfig();
    }


    /**
     * the configured CacheManagerProvider to use
     */
    @Bean
    public CacheManagerProvider getCacheManagerProvider(NISTCacheManagerConfig config,
                                                        BagStorage bagstor, S3Client s3client)
    {
        return new CacheManagerProvider(config, bagstor, s3client);
    }


    /**
     * create a configuration object for Restricted Public Access (RPA)
     */
    @Bean
    @ConfigurationProperties("distrib.rpa")
    public RPAConfiguration getRPAConfiguration() throws ConfigurationException {
        // this will have config properties injected into it
        return new RPAConfiguration();
    }

    /**
     * create an RPACachingServiceProvider, used to create the RPACachingService
     */
    @Bean
    public RPACachingServiceProvider getRPACachingServiceProvider(NISTCacheManagerConfig cmConfig,
                                                                  RPAConfiguration rpaConfig,
                                                                  BagStorage bagstor, S3Client s3client)
    {
        return new RPACachingServiceProvider(cmConfig, rpaConfig, bagstor, s3client);
    }

    /**
     * the service implementation to use for restricted data access caching service
     *
    public RPACachingService getRestrictedDataCachingService(RPACachingServiceProvider rpaProvider,
                                                             AmazonS3 s3)
        throws ConfigurationException, IOException, CacheManagementException
    {
        return rpaProvider.getRPACachingService(s3);
    }
     */

    /**
     * the configured RPAServiceProvider to use
     */
    @Bean
    public RPAServiceProvider getRPAServiceProvider(RPAConfiguration rpaConfiguration) {
        return new RPAServiceProvider(rpaConfiguration);
    }

    @Bean
    public RPAAsyncExecutor getRPAAsyncExecutor(RPACachingServiceProvider rpaCachingServiceProvider,
            RPAServiceProvider rpaServiceProvider, S3Client s3)
            throws ConfigurationException, IOException, CacheManagementException {

        RPACachingService cachingService = rpaCachingServiceProvider.getRPACachingService(s3);
        RPARequestHandler handler = rpaServiceProvider.getRPARequestHandler(cachingService);
        return new RPAAsyncExecutor(handler);
    }

    /**
     * configure MVC model, including setting CORS support and semicolon in URLs.
     * <p>
     * This gets called as a result of having the @SpringBootApplication annotation.
     * <p>
     * The returned configurer allows requested files to have semicolons in them.  By 
     * default, spring will truncate URLs after the location of a semicolon.  
     */
    @Bean
    public WebMvcConfigurer mvcConfigurer() {
        return new WebMvcConfigurer() { 
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**");
            }

            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                UrlPathHelper urlPathHelper = configurer.getUrlPathHelper();
                if (urlPathHelper == null) {
                    urlPathHelper = new UrlPathHelper();
                    configurer.setUrlPathHelper(urlPathHelper);
                }
                urlPathHelper.setRemoveSemicolonContent(false);
            }
        };
    }



    @Bean
    public OpenAPI customOpenAPI(@Value("1.1.0") String appVersion) {
       appVersion = VERSION;
       List<Server> servers = new ArrayList<>();
       servers.add(new Server().url("/od"));

       String description = "The Public data repository hosts the data which is available for users to download via " 
           + "web application.  This data is accessed by service called data distribution service. This is collection "
           + "of API endpoints which are used to distribute data based on the query criteria.";
       String licenseurl = "https://www.nist.gov/open/copyright-fair-use-and-licensing-statements-srd-data-software-and-technical-series-publications";

       return new OpenAPI().components(new Components())
                           .components(new Components())
                           .servers(servers)
                           .info(new Info().title("Data Distribution Service API")
                                           .version(VersionController.NAME+" "+VersionController.VERSION)
                                           .description(description)
                                           .license(new License().name("NIST Software")
                                                                 .url(licenseurl)));
    }
    
    /**
     * The service name
     */
    public final static String NAME;

    /**
     * The version of the service
     */
    public final static String VERSION;

    static {
        String name = null;
        String version = null;
        try (InputStream verf =  NISTDistribServiceConfig.class.getClassLoader().getResourceAsStream("VERSION")) {
            if (verf == null) {
                name = "oar-dist-service";
                version = "not set";
            }
            else {
                BufferedReader vrdr = new BufferedReader(new InputStreamReader(verf));
                String line = vrdr.readLine();
                String[] parts = line.split("\\s+");
                name = parts[0];
                version = (parts.length > 1) ? parts[1] : "missing";
            }
        } catch (Exception ex) {
            name = "oar-dist-service";
            version = "unknown";
        }
        NAME = name;
        VERSION = version;
    }
    
    /**
     * the spring-boot application main()
     */
    public static void main(String[] args) {
        // run with the configuration set here
        SpringApplication.run( NISTDistribServiceConfig.class, args );
    }
}

