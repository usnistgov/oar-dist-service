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
import java.util.regex.Pattern;

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.StorageVolumeException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * An implementation of the LongTermStorage interface for accessing files from
 * an AWS-S3 storage bucket.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class AWSS3LongTermStorage extends PDRBagStorageBase {

    public static long defaultChecksumSizeLimit = 50_000_000L; // 50 MB

    public final String bucket;
    protected S3Client s3client;
    protected Integer pagesz = null; // null means use default page size
    private long checksumSizeLim = defaultChecksumSizeLimit;

    /**
     * set the number of objects returned in a page of listing results. This can be
     * used for testing.
     * A null value means use the AWS default.
     */
    public void setPageSize(Integer sz) {
        pagesz = sz;
    }

    /**
     * create the storage instance
     * 
     * @param bucketname the name of the S3 bucket that provides the storage for
     *                   this interface
     * @param s3         the AmazonS3 client instance to use to access the bucket
     * @throws FileNotFoundException  if the specified bucket does not exist
     * @throws AmazonServiceException if there is a problem accessing the S3
     *                                service. While
     *                                this is a runtime exception that does not have
     *                                to be caught
     *                                by the caller, catching it is recommended to
     *                                address
     *                                connection problems early.
     */
    public AWSS3LongTermStorage(String bucketname, S3Client s3Client)
            throws FileNotFoundException, StorageVolumeException {
        super(bucketname);
        this.bucket = bucketname;
        this.s3client = s3Client;

        // Check if bucket exists
        try {
            s3client.headBucket(HeadBucketRequest.builder().bucket(bucketname).build());
        } catch (NoSuchBucketException ex) {
            throw new FileNotFoundException("Bucket not found: " + bucketname);
        } catch (S3Exception ex) {
            throw new StorageStateException("Error accessing bucket: " + bucketname, ex);
        }
        logger.info("Initialized AWSS3LongTermStorage for bucket: {}", bucket);
    }

    /**
     * return true if a file with the given name exists in the storage
     * 
     * @param filename The name of the desired file. Note that this does not refer
     *                 to files that
     *                 may reside inside a serialized bag or other archive (e.g.
     *                 zip) file.
     */
    @Override
    public boolean exists(String filename) throws StorageVolumeException {
        try {
            s3client.headObject(HeadObjectRequest.builder().bucket(bucket).key(filename).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            throw new StorageStateException("Error checking existence of " + filename + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Given an exact file name in the storage, return an InputStream open at the
     * start of the file
     * 
     * @param filename The name of the desired file. Note that this does not refer
     *                 to files that
     *                 may reside inside a serialized bag or other archive (e.g.
     *                 zip) file.
     * @return InputStream - open at the start of the file
     * @throws FileNotFoundException if the file with the given filename does not
     *                               exist
     */
    @Override
    public InputStream openFile(String filename) throws FileNotFoundException, StorageVolumeException {
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(filename).build();
            ResponseInputStream<GetObjectResponse> response = s3client.getObject(request);
            return response;
        } catch (NoSuchKeyException ex) {
            throw new FileNotFoundException("File not found in S3 bucket: " + filename);
        } catch (S3Exception ex) {
            throw new StorageStateException("Error accessing " + filename + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * return the checksum for the given file
     * 
     * @param filename The name of the desired file. Note that this does not refer
     *                 to files that
     *                 may reside inside a serialized bag or other archive (e.g.
     *                 zip) file.
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException if the file with the given filename does not
     *                               exist
     */
    @Override
    public Checksum getChecksum(String filename) throws FileNotFoundException, StorageVolumeException {
        String checksumKey = filename + ".sha256";
        ResponseInputStream<GetObjectResponse> s3ObjectStream = null;

        try {
            // Try to retrieve the checksum file from S3
            s3ObjectStream = s3client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(checksumKey)
                    .build());

            try (InputStreamReader reader = new InputStreamReader(s3ObjectStream)) {
                // Read and return the checksum from the file
                return Checksum.sha256(readHash(reader));
            } catch (IOException e) {
                throw new StorageStateException("Failed to read cached checksum value from " + checksumKey, e);
            }
        } catch (NoSuchKeyException e) {
            // Handle missing checksum file
            if (!filename.endsWith(".sha256")) {
                logger.warn("No cached checksum available for " + filename);
            }

            if (getSize(filename) > checksumSizeLim) {
                throw new StorageStateException("No cached checksum for large file: " + filename);
            }

            // Calculate checksum on the fly for small files
            try (InputStream fileStream = openFile(filename)) {
                return Checksum.calcSHA256(fileStream);
            } catch (IOException ex) {
                throw new StorageStateException("Unable to calculate checksum for small file: " + filename, ex);
            }
        } catch (S3Exception ex) {
            throw new StorageStateException(
                    "Trouble accessing " + checksumKey + ": " + ex.awsErrorDetails().errorMessage(), ex);
        } finally {
            if (s3ObjectStream != null) {
                try {
                    s3ObjectStream.close();
                } catch (IOException e) {
                    logger.warn("Trouble closing S3Object stream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Return the size of the named file in bytes
     * 
     * @param filename The name of the desired file. Note that this does not refer
     *                 to files that
     *                 may reside inside a serialized bag or other archive (e.g.
     *                 zip) file.
     * @return long, the size of the file in bytes
     * @throws FileNotFoundException if the file with the given filename does not
     *                               exist
     */
    @Override
    public long getSize(String filename) throws FileNotFoundException, StorageVolumeException {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(filename).build();
            HeadObjectResponse response = s3client.headObject(request);
            return response.contentLength();
        } catch (NoSuchKeyException ex) {
            throw new FileNotFoundException("File not found in S3 bucket: " + filename);
        } catch (S3Exception ex) {
            throw new StorageStateException("Error retrieving size for " + filename + ": " + ex.getMessage(), ex);
        }
    }

    protected ListObjectsV2Request createListRequest(String keyprefix, Integer pagesize) {
        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(keyprefix);
        if (pagesize != null) {
            builder.maxKeys(pagesize);
        }
        return builder.build();
    }

    /**
     * Return all the bags associated with the given ID
     * 
     * @param identifier the AIP identifier for the desired data collection
     * @return List<String>, the file names for all bags associated with given ID
     * @throws ResourceNotFoundException if there exist no bags with the given
     *                                   identifier
     */
    @Override
    public List<String> findBagsFor(String identifier)
            throws ResourceNotFoundException, StorageVolumeException {
        List<String> filenames = new ArrayList<>();
        ListObjectsV2Request request = createListRequest(identifier + ".", pagesz);

        try {
            ListObjectsV2Response response;
            do {
                response = s3client.listObjectsV2(request);
                for (S3Object obj : response.contents()) {
                    String name = obj.key();
                    if (!name.endsWith(".sha256") && BagUtils.isLegalBagName(name)) {
                        filenames.add(name);
                    }
                }
                request = request.toBuilder().continuationToken(response.nextContinuationToken()).build();
            } while (response.isTruncated());
        } catch (S3Exception ex) {
            logger.error("Error accessing bucket {}: {}", bucket, ex.getMessage(), ex);
            throw new StorageStateException("Error accessing bucket: " + bucket, ex);
        }

        if (filenames.isEmpty()) {
            throw ResourceNotFoundException.forID(identifier);
        }

        return filenames;
    }

    /**
     * Return the head bag associated with the given ID
     * 
     * @param identifier the AIP identifier for the desired data collection
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException if there exist no bags with the given
     *                                   identifier
     */
    @Override
    public String findHeadBagFor(String identifier)
            throws ResourceNotFoundException, StorageStateException {
        return findHeadBagFor(identifier, null);
    }

    /**
     * Return the name of the head bag for the identifier for given version
     * 
     * @param identifier the AIP identifier for the desired data collection
     * @param version    the desired version of the AIP; if null, assume the latest
     *                   version.
     *                   If the version is an empty string, the head bag for bags
     *                   without a
     *                   version designation will be selected.
     * @return String, the head bag's file name, or null if version is not found
     * @throws ResourceNotFoundException if there exist no bags with the given
     *                                   identifier or version
     */
    @Override
    public String findHeadBagFor(String identifier, String version)
            throws ResourceNotFoundException, StorageStateException {
        // Prefix handling with pattern matching for version
        String prefix = identifier + ".";
        if (version != null) {
            // Replace dots in version with underscores
            version = Pattern.compile("\\.").matcher(version).replaceAll("_");

            // Remove trailing "_0" for efficiency
            if (!Pattern.compile("^[01](_0)*$").matcher(version).find()) {
                prefix += Pattern.compile("(_0)+$").matcher(version).replaceAll("");
            }
        }

        String selected = null;
        int maxSeq = -1;
        ListObjectsV2Request request = createListRequest(prefix, pagesz);

        try {
            ListObjectsV2Response response;
            do {
                response = s3client.listObjectsV2(request);
                for (S3Object obj : response.contents()) {
                    String name = obj.key();

                    // Filter out ".sha256" files and ensure legal bag names
                    if (!name.endsWith(".sha256") && BagUtils.isLegalBagName(name)) {
                        // Check version match if provided
                        if (version != null && !BagUtils.matchesVersion(name, version)) {
                            continue;
                        }

                        // Determine sequence number and update selected file
                        int seq = BagUtils.sequenceNumberIn(name);
                        if (seq > maxSeq) {
                            maxSeq = seq;
                            selected = name;
                        }
                    }
                }

                // Update continuation token for the next page
                request = request.toBuilder().continuationToken(response.nextContinuationToken()).build();
            } while (response.isTruncated());
        } catch (S3Exception ex) {
            throw new StorageStateException("Error accessing bucket: " + bucket, ex);
        }

        // Handle case where no matching file is found
        if (selected == null) {
            throw ResourceNotFoundException.forID(identifier, version);
        }

        return selected;
    }

}