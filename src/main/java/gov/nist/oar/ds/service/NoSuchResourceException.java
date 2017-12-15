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
package gov.nist.oar.ds.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * an exception indicating that no such Resource (given by some identifier) can be found
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason="Resource not found")
public class NoSuchResourceException extends RuntimeException {

    /**
     * the identifier use to locate the resource
     */
    public final String id;

    /**
     * create the exception
     * @param id     the identifier used to locate the resource
     */
    public NoSuchResourceException(String id=null) {
        super("No such data resource found" + ((id != null) ? (" with id=" + id) : ""));
        this.id = id;
    }

}
