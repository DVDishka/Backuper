package ru.dvdishka.backuper.handlers.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.handlers.commands.Command;

public class MenuCommand extends Command {

    public MenuCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!LocalBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        buttonSound();

        LocalBackup localBackup = LocalBackup.getInstance(backupName);

        long backupSize = localBackup.getMBSize();
        String zipOrFolder = localBackup.zipOrFolder();

        Component header = Component.empty();

        header = header
                .append(Component.text("Backup Menu")
                        .decorate(TextDecoration.BOLD));

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender)) {

            message = message
                    .append(Component.text(backupName)
                            .hoverEvent(HoverEvent.showText(Component.text(zipOrFolder + " " + backupSize + " MB"))))
                    .append(Component.newline())
                    .append(Component.newline());

            if (localBackup.zipOrFolder().equals("(Folder)")) {
                message = message
                        .append(Component.text("[TO ZIP]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu \"" + backupName + "\"" + " toZIPConfirmation"))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.space());
            }

            if (localBackup.zipOrFolder().equals("(ZIP)")) {
                message = message
                        .append(Component.text("[UNZIP]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu \"" + backupName + "\"" + " unZIPConfirmation"))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.space());
            }

            message = message
                    .append(Component.text("[DELETE]")
                            .clickEvent(ClickEvent.runCommand("/backuper menu \"" + backupName + "\"" + " deleteConfirmation"))
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xB02100)));

            sendFramedMessage(header, message, 15);

        } else {

            message = message
                    .append(Component.text(backupName))
                    .append(Component.space())
                    .append(Component.text(zipOrFolder))
                    .append(Component.space())
                    .append(Component.text(backupSize))
                    .append(Component.space())
                    .append(Component.text(" MB"));

            sendFramedMessage(header, message);
        }
    }
}
