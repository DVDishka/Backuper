package ru.dvdishka.backuper.backend.tasks.local.zip.tozip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.common.BaseAddLocalDirsToZipTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class AddDirToZipTask extends BaseAddLocalDirsToZipTask {

    private static final String taskName = "AddDirToZip";

    private File targetZipFileDir;

    public AddDirToZipTask(List<File> sourceDirsToAdd, File targetZipFile, boolean createRootDirInTargetZIP, boolean forceExcludedDirs,
                           boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs, setLocked, permission, sender);
        this.targetZipFileDir = targetZipFile;
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

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getLocalConfig().getZipCompressionLevel();
    }
}