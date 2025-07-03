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
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.storage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/**
 * an implementation of the CacheVolume interface that stores its data
 * in a folder of an Amazon Web Services S3 bucket.
 *
 * The storage model has all data stored under a single folder within the
 * bucket. Within that
 * folder, objects are stored with paths matching the name as given via
 * addObject().
 * When that name includes a slash, the object file is stored in a subdirectory
 * consistent with directory path implied by the name.
 */
public class AWSS3CacheVolume implements CacheVolume {

    public final String bucket;
    public final String folder;
    public final String name;
    protected S3Client s3client = null;
    protected String baseurl = null;
    private static final Logger logger = LoggerFactory.getLogger(AWSS3CacheVolume.class);

    /**
     * create the storage instance
     * 
     * @param bucketname      the name of the S3 bucket that provides the storage
     *                        for this interface
     * @param folder          the name of the folder within the bucket where objects
     *                        will be stored. If null
     *                        or an empty string, it will be assumed that the
     *                        objects should reside at the
     *                        root of the bucket.
     * @param s3              the AmazonS3 client instance to use to access the
     *                        bucket
     * @param redirectBaseURL a base URL to use to form redirect URLs based on
     *                        object names
     *                        when {@link #getRedirectFor(String)} is called. This
     *                        implementation will form the URL by appending the
     *                        object
     *                        name to this base URL. Note that a delimiting slash
     *                        will
     *                        <i>not</i> be automatically inserted; if a slash is
     *                        needed,
     *                        it should be included as part of this base URL.
     * @throws FileNotFoundException if the specified bucket does not exist
     * @throws SdkServiceException   if there is a problem accessing the S3 service.
     *                               While
     *                               this is a runtime exception that does not have
     *                               to be caught
     *                               by the caller, catching it is recommended to
     *                               address
     *                               connection problems early.
     * @throws MalformedURLException if the given <code>redirectBaseURL</code>
     *                               cannot be used to form
     *                               legal URLs
     */
    public AWSS3CacheVolume(String bucketname, String folder, S3Client s3, String redirectBaseURL)
            throws FileNotFoundException, SdkServiceException, MalformedURLException {
        this(bucketname, folder, null, s3, redirectBaseURL);
    }

    /**
     * create the storage instance
     * 
     * @param bucketname      the name of the S3 bucket that provides the storage
     *                        for this interface
     * @param folder          the name of the folder within the bucket where objects
     *                        will be stored. If null
     *                        or an empty string, it will be assumed that the
     *                        objects should reside at the
     *                        root of the bucket.
     * @param name            a name to refer to this volume by
     * @param s3              the AmazonS3 client instance to use to access the
     *                        bucket
     * @param redirectBaseURL a base URL to use to form redirect URLs based on
     *                        object names
     *                        when {@link #getRedirectFor(String)} is called. This
     *                        implementation will form the URL by appending the
     *                        object
     *                        name to this base URL. Note that a delimiting slash
     *                        will
     *                        <i>not</i> be automatically inserted; if a slash is
     *                        needed,
     *                        it should be included as part of this base URL.
     * @throws FileNotFoundException if the specified bucket does not exist
     * @throws SdkServiceException   if there is a problem accessing the S3 service.
     *                               While
     *                               this is a runtime exception that does not have
     *                               to be caught
     *                               by the caller, catching it is recommended to
     *                               address
     *                               connection problems early.
     * @throws MalformedURLException if the given <code>redirectBaseURL</code>
     *                               cannot be used to form
     *                               legal URLs
     */
    public AWSS3CacheVolume(String bucketname, String folder, String name, S3Client s3, String redirectBaseURL)
            throws FileNotFoundException, S3Exception, MalformedURLException {
        this(bucketname, folder, name, s3);

        baseurl = redirectBaseURL;
        if (baseurl != null)
            // make sure we can make proper URLs with this base
            new URL(baseurl + "test");
    }

