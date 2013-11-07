package org.gk.gpml;

/**
 * Exception that occurs during import, export, save or load of a Pathway.
 */
public class ConverterException extends Exception {


    public ConverterException(String msg)
    {
        super(msg);
    }

    public ConverterException(Exception e)
    {
        super(e.getClass() + ": " + e.getMessage(), e);
        setStackTrace(e.getStackTrace());
    }


}
