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

import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.ObjectNotFoundException;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.MalformedURLException;

import org.json.JSONObject;
import org.json.JSONException;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;

/**
 * an implementation of the CacheVolume interface that stores its data 
 * in a folder of an Amazon Web Services S3 bucket.  
 *
 * The storage model has all data stored under a single folder within the bucket.  Within that 
 * folder, objects are stored with paths matching the name as given via addObject().
 * When that name includes a slash, the object file is stored in a subdirectory 
 * consistent with directory path implied by the name.  
 */
public class AWSS3CacheVolume implements CacheVolume {

    public final String bucket;
    public final String folder;
    public final String name;
    protected AmazonS3 s3client = null;
    protected String baseurl = null;

    /**
     * create the storage instance
     * @param bucketname    the name of the S3 bucket that provides the storage for this interface
     * @param folder        the name of the folder within the bucket where objects will be stored.  If null
     *                      or an empty string, it will be assumed that the objects should reside at the 
     *                      root of the bucket.  
     * @param s3            the AmazonS3 client instance to use to access the bucket
     * @param redirectBaseURL  a base URL to use to form redirect URLs based on object names
     *                            when {@link #getRedirectFor(String)} is called.  This 
     *                            implementation will form the URL by appending the object 
     *                            name to this base URL.  Note that a delimiting slash will 
     *                            <i>not</i> be automatically inserted; if a slash is needed, 
     *                            it should be included as part of this base URL.  
     * @throws FileNotFoundException    if the specified bucket does not exist
     * @throws AmazonServiceException   if there is a problem accessing the S3 service.  While 
     *                                  this is a runtime exception that does not have to be caught 
     *                                  by the caller, catching it is recommended to address 
     *                                  connection problems early.
     * @throws MalformedURLException  if the given <code>redirectBaseURL</code> cannot be used to form
     *                            legal URLs
     */
    public AWSS3CacheVolume(String bucketname, String folder, AmazonS3 s3, String redirectBaseURL)
        throws FileNotFoundException, AmazonServiceException, MalformedURLException
    {
        this(bucketname, folder, null, s3, redirectBaseURL);
    }

    /**
     * create the storage instance
     * @param bucketname    the name of the S3 bucket that provides the storage for this interface
     * @param folder        the name of the folder within the bucket where objects will be stored.  If null
     *                      or an empty string, it will be assumed that the objects should reside at the 
     *                      root of the bucket.  
     * @param name          a name to refer to this volume by
     * @param s3            the AmazonS3 client instance to use to access the bucket
     * @param redirectBaseURL  a base URL to use to form redirect URLs based on object names
     *                            when {@link #getRedirectFor(String)} is called.  This 
     *                            implementation will form the URL by appending the object 
     *                            name to this base URL.  Note that a delimiting slash will 
     *                            <i>not</i> be automatically inserted; if a slash is needed, 
     *                            it should be included as part of this base URL.  
     * @throws FileNotFoundException    if the specified bucket does not exist
     * @throws AmazonServiceException   if there is a problem accessing the S3 service.  While 
     *                                  this is a runtime exception that does not have to be caught 
     *                                  by the caller, catching it is recommended to address 
     *                                  connection problems early.
     * @throws MalformedURLException  if the given <code>redirectBaseURL</code> cannot be used to form
     *                            legal URLs
     */
    public AWSS3CacheVolume(String bucketname, String folder, String name, AmazonS3 s3, String redirectBaseURL)
        throws FileNotFoundException, AmazonServiceException, MalformedURLException
    {
        this(bucketname, folder, name, s3);

        baseurl = redirectBaseURL;
        if (baseurl != null)
            // make sure we can make proper URLs with this base
            new URL(baseurl + "test");
    }


    /**
     * create the storage instance
     * @param bucketname    the name of the S3 bucket that provides the storage for this interface
     * @param folder        the name of the folder within the bucket where objects will be stored.  If null
     *                      or an empty string, it will be assumed that the objects should reside at the 
     *                      root of the bucket.  
     * @param s3            the AmazonS3 client instance to use to access the bucket
     * @throws FileNotFoundException    if the specified bucket does not exist
     * @throws AmazonServiceException   if there is a problem accessing the S3 service.  While 
     *                                  this is a runtime exception that does not have to be caught 
     *                                  by the caller, catching it is recommended to address 
     *                                  connection problems early.
     */
    public AWSS3CacheVolume(String bucketname, String folder, AmazonS3 s3)
        throws FileNotFoundException, AmazonServiceException
    {
        this(bucketname, folder, null, s3);
    }


