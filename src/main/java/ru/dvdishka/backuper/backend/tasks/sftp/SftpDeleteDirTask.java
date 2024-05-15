package ru.dvdishka.backuper.backend.tasks.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import it.unimi.dsi.fastutil.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.SftpProgressMonitor;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.SftpUtils;

import java.util.ArrayList;
import java.util.Vector;

public class SftpDeleteDirTask extends Task {

    private static final String taskName = "SftpDeleteDir";

    private String remoteDirToDelete = "";

    Session session = null;
    ChannelSftp channelSftp = null;

    public SftpDeleteDirTask(String remoteDirToDelete, boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);

        this.remoteDirToDelete = remoteDirToDelete;
    }

    @Override
    public void run() {

        try {
            if (!isTaskPrepared) {
                prepareTask();
            }

            Pair<Session, ChannelSftp> sessionChannelSftpPair = SftpUtils.createSftpChannel(sender);
            session = sessionChannelSftpPair.first();
            channelSftp = sessionChannelSftpPair.second();

            channelSftp.connect(10000);

            deleteDir(remoteDirToDelete);

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong when trying to execute SftpDeleteDir task", sender);
            Logger.getLogger().warn(this, e);
        } finally {
            try {
                session.disconnect();
            } catch (Exception ignored) {}

            try {
                channelSftp.exit();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
    }

    private void deleteDir(String remoteDirToDelete) {

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
                channelSftp.rm(remoteDirToDelete);
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Something went while trying to delete SFTP directory", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        return 100;
    }

    @Override
    public long getTaskMaxProgress() {
        return 100;
    }
}
