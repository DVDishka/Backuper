package ru.dvdishka.backuper.backend.task;

import ru.dvdishka.backuper.backend.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class AddLocalDirToZipTask extends BaseAddLocalDirToZipTask {

    private final File targetZipFileDir;

    public AddLocalDirToZipTask(List<File> sourceDirsToAdd, File targetZipFile, boolean createRootDirInTargetZIP,
                                boolean forceExcludedDirs) {
        super(sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs);
        this.targetZipFileDir = targetZipFile;
    }

    @Override
    protected void run() throws IOException {

        if (targetZipFileDir != null && !targetZipFileDir.exists()) {
            if (!targetZipFileDir.createNewFile()) {
                devLog("Failed to create file %s".formatted(targetZipFileDir.getAbsolutePath()));
            }
        }

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(targetZipFileDir))) {

            for (File sourceDirToAdd : sourceDirsToAdd) {

                if (cancelled) {
                    break;
                }

                if (createRootDirInTargetZIP) {
                    File parent = sourceDirToAdd.getParentFile();
                    parent = parent == null ? new File("") : parent;
                    addDirToZip(zipOutputStream, sourceDirToAdd, parent.toPath());
                } else {
                    addDirToZip(zipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                }
            }

        }
    }

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getLocalConfig().getZipCompressionLevel();
    }
}