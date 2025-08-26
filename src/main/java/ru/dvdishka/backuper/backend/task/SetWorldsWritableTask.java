package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.Utils;

public class SetWorldsWritableTask extends BaseTask {

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
    public void run() {

        if (!Config.getInstance().isSetWorldsReadOnly() && !force) {
            return;
        }

        Utils.errorSetWritable = false;

        for (World world : Bukkit.getWorlds()) {

            if (!world.getWorldFolder().setWritable(true)) {
                warn("Can not set %s writable!".formatted(world.getWorldFolder().getPath()), sender);
                Utils.errorSetWritable = true;
            }

            if (Utils.isAutoSaveEnabled.containsKey(world.getName())) {
                world.setAutoSave(force || Utils.isAutoSaveEnabled.get(world.getName()));
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
