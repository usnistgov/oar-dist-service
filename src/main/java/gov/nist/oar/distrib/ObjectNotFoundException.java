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
package gov.nist.oar.distrib;

/**
 * an exception indicating a request for a data object that does not exist 
 * in the storage volume.  
 * <p>
 * It's intended that this exception is typically constructed by providing 
 * the name of the requested object and the name of the volume only, with a 
 * message being generated automatically from them:
 * <verbatim>
 *    throw new ObjectNotFoundException(objectname, volume);
 * </verbatim>
 */
public class ObjectNotFoundException extends StorageVolumeException {

    protected String object = null;

    /**
     * create the exception indicating the object that could not be found and 
     * the volume it was requested from.
     * @param objname   the name of the object that could not be found
     * @param volname   the name of the volume from which the object was requested
     */
    public ObjectNotFoundException(String objname, String volname) {
        this(messageFor(objname, volname), objname, volname);
    }

    /**
     * create the exception indicating the object that could not be found and 
     * the volume it was requested from.
     * @param objname   the name of the object that could not be found
     */
    public ObjectNotFoundException(String objname) {
        this(objname, null);
    }

    /**
     * create the exception indicating the object that could not be found and 
     * the volume it was requested from.
     * @param msg       a custom message
     * @param objname   the name of the object that could not be found
     * @param volname   the name of the volume from which the object was requested
     */
    public ObjectNotFoundException(String msg, String objname, String volname) {
        this(msg, objname, volname, null);
    }

    /**
     * create the exception indicating the object that could not be found and 
     * the volume it was requested from.
     * @param msg       a custom message
     * @param objname   the name of the object that could not be found
     * @param volname   the name of the volume from which the object was requested
     * @param cause     an underlying or related cause for not finding the object
     */
    public ObjectNotFoundException(String msg, String objname, String volname,
                                   Throwable cause)
    {
        super(msg, cause);
        object = objname;
    }

    /**
     * create the exception indicating the object that could not be found and 
     * the volume it was requested from.
     * @param objname   the name of the object that could not be found
     * @param volname   the name of the volume from which the object was requested
     * @param cause     an underlying or related cause for not finding the object
     */
    public ObjectNotFoundException(String objname, String volname, Throwable cause) {
        this(messageFor(objname, volname), objname, volname, cause);
    }

    /**
     * return the name of the object that could not be found
     */
    public String getObjectName() { return object; }

    protected static String messageFor(String objname, String volname) {
        StringBuilder sb = new StringBuilder("Data object, ");
        sb.append(objname).append(", not found in");
        if (volname != null)
            sb.append(" ").append(volname);
        sb.append(" storage volume");
        return sb.toString();
    }
}
