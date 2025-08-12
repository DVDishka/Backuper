package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.Utils;

public class SetWorldsWritableTask extends BaseAsyncTask {

    private final boolean force;

    public SetWorldsWritableTask(boolean force) {
        super();
        this.force = force;
    }

    public SetWorldsWritableTask() {
        super();
        this.force = false;
    }

    @Override
    protected void run() {

        if (!Config.getInstance().isSetWorldsReadOnly() && !force) {
            return;
        }

        Utils.errorSetWritable = false;

        for (World world : Bukkit.getWorlds()) {

            if (!world.getWorldFolder().setWritable(true)) {
                warn("Can not set " + world.getWorldFolder().getPath() + " writable!", sender);
                Utils.errorSetWritable = true;
            }

            if (Utils.isAutoSaveEnabled.containsKey(world.getName())) {
                world.setAutoSave(force || Utils.isAutoSaveEnabled.get(world.getName()));
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
