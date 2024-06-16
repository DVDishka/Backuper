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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FtpAddLocalDirsToZipTask extends Task {

    private static final String taskName = "FtpAddLocalDirToZip";

    private String targetZipPath;
    private ZipOutputStream targetZipOutputStream;
    private final ArrayList<File> sourceDirsToAdd;

    private FTPClient ftpClient = null;
    private boolean forceExcludedDirs;
    private boolean createRootDirInTargetZIP;

    public FtpAddLocalDirsToZipTask(ArrayList<File> sourceDirsToAdd, String targetZipPath, boolean createRootDirInTargetZIP, boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
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

            ftpClient = FtpUtils.createChannel(sender);
            if (ftpClient == null) {
                return;
            }

            try {
                targetZipOutputStream = new ZipOutputStream(ftpClient.storeFileStream(targetZipPath));

                for (File sourceDirToAdd : sourceDirsToAdd) {
                    if (createRootDirInTargetZIP) {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.getCanonicalFile().getParentFile().toPath());
                    } else {
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                    }
                }

            } catch (Exception e) {
                Logger.getLogger().warn(this, e);
                Logger.getLogger().warn("FtpAddLocalDirToZip task failed", sender);

                Backuper.unlock();
            } finally {
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
            Logger.getLogger().warn(this, e);
        } finally {
            Logger.getLogger().devLog("FtpAddLocalDirToZip task has been finished");
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {

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

                long notCompressedByteSize = Files.size(sourceDir.toPath());

                zip.setLevel(Config.getInstance().getFtpConfig().getZipCompressionLevel());

                ZipEntry zipEntry = new ZipEntry(relativeFilePath);

                zip.putNextEntry(zipEntry);
                FileInputStream fileInputStream = new FileInputStream(sourceDir);
                byte[] buffer = new byte[4048];
                int length;

                while ((length = fileInputStream.read(buffer)) > 0) {
                    zip.write(buffer, 0, length);
                }
                zip.closeEntry();
                fileInputStream.close();

                incrementCurrentProgress(notCompressedByteSize);

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while running FtpAddLocalDirToZIP task", sender);
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
            for (File sourceDirToAdd : sourceDirsToAdd) {
                this.maxProgress += Utils.getFileFolderByteSizeExceptExcluded(sourceDirToAdd);
            }
        } else {
            for (File sourceDirToAdd : sourceDirsToAdd) {
                this.maxProgress += Utils.getFileFolderByteSize(sourceDirToAdd);
            }
        }
    }
}
