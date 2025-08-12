package ru.dvdishka.backuper.backend.task;

import org.apache.commons.net.ftp.FTPClient;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.FtpUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class FtpAddLocalDirToZipTask extends BaseAddLocalDirToZipTask {

    private final String targetZipPath;
    private FTPClient ftpClient;

    public FtpAddLocalDirToZipTask(List<File> sourceDirsToAdd, String targetZipPath, boolean createRootDirInTargetZIP,
                                   boolean forceExcludedDirs) {
        super(sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs);
        this.targetZipPath = targetZipPath;
    }

    @Override
    protected void run() throws IOException {

        if (!cancelled) {
            ftpClient = FtpUtils.getClient();
            if (ftpClient == null) {
                return;
            }
        }

        try (OutputStream outputStream = ftpClient.storeFileStream(targetZipPath);
             ZipOutputStream targetZipOutputStream = new ZipOutputStream(outputStream)) {
            for (File sourceDirToAdd : sourceDirsToAdd) {

                if (cancelled) {
                    break;
                }

                if (createRootDirInTargetZIP) {
                    File parent = sourceDirToAdd.getParentFile();
                    parent = parent == null ? new File("") : parent;
                    addDirToZip(targetZipOutputStream, sourceDirToAdd, parent.toPath());
                } else {
                    addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                }
            }

        } finally {
            ftpClient.disconnect();
        }
    }

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getFtpConfig().getZipCompressionLevel();
    }
}
