package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FtpAddLocalDirsToZipTask extends Task {

    private static final String taskName = "FtpAddLocalDirToZip";

    private String targetZipPath;
    private final ArrayList<File> sourceDirsToAdd;

    private FTPClient ftpClient = null;
    private boolean forceExcludedDirs;
    private boolean createRootDirInTargetZIP;

    public FtpAddLocalDirsToZipTask(ArrayList<File> sourceDirsToAdd, String targetZipPath, boolean createRootDirInTargetZIP,
                                    boolean forceExcludedDirs, boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
        this.targetZipPath = targetZipPath;
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
            Logger.getLogger().devLog("FtpAddLocalDirToZip task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (!cancelled) {
                ftpClient = FtpUtils.getClient(sender);
                if (ftpClient == null) {
                    return;
                }
            }

            OutputStream outputStream = ftpClient.storeFileStream(targetZipPath);
            ZipOutputStream targetZipOutputStream = new ZipOutputStream(outputStream);

            try {
                for (File sourceDirToAdd : sourceDirsToAdd) {

                    if (cancelled) {
                        break;
                    }

                    if (createRootDirInTargetZIP) {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.getCanonicalFile().getParentFile().toPath());
                    } else {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                    }
                }

            } catch (Exception e) {
                Logger.getLogger().warn("FtpAddLocalDirToZip task failed", sender);
                Logger.getLogger().warn(this.getClass(), e);

                Backuper.unlock();
            } finally {
                targetZipOutputStream.finish();
                targetZipOutputStream.close();
                outputStream.close();
                ftpClient.disconnect();
            }

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running FtpAddLocalDirToZIP task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        } finally {
            Logger.getLogger().devLog("FtpAddLocalDirToZip task has been finished");
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {

        if (cancelled) {
            return;
        }

        if (!sourceDir.exists()) {
            Logger.getLogger().warn("Something went wrong while running FtpAddLocalDirToZIP task", sender);
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

                zip.setLevel(Config.getInstance().getFtpConfig().getZipCompressionLevel());

                ZipEntry zipEntry = new ZipEntry(relativeFilePath);

                zip.putNextEntry(zipEntry);
                FileInputStream fileInputStream = new FileInputStream(sourceDir);
                byte[] buffer = new byte[1024];
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

                Logger.getLogger().warn("Something went wrong while running FtpAddLocalDirToZIP task", sender);
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
        currentProgress = maxProgress;
    }
}
