package ru.dvdishka.backuper.backend.tasks.local.zip.tozip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
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
    private boolean createRootDirInTargetZIP = true;

    public AddDirToZipTask(File sourceDirToAdd, File targetZipFileDir, boolean createRootDirInTargetZIP, boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.targetZipFileDir = targetZipFileDir;
        this.sourceDirToAdd = sourceDirToAdd;
        this.sender = sender;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootDirInTargetZIP = createRootDirInTargetZIP;
    }

    public AddDirToZipTask(File sourceDirToAdd, ZipOutputStream targetZipOutputStream, boolean createRootDirInTargetZIP, boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.targetZipOutputStream = targetZipOutputStream;
        this.sourceDirToAdd = sourceDirToAdd;
        this.sender = sender;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootDirInTargetZIP = createRootDirInTargetZIP;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
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

                    if (createRootDirInTargetZIP) {
                        addDirToZip(zipOutputStream, sourceDirToAdd, sourceDirToAdd.getParentFile().toPath());
                    } else {
                        addDirToZip(zipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                    }

                } catch (Exception e) {
                    Logger.getLogger().warn(this, e);
                    Logger.getLogger().warn("AddDirToZip task failed", sender);

                    Backuper.unlock();
                }
            } else {

                try {

                    if (createRootDirInTargetZIP) {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.getCanonicalFile().getParentFile().toPath());
                    } else {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                    }

                } catch (Exception e) {
                    Logger.getLogger().warn(this, e);
                    Logger.getLogger().warn("AddDirToZip task failed", sender);

                    Backuper.unlock();
                }
            }

            Logger.getLogger().devLog("AddDirToZip task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running AddDirToZIP task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {

        if (!sourceDir.exists()) {
            Logger.getLogger().warn("Something went wrong while running AddDirToZIP task", sender);
            Logger.getLogger().warn("Directory " + sourceDir.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(sourceDir, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
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

                Logger.getLogger().warn("Something went wrong while running AddDirToZIP task", sender);
                Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + sourceDir.getName(), sender);
                Logger.getLogger().warn(this, e);
            }
        }

        if (sourceDir.listFiles() == null) {
            return;
        }

        for (File file : sourceDir.listFiles()) {

            if (!file.getName().equals("session.lock")) {

                addDirToZip(zip, file, relativeDirPath);
            }
        }
    }

    @Override
    public void prepareTask() {
        this.isTaskPrepared = true;
        if (!forceExcludedDirs) {
            this.maxProgress = Utils.getFileFolderByteSizeExceptExcluded(sourceDirToAdd);
        } else {
            this.maxProgress = Utils.getFileFolderByteSize(sourceDirToAdd);
        }
    }
}
