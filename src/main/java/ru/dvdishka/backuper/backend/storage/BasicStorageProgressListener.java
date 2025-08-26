package ru.dvdishka.backuper.backend.storage;

import java.util.concurrent.atomic.AtomicLong;

public class BasicStorageProgressListener implements StorageProgressListener {

    AtomicLong progress = new AtomicLong(0);
    long maxProgress = 0;

    @Override
    public long getCurrentProgress() {
        return progress.get();
    }

    @Override
    public long getMaxProgress() {
        return 0;
    }

    @Override
    public void incrementProgress(long value) {
        progress.addAndGet(value);
    }

    public void setMaxProgress(long maxProgress) {
        this.maxProgress = maxProgress;
    }
}