    /**
     * create the storage instance
     * @param bucketname    the name of the S3 bucket that provides the storage for this interface
     * @param folder        the name of the folder within the bucket where objects will be stored.  If null
     *                      or an empty string, it will be assumed that the objects should reside at the 
     *                      root of the bucket.  
     * @param name          a name to refer to this volume by
     * @param s3            the AmazonS3 client instance to use to access the bucket
     * @throws FileNotFoundException    if the specified bucket does not exist
     * @throws AmazonServiceException   if there is a problem accessing the S3 service.  While 
     *                                  this is a runtime exception that does not have to be caught 
     *                                  by the caller, catching it is recommended to address 
     *                                  connection problems early.
     */
    public AWSS3CacheVolume(String bucketname, String folder, String name, AmazonS3 s3)
        throws FileNotFoundException, AmazonServiceException
    {
        bucket = bucketname;
        if (folder != null && folder.length() == 0)
            folder = null;
        this.folder = folder;
        s3client = s3;

        // does bucket exist?
        try {
            s3client.headBucket(new HeadBucketRequest(bucket));
        }
        catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new FileNotFoundException("Not an existing bucket: "+bucket+
                                                " ("+ex.getMessage()+")");
            throw ex;
        }

        // does folder exist in the bucket?
        if (! s3client.doesObjectExist(bucket, folder+"/"))
            throw new FileNotFoundException("Not an existing folder in "+bucket+" bucket: "+folder);

