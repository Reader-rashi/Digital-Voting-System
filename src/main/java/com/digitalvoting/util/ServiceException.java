package com.digitalvoting.util;

/** A problem whose message is safe to show to the user. */
public class ServiceException extends Exception {
    public ServiceException(String message) { super(message); }
    public ServiceException(String message, Throwable cause) { super(message, cause); }
}
