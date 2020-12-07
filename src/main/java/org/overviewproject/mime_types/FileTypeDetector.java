package org.overviewproject.mime_types;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Detects the mime type of files (ideally based on marker in file content)
 */
public class FileTypeDetector extends java.nio.file.spi.FileTypeDetector {

    private final MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();
    
    @Override
    public String probeContentType(Path path) throws IOException {
        try {
            return mimeTypeDetector.detectMimeType(path.toFile());
        } catch (GetBytesException ex) {
            throw new IOException(ex);
        }
    }
}