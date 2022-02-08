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
 * a {@link gov.nist.oar.distrib.cachemgr cachemgr} sub-package that provides implementations of the 
 * {@link  gov.nist.oar.distrib.cachemgr.StorageInventoryDB} interface along with other classes that operate
 * on a {@link  gov.nist.oar.distrib.cachemgr.StorageInventoryDB} to do things like deletion planning
 * and integrity checking.  Such latter classes typically depend on the details of the particular database 
 * model provided by a {@link  gov.nist.oar.distrib.cachemgr.StorageInventoryDB} implementation.  This 
 * includes implementations of the {@link  gov.nist.oar.distrib.cachemgr.SelectionStrategy} interface.
 * <p>
 * Each {@link gov.nist.oar.distrib.cachemgr.StorageInventoryDB} implementation provides support for a 
 * particular database or metadata management system that can hold inventory information.  Most notably,
 * the {@link gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB} class an abstract base class 
 * for implementations built on any relational database that supports JDBC (such as 
 * {@linkplain gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB SQLite}).
 */
package gov.nist.oar.distrib.cachemgr.inventory;

