package org.overviewproject.mime_types;


import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MimeTypeDetectorTest {
    private final MimeTypeDetector detector = new MimeTypeDetector();

    @Test
    void emptyStringNullSafe() throws GetBytesException {
        InputStream is = getTestResource("empty");

        assertEquals("application/octet-stream", detector.detectMimeType("", is));
    }

    @Test
    void globLiteral() {
        assertEquals("text/x-makefile", detectMimeType("makefile"));
        assertEquals("text/x-makefile", detectMimeType("Makefile"));
    }

    @Test
    void globExtension() {
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
        assertEquals("text/javascript", detectMimeType("not-application-javascript.js"));
    }

    @Test
    void globFilename() {
        assertEquals("text/x-readme", detectMimeType("README"));
        assertEquals("text/x-readme", detectMimeType("READMEFILE"));
        assertEquals("text/x-readme", detectMimeType("READMEanim3"));
        assertEquals("text/x-log", detectMimeType("README.log"));
        assertEquals("text/x-readme", detectMimeType("README.file"));
    }

    @Test
    void octetStream() {
        assertEquals("application/octet-stream", detectMimeType("empty"));
        assertEquals("application/octet-stream", detectMimeType("octet-stream"));
    }

    @Test
    void multipleExtensions() {
        assertEquals("application/java-archive", detectMimeType("e.1.3.jar"));
    }

    @Test
    void magic() {
        assertEquals("application/xml", detectMimeType("e[xml]"));
    }

    @Test
    void magicIndent() {
        // "a\n" will match image/x-pcx if rules are treated as OR instead of AND.
        assertEquals("text/plain", detectMimeType("a"));
    }

    @Test
    void text() {
        assertEquals("text/plain", detectMimeType("plaintext"));
        assertEquals("text/plain", detectMimeType("textfiles/utf-8"));
        assertEquals("text/plain", detectMimeType("textfiles/windows-1255"));
    }

    @Test
    void matchletSearchIsThorough() {
        // returns application/octet-stream if the entire matchlet range is not searched
        assertEquals("application/x-matroska", detectMimeType("mkv-video-header"));
    }

    @Test
    void respectsMagicFileOrdering() {
        // MIME candidates are found in this order for this file: "application/ogg", "audio/ogg", "video/ogg" (note, the superclass comes
        // first)
        // however, if a HashSet is used internally, the iterable order will be something like: "audio/ogg", "application/ogg", "video/ogg"
        // and "audio/ogg" is returned for video as well as audio (not good)
        assertEquals("application/ogg", detectMimeType("ogv-video-header"));
    }

    @Test
    void mPEG4v1() {
        // ISO Media, MP4 v1 [ISO 14496-1:ch13] - new in shared-mime-info-1.13.1
        assertEquals("video/mp4", detectMimeType("mp4v1-video-header"));
    }

    @Test
    void mPEG4v2() {
        // ISO Media, MP4 v2 [ISO 14496-14] - included in shared-mime-info
        assertEquals("video/mp4", detectMimeType("mp4v2-video-header"));
    }

    private String detectMimeType(String resourceName) {
        try (InputStream is = getTestResource(resourceName)) {
            return detector.detectMimeType(resourceName, is);
        } catch (GetBytesException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream getTestResource(String resourceName) {
        return getClass().getResourceAsStream("/test/" + resourceName);
    }

    @Test
    void emptyFile() throws IOException, GetBytesException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();
        assertEquals("application/octet-stream", detector.detectMimeType(f));
    }

    @Test
    void file() throws IOException, GetBytesException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();

        try (FileWriter fw = new FileWriter(f)) {
            fw.append("foo bar baz");
        }
        assertEquals("text/plain", detector.detectMimeType(f));
    }

    @Test
    void path() throws IOException, GetBytesException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();

        try (FileWriter fw = new FileWriter(f)) {
            fw.append("foo bar baz");
        }
        assertEquals("text/plain", detector.detectMimeType(f.toPath()));
    }

    @Test
    void callback() throws GetBytesException {
        Callable<byte[]> getBytes = () -> "foo bar baz".getBytes(StandardCharsets.UTF_8);

        assertEquals("text/plain", detector.detectMimeType("mime-type-test.weird", getBytes));
    }

    @Test
    void async() throws InterruptedException, ExecutionException {
        byte[] bytes = "foo bar baz".getBytes(StandardCharsets.UTF_8);

        Supplier<CompletionStage<byte[]>> getBytes = () -> CompletableFuture.completedFuture(bytes);

        assertEquals("text/plain", detector.detectMimeTypeAsync("mime-type-test.weird", getBytes).toCompletableFuture().get());
    }

    @Test
    void asyncGetBytesException() throws InterruptedException {
        Supplier<CompletionStage<byte[]>> getBytes = () -> {
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            future.completeExceptionally(new GetBytesException(new IOException("oops")));
            return future;
        };

        Exception e = assertThrows(ExecutionException.class,
                () -> detector.detectMimeTypeAsync("mime-type-test.weird", getBytes).toCompletableFuture().get());
        assertEquals(GetBytesException.class, e.getCause().getClass());
    }

    @Test
    void pathAsync() throws ExecutionException, IOException, InterruptedException {
        File f = File.createTempFile("mime-type-test", ".weird");
        f.deleteOnExit();

        try (FileWriter fw = new FileWriter(f)) {
            fw.append("foo bar baz");
        }
        String actual = detector.detectMimeTypeAsync(f.toPath()).toCompletableFuture().get();
        assertEquals("text/plain", actual);
    }
}
