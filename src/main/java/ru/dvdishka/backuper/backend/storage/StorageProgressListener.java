package ru.dvdishka.backuper.backend.storage;

public interface StorageProgressListener {

    long getCurrentProgress();

    long getMaxProgress();

    void incrementProgress(long value);
}
