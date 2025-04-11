package ru.dvdishka.backuper.backend.tasks.local.zip.tozip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AddDirToZipTask extends Task {

    private static final String taskName = "AddDirToZip";

    private File targetZipFileDir;
    private final List<File> sourceDirsToAdd;

    private final boolean forceExcludedDirs;
    private final boolean createRootDirInTargetZIP;

    public AddDirToZipTask(List<File> sourceDirsToAdd, File targetZipFile, boolean createRootDirInTargetZIP, boolean forceExcludedDirs,
                           boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
        this.targetZipFileDir = targetZipFile;
        this.sourceDirsToAdd = sourceDirsToAdd;
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
            Logger.getLogger().devLog(taskName + " task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (targetZipFileDir != null && !targetZipFileDir.exists()) {
                if (!targetZipFileDir.createNewFile()) {
                    Logger.getLogger().devLog("Failed to create file " + targetZipFileDir.getAbsolutePath());
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

            } catch (Exception e) {
                Logger.getLogger().warn("AddDirToZip task failed", sender);
                Logger.getLogger().warn(this.getClass(), e);

                Backuper.unlock();
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

            Logger.getLogger().warn("Something went wrong while running " + taskName + " task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {

        if (cancelled) {
            return;
        }

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

        if (!cancelled && sourceDir.isFile()) {

            try {

                String relativeFilePath = relativeDirPath.toAbsolutePath().relativize(sourceDir.toPath().toAbsolutePath()).toString();

                zip.setLevel(Config.getInstance().getLocalConfig().getZipCompressionLevel());

                ZipEntry zipEntry = new ZipEntry(relativeFilePath);

                zip.putNextEntry(zipEntry);
                FileInputStream fileInputStream = new FileInputStream(sourceDir);
                byte[] buffer = new byte[4048];
                int length;

                while ((length = fileInputStream.read(buffer)) >= 0) {

                    if (cancelled) {
                        break;
                    }

                    zip.write(buffer, 0, length);
                    incrementCurrentProgress(length);
                }
                zip.closeEntry();
                fileInputStream.close();

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while running AddDirToZIP task", sender);
                Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + sourceDir.getName(), sender);
                Logger.getLogger().warn(this.getClass(), e);
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
            for (File sourceDirToAdd : sourceDirsToAdd) {
                this.maxProgress += Utils.getFileFolderByteSizeExceptExcluded(sourceDirToAdd);
            }
        } else {
            for (File sourceDirToAdd : sourceDirsToAdd) {
                this.maxProgress += Utils.getFileFolderByteSize(sourceDirToAdd);
            }
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
