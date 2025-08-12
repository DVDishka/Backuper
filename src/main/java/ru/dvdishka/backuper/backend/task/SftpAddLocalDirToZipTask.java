package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.apache.commons.lang3.tuple.Pair;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.SftpUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class SftpAddLocalDirToZipTask extends BaseAddLocalDirToZipTask {

    private final String targetZipPath;

    private com.jcraft.jsch.Session sshSession;
    private ChannelSftp sftpChannel;

    public SftpAddLocalDirToZipTask(List<File> sourceDirsToAdd, String targetZipPath, boolean createRootDirInTargetZIP,
                                    boolean forceExcludedDirs) {

        super(sourceDirsToAdd, createRootDirInTargetZIP, forceExcludedDirs);
        this.targetZipPath = targetZipPath;
    }

    @Override
    protected int getZipCompressionLevel() {
        return Config.getInstance().getSftpConfig().getZipCompressionLevel();
    }

    @Override
    protected void run() throws IOException, JSchException, SftpException {

        if (!cancelled) {
            Pair<Session, ChannelSftp> sessionChannelSftpPair = SftpUtils.createChannel();

            assert sessionChannelSftpPair != null;

            sshSession = sessionChannelSftpPair.getLeft();
            sftpChannel = sessionChannelSftpPair.getRight();

            assert sftpChannel != null;
            sftpChannel.connect(10000);
        }

        try (OutputStream outputStream = sftpChannel.put(targetZipPath);
             ZipOutputStream targetZipOutputStream = new ZipOutputStream(outputStream)) {
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

        } finally {
            sftpChannel.exit();
            sshSession.disconnect();
        }
    }
}
