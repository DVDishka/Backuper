package ru.dvdishka.backuper.backend.task;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.util.FtpUtils;

import java.io.IOException;

public class FtpDeleteDirTask extends BaseAsyncTask {

    private final String remoteDirToDelete;

    private FTPClient ftp;

    public FtpDeleteDirTask(String remoteDirToDelete) {
        super();
        this.remoteDirToDelete = remoteDirToDelete;
    }

    @Override
    protected void run() throws IOException {

        try {
            if (!cancelled) {
                ftp = FtpUtils.getClient();
                if (ftp == null) {
                    return;
                }
            }

            if (!cancelled) {
                deleteDir(remoteDirToDelete);
            }

        } finally {
            ftp.disconnect();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        try {
            maxProgress = FtpUtils.getDirByteSize(remoteDirToDelete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;
    }

    private void deleteDir(String remoteDirToDelete) {

        if (cancelled) {
            return;
        }

        try {
            ftp.changeWorkingDirectory("");
            FTPFile remoteFile = ftp.mlistFile(remoteDirToDelete);

            if (remoteFile.isDirectory()) {

                if (!ftp.changeWorkingDirectory(remoteDirToDelete)) {
                    return;
                }

                for (FTPFile file : ftp.listFiles()) {
                    if (file.getName().equals(".") || file.getName().equals("..")) {
                        continue;
                    }
                    deleteDir(FtpUtils.resolve(remoteDirToDelete, file.getName()));
                }
                ftp.changeWorkingDirectory("");
                ftp.removeDirectory(remoteDirToDelete);
            }
            if (remoteFile.isFile()) {
                long fileSize = remoteFile.getSize();
                ftp.deleteFile(remoteDirToDelete);
                incrementCurrentProgress(fileSize);
            }
        } catch (Exception e) {
            warn("Something went while trying to delete FTP(S) directory", sender);
            warn(e);
        }
    }
}
