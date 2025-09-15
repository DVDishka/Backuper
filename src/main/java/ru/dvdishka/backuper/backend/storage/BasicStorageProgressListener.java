package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

public class BasicStorageProgressListener implements StorageProgressListener {

    AtomicLong progress = new AtomicLong(0);
    @Setter
    long maxProgress = 0;

    @Override
    public long getCurrentProgress() {
        return progress.get();
    }

    @Override
    public long getMaxProgress() {
        return maxProgress;
    }

    @Override
    public void incrementProgress(long value) {
        progress.addAndGet(value);
    }
}
