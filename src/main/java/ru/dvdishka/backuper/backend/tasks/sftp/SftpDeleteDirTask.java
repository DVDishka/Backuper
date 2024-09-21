package ru.dvdishka.backuper.backend.tasks.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;
import java.util.Vector;

public class SftpDeleteDirTask extends Task {

    private static final String taskName = "SftpDeleteDir";

    private String remoteDirToDelete = "";

    Session session = null;
    ChannelSftp channelSftp = null;

    public SftpDeleteDirTask(String remoteDirToDelete, boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, setLocked, permission, sender);

        this.remoteDirToDelete = remoteDirToDelete;
    }

    @Override
    public void run() {

        try {
            if (!isTaskPrepared) {
                prepareTask();
            }

            if (setLocked) {
                Backuper.lock(this);
            }

            Logger.getLogger().devLog("SftpDeleteDir task has been started");

            if (!cancelled) {
                Pair<Session, ChannelSftp> sessionChannelSftpPair = SftpUtils.createChannel(sender);

                if (sessionChannelSftpPair == null) {
                    return;
                }

                session = sessionChannelSftpPair.getLeft();
                channelSftp = sessionChannelSftpPair.getRight();

                channelSftp.connect(10000);
            }

            if (!cancelled) {
                deleteDir(remoteDirToDelete);
            }

            if (setLocked) {
                Backuper.unlock();
                UIUtils.successSound(sender);
            }

        } catch (Exception e) {
            if (setLocked) {
                Backuper.unlock();
                UIUtils.cancelSound(sender);
            }
            Logger.getLogger().warn("Something went wrong when trying to execute SftpDeleteDir task", sender);
            Logger.getLogger().warn(this, e);
        } finally {
            try {
                session.disconnect();
            } catch (Exception ignored) {}

            try {
                channelSftp.exit();
            } catch (Exception ignored) {}

            Logger.getLogger().devLog("SftpDeleteDir task has been finished");
        }
    }

    @Override
    public void prepareTask() {
        maxProgress = SftpUtils.getDirByteSize(remoteDirToDelete, sender);
        isTaskPrepared = true;
    }

    private void deleteDir(String remoteDirToDelete) {

        if (cancelled) {
            return;
        }

        try {
            SftpATTRS stat = channelSftp.stat(remoteDirToDelete);

            if (stat.isDir()) {
                Vector<ChannelSftp.LsEntry> ls = channelSftp.ls(remoteDirToDelete);
                for (ChannelSftp.LsEntry file : ls) {
                    if (file.getFilename().equals(".") || file.getFilename().equals("..")) {
                        continue;
                    }
                    deleteDir(SftpUtils.resolve(remoteDirToDelete, file.getFilename()));
                }
                channelSftp.rmdir(remoteDirToDelete);
            } else {
                long fileSize = stat.getSize();
                channelSftp.rm(remoteDirToDelete);
                incrementCurrentProgress(fileSize);
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Something went while trying to delete SFTP directory", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        currentProgress = maxProgress;
    }
}
