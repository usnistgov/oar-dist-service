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
 * a package that implements services that constitute the OAR data distribution service.
 * <p>
 * This package provides interfaces for accessing repository files in various contexts:
 * <ul>
 *   <li> individual data file that are part of published dataset, </li>
 *   <li> archive packages of datasets that were created for preservation purposes, </li>
 *   <li> user-defined data packages containing arbitrary data files from datasets. </li>
 * </ul>
 * Generally, a service can be instantiated and live for a long time, responding to many requests.
 * <p>
 * The services in this package interact with underlying implementations primarily via the abstract 
 * interfaces and common classes provided by the parent {@link gov.nist.oar.distrib distrib} package.  
 * (An exception is the {@link gov.nist.oar.distrib.DataPackagingService} which leverages the 
 * {@link gov.nist.oar.distrib.datapackage} package.)  The implementations also make use of the 
 * {@link gov.nist.oar.bags oar.bags} and {@link gov.nist.oar.distrib.cachemgr oar.distrib.cachemgr} 
 * packages.  It does not have any dependencies on the sibling {@link gov.nist.oar.distrib.web web} 
 * package.
 */
package gov.nist.oar.distrib.service;

