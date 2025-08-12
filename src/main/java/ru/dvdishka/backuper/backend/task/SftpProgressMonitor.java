package ru.dvdishka.backuper.backend.task;

class SftpProgressMonitor implements com.jcraft.jsch.SftpProgressMonitor {

    private long currentProgress = 0;
    private long maxProgress = 0;

    @Override
    public void init(int operationCode, String sourceDir, String destDir, long maxProgress) {
        this.maxProgress = maxProgress;
    }

    @Override
    public boolean count(long progress) {
        currentProgress += progress;
        return true;
    }

    @Override
    public void end() {
    }

    public long getCurrentProgress() {
        return currentProgress;
    }

    public long getMaxProgress() {
        return maxProgress;
    }
}
