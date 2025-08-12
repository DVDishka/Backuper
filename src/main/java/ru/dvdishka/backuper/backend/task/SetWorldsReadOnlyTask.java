package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.Utils;

public class SetWorldsReadOnlyTask extends BaseAsyncTask {

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
    protected void run() {

        if (!Config.getInstance().isSetWorldsReadOnly() && !force) {
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
    protected void prepareTask(CommandSender sender) {
    }

    @Override
    protected void cancel() {
        cancelled = true;
    }
}
