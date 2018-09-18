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
package gov.nist.oar.distrib.service;

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.bags.preservation.HeadBagUtils;
import gov.nist.oar.bags.preservation.ZipBagUtils;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Closeable;
import java.util.List;
import javax.activation.MimetypesFileTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link gov.nist.oar.distrib.service.FileDownloadService FileDownloadService}
 * interface that reads files directly from preservation bags.  No local cache is involved.
 */
public class FromBagFileDownloadService implements FileDownloadService {

    protected PreservationBagService pres;

    protected MimetypesFileTypeMap typemap = null;

    protected static Logger logger = LoggerFactory.getLogger(FileDownloadService.class);

    /**
     * create the service instance.  
     * 
     * @param svc      an instance of a PreservationBagService to use to access bags.
     * @param typemap  the map to use for determining content types from filename extensions; 
     *                 if null, a default will be used.  
     */
    public FromBagFileDownloadService(PreservationBagService svc, MimetypesFileTypeMap mimemap) {
        if (mimemap == null) {
            InputStream mis = getClass().getResourceAsStream("/mime.types");
            mimemap = (mis == null) ? new MimetypesFileTypeMap()
                                    : new MimetypesFileTypeMap(mis);
        }
        typemap = mimemap;
        pres = svc;
        if (pres == null)
            throw new IllegalArgumentException("FromBagFileDownloadService: missing "+
                                               "PreservationBagService instance");
    }

    /**
     * create the service instance.  
     * 
     * @param svc   an instance of a PreservationBagService to use to access bags.
     */
    public FromBagFileDownloadService(PreservationBagService svc) {
        this(svc, null);
    }

    /**
     * create the service instance.  
     * 
     * @param bagstore   a LongTermStorage instance repesenting the storage holding the 
     *                   preservation bags
     */
    public FromBagFileDownloadService(LongTermStorage bagstore) {
        this(new DefaultPreservationBagService(bagstore));
    }

