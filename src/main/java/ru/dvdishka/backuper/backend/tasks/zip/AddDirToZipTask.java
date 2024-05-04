package ru.dvdishka.backuper.backend.tasks.zip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AddDirToZipTask extends Task {

    private static final String taskName = "AddDirToZip";

    private File targetZipFileDir = null;
    private ZipOutputStream targetZipOutputStream = null;
    private final File sourceDirToAdd;

    private boolean forceExcludedDirs = false;
    private boolean createRootFolderInZIP = true;

    public AddDirToZipTask(File sourceDirToAdd, File targetZipFileDir, boolean createRootFolderInZIP, boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.targetZipFileDir = targetZipFileDir;
        this.sourceDirToAdd = sourceDirToAdd;
        this.sender = sender;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootFolderInZIP = createRootFolderInZIP;
    }

    public AddDirToZipTask(File sourceDirToAdd, ZipOutputStream targetZipOutputStream, boolean createRootFolderInZIP, boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.targetZipOutputStream = targetZipOutputStream;
        this.sourceDirToAdd = sourceDirToAdd;
        this.sender = sender;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootFolderInZIP = createRootFolderInZIP;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backup.lock(this);
        }

        try {
            Logger.getLogger().devLog("AddDirToZip task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (targetZipFileDir != null && !targetZipFileDir.exists()) {
                if (!targetZipFileDir.createNewFile()) {
                    Logger.getLogger().devLog("Failed to create file " + targetZipFileDir.getAbsolutePath());
                }
            }

            if (targetZipOutputStream == null) {

                try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(targetZipFileDir))) {

                    if (createRootFolderInZIP) {
                        addDirToZip(zipOutputStream, sourceDirToAdd, sourceDirToAdd.getParentFile().toPath());
                    } else {
                        addDirToZip(zipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                    }

                } catch (Exception e) {
                    Logger.getLogger().warn(this, e);
                    Logger.getLogger().warn("AddDirToZip task failed", sender);

                    Backup.unlock();
                }
            } else {

                try {

                    if (createRootFolderInZIP) {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.getParentFile().toPath());
                    } else {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                    }

                } catch (Exception e) {
                    Logger.getLogger().warn(this, e);
                    Logger.getLogger().warn("AddDirToZip task failed", sender);

                    Backup.unlock();
                }
            }

            Logger.getLogger().devLog("AddDirToZip task has been finished", sender);

            if (setLocked) {
                Backup.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
                Backup.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running AddDirToZIP task");
            Logger.getLogger().warn(this, e);
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {

        if (!sourceDir.exists()) {
            Logger.getLogger().warn("Directory " + sourceDir.getAbsolutePath() + " does not exist");
            return;
        }

        if (Utils.isExcludedDirectory(sourceDir, sender) && !forceExcludedDirs) {
            return;
        }

        if (sourceDir.isFile()) {

            try {

                String relativeFilePath = relativeDirPath.toAbsolutePath().relativize(sourceDir.toPath().toAbsolutePath()).toString();

                zip.putNextEntry(new ZipEntry(relativeFilePath));
                FileInputStream fileInputStream = new FileInputStream(sourceDir);
                byte[] buffer = new byte[4048];
                int length;

                while ((length = fileInputStream.read(buffer)) > 0) {
                    zip.write(buffer, 0, length);
                }
                zip.closeEntry();
                fileInputStream.close();

                incrementCurrentProgress(Files.size(sourceDir.toPath()));

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + sourceDir.getName(), sender);
                Logger.getLogger().warn(this, e);
            }
        }

        if (sourceDir.listFiles() == null) {
            return;
        }

        for (File file : sourceDir.listFiles()) {

            boolean isExcludedDirectory = Utils.isExcludedDirectory(file, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                continue;
            }

            if (!file.getName().equals("session.lock")) {

                addDirToZip(zip, file, relativeDirPath);
            }
        }
    }

    @Override
    public void prepareTask() {
        this.isTaskPrepared = true;
        this.maxProgress = Utils.getFolderOrFileByteSize(sourceDirToAdd);
    }
}