package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;

public class DeleteOldBackupsTask extends BaseTask {

    private final ArrayList<Task> tasks = new ArrayList<>();

    public DeleteOldBackupsTask() {
        super();
    }

    @Override
    public void run() throws IOException {
        for (Task deleteDirTask : tasks) {
            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(deleteDirTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(deleteDirTask, e));
                }
            }
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        for (Task task : tasks) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) {
        for (Storage storage : Backuper.getInstance().getStorageManager().getStorages()) {
            if (cancelled || storage.getConfig().getBackupsNumber() == 0 && storage.getConfig().getBackupsWeight() == 0) return;

            HashSet<LocalDateTime> backupsToDeleteList = new HashSet<>();
            long backupsFolderByteSize = 0;

            ArrayList<Backup> backups = new ArrayList<>(storage.getBackupManager().getBackupList());
            if (cancelled) return;

            for (Backup backup : backups) {
                if (cancelled) return;
                try {
                    backupsFolderByteSize += backup.getByteSize();
                } catch (Exception e) {
                    warn("Failed to get \"%s\" backup byte size in %s storage".formatted(backup.getName(), storage.getId()), sender);
                    warn(e);
                }
            }

            ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();
            for (Backup backup : backups) {
                backupDateTimes.add(backup.getLocalDateTime());
            }
            Utils.sortLocalDateTime(backupDateTimes);

            if (storage.getConfig().getBackupsNumber() != 0) {

                int backupsToDelete = backups.size() - storage.getConfig().getBackupsNumber();
                for (LocalDateTime fileName : backupDateTimes) {
                    if (backupsToDelete <= 0) break;
                    if (backupsToDeleteList.contains(fileName)) continue;

                    for (Backup backup : backups) {
                        if (cancelled) return;

                        String backupFileName = backup.getName().replace(".zip", "");
                        try {
                            if (LocalDateTime.parse(backupFileName, Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter()).equals(fileName)) {

                                Task deleteBackupTask = backup.getDeleteTask();
                                Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);
                                tasks.add(deleteBackupTask);
                                backupsToDeleteList.add(fileName);
                                backupsFolderByteSize -= backup.getByteSize();
                            }
                        } catch (Exception ignored) {}
                    }
                    backupsToDelete--;
                }
            }

            if (storage.getConfig().getBackupsWeight() != 0) {

                long bytesToDelete = backupsFolderByteSize - storage.getConfig().getBackupsWeight();
                for (LocalDateTime fileName : backupDateTimes) {
                    if (bytesToDelete <= 0) break;
                    if (backupsToDeleteList.contains(fileName)) continue;

                    for (Backup backup : backups) {
                        if (cancelled) return;

                        String backupFileName = backup.getName().replace(".zip", "");
                        try {
                            if (LocalDateTime.parse(backupFileName, Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter()).equals(fileName)) {

                                bytesToDelete -= backup.getByteSize();
                                BaseTask deleteBackupTask = backup.getDeleteTask();
                                Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);
                                tasks.add(deleteBackupTask);
                                backupsToDeleteList.add(fileName);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        return tasks.stream().mapToLong(Task::getTaskCurrentProgress).sum();
    }

    @Override
    public long getTaskMaxProgress() {
        return tasks.stream().mapToLong(Task::getTaskMaxProgress).sum();
    }
}
