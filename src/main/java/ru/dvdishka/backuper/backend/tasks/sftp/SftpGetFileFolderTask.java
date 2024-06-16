package ru.dvdishka.backuper.backend.tasks.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import it.unimi.dsi.fastutil.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.SftpProgressMonitor;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class SftpGetFileFolderTask extends Task {

    private static final String taskName = "SftpGetFileFolder";

    private String remotePathToGet = "";
    private File localTargetPathFile;
    private boolean createRootDirInTargetDir = false;

    private Session session = null;
    private ChannelSftp sftpChannel = null;
    private long dirSize = 0;
    private ArrayList<SftpProgressMonitor> progressMonitors = new ArrayList<>();

    public SftpGetFileFolderTask(String remotePathToGet, File localTargetPathFile, boolean createRootDirInTargetDir,
                                 boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);

        this.remotePathToGet = remotePathToGet;
        this.localTargetPathFile = localTargetPathFile;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    public void run() {

        try {

            if (setLocked) {
                Backuper.lock(this);
            }

            if (!isTaskPrepared) {
                prepareTask();
            }

            Logger.getLogger().devLog("SftpGetFileFolder task has been started");

            Pair<Session, ChannelSftp> sessionChannelSftpPair = SftpUtils.createChannel(sender);

            if (sessionChannelSftpPair == null) {
                return;
            }

            session = sessionChannelSftpPair.first();
            sftpChannel = sessionChannelSftpPair.second();

            sftpChannel.connect(10000);

            if (createRootDirInTargetDir) {

                String remoteDirName = "";
                for (char c : remotePathToGet.toCharArray()) {

                    String symbol = String.valueOf(c);

                    if (Objects.equals(symbol, Config.getInstance().getSftpConfig().getPathSeparatorSymbol())) {
                        remoteDirName = "";
                    } else {
                        remoteDirName += symbol;
                    }
                }

                localTargetPathFile = localTargetPathFile.toPath().resolve(remoteDirName).toFile();
            }

            getRemoteDir(remotePathToGet, localTargetPathFile);

            sftpChannel.exit();
            session.disconnect();

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            try {
                sftpChannel.exit();
            } catch (Exception ignored) {}

            try {
                session.disconnect();
            } catch (Exception ignored) {}

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong when trying to get file from the SFTP channel", sender);
            Logger.getLogger().warn(this, e);
        } finally {
            Logger.getLogger().devLog("SftpGetFileFolder task has been finished");
        }
    }

    @Override
    public void prepareTask() {

        dirSize = SftpUtils.getDirByteSize(remotePathToGet, sender);
    }

    private void getRemoteDir(String remotePath, File localPath) {

        try {
            SftpATTRS remoteAttrs = sftpChannel.stat(remotePath);

            if (!remoteAttrs.isDir()) {

                localPath.createNewFile();
                SftpProgressMonitor progressMonitor = new SftpProgressMonitor();
                progressMonitors.add(progressMonitor);
                sftpChannel.get(remotePath, localPath.getCanonicalPath(), progressMonitor);

            } else {

                localPath.mkdirs();
                for (ChannelSftp.LsEntry entry : sftpChannel.ls(remotePath)) {
                    if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                        continue;
                    }

                    getRemoteDir(SftpUtils.resolve(remotePath, entry.getFilename()), localPath.toPath().resolve(entry.getFilename()).toFile());
                }
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to get file/folder from the SFTP channel", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        long currentProgress = 0;

        for (ru.dvdishka.backuper.backend.classes.SftpProgressMonitor progressMonitor : progressMonitors) {
            currentProgress += progressMonitor.getCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }
}