        if (name == null) {
            name = "s3:/" + bucket + "/";
            if (folder != null) name += folder + "/";
        }
        this.name = name;
    }

    /**
     * return the identifier or name assigned to this volume.  If null is returned, 
     * the name is not known.
     */
    public String getName() { return name; }

    private String s3name(String name) {
        if (folder == null)
            return name;
        return folder+"/"+name;
    }

    /**
     * return True if an object with a given name exists in this storage volume
     * @param name  the name of the object
     * @throws StorageVolumeException   if there is an error accessing the underlying storage system.
     */
    public boolean exists(String name) throws StorageVolumeException {
        try {
            return s3client.doesObjectExist(bucket, s3name(name));
        } catch (AmazonServiceException ex) {
            throw new StorageVolumeException("Trouble accessing bucket "+bucket+": "+ex.getMessage(), ex);
        }
    }

    /**
     * save a copy of the named object to this storage volume.  If an object 
     * already exists in the volume with this name, it will be replaced.  
     * <p>
     * This implementation will look for three metadata properties that will be incorporated into 
     * the S3 transfer request for robustness:
     * <ul>
     *  <li> <code>size</code> -- this will be set as the content-length header property for the file stream;
     *                            if this number of bytes is not transfered successfully, an exception will 
     *                            occur. </li>
     *  <li> <code>contentMD5</code> -- a base-64 encoding of the MD5 hash of the file which will be checked 
     *                            against the server-side value calculated by the AWS server; a mismatch will
     *                            result in an error.  Note that if this is not provided the AWS SDK will 
     *                            calculate and verify a value automatically; thus, it should not be necessary 
     *                            to set this.  </li>
     *  <li> <code>contentType</code> -- the MIME-type to associate with this file.  This is stored as 
     *                            associated AWS object metadata and will be used if the file is downloaded 
     *                            via an AWS public GET URL (and perhaps other download frontends). </li>
     * </ul>
     * @param from   an InputStream that contains the bytes the make up object to save
     * @param name   the name to assign to the object within the storage.  
     * @param md     the metadata to be associated with that object.  This parameter cannot be null
     *                 and must include the object size.  
     * @throws StorageVolumeException  if the method fails to save the object correctly.
     */
    public void saveAs(InputStream from, String name, JSONObject md)
        throws StorageVolumeException
    {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("AWSS3CacheVolume.saveAs(): must provide name");
        long size = -1L;
        String ct = null, cmd5 = null;
        if (md != null) {
            try {
                size = md.getLong("size");
            } catch (JSONException ex) { }
            try {
                ct = md.getString("contentType");
            } catch (JSONException ex) { }
            try {
                cmd5 = md.getString("contentMD5");
            } catch (JSONException ex) { }
        }
        if (size < 0)
            throw new IllegalArgumentException("AWSS3CacheVolume.saveAs(): metadata must be provided with " +
                                               "size property");

        // set some metadata for the object
        ObjectMetadata omd = new ObjectMetadata();
        omd.setContentLength(size);  // required
        if (ct != null)
            omd.setContentType(ct);  // for redirect web server
        if (cmd5 != null)
            omd.setContentMD5(cmd5); // for on-the-fly checksum checking

        // set the name to download as (for benefit of redirect web server)
        if (name.endsWith("/")) name = name.substring(0, name.length()-1);
        String[] nmflds = name.split("/");
        omd.setContentDisposition(nmflds[nmflds.length-1]);

        Upload uplstat = null;
        try {
            TransferManager trxmgr = TransferManagerBuilder.standard().withS3Client(s3client)
                                                           .withMultipartUploadThreshold(200000000L) 
                                                           .withMinimumUploadPartSize(100000000L)
                                                           .build();
            uplstat = trxmgr.upload(bucket, s3name(name), from, omd);
            uplstat.waitForUploadResult();
        } catch (InterruptedException ex) {
            throw new StorageVolumeException("Upload interrupted for object, " + s3name(name) +
                                             ", to s3:/"+bucket+": " + ex.getMessage(), ex);
        } catch (AmazonServiceException ex) {
            throw new StorageVolumeException("Failure to save object, " + s3name(name) +
                                             ", to s3:/"+bucket+": " + ex.getMessage(), ex);
        } catch (AmazonClientException ex) {
            if (ex.getMessage().contains("verify integrity") && ex.getMessage().contains("contentMD5")) {
                // unfortunately this is how we identify a checksum error
                // clean-up badly transfered file.
                try { remove(name); }
                catch (StorageVolumeException e) { }
                throw new StorageVolumeException("Failure to save object, " + s3name(name) +
                                                 ", to s3:/"+bucket+": md5 transfer checksum failed");
            }
            if (ex.getMessage().contains("dataLength=") && ex.getMessage().contains("expectedLength=")) {
                throw new StorageVolumeException("Failure to transfer correct number of bytes for " + 
                                                 s3name(name) + " to s3:/"+bucket+" ("+ex.getMessage()+").");
            }
            throw new StorageVolumeException("AWS client error: "+ex.getMessage()+"; object status unclear");
        }

        if (md != null) {
            try {
                CacheObject co = get(name);
                long mod = co.getLastModified();
                if (mod > 0L)
                    md.put("modified", mod);
                if (co.hasMetadatum("volumeChecksum"))
                    md.put("volumeChecksum", co.getMetadatumString("volumeChecksum", " "));
            }
            catch (ObjectNotFoundException ex) {
                throw new StorageStateException("Upload apparently failed: "+ex.getMessage(), ex);
            }
            catch (StorageVolumeException ex) {
                throw new StorageStateException("Uploaded object status unclear: "+ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * save a copy of an object currently stored in another volume.  If an object 
     * already exists in the volume with this name, it will be replaced.  This 
     * allows for an implementation to invoke special optimizations for certain 
     * kinds of copies (e.g. S3 to S3).  
     * @param obj    an object in another storage volume.
     * @param name   the name to assign to the object within the storage.  
     * @throws ObjectNotFoundException  if the object does not exist in specified
     *                                     volume
     * @throws StorageVolumeException   if method fails to save the object correctly 
     *               or if the request calls for copying an object to itself or 
     *               if the given CacheObject is not sufficiently specified. 
     */
    public synchronized void saveAs(CacheObject obj, String name) throws StorageVolumeException {
        if (obj.name == null)
            throw new StorageVolumeException("name for cache object (in volume, "+obj.volname+
                                           ") not set.");
        if (obj.volume == null)
            throw new StorageVolumeException("Unable to locate volume, "+obj.volname+
                                           ", for cache object, "+obj.name);
        if (this.name.equals(obj.volname) && name.equals(obj.name))
            throw new StorageVolumeException("Request to copy "+obj.volname+":"+obj.name+
                                           " onto itself");
        if (! obj.volume.exists(obj.name))
            throw new ObjectNotFoundException(obj.name, obj.volname);

        try (InputStream is = obj.volume.getStream(obj.name)) {
            this.saveAs(is, name, obj.exportMetadata());
        }
        catch (IOException ex) {
            throw new StorageVolumeException("Trouble closing source stream while reading object "+obj.name);
        }
    }

    /**
     * return an open InputStream to the object with the given name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     * @throws StorageVolumeException     if there is any other problem opening the 
     *                                     named object
     */
    public InputStream getStream(String name) throws StorageVolumeException {
        String use = s3name(name);
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, use);
            S3Object s3Object = s3client.getObject(getObjectRequest);
            return s3Object.getObjectContent();
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new ObjectNotFoundException("Object not found: s3:/"+bucket+"/"+use, this.getName());
            throw new StorageStateException("Trouble accessing "+name+": "+ex.getMessage(), ex);
        }
    }

    /**
     * return a reference to an object in the volume given its name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     */
    public CacheObject get(String name) throws StorageVolumeException {
        String use = s3name(name);
        ObjectMetadata omd = null;
        try {
            omd = s3client.getObjectMetadata(bucket, use);
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                throw new ObjectNotFoundException("Object not found: s3:/"+bucket+"/"+use, this.getName());
            throw new StorageStateException("Trouble accessing "+name+": "+ex.getMessage(), ex);
        }

        JSONObject md = new JSONObject();
        md.put("size", omd.getContentLength());
        md.put("contentType", omd.getContentType());
        md.put("modified", omd.getLastModified().getTime());
        md.put("volumeChecksum", "etag " + omd.getETag());

        return new CacheObject(name, md, this);
    }

    /** 
     * remove the object with the give name from this storage volume
     * @param name       the name of the object to get
     * @return boolean  True if the object existed in the volume; false if it was 
     *                       not found in this volume
     * @throws StorageVolumeException     if there is an internal error while trying to 
     *                                     remove the Object
     */
    public boolean remove(String name) throws StorageVolumeException {
        String use = s3name(name);
        try {
            s3client.deleteObject(bucket, use);
            return true;
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404)
                return false;
            throw new StorageStateException("Trouble accessing "+name+": "+ex.getMessage(), ex);
        }
    }

    /**
     * return a URL that th eobject with the given name can be alternatively 
     * read from.  This allows for a potentially faster way to deliver a file
     * to web clients than via a Java stream copy.  Not all implementations may
     * support this. 
     *
     * This implementation throws an UnsupportedOperationException if 
     * {@linkplain #AWSS3CacheVolume(String,String,AmazonS3,String) the constructor} 
     * was not provided with a <code>redirectBaseURL</code> argument.
     *
     * @param name       the name of the object to get
     * @return URL      a URL where the object can be streamed from
     * @throws UnsupportedOperationException     always as this function is not supported
     */
    public URL getRedirectFor(String name) throws StorageVolumeException, UnsupportedOperationException {
        if (baseurl == null)
            throw new UnsupportedOperationException("AWSS3CacheVolume: getRedirectFor not supported");

        if (exists(name)) {
            try {
                return s3client.getUrl(bucket, s3name(name));
            }
            catch (AmazonServiceException ex) {
                throw new StorageVolumeException("Failed to determine redirect URL for name="+name+": "+
                                                 ex.getMessage(), ex);
            }
        }
        else {
            try {
                return new URL(baseurl + name.replace(" ", "%20"));
            }
            catch (MalformedURLException ex) {
                throw new StorageVolumeException("Failed to form legal URL: "+ex.getMessage(), ex);
            }
        }
    }

    /**
     * create a folder/subdirectory in a bucket if it already doesn't exist
     *
     * @param bucketname  the name of the bucket where the folder should exist
     * @param folder      the name of the folder to ensure exists
     * @param s3          the authenticated <code>AmazonS3</code> client to use to access the bucket
     */
    public static boolean ensureBucketFolder(AmazonS3 s3, String bucketname, String folder)
        throws AmazonServiceException
    {
        if (! folder.endsWith("/")) folder += "/";
        if (! s3.doesObjectExist(bucketname, folder)) {
            ObjectMetadata md = new ObjectMetadata();
            md.setContentLength(0);
            InputStream mt = new ByteArrayInputStream(new byte[0]);
            try {
                s3.putObject(bucketname, folder, mt, md);
                return true;
            } finally {
                try { mt.close(); } catch (IOException ex) { }
            }
        }
        return false;
    }
}
