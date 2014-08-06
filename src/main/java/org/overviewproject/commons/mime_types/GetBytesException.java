package org.overviewproject.commons.mime_types;

/**
 * Thrown by MimeTypeDetector when it cannot read bytes.
 * 
 * @author Adam Hooper <adam@adamhooper.com>
 */
public class GetBytesException extends Exception {
	private static final long serialVersionUID = 1L;

	public GetBytesException(Throwable cause) {
		super(cause);
	}
}
