package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;

import java.util.ArrayList;

public class DeleteBrokenBackupsTask extends BaseTask {

    private final ArrayList<Task> tasks = new ArrayList<>();

    @Override
    public void run() {
        for (Task task : tasks) {
            if (cancelled) return;
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(task, sender);
            } catch (TaskException e) {
                warn(e);
            }
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws Throwable {
        if (cancelled) return;

        for (Storage storage : Backuper.getInstance().getStorageManager().getStorages()) {
            if (!storage.getConfig().isEnabled()) continue;
            if (!storage.checkConnection(sender)) continue;

            for (String file : storage.ls(storage.getConfig().getBackupsFolder())) {
                if (cancelled) return;
                if (file.replace(".zip", "").endsWith(" in progress")) {
                    Task task = new DeleteDirTask(storage, storage.resolve(storage.getConfig().getBackupsFolder(), file));
                    Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                    tasks.add(task);
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
    public long getTaskMaxProgress() {
        return tasks.stream().mapToLong(Task::getTaskMaxProgress).sum();
    }

    @Override
    public long getTaskCurrentProgress() {
        return tasks.stream().mapToLong(Task::getTaskCurrentProgress).sum();

    }
}
