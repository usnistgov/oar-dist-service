package gov.nist.oar.distrib.cachemgr.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * An {@link InputStream} implementation that returns the same byte over and
 * over up to a specified number of times. This is useful for testing the behavior 
 * of classes that read from an {@link InputStream} without having to create 
 * a massive file or other data source.
 * This is to simulate a large input stream that returns the same byte over and
 * over.
 */
public class LargeInputStream extends InputStream {
    private final long totalSize;
    private long bytesRead = 0;
    private final byte data;

    /**
     * Creates a new {@link LargeInputStream} that will return the byte
     * <code>data</code> up to <code>totalSize</code> times.
     * 
     * @param totalSize the total number of times to return the byte
     * @param data      the byte to return
     */
    public LargeInputStream(long totalSize, byte data) {
        this.totalSize = totalSize;
        this.data = data;
    }

    /**
     * Reads the next byte of data from the input stream.
     *
     * @return the next byte of data, or -1 if the end of the stream is reached
     * @throws IOException if an I/O error occurs
     */

    @Override
    public int read() throws IOException {
        if (bytesRead >= totalSize) {
            return -1;
        }
        bytesRead++;
        return data & 0xff;
    }

    /**
     * Reads up to <code>len</code> bytes of data from this input stream into an
     * array of bytes.
     * If the end of the stream is reached before <code>len</code> bytes have been
     * read, the remainder
     * of the array is left unfilled.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in array <code>b</code> at which the data is
     *            written.
     * @param len the maximum number of bytes to read.
     * @return the total number of bytes read into the buffer, or -1 if there is no
     *         more data
     *         because the end of the stream has been reached.
     * @exception IOException if an I/O error occurs.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bytesRead >= totalSize) {
            return -1;
        }
        // Calculate how many bytes remain to be read
        int bytesRemaining = (int) Math.min(len, totalSize - bytesRead);
        Arrays.fill(b, off, off + bytesRemaining, data);
        bytesRead += bytesRemaining;
        return bytesRemaining;
    }

    /**
     * Skips over and discards <code>n</code> bytes of data from this
     * input stream. The <code>skip</code> method may, for a variety of
     * reasons, end up skipping over some smaller number of bytes,
     * possibly <code>0</code>. This may result from any of a number of
     * conditions; reaching end of file before <code>n</code> bytes have been
     * skipped is only one possibility. The actual number of bytes skipped
     * is returned. If <code>n</code> is negative, no bytes are skipped. The
     * <code>skip</code> method never throws an <code>EOFException</code>.
     * The end of the stream is reached when the total number of bytes
     * skipped is equal to the total number of bytes that were ever present
     * in the stream.
     *
     * @param n the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     * @exception IOException if an I/O error occurs.
     */
    @Override
    public long skip(long n) throws IOException {
        long k = Math.min(n, totalSize - bytesRead);
        bytesRead += k;
        return k;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. The next invocation might be
     * the same thread or another thread. A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     *
     * Note that while some implementations of {@code InputStream} will block until
     * some data is
     * available, this implementation will not block and will return 0 if the end of
     * the stream has
     * been reached.
     *
     * @return an estimate of the number of bytes that can be read (or
     *         skipped over) from this input stream without blocking.
     * @exception IOException if an I/O error occurs.
     */
    @Override
    public int available() throws IOException {
        return (int) Math.min(Integer.MAX_VALUE, totalSize - bytesRead);
    }
}
