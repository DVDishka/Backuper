package ru.dvdishka.backuper.handlers.commands.menu.copyToFtp;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.FtpBackup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.FtpSendDirTask;
import ru.dvdishka.backuper.backend.util.FtpUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class CopyToFtpCommand extends Command {

    public CopyToFtpCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!Config.getInstance().getFtpConfig().isEnabled()) {
            cancelSound();
            returnFailure("FTP storage is disabled");
            return;
        }

        LocalBackup localBackup = LocalBackup.getInstance((String) arguments.get("backupName"));

        if (localBackup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        for (FtpBackup ftpBackup : FtpBackup.getBackups()) {
            if (ftpBackup.getName().equals(localBackup.getName())) {
                cancelSound();
                returnFailure("Ftp storage already contains this backup");
                return;
            }
        }

        buttonSound();

        Backuper.getInstance().getScheduleManager().runAsync(() -> {

            String inProgressName = "%s in progress".formatted(localBackup.getName());
            if (Backup.BackupFileType.ZIP.equals(localBackup.getFileType())) {
                inProgressName += ".zip";
            }

            BaseAsyncTask task = new FtpSendDirTask(localBackup.getFile(),
                    FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), inProgressName),
                    false, true);
            Backuper.getInstance().getTaskManager().startTask(task, sender, List.of(Permissions.LOCAL_COPY_TO_FTP));

            if (!task.isCancelled()) {
                try {
                    FtpUtils.renameFile(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), inProgressName),
                            FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), localBackup.getFileName())
                    );
                    Backup.saveBackupSizeToCache(Backup.StorageType.FTP, localBackup.getName(), task.getTaskMaxProgress());
                } catch (Exception e) {
                    Backuper.getInstance().getLogManager().warn("Failed to rename backup \"in-progress\" file on FTP(S) server");
                    Backuper.getInstance().getLogManager().warn(e);
                }
            }
        });
    }
}
