/*
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

/**
 * a package that defines and implements a framework for managing a file cache.
 * <p>
 * A file cache is useful in an environment where data products are stored on slow persistent storage 
 * and where there is a limited amount of fast storage space suited for delivering that data to 
 * users/applications.  Data caches can be set on the fast storage to house the most recently or 
 * most requested data products.  In the framework defined in this package, a 
 * {@link gov.nist.oar.cachemgr.CacheManager} is responsible for moving data in and out of one or 
 * more {@link gov.nist.oar.cachemgr.Cache}s.  The long-term source of the data can be in slow storage
 * or be packaged in form that is less performant for fast access on the individual file level.  
 * The {@link gov.nist.oar.cachemgr.Restorer} interface, used to pull data from a source location, 
 * adapts to different types of data storage and packaging.  A 
 * {@link gov.nist.oar.cachemgr.CacheManager} can also encapsulate other duties associated with 
 * managing data in cache volumes, including removing old or less-frequently accessed data to make 
 * room for more recently requested data.  
 */
package gov.nist.oar.cachemgr;
