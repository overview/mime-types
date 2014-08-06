package org.overviewproject.mime_types;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;

import junit.framework.TestCase;

public class MimeTypeDetectorTest extends TestCase {
	private MimeTypeDetector detector = new MimeTypeDetector();
	
	public void testGlobLiteral() {
		assertEquals("text/x-makefile", detectMimeType("makefile"));
		assertEquals("text/x-makefile", detectMimeType("Makefile"));
	}
	
	public void testGlobExtension() {
		assertEquals("text/plain", detectMimeType("abc.txt"));
		assertEquals("image/x-win-bitmap", detectMimeType("x.cur"));
		assertEquals("application/vnd.ms-tnef", detectMimeType("winmail.dat"));
		assertEquals("text/x-troff-mm", detectMimeType("abc.mm"));
		assertEquals("video/x-anim", detectMimeType("abc.anim5"));
		assertEquals("video/x-anim", detectMimeType("abc.animj"));
		assertEquals("application/x-compress", detectMimeType("README.Z"));
	}
	
	public void testGlobFilename() {
		assertEquals("text/x-readme", detectMimeType("README"));
		assertEquals("text/x-readme", detectMimeType("READMEFILE"));
		assertEquals("text/x-readme", detectMimeType("READMEanim3"));
		assertEquals("text/x-log", detectMimeType("README.log"));
		assertEquals("text/x-readme", detectMimeType("README.file"));
	}
	
	public void testOctetStream() {
		assertEquals("application/octet-stream", detectMimeType("empty"));
		assertEquals("application/octet-stream", detectMimeType("octet-stream"));
	}
	
	public void testMultipleExtensions() {
		assertEquals("application/x-java-archive", detectMimeType("e.1.3.jar"));
	}
	
	public void testMagic() {
		assertEquals("application/xml", detectMimeType("e[xml]"));
	}
	
	public void testText() {
		assertEquals("text/plain", detectMimeType("plaintext"));
		assertEquals("text/plain", detectMimeType("textfiles/utf-8"));
		assertEquals("text/plain", detectMimeType("textfiles/windows-1255"));
	}
	
	private String detectMimeType(String resourceName) {
		try (InputStream is = getClass().getResourceAsStream("/test/" + resourceName)) {
			return detector.detectMimeType(resourceName, is);
		} catch (GetBytesException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void testFile() throws IOException, GetBytesException {
		File f = File.createTempFile("mime-type-test", ".weird");
		f.deleteOnExit();
		
		try (FileWriter fw = new FileWriter(f)) {
			fw.append("foo bar baz");
		}
		assertEquals("text/plain", detector.detectMimeType(f));
	}
	
	public void testCallback() throws GetBytesException {
		Callable<byte[]> getBytes = new Callable<byte[]>() {
			public byte[] call() throws UnsupportedEncodingException {
				return "foo bar baz".getBytes("utf-8");
			}
		};
		
		assertEquals("text/plain", detector.detectMimeType("mime-type-test.weird", getBytes));
	}
}
