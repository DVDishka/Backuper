package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.SftpUtils;
import ru.dvdishka.backuper.backend.util.Utils;

import javax.security.sasl.AuthenticationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class SftpSendDirTask extends BaseAsyncTask {

    private final File localDirToSend;
    private final boolean createRootDirInTargetDir;
    private String remoteTargetDir;
    private final boolean forceExcludedDirs;

    private com.jcraft.jsch.Session sshSession;
    private ChannelSftp sftpChannel;
    private final ArrayList<CompletableFuture<Void>> sftpTasks = new ArrayList<>();
    private ArrayList<SftpProgressMonitor> progressMonitors;
    private long dirSize = 0;

    public SftpSendDirTask(File localDirToSend, String remoteTargetDir, boolean createRootDirInTargetDir,
                           boolean forceExcludedDirs) {
        super();

        this.localDirToSend = localDirToSend;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.remoteTargetDir = remoteTargetDir;
        this.forceExcludedDirs = forceExcludedDirs;
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

                sshSession = sessionChannelSftpPair.getLeft();
                sftpChannel = sessionChannelSftpPair.getRight();

                if (sftpChannel == null) {
                    return;
                }
                sftpChannel.connect(10000);
            }

            if (createRootDirInTargetDir) {
                remoteTargetDir = SftpUtils.resolve(remoteTargetDir, localDirToSend.getName());
            }

            progressMonitors = new ArrayList<>();
            if (!cancelled) {
                sendFolder(localDirToSend, remoteTargetDir);
            }

        } finally {
            sftpChannel.exit();
            sshSession.disconnect();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        if (forceExcludedDirs) {
            dirSize = Utils.getFileFolderByteSize(localDirToSend);
        } else {
            dirSize = Utils.getFileFolderByteSizeExceptExcluded(localDirToSend);
        }
    }

    private void sendFolder(File localDirToSend, String remotePath) {

        if (cancelled) {
            return;
        }

        if (!localDirToSend.exists()) {
            warn("Something went wrong while trying to send files from " + localDirToSend.getAbsolutePath());
            warn("Directory " + localDirToSend.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(localDirToSend, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (localDirToSend.isFile() && !localDirToSend.getName().equals("session.lock")) {

            try {
                String localPath = localDirToSend.getCanonicalPath();

                SftpProgressMonitor progressMonitor = new SftpProgressMonitor();
                progressMonitors.add(progressMonitor);

                CompletableFuture<Void> task = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try {
                        sftpChannel.put(localPath, remotePath, progressMonitor);
                    } catch (Exception e) {
                        devWarn("Failed to send file \"" + localPath + "\" using SFTP connection");
                        devWarn(Arrays.toString(e.getStackTrace()));
                    }
                });

                sftpTasks.add(task);
                try {
                    task.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Failed to send file \"" + localPath + "\" using SFTP connection", sender);
                        warn(e);
                    }
                }

            } catch (Exception e) {

                warn("Something went wrong while sending file to the SFTP channel", sender);
                warn(e);
            }
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            try {
                sftpChannel.mkdir(remotePath);
            } catch (Exception ignored) {
            }

            for (File file : localDirToSend.listFiles()) {
                sendFolder(file, SftpUtils.resolve(remotePath, file.getName()));
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return maxProgress;
        }

        if (progressMonitors == null) {
            return 0;
        }
        long currentProgress = 0;

        for (SftpProgressMonitor progressMonitor : progressMonitors) {
            currentProgress += progressMonitor.getCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> sftpTask : sftpTasks) {
            sftpTask.cancel(true);
        }
    }
}
