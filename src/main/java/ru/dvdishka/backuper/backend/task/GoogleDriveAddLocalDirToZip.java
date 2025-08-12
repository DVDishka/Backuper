package ru.dvdishka.backuper.backend.task;

import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;

import java.io.*;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class GoogleDriveAddLocalDirToZip extends BaseAddLocalDirToZipTask {

    private static final int STREAM_BUFFER_SIZE = 1048576; // 1MB for stream buffering
    private static final int PIPE_BUFFER_SIZE = 4194304; // 4MB for pipe buffer

    private String parentId;
    private String zipFileName;

    public GoogleDriveAddLocalDirToZip(List<File> sourceDirsToAdd, String parentId, String zipFileName,
            boolean createRootDirInTargetZIP, boolean forceExcludedDirs) {

        super(sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs);
        this.parentId = parentId;
        this.zipFileName = zipFileName;
    }

    @Override
    protected void run() throws IOException {

        try (PipedInputStream pipedInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {

            Backuper.getInstance().getScheduleManager().runAsync(() -> {

                // Use BufferedOutputStream for better performance
                try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(pipedOutputStream,
                        STREAM_BUFFER_SIZE);
                        ZipOutputStream targetZipOutputStream = new ZipOutputStream(bufferedOutputStream)) {

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

                } catch (Exception e) {
                    warn("Failed to send ZIP entry to GoogleDrive", sender);
                    warn(e);
                }
            });

            final GoogleDriveSendDirTask.GoogleDriveUploadProgressListener progressListener = new GoogleDriveSendDirTask.GoogleDriveUploadProgressListener();
            try {
                GoogleDriveUtils.uploadFile(pipedInputStream, zipFileName, parentId, progressListener);
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("Failed to upload ZIP file to GoogleDrive", sender);
            }
        }
    }

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getLocalConfig().getZipCompressionLevel();
    }
}