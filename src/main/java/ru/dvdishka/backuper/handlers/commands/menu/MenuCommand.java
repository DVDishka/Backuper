package ru.dvdishka.backuper.handlers.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.util.HashMap;

public class MenuCommand extends Command {

    private Storage storage;
    private Backup backup;

    public MenuCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    public boolean check() {
        storage = Backuper.getInstance().getStorageManager().getStorage((String) arguments.get("storage"));
        if (storage == null) {
            returnFailure("Wrong storage name %s".formatted((String) arguments.get("storage")));
            return false;
        }
        if (!storage.checkConnection()) {
            returnFailure("Failed to establish connection to storage %s".formatted(storage.getId()));
            return false;
        }
        backup = storage.getBackupManager().getBackup((String) arguments.get("backupName"));
        if (backup == null) {
            returnFailure("Wrong backup name %s".formatted((String) arguments.get("backupName")));
            return false;
        }
        if (!sender.hasPermission(Permission.STORAGE.getPermission(storage))) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        String backupFormattedName = backup.getFormattedName();
        long backupMbSize = backup.getMbSize();

        Component header = Component.empty();
        header = header
                .append(Component.text("Backup menu")
                        .decorate(TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text("(%s)".formatted(storage.getId()))
                        .color(UIUtils.getSecondaryColor())
                        .decorate(TextDecoration.BOLD));

        Component message = Component.empty();
        if (!(sender instanceof ConsoleCommandSender)) {
            message = message
                    .append(Component.text(backupFormattedName)
                            .hoverEvent(HoverEvent.showText(Component.text("(%s) (%s) %s MB".formatted(storage.getId(), backup.getFileType().name(), backup.getMbSize())))))
                    .append(Component.newline())
                    .append(Component.newline());

            if (Backup.BackupFileType.DIR.equals(backup.getFileType())) {
                message = message
                        .append(Component.text("[TO ZIP]")
                                .clickEvent(ClickEvent.callback((audience) -> {
                                    new ToZIPCommand((CommandSender) audience,
                                            new CommandArguments(
                                                    new Object[]{backup.getStorage().getId(), backup.getName()},
                                                    new HashMap<>(){
                                                        {
                                                            put("storage", backup.getStorage().getId());
                                                            put("backupName", backup.getName());
                                                        }
                                                    },
                                                    new String[]{ backup.getStorage().getId(), backup.getName() },
                                                    new HashMap<>() {
                                                        {
                                                            put("storage", backup.getStorage().getId());
                                                            put("backupName", backup.getName());
                                                        }
                                                    },
                                                    "/backuper menu \"%s\" \"%s\" tozip".formatted(backup.getStorage().getId(), backup.getName())
                                            )).executeConfirm();
                                }))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.space());
            }

            if (Backup.BackupFileType.ZIP.equals(backup.getFileType())) {
                message = message
                        .append(Component.text("[UNZIP]")
                                .clickEvent(ClickEvent.callback((audience) -> {
                                    new UnZIPCommand((CommandSender) audience,
                                            new CommandArguments(
                                                    new Object[]{backup.getStorage().getId(), backup.getName()},
                                                    new HashMap<>(){
                                                        {
                                                            put("storage", backup.getStorage().getId());
                                                            put("backupName", backup.getName());
                                                        }
                                                    },
                                                    new String[]{ backup.getStorage().getId(), backup.getName() },
                                                    new HashMap<>() {
                                                        {
                                                            put("storage", backup.getStorage().getId());
                                                            put("backupName", backup.getName());
                                                        }
                                                    },
                                                    "/backuper menu \"%s\" \"%s\" unzip".formatted(backup.getStorage().getId(), backup.getName())
                                            )).executeConfirm();
                                }))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.space());
            }

            if (Backuper.getInstance().getStorageManager().getStorages().size() >= 2) {
                message = message
                        .append(Component.text("[COPY TO]")
                                .clickEvent(ClickEvent.suggestCommand("/backuper menu %s \"%s\" copyto ".formatted(storage.getId(), backup.getName())))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(17, 102, 212)))
                        .append(Component.space());
            }

            message = message
                    .append(Component.text("[DELETE]")
                            .clickEvent(ClickEvent.callback((audience) -> {
                                new DeleteCommand((CommandSender) audience,
                                        new CommandArguments(
                                                new Object[]{backup.getStorage().getId(), backup.getName()},
                                                new HashMap<>(){
                                                    {
                                                        put("storage", backup.getStorage().getId());
                                                        put("backupName", backup.getName());
                                                    }
                                                },
                                                new String[]{ backup.getStorage().getId(), backup.getName() },
                                                new HashMap<>() {
                                                    {
                                                        put("storage", backup.getStorage().getId());
                                                        put("backupName", backup.getName());
                                                    }
                                                },
                                                "/backuper menu \"%s\" \"%s\" delete".formatted(backup.getStorage().getId(), backup.getName())
                                        )).executeConfirm();
                            }))
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xB02100)));

            sendFramedMessage(header, message, 15);

        } else {
            message = message
                    .append(Component.text(backupFormattedName))
                    .append(Component.space())
                    .append(Component.text("(%s)".formatted(backup.getStorage().getId())))
                    .append(Component.space())
                    .append(Component.text(backup.getFileType().name()))
                    .append(Component.space())
                    .append(Component.text(backupMbSize))
                    .append(Component.space())
                    .append(Component.text("MB"));

            sendFramedMessage(header, message);
        }
    }
}
