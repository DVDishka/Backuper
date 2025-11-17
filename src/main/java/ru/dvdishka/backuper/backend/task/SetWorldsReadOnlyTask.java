package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.Utils;

public class SetWorldsReadOnlyTask extends BaseTask {

    private final boolean force;

    public SetWorldsReadOnlyTask(boolean force) {
        super();
        this.force = force;
    }

    public SetWorldsReadOnlyTask() {
        super();
        this.force = false;
    }

    @Override
    public void run() {

        if (!Backuper.getInstance().getConfigManager().getBackupConfig().isSetWorldsReadOnly() && !force) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            if (!Utils.errorSetWritable) {
                Utils.isAutoSaveEnabled.put(world.getName(), world.isAutoSave());
            }

            world.setAutoSave(false);
            if (!world.getWorldFolder().setReadOnly()) {
                warn("Can not set folder read only!", sender);
            }
        }
    }

    @Override
    public void prepareTask(CommandSender sender) {
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
