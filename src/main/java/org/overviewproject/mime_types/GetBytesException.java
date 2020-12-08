package org.overviewproject.mime_types;

/**
 * Thrown by MimeTypeDetector when it cannot read bytes.
 *
 * @author Adam Hooper &lt;adam@adamhooper.com&gt;
 */
public class GetBytesException extends Exception {
    private static final long serialVersionUID = 1L;

    public GetBytesException(Throwable cause) {
        super(cause);
    }
}
