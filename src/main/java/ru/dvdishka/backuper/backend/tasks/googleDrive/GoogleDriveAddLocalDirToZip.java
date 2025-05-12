package ru.dvdishka.backuper.backend.tasks.googleDrive;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.common.BaseAddLocalDirsToZipTask;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.*;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class GoogleDriveAddLocalDirToZip extends BaseAddLocalDirsToZipTask {

    private static final String taskName = "GoogleDriveAddLocalDirToZip";

    private String parentId;
    private String zipFileName;

    public GoogleDriveAddLocalDirToZip(List<File> sourceDirsToAdd, String parentId, String zipFileName,
            boolean createRootDirInTargetZIP,
            boolean forceExcludedDirs, boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs, setLocked, permission, sender);
        this.parentId = parentId;
        this.zipFileName = zipFileName;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try (PipedInputStream pipedInputStream = new PipedInputStream();
                PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {
            Logger.getLogger().devLog(taskName + " task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

                ZipOutputStream targetZipOutputStream = new ZipOutputStream(pipedOutputStream);
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
                    Logger.getLogger().warn(taskName + " task failed", sender);
                    Logger.getLogger().warn(this.getClass(), e);

                    Backuper.unlock();
                } finally {
                    try {
                        targetZipOutputStream.finish();
                        targetZipOutputStream.close();
                    } catch (Exception e) {
                        Logger.getLogger().warn(this.getClass(), e);
                    }
                }
            });

            final GoogleDriveSendFileFolderTask.GoogleDriveUploadProgressListener progressListener = new GoogleDriveSendFileFolderTask.GoogleDriveUploadProgressListener();
            GoogleDriveUtils.uploadFile(pipedInputStream, zipFileName, parentId, progressListener, sender);

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
            Logger.getLogger().devLog(taskName + " task has been finished");
        }
    }

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getLocalConfig().getZipCompressionLevel();
    }
}