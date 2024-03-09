package ru.dvdishka.backuper.handlers.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;
import java.util.Objects;

public class DeleteCommand extends Command implements Task {

    private String taskName = "Delete backup";
    private long maxProgress = 0;
    private volatile long currentProgress = 0;

    private boolean isDeleteSuccessful = true;

    public DeleteCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound();
            returnFailure("Backup does not exist!");
            return;
        }

        normalButtonSound();

        Backup backup = new Backup(backupName);

        if (Backup.isLocked() || Backup.isLocked()) {
            cancelButtonSound();
            returnFailure("Backup is blocked by another operation!");
            return;
        }

        File backupFile = backup.getFile();

        Backup.lock(this);
        maxProgress = backup.getByteSize();

        if (backup.zipOrFolder().equals("(ZIP)")) {

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
                if (backupFile.delete()) {
                    returnSuccess("Backup has been deleted successfully");
                } else {
                    returnFailure("Backup " + backupName + " can not be deleted!");
                }
                Backup.unlock();
            });

        } else {

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
                deleteDir(backupFile);
                if (!isDeleteSuccessful) {
                    returnFailure("Delete task has been finished with an exception!");
                } else {
                    returnSuccess("Backup has been deleted successfully");
                }
                backup.unlock();
            });
        }
    }

    public void deleteDir(File dir) {

        if (dir != null && dir.listFiles() != null) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                if (file.isDirectory()) {

                    deleteDir(file);

                } else {

                    incrementCurrentProgress(Utils.getFolderOrFileByteSize(file));

                    if (!file.delete()) {

                        isDeleteSuccessful = false;
                        Logger.getLogger().devWarn(this, "Can not delete file " + file.getName());
                    }
                }
            }
            if (!dir.delete()) {

                isDeleteSuccessful = false;
                Logger.getLogger().devWarn(this, "Can not delete directory " + dir.getName());
            }
        }
    }

    private synchronized void incrementCurrentProgress(long progress) {
        currentProgress += progress;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public long getTaskProgress() {
        return (long) (((double) currentProgress) / ((double) maxProgress) * 100.0);
    }
}