    /**
     * create the storage instance
     * 
     * @param bucketname the name of the S3 bucket that provides the storage for
     *                   this interface
     * @param folder     the name of the folder within the bucket where objects will
     *                   be stored. If null
     *                   or an empty string, it will be assumed that the objects
     *                   should reside at the
     *                   root of the bucket.
     * @param s3         the AmazonS3 client instance to use to access the bucket
     * @throws FileNotFoundException if the specified bucket does not exist
     * @throws SdkServiceException   if there is a problem accessing the S3 service.
     *                               While
     *                               this is a runtime exception that does not have
     *                               to be caught
     *                               by the caller, catching it is recommended to
     *                               address
     *                               connection problems early.
     */
    public AWSS3CacheVolume(String bucketname, String folder, S3Client s3)
            throws FileNotFoundException, SdkServiceException {
        this(bucketname, folder, null, s3);
    }

    /**
     * create the storage instance
     * 
     * @param bucketname the name of the S3 bucket that provides the storage for
     *                   this interface
     * @param folder     the name of the folder within the bucket where objects will
     *                   be stored. If null
     *                   or an empty string, it will be assumed that the objects
     *                   should reside at the
     *                   root of the bucket.
     * @param name       a name to refer to this volume by
     * @param s3         the AmazonS3 client instance to use to access the bucket
     * @throws FileNotFoundException if the specified bucket does not exist
     * @throws SdkServiceException   if there is a problem accessing the S3 service.
     *                               While
     *                               this is a runtime exception that does not have
     *                               to be caught
     *                               by the caller, catching it is recommended to
     *                               address
     *                               connection problems early.
     */
    public AWSS3CacheVolume(String bucketname, String folder, String name, S3Client s3)
            throws FileNotFoundException {
        bucket = bucketname;

        if (folder != null && folder.length() == 0) {
            folder = null;
        }
        this.folder = folder;
        s3client = s3;

        // Check if the bucket exists
        try {
            s3client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new FileNotFoundException("Not an existing bucket: " + bucket + " (" + ex.getMessage() + ")");
            }
            throw ex;
        }

        // Check if the folder exists (folder is a zero-byte object with a trailing '/')
        if (folder != null) {
            String folderKey = folder.endsWith("/") ? folder : folder + "/";
            try {
                s3client.headObject(HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(folderKey)
                        .build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    throw new FileNotFoundException("Not an existing folder in " + bucket + " bucket: " + folder);
                }
                throw ex;
            }
        }

