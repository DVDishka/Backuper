package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.*;

import java.io.File;

public abstract class ExternalBackup extends Backup {

    public BaseTask getRawCopyToLocalTask() {

        String inProgressName = "%s in progress".formatted(this.getName());
        if (BackupFileType.ZIP.equals(this.getFileType())) {
            inProgressName += ".zip";
        }
        File inProgressFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder(), inProgressName);

        BaseTask task = null;
        if (this instanceof FtpBackup) {
            task = new FtpGetDirTask(this.getPath(), inProgressFile,
                    false);
        }
        if (this instanceof SftpBackup) {
            task = new SftpGetDirTask(this.getPath(), inProgressFile,
                    false);
        }
        if (this instanceof GoogleDriveBackup) {
            task = new GoogleDriveGetDirTask(this.getPath(), inProgressFile,
                    false);
        }
        return task;
    }

    public CopyExternalBackupToLocalTask getCopyToLocalTask() {
        return new CopyExternalBackupToLocalTask(this);
    }
}
