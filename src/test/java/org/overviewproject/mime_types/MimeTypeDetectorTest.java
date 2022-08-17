package org.overviewproject.mime_types;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class MimeTypeDetectorTest extends TestCase {
    private final MimeTypeDetector detector = new MimeTypeDetector();

    public void testGlobLiteral() {
        assertEquals("text/x-makefile", detectMimeType("makefile"));
        assertEquals("text/x-makefile", detectMimeType("Makefile"));
    }

    public void testGlobExtension() {
        // These files don't exist; if the test throws NullPointerException
        // that's because we need to read the file. We shouldn't need to read
        // these files because the extension should be enough.
        assertEquals("text/plain", detectMimeType("abc.txt"));
        assertEquals("image/x-win-bitmap", detectMimeType("x.cur"));
        assertEquals("application/vnd.ms-tnef", detectMimeType("winmail.dat"));
        assertEquals("video/x-anim", detectMimeType("abc.anim5"));
        assertEquals("video/x-anim", detectMimeType("abc.animj"));
        assertEquals("application/x-compress", detectMimeType("README.Z"));
        assertEquals("application/vnd.ms-outlook", detectMimeType("t.pst"));
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

    public void testMagicIndent() {
        // "a\n" will match image/x-pcx if rules are treated as OR instead of AND.
        assertEquals("text/plain", detectMimeType("a"));
    }

    public void testText() {
        assertEquals("text/plain", detectMimeType("plaintext"));
        assertEquals("text/plain", detectMimeType("textfiles/utf-8"));
        assertEquals("text/plain", detectMimeType("textfiles/windows-1255"));
    }

    public void testMatchletSearchIsThorough() {
        // returns application/octet-stream if the entire matchlet range is not searched
        assertEquals("application/x-matroska", detectMimeType("mkv-video-header"));
    }

    public void testRespectsMagicFileOrdering() {
        // MIME candidates are found in this order for this file: "application/ogg", "audio/ogg", "video/ogg" (note, the superclass comes first)
        // however, if a HashSet is used internally, the iterable order will be something like: "audio/ogg", "application/ogg", "video/ogg"
        // and "audio/ogg" is returned for video as well as audio (not good)
        assertEquals("application/ogg", detectMimeType("ogv-video-header"));
    }

    public void testMPEG4v1() {
        // ISO Media, MP4 v1 [ISO 14496-1:ch13] - new in shared-mime-info-1.13.1
        assertEquals("video/mp4", detectMimeType("mp4v1-video-header"));
    }

    public void testMPEG4v2() {
        // ISO Media, MP4 v2 [ISO 14496-14] - included in shared-mime-info
        assertEquals("video/mp4", detectMimeType("mp4v2-video-header"));
    }

    private String detectMimeType(String resourceName) {
        try (InputStream is = getClass().getResourceAsStream("/test/" + resourceName)) {
            return detector.detectMimeType(resourceName, is);
        } catch (GetBytesException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void testEmptyFile() throws IOException, GetBytesException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();
        assertEquals("application/octet-stream", detector.detectMimeType(f));
    }

    public void testFile() throws IOException, GetBytesException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();

        try (FileWriter fw = new FileWriter(f)) {
            fw.append("foo bar baz");
        }
        assertEquals("text/plain", detector.detectMimeType(f));
    }

    public void testPath() throws IOException, GetBytesException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();

        try (FileWriter fw = new FileWriter(f)) {
            fw.append("foo bar baz");
        }
        assertEquals("text/plain", detector.detectMimeType(f.toPath()));
    }

    public void testCallback() throws GetBytesException {
        Callable<byte[]> getBytes = () -> "foo bar baz".getBytes(StandardCharsets.UTF_8);

        assertEquals("text/plain", detector.detectMimeType("mime-type-test.weird", getBytes));
    }

    public void testAsync() throws InterruptedException, ExecutionException {
        byte[] bytes = "foo bar baz".getBytes(StandardCharsets.UTF_8);

        Supplier<CompletionStage<byte[]>> getBytes = () -> CompletableFuture.completedFuture(bytes);

        assertEquals("text/plain", detector.detectMimeTypeAsync("mime-type-test.weird", getBytes).toCompletableFuture().get());
    }

    public void testAsyncGetBytesException() throws InterruptedException {
        Supplier<CompletionStage<byte[]>> getBytes = () -> {
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            future.completeExceptionally(new GetBytesException(new IOException("oops")));
            return future;
        };

        try {
            detector.detectMimeTypeAsync("mime-type-test.weird", getBytes).toCompletableFuture().get();
            fail("That should have thrown an exception");
        } catch (ExecutionException ex) {
            assertEquals(GetBytesException.class, ex.getCause().getClass());
        }
    }

    public void testPathAsync() throws ExecutionException, IOException, InterruptedException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();

        try (FileWriter fw = new FileWriter(f)) {
            fw.append("foo bar baz");
        }
        String actual = detector.detectMimeTypeAsync(f.toPath()).toCompletableFuture().get();
        assertEquals("text/plain", actual);
    }
}