        // Set the name field
        if (name == null) {
            name = "s3:/" + bucket + "/";
            if (folder != null) {
                name += folder + "/";
            }
        }
        this.name = name;
    }

    /**
     * return the identifier or name assigned to this volume. If null is returned,
     * the name is not known.
     */
    public String getName() {
        return name;
    }

    private String s3name(String name) {
        if (folder == null)
            return name;
        return folder + "/" + name;
    }

    /**
     * return True if an object with a given name exists in this storage volume
     * 
     * @param name the name of the object
     * @throws StorageVolumeException if there is an error accessing the underlying
     *                                storage system.
     */
    public boolean exists(String name) throws StorageVolumeException {
        try {
            // Use headObject to check if the object exists
            s3client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3name(name))
                    .build());
            return true; // If no exception, the object exists
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false; // Object does not exist
            }
            throw new StorageVolumeException("Trouble accessing bucket " + bucket + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new StorageVolumeException("Unexpected error checking object existence: " + ex.getMessage(), ex);
        }
    }

    /**
     * Saves a copy of the named object to this storage volume. If an object
     * already exists in the volume with this name, it will be replaced.
     * <p>
     * <strong>Note:</strong> This implementation now uses multipart uploads instead of a single
     * PUT operation. This change is necessary because AWS S3 has a hard limit of 5GB per single PUT.
     * Multipart uploads also impose a maximum of 10,000 parts per upload, so developers should
     * pay careful attention to the part size used. In this implementation, each part is set to 50MB,
     * but if a file is extremely large, consider adjusting the part size to avoid exceeding the
     * 10,000 parts limit. <strong>TODO:</strong> Make the part size configurable.
     * <p>
     * This implementation will look for three metadata properties that will be
     * incorporated into
     * the S3 transfer request for robustness:
     * <ul>
     * <li><code>size</code> -- this will be set as the content-length header
     * property for the file stream;
     * if this number of bytes is not transfered successfully, an exception will
     * occur.</li>
     * <li><code>contentMD5</code> -- a base-64 encoding of the MD5 hash of the file
     * which will be checked
     * against the server-side value calculated by the AWS server; a mismatch will
     * result in an error. Note that if this is not provided the AWS SDK will
     * calculate and verify a value automatically; thus, it should not be necessary
     * to set this.</li>
     * <li><code>contentType</code> -- the MIME-type to associate with this file.
     * This is stored as
     * associated AWS object metadata and will be used if the file is downloaded
     * via an AWS public GET URL (and perhaps other download frontends).</li>
     * </ul>
     * 
     * @param from an InputStream that contains the bytes the make up object to save
     * @param name the name to assign to the object within the storage.
     * @param md   the metadata to be associated with that object. This parameter
     *             cannot be null
     *             and must include the object size.
     * @throws StorageVolumeException if the method fails to save the object
     *                                correctly.
     */
    public void saveAs(InputStream from, String name, JSONObject md) throws StorageVolumeException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("AWSS3CacheVolume.saveAs(): must provide name");
        }
    
        long size = -1L;
        String contentType = null;
        String contentMD5 = null;
    
        // Extract metadata
        if (md != null) {
            try {
                size = md.getLong("size");
            } catch (Exception e) {
                logger.warn("Failed to retrieve size from metadata, size is required.");
            }
            contentType = md.optString("contentType", null);
            contentMD5 = md.optString("contentMD5", null);
        }
    
        if (size <= 0) {
            throw new IllegalArgumentException("AWSS3CacheVolume.saveAs(): metadata must include size property");
        }
    
        logger.info("Starting upload: {} (Size: {} bytes)", name, size);
    
        try {
            // If an MD5 checksum is provided, validate it first.
            if (contentMD5 != null) {
                InputStream markableInputStream = from.markSupported() ? from : new BufferedInputStream(from);
                markableInputStream.mark((int) size);
                String calculatedMD5 = calculateMD5(markableInputStream, size);
                if (!calculatedMD5.equals(contentMD5)) {
                    throw new StorageVolumeException("MD5 checksum mismatch for object: " + s3name(name));
                }
                markableInputStream.reset();
                from = markableInputStream;
            }
    
            // Build the CreateMultipartUpload request
            CreateMultipartUploadRequest.Builder createRequestBuilder = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(s3name(name));
    
            if (contentType != null) {
                createRequestBuilder.contentType(contentType);
            }
    
            // Start multipart upload
            CreateMultipartUploadResponse createResponse = s3client.createMultipartUpload(createRequestBuilder.build());
            String uploadId = createResponse.uploadId();
    
            List<CompletedPart> completedParts = new ArrayList<>();
            final int partSize = 50 * 1024 * 1024; // 50MB
            byte[] buffer = new byte[partSize];
            int partNumber = 1;
    
            try {
                while (true) {
                    int totalRead = 0;
    
                    // Make sure we read exactly partSize (50MB) before uploading
                    while (totalRead < partSize) {
                        int bytesRead = from.read(buffer, totalRead, partSize - totalRead);
                        if (bytesRead == -1) {
                            break; // End of stream
                        }
                        totalRead += bytesRead;
                    }
    
                    if (totalRead == 0) { 
                        break; // No more data to upload
                    }
    
                    // Upload only when we have a reasonable part size
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, totalRead);
    
                    UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                            .bucket(bucket)
                            .key(s3name(name))
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength((long) totalRead)
                            .build();
    
                    UploadPartResponse uploadPartResponse = s3client.uploadPart(
                            uploadPartRequest,
                            RequestBody.fromByteBuffer(byteBuffer));
    
                    completedParts.add(CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(uploadPartResponse.eTag())
                            .build());

                    partNumber++;
                }
            } catch (Exception e) {
                s3client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(s3name(name))
                        .uploadId(uploadId)
                        .build());
                throw e;
            }
    
            // Complete the multipart upload
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();
    
            s3client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(s3name(name))
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build());
    
            logger.info("Multipart upload completed successfully for {}. Total parts uploaded: {}", name, completedParts.size());
    
            // Update metadata if provided.
            if (md != null) {
                CacheObject co = get(name);
                long modifiedTime = co.getLastModified();
                if (modifiedTime > 0L) {
                    md.put("modified", modifiedTime);
                }
                if (co.hasMetadatum("volumeChecksum")) {
                    md.put("volumeChecksum", co.getMetadatumString("volumeChecksum", " "));
                }
            }
        } catch (S3Exception e) {
            if (e.awsErrorDetails() != null && "InvalidDigest".equals(e.awsErrorDetails().errorCode())) {
                logger.error("MD5 checksum mismatch for {}", s3name(name));
                throw new StorageVolumeException("MD5 checksum mismatch for object: " + s3name(name), e);
            }
            logger.error("Failed to upload object {}: {}", s3name(name), e.getMessage());
            throw new StorageVolumeException("Failed to upload object: " + s3name(name) + " (" + e.getMessage() + ")", e);
        } catch (Exception e) {
            logger.error("Unexpected error saving object {}: {}", s3name(name), e.getMessage());
            throw new StorageVolumeException("Unexpected error saving object " + s3name(name) + ": " + e.getMessage(), e);
        }
    }

    // Helper method to calculate MD5 checksum
    private String calculateMD5(InputStream is, long size) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalRead = 0;

        while ((bytesRead = is.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
            totalRead += bytesRead;
            if (totalRead > size) {
                throw new IllegalArgumentException("InputStream size exceeds specified size");
            }
        }

        if (totalRead != size) {
            throw new IllegalArgumentException("InputStream size does not match specified size");
        }

        byte[] digest = md.digest();
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * save a copy of an object currently stored in another volume. If an object
     * already exists in the volume with this name, it will be replaced. This
     * allows for an implementation to invoke special optimizations for certain
     * kinds of copies (e.g. S3 to S3).
     * 
     * @param obj  an object in another storage volume.
     * @param name the name to assign to the object within the storage.
     * @throws ObjectNotFoundException if the object does not exist in specified
     *                                 volume
     * @throws StorageVolumeException  if method fails to save the object correctly
     *                                 or if the request calls for copying an object
     *                                 to itself or
     *                                 if the given CacheObject is not sufficiently
     *                                 specified.
     */
    public synchronized void saveAs(CacheObject obj, String name) throws StorageVolumeException {
        if (obj.name == null)
            throw new StorageVolumeException("name for cache object (in volume, " + obj.volname +
                    ") not set.");
        if (obj.volume == null)
            throw new StorageVolumeException("Unable to locate volume, " + obj.volname +
                    ", for cache object, " + obj.name);
        if (this.name.equals(obj.volname) && name.equals(obj.name))
            throw new StorageVolumeException("Request to copy " + obj.volname + ":" + obj.name +
                    " onto itself");
        if (!obj.volume.exists(obj.name))
            throw new ObjectNotFoundException(obj.name, obj.volname);

        try (InputStream is = obj.volume.getStream(obj.name)) {
            this.saveAs(is, name, obj.exportMetadata());
        } catch (IOException ex) {
            throw new StorageVolumeException("Trouble closing source stream while reading object " + obj.name);
        }
    }

    /**
     * return an open InputStream to the object with the given name
     * 
     * @param name the name of the object to get
     * @throws ObjectNotFoundException if the named object does not exist in this
     *                                 volume
     * @throws StorageVolumeException  if there is any other problem opening the
     *                                 named object
     */
    public InputStream getStream(String name) throws StorageVolumeException {
        String key = s3name(name);
        try {
            // Create a GetObjectRequest
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // Get the object as a stream
            ResponseInputStream<GetObjectResponse> s3InputStream = s3client.getObject(getObjectRequest);

            return s3InputStream;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ObjectNotFoundException("Object not found: s3:/" + bucket + "/" + key, this.getName());
            }
            throw new StorageStateException("Trouble accessing " + name + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new StorageVolumeException("Unexpected error accessing " + name + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * return a reference to an object in the volume given its name
     * 
     * @param name the name of the object to get
     * @throws ObjectNotFoundException if the named object does not exist in this
     *                                 volume
     */
    public CacheObject get(String name) throws StorageVolumeException {
        String key = s3name(name);
        try {
            // Use headObject to fetch metadata
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            HeadObjectResponse headResponse = s3client.headObject(headRequest);

            // Extract metadata
            JSONObject md = new JSONObject();
            md.put("size", headResponse.contentLength());
            md.put("contentType", headResponse.contentType());
            md.put("modified", headResponse.lastModified().toEpochMilli());
            md.put("volumeChecksum", "etag " + headResponse.eTag());

            return new CacheObject(name, md, this);

        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ObjectNotFoundException("Object not found: s3:/" + bucket + "/" + key, this.getName());
            }
            throw new StorageStateException("Trouble accessing " + name + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new StorageVolumeException("Unexpected error accessing " + name + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * remove the object with the give name from this storage volume
     * 
     * @param name the name of the object to get
     * @return boolean True if the object existed in the volume; false if it was
     *         not found in this volume
     * @throws StorageVolumeException if there is an internal error while trying to
     *                                remove the Object
     */
    public boolean remove(String name) throws StorageVolumeException {
        String key = s3name(name);
        try {
            // Create the delete object request
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // Delete the object
            s3client.deleteObject(deleteRequest);
            return true; // If no exception, the object was successfully deleted
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false; // Object not found, return false
            }
            throw new StorageStateException("Trouble deleting " + name + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new StorageVolumeException("Unexpected error deleting object " + name + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * return a URL that th eobject with the given name can be alternatively
     * read from. This allows for a potentially faster way to deliver a file
     * to web clients than via a Java stream copy. Not all implementations may
     * support this.
     *
     * This implementation throws an UnsupportedOperationException if
     * {@linkplain #AWSS3CacheVolume(String,String,AmazonS3,String) the constructor}
     * was not provided with a <code>redirectBaseURL</code> argument.
     *
     * @param name the name of the object to get
     * @return URL a URL where the object can be streamed from
     * @throws UnsupportedOperationException always as this function is not
     *                                       supported
     */
    @Override
    public URL getRedirectFor(String name) throws StorageVolumeException, UnsupportedOperationException {
        if (baseurl == null) {
            throw new UnsupportedOperationException("AWSS3CacheVolume: getRedirectFor not supported");
        }

        if (exists(name)) {
            try {
                // New way is to use S3Utilities to get the object URL
                GetUrlRequest request = GetUrlRequest.builder()
                        .bucket(bucket)
                        .key(s3name(name))
                        .build();

                return s3client.utilities().getUrl(request);
            } catch (S3Exception ex) {
                throw new StorageVolumeException("Failed to determine redirect URL for name=" + name + ": " +
                        ex.awsErrorDetails().errorMessage(), ex);
            }
        } else {
            try {
                // properly encode the URL
                String encodedName = UriUtils.encodePath(name, StandardCharsets.UTF_8);
                String url = baseurl.endsWith("/") ? baseurl + encodedName : baseurl + "/" + encodedName;
                return new URL(url);
            } catch (MalformedURLException ex) {
                throw new StorageVolumeException("Failed to form legal URL: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * create a folder/subdirectory in a bucket if it already doesn't exist
     *
     * @param bucketname the name of the bucket where the folder should exist
     * @param folder     the name of the folder to ensure exists
     * @param s3         the authenticated <code>AmazonS3</code> client to use to
     *                   access the bucket
     */
    public static boolean ensureBucketFolder(S3Client s3, String bucketname, String folder) throws S3Exception {
        if (!folder.endsWith("/")) {
            folder += "/";
        }

        try {
            // Check if the folder exists by calling headObject
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucketname)
                    .key(folder)
                    .build());
            return false; // Folder already exists
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex; // Re-throw exception if it's not a 404 (Not Found) error
            }
        }

        // Folder does not exist, create it as a zero-byte object
        try (InputStream emptyContent = new ByteArrayInputStream(new byte[0])) {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucketname)
                    .key(folder)
                    .contentLength(0L)
                    .build(),
                    RequestBody.fromInputStream(emptyContent, 0));
            return true; // Folder created successfully
        } catch (Exception e) {
            throw new RuntimeException("Failed to create folder in bucket " + bucketname + ": " + e.getMessage(), e);
        }
    }
}
