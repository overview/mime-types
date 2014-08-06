package org.overviewproject.commons.mime_types;

import java.io.File;

public class App 
{
    public static void main(String[] args) throws GetBytesException
    {
    	if (args.length < 1) {
    		System.err.println("Usage: java -jar MimeTypeDetector.jar FILE1 [FILE2 ... ]");
    		System.exit(1);
    	}
    	
    	MimeTypeDetector detector = new MimeTypeDetector();
    	
    	for (String filename : args) {
    		File f = new File(filename);
    		if (!f.exists()) {
    			System.out.println(filename + ": [does not exist]");
    		}
			System.out.println(filename+ ": " + detector.detectMimeType(f));
    	}
    }
}