    /**
     * Return the filepaths of data files available from the dataset with a given identifier
     *
     * @param dsid      the dataset identifier for the desired dataset
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws DistributionException       if an internal error has occurred
     */
    public List<String> listDataFiles(String dsid, String version) 
        throws ResourceNotFoundException, DistributionException
    {
        String headbag = (version == null) ? pres.getHeadBagName(dsid)
                                           : pres.getHeadBagName(dsid, version);
        if (! headbag.endsWith(".zip"))
            throw new DistributionException("Bag uses unsupported serialization: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);
        String bv = BagUtils.multibagVersionOf(bagname);

        try (StreamHandle sh = pres.getBag(headbag)) {
            return HeadBagUtils.listDataFiles(bv,
                                       ZipBagUtils.openFileLookup(bv, sh.dataStream, bagname).stream);
        }
        catch (FileNotFoundException ex) {
            throw new DistributionException(headbag +
                                            ": file-lookup.tsv not found (is this a head bag?)", ex);
        }
        catch (IOException ex) {
            throw new DistributionException("Error accessing file-lookup.tsv: " + ex.getMessage(), ex);
        }
    }

    /**
     * Download the data file with the given filepath.
     * <p>
     * The caller is responsible for closing the return stream.
     *
     * @param dsid    the dataset identifier for the desired dataset
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @return StreamHandle - an open stream to the file data accompanied by file metadata (like 
     *                  content length, type, checksum).
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    public StreamHandle getDataFile(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // assign a contentType based on the filename
        String ct = getDefaultContentType(filepath);

        // find the bag containing file, open the bag, find the file, set stream to file's start
        // (see openDataFile() and findDataFile())
        ZipBagUtils.OpenEntry fentry = openDataFile(dsid, filepath, version);

        return new StreamHandle(fentry.stream, fentry.info.getSize(), filepath, ct);
    }

    /**
     * Describe the data file with the given filepath.  The returned information includes the 
     * file size, type, and checksum information.  
     *
     * @param dsid      the dataset identifier 
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    public FileDescription getDataFileInfo(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // assign a contentType based on the filename
        String ct = getDefaultContentType(filepath);

        // find the bag containing file, open the bag, find the file, set stream to file's start
        // (see openDataFile() and findDataFile())
        ZipBagUtils.OpenEntry fentry = openDataFile(dsid, filepath, version);

        return new FileDescription(filepath, fentry.info.getSize(), ct);
    }

    private ZipBagUtils.OpenEntry openDataFile(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // which bag is the file in (this may throw FileNotFoundException)
        String bagfile = findBagWithFile(dsid, "data/" + filepath, version);
        if (! bagfile.endsWith(".zip"))
            throw new DistributionException("Bag uses unsupported serialization: " + bagfile);
        String bagname = bagfile.substring(0, bagfile.length()-4);

        // open the bag to the location of the file
        StreamHandle bag = null;
        try {
            bag = pres.getBag(bagfile);
            try {
                return ZipBagUtils.openDataFile(bag.dataStream, bagname, filepath);
            }
            catch (FileNotFoundException ex) {
                quietClose(bag, bagfile);
                throw new DistributionException(bagname +
                                                ": file-lookup.tsv not found (is this a head bag?)", ex);
            }
        }
        catch (FileNotFoundException ex) {
            quietClose(bag, bagfile);
            throw new DistributionException("Can't find needed bag in store: "+bagfile);
        }
        catch (IOException ex) {
            quietClose(bag, bagfile);
            throw new DistributionException("Error accessing file-lookup.tsv: " + ex.getMessage(), ex);
        }
    }

    private void quietClose(Closeable c) { quietClose(c, null); }
    private void quietClose(Closeable c, String name) {
        try {
            if (c != null) c.close();
        } catch (IOException ex) {
            StringBuffer sb = new StringBuffer();
            if (name != null) sb.append(name).append(": ");
            sb.append("Trouble closing open stream: ").append(ex.getMessage());
            logger.warn(sb.toString());
        }
    }

    /**
     * return the name of the bagfile that has a given filepath
     * @param dsid      the dataset identifier 
     * @param filepath  the path within the dataset to the desired file
     * @param version   the desired version of the dataset.  If null, the location of file from
     *                  the lastest version is returned.  
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    protected String findBagWithFile(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // find the head bag for the requested version
        String headbag = (version == null) ? pres.getHeadBagName(dsid)
                                           : pres.getHeadBagName(dsid, version);
        if (! headbag.endsWith(".zip"))
            throw new DistributionException("Bag uses unsupported serialization: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);
        String bv = BagUtils.multibagVersionOf(bagname);

        // lookup the via the file-lookup.tsv file
        String bagwith = null;
        try (StreamHandle sh = pres.getBag(headbag)) {
            InputStream is = null;
            try {
                is = ZipBagUtils.openFileLookup(bv, sh.dataStream, bagname).stream;
            }
            catch (FileNotFoundException ex) {
                throw new DistributionException(headbag +
                                            ": file-lookup.tsv not found (is this a head bag?)", ex);
            }
            
            bagwith = HeadBagUtils.lookupFile(bv, is, filepath);
        }
        catch (FileNotFoundException ex) {
            throw new DistributionException("Unexpectedly missing bag file: "+headbag);
        }
        catch (IOException ex) {
            throw new DistributionException("Error accessing file-lookup.tsv: " + ex.getMessage(), ex);
        }

        if (bagwith == null) {
            StringBuffer msg = new StringBuffer(filepath);
            msg.append(": Filepath not found in dataset id=").append(dsid);
            if (version != null)
                msg.append(" version="+version);
            throw new FileNotFoundException(msg.toString());
        }
        return bagwith + ".zip";
    }

    /**
     * return a default content type based on the given file name.  This implementation determines
     * the content type based on the file name's extension.  
     */
    public String getDefaultContentType(String filename) {
        return typemap.getContentType(filename);
    }
}
