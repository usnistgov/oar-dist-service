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
 * An implementation of the LongTermStorage interface for accessing files from an AWS-S3 storage bucket.
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public class AWSS3LongTermStorage extends PDRBagStorageBase {

    public static long defaultChecksumSizeLimit = 50_000_000L; // 50 MB

    public final String bucket;
    protected S3Client s3client;
    protected Integer pagesz = null;  // null means use default page size
    private long checksumSizeLim = defaultChecksumSizeLimit;

    public void setPageSize(Integer sz) {
        this.pagesz = sz;
    }

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

    @Override
    public Checksum getChecksum(String filename) throws FileNotFoundException, StorageVolumeException {
        String checksumKey = filename + ".sha256";
        try (InputStream is = openFile(checksumKey);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String checksumValue = reader.readLine();
            return Checksum.sha256(checksumValue);
        } catch (FileNotFoundException ex) {
            if (getSize(filename) > checksumSizeLim) {
                throw new StorageStateException("No cached checksum for large file: " + filename);
            }
            try (InputStream fileStream = openFile(filename)) {
                return Checksum.calcSHA256(fileStream);
            } catch (IOException e) {
                throw new StorageStateException("Unable to calculate checksum: " + filename, e);
            }
        } catch (IOException ex) {
            throw new StorageStateException("Error reading checksum for " + filename, ex);
        }
    }

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
            throw new StorageStateException("Error accessing bucket: " + bucket, ex);
        }

        if (filenames.isEmpty()) {
            throw ResourceNotFoundException.forID(identifier);
        }

        return filenames;
    }

    @Override
    public String findHeadBagFor(String identifier, String version)
            throws ResourceNotFoundException, StorageStateException {
        String prefix = identifier + ".";
        if (version != null) {
            version = version.replace(".", "_");
            prefix += version.replaceAll("(_0)+$", "");
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
                    if (!name.endsWith(".sha256") && BagUtils.isLegalBagName(name)) {
                        int seq = BagUtils.sequenceNumberIn(name);
                        if (seq > maxSeq) {
                            maxSeq = seq;
                            selected = name;
                        }
                    }
                }
                request = request.toBuilder().continuationToken(response.nextContinuationToken()).build();
            } while (response.isTruncated());
        } catch (S3Exception ex) {
            throw new StorageStateException("Error accessing bucket: " + bucket, ex);
        }

        if (selected == null) {
            throw ResourceNotFoundException.forID(identifier, version);
        }

        return selected;
    }
}