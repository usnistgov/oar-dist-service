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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.ds.exception.IDNotFoundException;
import gov.nist.oar.ds.exception.ResourceNotFoundException;
import gov.nist.oar.bags.preservation.BagUtils;

/**
 * An implementation of the LongTermStorage interface for accessing files from a locally mounted disk.
 *
 * @see gov.nist.oar.distrib.LongTermStorage
 * @author Deoyani Nandrekar-Heinis
 */
@Service
@Profile("FileStorage")
public class FilesystemLongTermStorage implements LongTermStorage {

    private static Logger logger = LoggerFactory.getLogger(FilesystemLongTermStorage.class);
    
    @Value("${distservice.ec2storage}")
    public String dataDir;
    
    public FilesystemLongTermStorage(){
        logger.info("Constructor FilesystemLongTermStorage dataDir/:"+dataDir);
    }
    
    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file
     * @param filename   The name of the desired file.  Note that this does not refer to files that may reside inside a 
                         serialized bag or other archive (e.g. zip) file.  
     * @return InputStream open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public InputStream openFile(String filename) throws FileNotFoundException {

      return new FileInputStream(this.dataDir+filename);
    }

    /**
     * return the checksum for the given file
     * @param filename   The name of the desired file.  Note that this does not refer to files that may reside inside a 
     *                   serialized bag or other archive (e.g. zip) file.  
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public Checksum getChecksum(String filename) throws FileNotFoundException {
      
        logger.info("FilesystemLongTermStorage GetChecksumfor "+filename);
        logger.info("FilesystemLongTermStorage Directory:"+dataDir);
       
        RegexFileFilter regfilter=  new RegexFileFilter(this.dataDir+filename+".sha256");
        
        logger.info("Check file name with path:"+regfilter.pattern.pattern());
        
        File file = new File(regfilter.pattern.pattern());
        if (! file.isFile())  
            throw new FileNotFoundException();
        byte[] encoded;
        logger.info("FileExists");
        try {
            encoded = Files.readAllBytes(Paths.get(regfilter.pattern.pattern()));
            logger.info("encoded ::"+new String(encoded, Charset.defaultCharset()));
        } catch(IOException exp) {
            throw new FileNotFoundException(exp.getMessage());
        }
        return Checksum.sha256(new String(encoded, Charset.defaultCharset()));
    }

    /**
     * Return the size of the named file in bytes
     * @param filename   The name of the desired file.  Note that this does not refer to files that may reside inside a 
     *                   serialized bag or other archive (e.g. zip) file.  
     * @return long, the size of the file in bytes
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public long getSize(String filename) throws FileNotFoundException {
        if(!BagUtils.isLegalBagName(filename)){
            throw new IllegalArgumentException("File name is not legal.");
        }
       
        File file = new File(this.dataDir+filename);
        logger.info("FilesystemLongTermStorage CheckFileSize" +file.length());
        return file.length();
    }

    /**
     * Return all the bags associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return List<String>, the file names for all bags associated with given ID
     * @throws IDNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public List<String> findBagsFor(String identifier) throws IDNotFoundException {
        logger.info("List of files FilesystemLongTermStorage dataDir/:"+dataDir);
        
        List<String> filenames = new ArrayList<>(); 
        File dir = new File(this.dataDir);
        if (! dir.isDirectory()) 
            throw new IllegalStateException("Directory/folder does not exist");
        
        File[] files = dir.listFiles(new RegexFileFilter(identifier));
        
        if (files.length == 0 ) {
            logger.error("No data available for given id.");
            throw new ResourceNotFoundException("No data available for given id.");
        } else {
            for(File file :files) {
                String fname = file.getName();
                if (fname.endsWith(".zip") && BagUtils.isLegalBagName(fname))
                    filenames.add(fname);
            }
        }
        
        return filenames;
    }

    /**
     * Return the head bag associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return String, the head bag's file name
     * @throws IDNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public String findHeadBagFor(String identifier) throws IDNotFoundException {
        logger.info("Looking for file in :"+ dataDir);
        return BagUtils.findLatestHeadBag(this.findBagsFor(identifier));
    }

    /**
     * Return the name of the head bag for the identifier for given version
     * @param identifier  the AIP identifier for the desired data collection 
     * @param version     the desired version of the AIP
     * @return String, the head bag's file name
     * @throws IDNotFoundException   if there exist no bags with the given identifier
     */
     */
    @Override
    public String findHeadBagFor(String identifier, String version) throws IDNotFoundException {
        logger.info("Looking for file with given version in :"+ dataDir);
        return BagUtils.findLatestHeadBagWithBagVersion(this.findBagsFor(identifier+"."+version));
    }

    //Setters for the data directory
    public void setDataDir(String dir){
        dataDir = dir;
    }
}


//To check the file path/name pattern
class RegexFileFilter implements java.io.FileFilter {
    final java.util.regex.Pattern pattern;

    public RegexFileFilter(String regex) {
        pattern = java.util.regex.Pattern.compile(regex);
    }

    public boolean accept(java.io.File f) {
        return pattern.matcher(f.getName()).find();
    }
}
