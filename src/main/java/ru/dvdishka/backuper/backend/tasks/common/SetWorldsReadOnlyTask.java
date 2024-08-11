package ru.dvdishka.backuper.backend.tasks.common;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.ArrayList;
import java.util.List;

public class SetWorldsReadOnlyTask extends Task {

    private static final String taskName = "SetWorldsReadOnly";

    private final boolean force;

    public SetWorldsReadOnlyTask(boolean force, boolean setLocked, ArrayList<Permissions> permission, CommandSender sender) {
        super(taskName, setLocked, permission, sender);
        this.force = force;
    }

    public SetWorldsReadOnlyTask(boolean setLocked, List<Permissions> permissions, CommandSender sender) {
        super(taskName, setLocked, permissions, sender);
        this.force = false;
    }

    @Override
    public void run() {

        if (!Config.getInstance().isSetWorldsReadOnly() && !force) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {

            if (!Utils.errorSetWritable) {
                Utils.isAutoSaveEnabled.put(world.getName(), world.isAutoSave());
            }

            world.setAutoSave(false);
            if (!world.getWorldFolder().setReadOnly()) {
                Logger.getLogger().warn("Can not set folder read only!", sender);
            }
        }
    }

    @Override
    public void prepareTask() {
        this.isTaskPrepared = true;
    }

    @Override
    public void cancel() {

    }
}
