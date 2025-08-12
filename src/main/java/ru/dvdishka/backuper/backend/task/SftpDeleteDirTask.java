package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.util.SftpUtils;

import javax.security.sasl.AuthenticationException;
import java.util.Vector;

public class SftpDeleteDirTask extends BaseAsyncTask {

    private final String remoteDirToDelete;

    Session session = null;
    ChannelSftp channelSftp = null;

    public SftpDeleteDirTask(String remoteDirToDelete) {
        super();

        this.remoteDirToDelete = remoteDirToDelete;
    }

    @Override
    protected void run() throws JSchException {

        try {

            if (!cancelled) {
                Pair<Session, ChannelSftp> sessionChannelSftpPair;
                try {
                    sessionChannelSftpPair = SftpUtils.createChannel();
                } catch (AuthenticationException e) {
                    throw new RuntimeException(e);
                }

                session = sessionChannelSftpPair.getLeft();
                channelSftp = sessionChannelSftpPair.getRight();

                channelSftp.connect(10000);
            }

            if (!cancelled) {
                deleteDir(remoteDirToDelete);
            }

        } finally {
            channelSftp.exit();
            session.disconnect();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) throws SftpException {
        maxProgress = SftpUtils.getDirByteSize(remoteDirToDelete);
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
            warn("Something went while trying to delete SFTP directory", sender);
            warn(e);
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;
    }
}
