package org.overviewproject.mime_types;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier; // Callable throws checked exceptions, which are incompatible with CompletionStage
import java.util.regex.Pattern;

import org.mozilla.universalchardet.UniversalDetector;

/**
 * Determines the MIME type of files.
 *
 * <p>
 * The Opendesktop shared mime database contains glob rules and magic number
 * lookup information to enable applications to detect the mime types of files.
 * </p>
 *
 * <p>
 * For a complete description of the information contained in this file please
 * see <a href="http://standards.freedesktop.org/shared-mime-info-spec/shared-mime-info-spec-latest.html">shared-mime-info-spec</a>
 * </p>
 *
 * @author Steven McArdle
 * @author Adam Hooper &lt;adam@adamhooper.com&gt;
 */
public class MimeTypeDetector {
    private static final String MIME_CACHE = "/mime.cache";

    /** MimeCache file content, as a ByteBuffer. */
    private final ByteBuffer content;

    /** MimeCache file content, as an array of bytes. */
    private final byte[] contentBytes;

    /**
     * Creates a new MimeTypeDetector.
     *
     * <p>
     * This class is thread-safe. You should create it once and reuse it, as
     * it allocates a bit of memory (hundreds of kilobytes) on load.
     * </p>
     */
    public MimeTypeDetector() {
        try (InputStream is = getClass().getResourceAsStream(MIME_CACHE)) {
            assert is != null;
            contentBytes = inputStreamToByteArray(is);
            content = ByteBuffer.wrap(contentBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] inputStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int nRead;
        while ((nRead = is.read(buf)) != -1) {
            baos.write(buf, 0, nRead);
        }
        return baos.toByteArray();
    }

    /**
     * Waits for a CompletionStage, for non-async methods.
     */
    private static String unwrapFutureString(CompletionStage<String> future) throws GetBytesException {
        try {
            return future.toCompletableFuture().get();
        } catch (Throwable e) {
            if (e.getCause() instanceof GetBytesException) {
                throw (GetBytesException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * Detects a MIME type from a filename and bytes.
     *
     * <p>
     * This method follows the Shared Mime Info database's
     * <a href="http://standards.freedesktop.org/shared-mime-info-spec/shared-mime-info-spec-latest.html#idm140625828606432">Recommended
     * Checking Order</a>. The only difference: it tests for {@code text/plain}
     * thoroughly, both by scanning more of the file and by supporting many
     * character sets.
     * </p>
     *
     * <p>
     * getBytes() is a {@link java.util.concurrent.Callable} because it may or
     * may not be needed. A file named {@code README} will always be detected
     * as {@code text/plain}, for instance; a file named {@code foo.doc} will
     * need a magic-number check because it may be plain text or it may be a
     * Word document.
     * </p>
     *
     * <p>
     * If you are creating your own {@code getBytesAsync} method, ensure its return
     * value is unpadded. (Use {@link java.util.Arrays#copyOf(byte[],int)} to truncate
     * it.) It needn't be any longer than {@link #getMaxGetBytesLength()}
     * bytes.
     * </p>
     *
     * <p>
     * The CompletionStage may return a {@link ExecutionException} which is caused
     * by a {@link GetBytesException}. (That, in turn, will wrap a
     * {@link IOException} or other exception that prevented getBytesAsync() from
     * working.
     * </p>
     *
     * @param filename Filename. To skip filename globbing, pass {@code ""}
     * @param getBytesAsync Supplier that eventually returns a {@code byte[]}
     * @return Eventual MIME type String, falling back to {@code "application/octet-stream"}
     */
    public CompletionStage<String> detectMimeTypeAsync(String filename, Supplier<CompletionStage<byte[]>> getBytesAsync) {
        Set<WeightedMimeType> weightedMimeTypes = filenameToWmts(filename);
        Set<String> globMimeTypes = findBestMimeTypes(weightedMimeTypes);

        if (globMimeTypes.size() == 1) {
            return CompletableFuture.completedFuture(globMimeTypes.iterator().next());
        }

        return getBytesAsync.get()
            .thenApply(bytes -> {
                for (String magicMimeType : bytesToMimeTypes(bytes)) {
                    if (globMimeTypes.isEmpty()) {
                        return magicMimeType;
                    } else {
                        for (String globMimeType : globMimeTypes) {
                            if (isMimeTypeEqualOrSubclass(globMimeType, magicMimeType)) {
                                return globMimeType;
                            }
                        }
                    }
                }

                if (isText(bytes)) {
                    return "text/plain";
                }

                return "application/octet-stream";
            })
            .exceptionally(ex -> { throw new CompletionException(new GetBytesException(ex)); });
    }

    /**
     * Synchronously detects a MIME type from a filename and bytes.
     *
     * <p>
     * This method follows the Shared Mime Info database's
     * <a href="http://standards.freedesktop.org/shared-mime-info-spec/shared-mime-info-spec-latest.html#idm140625828606432">Recommended
     * Checking Order</a>. The only difference: it tests for {@code text/plain}
     * thoroughly, both by scanning more of the file and by supporting many
     * character sets.
     * </p>
     *
     * <p>
     * getBytes() is a {@link java.util.concurrent.Callable} because it may or
     * may not be needed. A file named {@code README} will always be detected
     * as {@code text/plain}, for instance; a file named {@code foo.doc} will
     * need a magic-number check because it may be plain text or it may be a
     * Word document.
     * </p>
     *
     * <p>
     * If you are creating your own {@code getBytes} method, ensure its return
     * value is unpadded. (Use {@link java.util.Arrays#copyOf(byte[],int)} to truncate
     * it.) It needn't be any longer than {@link #getMaxGetBytesLength()}
     * bytes.
     * </p>
     *
     * @param filename Filename. To skip filename globbing, pass {@code ""}
     * @param getBytes Callable that returns a {@code byte[]}
     * @return a MIME type such as {@code "text/plain"}
     * @throws GetBytesException if {@code getBytes.call()} throws an Exception
     */
    public String detectMimeType(String filename, Callable<byte[]> getBytes) throws GetBytesException {
        Supplier<CompletionStage<byte[]>> supplier = () -> {
            try {
                return CompletableFuture.completedFuture(getBytes.call());
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        };
        return unwrapFutureString(detectMimeTypeAsync(filename, supplier));
    }

    /**
     * Determines the MIME type of file with a given input stream.
     *
     * <p>
     * The InputStream must exist. It must point to the beginning of the file
     * contents. And {@link java.io.InputStream#markSupported()} must return
     * {@code true}. When in doubt, pass a {@link java.io.BufferedInputStream}.
     * </p>
     *
     * @param filename Name of file. To skip filename globbing, pass {@code ""}
     * @param is InputStream that supports mark and reset.
     * @return a MIME type such as {@code "text/plain"}
     * @throws GetBytesException if marking, reading or resetting the InputStream fails.
     * @see #detectMimeType(String, Callable)
     */
    public String detectMimeType(String filename, final InputStream is) throws GetBytesException {
        Callable<byte[]> getBytes = () -> inputStreamToFirstBytes(is);

        return detectMimeType(filename, getBytes);
    }

    /**
     * Determines the MIME type of file.
     *
     * <p>
     * The file must exist and be readable.
     * </p>
     *
     * @param file A file that exists and is readable
     * @return a MIME type such as {@code "text/plain"}
     * @throws GetBytesException if reading the file fails.
     * @see #detectMimeType(String, Callable)
     */
    public String detectMimeType(final File file) throws GetBytesException {
        return detectMimeType(file.toPath());
    }

    /**
     * Determines the MIME type of file.
     *
     * <p>
     * The file must exist and be readable.
     * </p>
     *
     * @param path A file that exists and is readable
     * @return a MIME type such as {@code "text/plain"}
     * @throws GetBytesException if reading the file fails.
     * @see #detectMimeType(String, Callable)
     */
    public String detectMimeType(final Path path) throws GetBytesException {
        return unwrapFutureString(detectMimeTypeAsync(path));
    }

    /**
     * Determines the MIME type of file.
     *
     * <p>
     * The file must exist and be readable.
     * </p>
     *
     * <p>
     * The CompletionStage may return a {@link ExecutionException} which is caused
     * by a {@link GetBytesException}. (That, in turn, will wrap a
     * {@link IOException} or other exception that prevented getBytesAsync() from
     * working.
     * </p>
     *
     * @param path A file that exists and is readable
     * @return a MIME type such as {@code "text/plain"}
     * @see #detectMimeType(String, Callable)
     */
    public CompletionStage<String> detectMimeTypeAsync(final Path path) {
        String filename = path.getFileName().toString();

        Supplier<CompletionStage<byte[]>> supplier = () -> {
            final CompletableFuture<byte[]> futureBytes = new CompletableFuture<>();

            AsynchronousFileChannel channel;
            try {
                channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            } catch (IOException e) {
                futureBytes.completeExceptionally(new GetBytesException(e));
                return futureBytes;
            }

            final byte[] wrappedBytes = new byte[getMaxGetBytesLength()];
            final ByteBuffer buf = ByteBuffer.wrap(wrappedBytes);
            channel.read(buf, 0, futureBytes, new CompletionHandler<Integer, CompletableFuture<byte[]>>() {
                @Override public void completed(Integer nBytes, CompletableFuture<byte[]> f) {
                    if (nBytes == wrappedBytes.length) {
                        f.complete(wrappedBytes);
                    } else {
                        if (nBytes == -1) nBytes = 0; // handle empty file
                        f.complete(Arrays.copyOf(wrappedBytes, nBytes));
                    }
                }

                @Override public void failed(Throwable exc, CompletableFuture<byte[]> f) {
                    f.completeExceptionally(new GetBytesException(exc));
                }
            });
            return futureBytes;
        };

        return detectMimeTypeAsync(filename, supplier);
    }

    private boolean isText(byte[] bytes) {
        // 0 bytes is application/octet-stream, like Tika
        // https://issues.apache.org/jira/browse/TIKA-483
        return bytes.length > 0 && isEncodedText(bytes);
    }

    private boolean isEncodedText(byte[] bytes) {
        UniversalDetector ud = new UniversalDetector(null);
        ud.handleData(bytes, 0, bytes.length);
        ud.dataEnd();
        return ud.getDetectedCharset() != null;
    }

    private byte[] inputStreamToFirstBytes(InputStream is) throws IOException {
        int extent = getMaxExtents();
        int cur = 0;
        byte[] ret = new byte[extent];

        is.mark(extent); // throws IOException if not supported

        while (cur < extent) {
            int n = is.read(ret, cur, extent - cur);
            if (n == -1) {
                // EOF; return a correctly-sized Array
                is.reset();
                return Arrays.copyOf(ret, cur);
            } else {
                cur += n;
            }
        }

        is.reset();

        return ret;
    }

    private Iterable<String> bytesToMimeTypes(byte[] data) {
        Set<String> mimeTypes = new LinkedHashSet<>();

        int listOffset = getMagicListOffset();
        int numEntries = content.getInt(listOffset);
        int offset = content.getInt(listOffset + 8);

        for (int i = 0; i < numEntries; i++) {
            String mimeType = compareToMagicData(offset + (16 * i), data);
            if (mimeType != null) {
                mimeTypes.add(mimeType);
            }
        }

        return mimeTypes;
    }

    private String compareToMagicData(int offset, byte[] data) {
        int nMatchlets = content.getInt(offset + 8);
        int firstMatchletOffset = content.getInt(offset + 12);

        if (matchletMagicCompareOr(nMatchlets, firstMatchletOffset, data)) {
            int mimeOffset = content.getInt(offset + 4);
            return getMimeType(mimeOffset);
        }

        return null;
    }

    /**
     * Returns whether one of the specified matchlets matches the data.
     */
    private boolean matchletMagicCompareOr(int nMatchlets, int firstMatchletOffset, byte[] data) {
        for (int i = 0, matchletOffset = firstMatchletOffset; i < nMatchlets; i++, matchletOffset += 32) {
            if (matchletMagicCompare(matchletOffset, data)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if subarrays are equal.
     */
    private boolean subArraysEqual(byte[] a, int aStart, byte[] b, int bStart, int len) {
        for (int i = aStart, j = bStart; len > 0; i++, j++, len--) {
            if (a[i] != b[j]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if subarrays are equal, with the given mask.
     *
     * <p>
     * The mask must have length {@code len}.
     * </p>
     */
    private boolean subArraysEqualWithMask(byte[] a, int aStart, byte[] b, int bStart, byte[] mask, int maskStart, int len) {
        for (int i = aStart, j = bStart, k = maskStart; len > 0; i++, j++, k++, len--) {
            if ((a[i] & mask[k]) != (b[j] & mask[k])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests whether one matchlet (but not its children) matches data.
     */
    private boolean oneMatchletMagicEquals(int offset, byte[] data) {
        int rangeStart = content.getInt(offset);      // first byte of data for check to start at
        int rangeLength = content.getInt(offset + 4); // last byte of data for check to start at
        int dataLength = content.getInt(offset + 12); // number of bytes in match data/mask
        int dataOffset = content.getInt(offset + 16); // contentBytes offset to the match data
        int maskOffset = content.getInt(offset + 20); // contentBytes offset to the mask

        boolean found = false;

        for (int i = 0; !found && (i <= rangeLength) && (i + rangeStart + dataLength <= data.length); i++) {
            if (maskOffset != 0) {
                found = subArraysEqualWithMask(
                        contentBytes, dataOffset,
                        data, rangeStart + i,
                        contentBytes, maskOffset,
                        dataLength
                );
            } else {
                found = subArraysEqual(
                        contentBytes, dataOffset,
                        data, rangeStart + i,
                        dataLength
                );
            }
        }

        return found;
    }

    /**
     * Returns whether data satisfies the matchlet and its children.
     */
    private boolean matchletMagicCompare(int offset, byte[] data) {
        if (oneMatchletMagicEquals(offset, data)) {
            int nChildren = content.getInt(offset + 24);

            if (nChildren > 0) {
                int firstChildOffset = content.getInt(offset + 28);
                return matchletMagicCompareOr(nChildren, firstChildOffset, data);
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private WeightedMimeType filenameToWmtOrNullByLiteral(String filename) {
        String filenameLower = filename.toLowerCase();
        int listOffset = getLiteralListOffset();
        int numEntries = content.getInt(listOffset);

        int min = 0;
        int max = numEntries - 1;
        while (max >= min) {
            int mid = (min + max) / 2;
            String literal = getString(content.getInt((listOffset + 4) + (12 * mid)));
            int weightAndCaseSensitive = content.getInt((listOffset + 4) + (12 * mid) + 8);
            boolean ignoreCase = (weightAndCaseSensitive & 0x100) == 0;
            int cmp = literal.compareTo(ignoreCase ? filenameLower : filename);
            if (cmp < 0) {
                min = mid + 1;
            } else if (cmp > 0) {
                max = mid - 1;
            } else {
                String mimeType = getMimeType(content.getInt((listOffset + 4) + (12 * mid) + 4));
                int weight = weightAndCaseSensitive & 0xff;
                return new WeightedMimeType(mimeType, literal, weight);
            }
        }

        return null;
    }

    private Set<WeightedMimeType> filenameToWmtsByGlob(String filename) {
        Set<WeightedMimeType> ret = new HashSet<>();

        int listOffset = getGlobListOffset();
        int numEntries = content.getInt(listOffset);

        for (int i = 0; i < numEntries; i++) {
            int offset = content.getInt((listOffset + 4) + (12 * i));

            String rawPattern = getRegex(offset);
            int weightAndIgnoreCase = content.getInt((listOffset + 4) + (12 * i) + 8);
            boolean ignoreCase = (weightAndIgnoreCase & 0x100) == 0;

            Pattern pattern = Pattern.compile(rawPattern, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);

            if (pattern.matcher(filename).matches()) {
                int mimeTypeOffset = content.getInt((listOffset + 4) + (12 * i) + 4);
                String mimeType = getMimeType(mimeTypeOffset);
                int weight = weightAndIgnoreCase & 0xff;
                ret.add(new WeightedMimeType(mimeType, rawPattern, weight));
            }
        }

        return ret;
    }

    private Set<String> findBestMimeTypes(Collection<WeightedMimeType> weightedMimeTypes) {
        // Find top weight
        int bestWeight = 0;
        for (WeightedMimeType wmt : weightedMimeTypes) {
            if (wmt.weight > bestWeight) bestWeight = wmt.weight;
        }

        // bestWeightWmts: filtered for just top weight
        Collection<WeightedMimeType> bestWeightWmts = new ArrayList<>();
        for (WeightedMimeType wmt : weightedMimeTypes) {
            if (wmt.weight == bestWeight) bestWeightWmts.add(wmt);
        }

        // Find the longest pattern length
        int bestPatternLength = 0;
        for (WeightedMimeType wmt : bestWeightWmts) {
            if (wmt.pattern.length() > bestPatternLength) bestPatternLength = wmt.pattern.length();
        }

        // ret: filtered for just top pattern
        Set<String> ret = new HashSet<>();
        for (WeightedMimeType wmt : bestWeightWmts) {
            if (wmt.pattern.length() == bestPatternLength) ret.add(wmt.mimeType);
        }

        return ret;
    }

    private Set<WeightedMimeType> filenameToWmts(String filename) {
        if("".equals(filename) || null == filename){
            return new HashSet<>();
        }

        Set<WeightedMimeType> ret;
        WeightedMimeType wmt;

        if ((wmt = filenameToWmtOrNullByLiteral(filename)) != null) {
            ret = new HashSet<>();
            ret.add(wmt);
            return ret;
        }

        if ((ret = filenameToWmtsOrNullBySuffixAndIgnoreCase(filename, false)) != null) return ret;
        if ((ret = filenameToWmtsOrNullBySuffixAndIgnoreCase(filename, true)) != null) return ret;

        return filenameToWmtsByGlob(filename);
    }

    private Set<WeightedMimeType> filenameToWmtsOrNullBySuffixAndIgnoreCase(String filename, boolean ignoreCase) {
        int listOffset = getReverseSuffixTreeOffset();
        int numEntries = content.getInt(listOffset);
        int offset = content.getInt(listOffset + 4);
        int len = filename.length();

        Set<WeightedMimeType> wmts = new HashSet<>();

        lookupGlobNodeSuffix(filename, numEntries, offset, ignoreCase, len, wmts, new StringBuilder());

        if (wmts.isEmpty()) return null;
        return wmts;
    }

    private void lookupGlobNodeSuffix(String fileName, int numEntries,
            int offset, boolean ignoreCase, int len, Set<WeightedMimeType> mimeTypes,
            StringBuilder pattern) {
        char c = ignoreCase ? fileName.toLowerCase().charAt(len - 1) : fileName.charAt(len - 1);

        if (c == '\0') return;

        int min = 0;
        int max = numEntries - 1;
        while (max >= min && len >= 0) {
            int mid = (min + max) / 2;

            char matchChar = (char) content.getInt(offset + (12 * mid));
            if (matchChar < c) {
                min = mid + 1;
            } else if (matchChar > c) {
                max = mid - 1;
            } else {
                len--;
                int numChildren = content.getInt(offset + (12 * mid) + 4);
                int childOffset = content.getInt(offset + (12 * mid) + 8);
                if (len > 0) {
                    pattern.append(matchChar);
                    lookupGlobNodeSuffix(fileName, numChildren, childOffset,
                            ignoreCase, len, mimeTypes, pattern);
                }
                if (mimeTypes.isEmpty()) {
                    for (int i = 0; i < numChildren; i++) {
                        matchChar = (char) content.getInt(childOffset + (12 * i));
                        if (matchChar != '\0') break;
                        int mimeOffset = content.getInt(childOffset + (12 * i) + 4);
                        int weight = content.getInt(childOffset + (12 * i) + 8);
                        mimeTypes.add(new WeightedMimeType(
                                    getMimeType(mimeOffset),
                                    pattern.toString(),
                                    weight
                        ));
                    }
                }
                return;
            }
        }
    }

    private static class WeightedMimeType {
        String mimeType;
        String pattern;
        int weight;

        WeightedMimeType(String mimeType, String pattern, int weight) {
            this.mimeType = mimeType;
            this.pattern = pattern;
            this.weight = weight;
        }

        public String toString() {
            return this.mimeType + "(" + this.pattern + ", " + this.weight + ")";
        }
    }

    private int getMaxExtents() {
        return content.getInt(getMagicListOffset() + 4);
    }

    /**
     * Returns the number of bytes the magic number sniffers may read.
     *
     * <p>
     * If you are crafting your own getBytes() method, you may use this as a
     * hint. getBytes() may return an Array with any number of bytes you like,
     * but MimeTypeDetector will not read more than this many.
     * </p>
     *
     * @return The most bytes getBytes() could ever possibly need to return.
     */
    public int getMaxGetBytesLength() {
        return getMaxExtents();
    }

    private String aliasLookup(String alias) {
        int aliasListOffset = getAliasListOffset();
        int min = 0;
        int max = content.getInt(aliasListOffset) - 1;

        while (max >= min) {
            int mid = (min + max) / 2;

            int aliasOffset = content.getInt((aliasListOffset + 4) + (mid * 8));
            int mimeOffset = content.getInt((aliasListOffset + 4) + (mid * 8) + 4);

            int cmp = getMimeType(aliasOffset).compareTo(alias);
            if (cmp < 0) {
                min = mid + 1;
            } else if (cmp > 0) {
                max = mid - 1;
            } else {
                return getMimeType(mimeOffset);
            }
        }
        return null;
    }

    private String unaliasMimeType(String mimeType) {
        String lookup = aliasLookup(mimeType);
        return lookup == null ? mimeType : lookup;
    }

    private boolean isMimeTypeEqualOrSubclass(String child, String parent) {
        String uChild = unaliasMimeType(child);
        String uParent = unaliasMimeType(parent);
        String uChildMediaType = uChild.substring(0, uChild.indexOf('/'));
        String uParentMediaType = uParent.substring(0, uParent.indexOf('/'));

        if (uChild.equals(uParent)) return true;
        if (uParent.equals("text/plain") && child.startsWith("text/")) return true;
        if (uParent.equals("application/octet-stream")) return true;
        if (uParent.endsWith("/*") && uChildMediaType.equals(uParentMediaType)) return true;

        int parentListOffset = getParentListOffset();
        int numParents = content.getInt(parentListOffset);
        int min = 0;
        int max = numParents - 1;
        while (max >= min) {
            int med = (min + max) / 2;
            int offset = content.getInt((parentListOffset + 4) + (8 * med));
            String parentMime = getMimeType(offset);
            int cmp = parentMime.compareTo(uChild);
            if (cmp < 0) {
                min = med + 1;
            } else if (cmp > 0) {
                max = med - 1;
            } else {
                offset = content.getInt((parentListOffset + 4) + (8 * med) + 4);
                int _numParents = content.getInt(offset);
                for (int i = 0; i < _numParents; i++) {
                    int parentOffset = content.getInt((offset + 4) + (4 * i));
                    if (isMimeTypeEqualOrSubclass(getMimeType(parentOffset), uParent)) {
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    private int getMagicListOffset() {
        return content.getInt(24);
    }

    private int getGlobListOffset() {
        return content.getInt(20);
    }

    private int getReverseSuffixTreeOffset() {
        return content.getInt(16);
    }

    private int getLiteralListOffset() {
        return content.getInt(12);
    }

    private int getParentListOffset() {
        return content.getInt(8);
    }

    private int getAliasListOffset() {
        return content.getInt(4);
    }

    private String getMimeType(int offset) {
        return getString(offset);
    }

    private String getString(int offset) {
        return getStringOrRegex(offset, false);
    }

    private String getRegex(int offset) {
        return getStringOrRegex(offset, true);
    }

    private String getStringOrRegex(int offset, boolean regularExpression) {
        StringBuilder buf = new StringBuilder();
        if (regularExpression) buf.append('^');
        byte b;
        while ((b = content.get(offset)) != '\0') {
            if (regularExpression) {
                switch (b) {
                case '.':
                    buf.append('\\');
                    break;
                case '*':
                case '+':
                case '?':
                    buf.append('.');
                }
            }
            buf.append((char) b);

            offset++;
        }

        if (regularExpression) buf.append('$');

        return buf.toString();
    }
}
