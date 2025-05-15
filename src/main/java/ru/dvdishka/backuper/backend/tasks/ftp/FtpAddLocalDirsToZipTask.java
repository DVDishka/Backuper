package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.common.BaseAddLocalDirsToZipTask;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class FtpAddLocalDirsToZipTask extends BaseAddLocalDirsToZipTask {

    private static final String taskName = "FtpAddLocalDirToZip";

    private String targetZipPath;
    private FTPClient ftpClient = null;

    public FtpAddLocalDirsToZipTask(List<File> sourceDirsToAdd, String targetZipPath, boolean createRootDirInTargetZIP,
            boolean forceExcludedDirs, boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs, setLocked, permission, sender);
        this.targetZipPath = targetZipPath;
        this.sender = sender;
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
                        File parent = sourceDirToAdd.getParentFile();
                        parent = parent == null ? new File("") : parent;
                        addDirToZip(targetZipOutputStream, sourceDirToAdd, parent.toPath());
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

            Logger.getLogger().warn("Something went wrong while running " + taskName + " task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        } finally {
            Logger.getLogger().devLog("FtpAddLocalDirToZip task has been finished");
        }
    }

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getFtpConfig().getZipCompressionLevel();
    }
}
