package ru.dvdishka.backuper.backend.storage.util;

import java.io.IOException;
import java.io.InputStream;

/***\
 * Closes original input stream on close() method call
 */
public class StorageProgressInputStream extends InputStream {

    private final InputStream inputStream;
    private final StorageProgressListener progressListener;

    public StorageProgressInputStream(InputStream inputStream, StorageProgressListener progressListener) {
        this.inputStream = inputStream;
        this.progressListener = progressListener;
    }

    @Override
    public int read() throws IOException {
        int result = inputStream.read();
        if (result != -1) {
            progressListener.incrementProgress(1);
        }
        return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int bytesRead = inputStream.read(b);
        if (bytesRead > 0) {
            progressListener.incrementProgress(bytesRead);
        }
        return bytesRead;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = inputStream.read(b, off, len);
        if (bytesRead > 0) {
            progressListener.incrementProgress(bytesRead);
        }
        return bytesRead;
    }

    @Override
    public void close() throws IOException {
        super.close();
        inputStream.close();
    }
}
