package ru.dvdishka.backuper.backend.tasks.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.Utils;

public class SetWorldsWritableTask extends Task {

    private static final String taskName = "SetWorldsWritable";

    private final boolean force;

    public SetWorldsWritableTask(boolean force, boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);
        this.force = force;
    }

    public SetWorldsWritableTask(boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);
        this.force = false;
    }

    @Override
    public void run() {

        if (!Config.getInstance().isSetWorldsReadOnly() && !force) {
            return;
        }

        Utils.errorSetWritable = false;

        for (World world : Bukkit.getWorlds()) {

            if (!world.getWorldFolder().setWritable(true)) {
                Logger.getLogger().warn("Can not set " + world.getWorldFolder().getPath() + " writable!", sender);
                Utils.errorSetWritable = true;
            }

            if (Utils.isAutoSaveEnabled.containsKey(world.getName())) {
                world.setAutoSave(force || Utils.isAutoSaveEnabled.get(world.getName()));
            }
        }
    }

    @Override
    public void prepareTask() {
        this.isTaskPrepared = true;
    }
}
