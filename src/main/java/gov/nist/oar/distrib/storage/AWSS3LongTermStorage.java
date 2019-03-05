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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.LongTermStorageBase;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.bags.preservation.BagUtils;


/**
 * An implementation of the LongTermStorage interface for accessing files from an AWS-S3 storage bucket.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class AWSS3LongTermStorage extends LongTermStorageBase {

    public final String bucket;
    protected AmazonS3 s3client = null;

    /**
     * create the storage instance
     * 
     * @throws FileNotFoundException    if the specified bucket does not exist
     * @throws AmazonServiceException   if there is a problem accessing the S3 service.  While 
     *                                  this is a runtime exception that does not have to be caught 
     *                                  by the caller, catching it is recommended to address 
     *                                  connection problems early.
     */
    public AWSS3LongTermStorage(String bucketname, AmazonS3 s3)
        throws FileNotFoundException, AmazonServiceException
    {
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
     * Given an exact file name in the storage, return an InputStream open at the start of the file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
                         may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream - open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public InputStream openFile(String filename) throws FileNotFoundException, StorageStateException {
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, filename);
            S3Object s3Object = s3client.getObject(getObjectRequest);
            return s3Object.getObjectContent();
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
    public Checksum getChecksum(String filename) throws FileNotFoundException, StorageStateException {
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

            if (getSize(filename) > 5000000)  // 10x smaller limit than for local files
                throw new StorageStateException("No cached checksum for large file: "+filename);

            // ok, calculate it on the fly
            try {
                return Checksum.sha256(calcSHA256(filename));
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
    }

    /**
     * Return the size of the named file in bytes
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return long, the size of the file in bytes
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public long getSize(String filename) throws FileNotFoundException, StorageStateException {
        try {
            return s3client.getObjectMetadata(bucket, filename).getContentLength();
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new FileNotFoundException("File not found in S3 bucket: "+filename);
            throw new StorageStateException("Trouble accessing "+filename+": "+ex.getMessage(), ex);
        }
    }

    /**
     * Return all the bags associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return List<String>, the file names for all bags associated with given ID
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public List<String> findBagsFor(String identifier)
        throws ResourceNotFoundException, StorageStateException
    {
        List<S3ObjectSummary> files = null;
        try {
            ObjectListing objectListing = s3client.listObjects(bucket, identifier+".");
            files = objectListing.getObjectSummaries();
        } catch (AmazonServiceException ex) {
            throw new StorageStateException("Trouble accessing bucket, "+bucket+": "+ex.getMessage(),ex);
        }
                
        if (files.isEmpty()) 
            throw ResourceNotFoundException.forID(identifier);

        List<String> filenames = new ArrayList<String>(files.size());
        files.forEach(new Consumer<S3ObjectSummary>() {
            public void accept(S3ObjectSummary f) {
                String name = f.getKey();
                if (! name.endsWith(".sha256") && BagUtils.isLegalBagName(name))
                    filenames.add(name);
            }
        });
     
        return filenames;
    }
}
