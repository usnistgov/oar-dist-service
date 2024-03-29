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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.storage;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.PDRBagStorageBase;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.bags.preservation.BagUtils;


/**
 * An implementation of the LongTermStorage interface for accessing files from an AWS-S3 storage bucket.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class AWSS3LongTermStorage extends PDRBagStorageBase {

    public static long defaultChecksumSizeLimit = 50000000L;  // 50 MB

    public final String bucket;
    protected AmazonS3 s3client = null;
    protected Integer pagesz = null;  // null means use default page size (1000)
    private long checksumSizeLim = defaultChecksumSizeLimit;

    /**
     * set the number of objects returned in a page of listing results.  This can be used for testing.
     * A null value means use the AWS default.  
     */
    public void setPageSize(Integer sz) { pagesz = sz; }

    /**
     * create the storage instance
     * @param bucketname    the name of the S3 bucket that provides the storage for this interface
     * @param s3            the AmazonS3 client instance to use to access the bucket
     * @throws FileNotFoundException    if the specified bucket does not exist
     * @throws AmazonServiceException   if there is a problem accessing the S3 service.  While 
     *                                  this is a runtime exception that does not have to be caught 
     *                                  by the caller, catching it is recommended to address 
     *                                  connection problems early.
     */
    public AWSS3LongTermStorage(String bucketname, AmazonS3 s3)
        throws FileNotFoundException, AmazonServiceException
    {
        super(bucketname);
        bucket = bucketname;
        s3client = s3;

        // does bucket exist?
        try {
            s3client.headBucket(new HeadBucketRequest(bucket));
        }
        catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new FileNotFoundException("Not an existing bucket: "+bucket+
                                                "("+ex.getMessage()+")");
            throw ex;
        }
        logger.info("Creating AWSS3LongTermStorage for the bucket, " + bucket);
    }

    /**
     * return true if a file with the given name exists in the storage 
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     */
    @Override
    public boolean exists(String filename) throws StorageVolumeException {
        try {
            return s3client.doesObjectExist(bucket, filename);
        } catch (AmazonServiceException ex) {
            throw new StorageStateException("Trouble accessing bucket "+bucket+": "+ex.getMessage(), ex);
        }
    }

    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
                         may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream - open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public InputStream openFile(String filename) throws FileNotFoundException, StorageVolumeException {
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, filename);
            S3Object s3Object = s3client.getObject(getObjectRequest);
            return new DrainingInputStream(s3Object, logger, filename);
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new FileNotFoundException("File not found in S3 bucket: "+filename);
            throw new StorageStateException("Trouble accessing "+filename+": "+ex.getMessage(), ex);
        }
    }

    /**
     * return the checksum for the given file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public Checksum getChecksum(String filename) throws FileNotFoundException, StorageVolumeException {
        S3Object s3Object = null;
        GetObjectRequest getObjectRequest = null;
        try {
            getObjectRequest = new GetObjectRequest(bucket, filename+".sha256");
            s3Object = s3client.getObject(getObjectRequest);
        }
        catch (AmazonServiceException ex) {
            if (ex.getStatusCode() != 404)
                throw new StorageStateException("Trouble accessing "+filename+": "+ex.getMessage(), ex);
        }

        if (s3Object == null) {
            // no cached checksum, calculate it the file is not too big
            if (! filename.endsWith(".sha256"))
                logger.warn("No cached checksum available for "+filename);

            if (getSize(filename) > checksumSizeLim)  // 10x smaller limit than for local files
                throw new StorageStateException("No cached checksum for large file: "+filename);

            // ok, calculate it on the fly
            try (InputStream is = openFile(filename)) {
                return Checksum.calcSHA256(is);
            } catch (Exception ex) {
                throw new StorageStateException("Unable to calculate checksum for small file: " + 
                                                filename + ": " + ex.getMessage());
            }
        }

        try (InputStreamReader csrdr = new InputStreamReader(s3Object.getObjectContent())) {
            return Checksum.sha256(readHash(csrdr));
        }
        catch (IOException ex) {
            throw new StorageStateException("Failed to read cached checksum value from "+ filename+".sha256" +
                                            ": " + ex.getMessage(), ex);
        }
        finally {
            try { s3Object.close(); }
            catch (IOException ex) {
                logger.warn("Trouble closing S3Object (double close?): "+ex.getMessage());
            }
        }
    }

    /**
     * Return the size of the named file in bytes
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return long, the size of the file in bytes
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public long getSize(String filename) throws FileNotFoundException, StorageVolumeException {
        try {
            return s3client.getObjectMetadata(bucket, filename).getContentLength();
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new FileNotFoundException("File not found in S3 bucket: "+filename);
            throw new StorageStateException("Trouble accessing "+filename+": "+ex.getMessage(), ex);
        }
    }

    protected ListObjectsV2Request createListRequest(String keyprefix, Integer pagesize) {
        return new ListObjectsV2Request().withBucketName(bucket)
                                         .withPrefix(keyprefix)
                                         .withMaxKeys(pagesize);
    }
             

    /**
     * Return all the bags associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return List<String>, the file names for all bags associated with given ID
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public List<String> findBagsFor(String identifier)
        throws ResourceNotFoundException, StorageVolumeException
    {
        // Because of S3 result paging, we need a specialized implementation of this method

        ListObjectsV2Result objectListing = null;
        List<S3ObjectSummary> files = null;
        List<String> filenames = new ArrayList<String>();

        ListObjectsV2Request req = createListRequest(identifier+".", pagesz);
        do {
            try {
                objectListing = s3client.listObjectsV2(req);
                files = objectListing.getObjectSummaries();
            } catch (AmazonServiceException ex) {
                throw new StorageStateException("Trouble accessing bucket, "+bucket+": "+ex.getMessage(),ex);
            }
                
            for(S3ObjectSummary f : files) {
                String name = f.getKey();
                if (! name.endsWith(".sha256") && BagUtils.isLegalBagName(name))
                    filenames.add(name);
            }

            req.setContinuationToken(objectListing.getNextContinuationToken());
        }
        while (objectListing.isTruncated());  // are there more pages to fetch?

        if (filenames.size() == 0)   
            throw ResourceNotFoundException.forID(identifier);

        return filenames;
    }

    /**
     * Return the head bag associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public String findHeadBagFor(String identifier)
        throws ResourceNotFoundException, StorageStateException
    {
        return findHeadBagFor(identifier, null);
    }

    /**
     * Return the name of the head bag for the identifier for given version
     * @param identifier  the AIP identifier for the desired data collection 
     * @param version     the desired version of the AIP; if null, assume the latest version.
     *                    If the version is an empty string, the head bag for bags without a 
     *                    version designation will be selected.  
     * @return String, the head bag's file name, or null if version is not found
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier or version
     */
    @Override
    public String findHeadBagFor(String identifier, String version)
        throws ResourceNotFoundException, StorageStateException
    {
        // Because of S3 result paging, we need a specialized implementation of this method

        // Be efficient in selecting files via a key; if possible include a version designator
        String prefix = identifier+".";
        if (version != null) {
            version = Pattern.compile("\\.").matcher(version).replaceAll("_");
            if (! Pattern.compile("^[01](_0)*$").matcher(version).find())
                prefix = prefix + Pattern.compile("(_0)+$").matcher(version).replaceAll("");
        }

        String selected = null;
        int maxseq = -1;
        ListObjectsV2Result objectListing = null;
        List<S3ObjectSummary> files = null;

        ListObjectsV2Request req = createListRequest(prefix, pagesz);
        do {
            try {
                objectListing = s3client.listObjectsV2(req);
                files = objectListing.getObjectSummaries();
            } catch (AmazonServiceException ex) {
                throw new StorageStateException("Trouble accessing bucket, "+bucket+": "+ex.getMessage(),ex);
            }

            if (! files.isEmpty()) {
                int seq = -1;
                for(S3ObjectSummary f : files) {
                    String name = f.getKey();
                    if (! name.endsWith(".sha256") && BagUtils.isLegalBagName(name)) {
                        if (version != null && ! BagUtils.matchesVersion(name, version))
                            continue;
                        seq = BagUtils.sequenceNumberIn(name);
                        if (seq > maxseq) {
                            maxseq = seq;
                            selected = name;
                        }
                    }
                }
            }

            req.setContinuationToken(objectListing.getNextContinuationToken());
        }
        while (objectListing.isTruncated());  // are there more pages to fetch?

        if (selected == null)   
            throw ResourceNotFoundException.forID(identifier, version);

        return selected;
    }

    /*
     * AWS urges that opened S3 objects be fully streamed.
     */
    static class DrainingInputStream extends FilterInputStream implements Runnable {
        private Logger logger = null;
        private String name = null;
        private S3Object s3o = null;

        public DrainingInputStream(S3Object s3object, Logger log, String name) {
            super(s3object.getObjectContent());
            s3o = s3object;
            logger = log;
            this.name = name;
        }
        public DrainingInputStream(S3Object s3object, Logger log) {
            this(s3object, log, null);
        }

        public void close() {
            /*
             * does not work under heavy load
             *
            Thread t = new Thread(this, "S3Object closer");
            t.start();
             */
            runClose();
        }

        public void run() { runClose(); }

        void runClose() {
            long start = System.currentTimeMillis();
            String what = (name == null) ? "" : name+" ";
            try {
                byte[] buf = new byte[100000];
                int len = 0;
                logger.debug("Draining {}S3 Object stream ({})", what, in.toString());
                while ((len = read(buf)) != -1) { /* fugetaboutit */ }
                if (logger.isInfoEnabled()) {
                    String[] flds = in.toString().split("\\.");
                    logger.info("Drained {}S3 object stream ({}) in {} millseconds", what,
                                flds[flds.length-1], (System.currentTimeMillis() - start));
                }
            }
            catch (IOException ex) {
                logger.warn("Trouble draining {}S3 object stream ({}): {}",
                            what, in.toString(), ex.getMessage());
            }
            finally {
                try { super.close(); }
                catch (IOException ex) {
                    logger.warn("Trouble closing {}S3 object stream ({}): {}",
                                what, in.toString(), ex.getMessage());
                }
                try { s3o.close(); }
                catch (IOException ex) {
                    logger.warn("Trouble closing S3Object {}(double close?): "+ex.getMessage());
                }
            }
        }
    }
}
