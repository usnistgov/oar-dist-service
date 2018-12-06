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
 * a package for handling data packages based on the BagIt specification.  
 *
 * BagIt is a simple specification for packaging related data into a serialazable 
 * and self-documenting unit 
 * (<a href="https://tools.ietf.org/html/draft-kunze-bagit-14">specification</a>).  
 * A bag is a directory with a name whose contents are constrained in a lightweight 
 * way.  The contents include the data files (refered to as the payload) in 
 * <code>data</code> subdirectory as well as some package-level metadata.  Also 
 * included are checksums for all the data files in the payload.  
 *
 * This package provides the OAR system's support for data bags.  It segregates that 
 * support into the following subpackages:
 * <dl>
 *   <dt> <code>{@link gov.nist.oar.bags.preservation preservation}</code> </dt>
 *   <dd> provides support for bags complying with the NIST preservation bag file. </dd>
 * </dl>
 * 
 * This package (and all its subpackages) are independent of all other sibling packages 
 * under the <code>oar</code> parent package.  In particular, it contains no dependencies 
 * on the {@link gov.nist.oar.distrib distrib} package.  
 */
package gov.nist.oar.bags;

