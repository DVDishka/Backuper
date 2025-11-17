package ru.dvdishka.backuper.backend.storage.util;

public interface StorageProgressListener {

    long getCurrentProgress();

    long getMaxProgress();

    void incrementProgress(long value);
}
