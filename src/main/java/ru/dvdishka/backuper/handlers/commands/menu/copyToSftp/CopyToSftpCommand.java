package ru.dvdishka.backuper.handlers.commands.menu.copyToSftp;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.backup.SftpBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.SftpSendDirTask;
import ru.dvdishka.backuper.backend.util.SftpUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class CopyToSftpCommand extends Command {

    public CopyToSftpCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            cancelSound();
            returnFailure("SFTP storage is disabled");
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

        for (SftpBackup sftpBackup : SftpBackup.getBackups()) {
            if (sftpBackup.getName().equals(localBackup.getName())) {
                cancelSound();
                returnFailure("Sftp storage already contains this backup");
                return;
            }
        }

        buttonSound();

        Backuper.getInstance().getScheduleManager().runAsync(() -> {

            String inProgressName = "%s in progress".formatted(localBackup.getName());
            if (Backup.BackupFileType.ZIP.equals(localBackup.getFileType())) {
                inProgressName += ".zip";
            }

            BaseAsyncTask task = new SftpSendDirTask(localBackup.getFile(),
                    SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), inProgressName),
                    false, true);
            Backuper.getInstance().getTaskManager().startTask(task, sender, List.of(Permissions.LOCAL_COPY_TO_SFTP));

            if (!task.isCancelled()) {
                try {
                    SftpUtils.renameFile(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), inProgressName),
                            SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), localBackup.getFileName())
                    );
                    Backup.saveBackupSizeToCache(Backup.StorageType.SFTP, localBackup.getName(), task.getTaskMaxProgress());
                } catch (Exception e) {
                    Backuper.getInstance().getLogManager().warn("Failed to rename backup \"in-progress\" file on SFTP server");
                }
            }
        });
    }
}
