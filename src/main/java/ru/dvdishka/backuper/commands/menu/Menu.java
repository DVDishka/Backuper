package ru.dvdishka.backuper.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.common.Backup;

public class Menu implements CommandInterface {

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        String backupName = (String) args.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound(sender);
            returnFailure("Backup does not exist!", sender);
            return;
        }

        assert backupName != null;

        normalButtonSound(sender);

        Backup backup = new Backup(backupName);

        long backupSize = backup.getMBSize();
        String zipOrFolder = backup.zipOrFolder();

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender)) {

            message = message
                    .append(Component.text("---------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)))
                    .append(Component.newline());

            message = message
                    .append(Component.text(backupName)
                            .hoverEvent(HoverEvent.showText(Component.text(zipOrFolder + " " + backupSize + " MB"))))
                    .append(Component.newline());

            message = message
                    .append(Component.text("[TO ZIP]")
                            .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\"" + " toZIPConfirmation"))
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x4974B)))
                    .append(Component.space());

            message = message
                    .append(Component.text("[UNZIP]")
                            .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\"" + " unZIPConfirmation"))
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xB8A500)))
                    .append(Component.space());

            message = message
                    .append(Component.text("[DELETE]")
                            .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\"" + " deleteConfirmation"))
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xB02100)))
                    .append(Component.newline());

            message = message
                    .append(Component.text("---------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)));

        } else {

            message = message
                    .append(Component.newline())
                    .append(Component.text("--------------------------------"));

            message = message
                    .append(Component.newline())
                    .append(Component.text(backupName))
                    .append(Component.space())
                    .append(Component.text(zipOrFolder))
                    .append(Component.space())
                    .append(Component.text(backupSize))
                    .append(Component.space())
                    .append(Component.text(" MB"));

            message = message
                    .append(Component.newline())
                    .append(Component.text("[TO ZIP]"))
                    .append(Component.space())
                    .append(Component.text("[UNZIP]"))
                    .append(Component.space())
                    .append(Component.text("[DELETE]"));

            message = message
                    .append(Component.newline())
                    .append(Component.text("--------------------------------"));
        }

        sender.sendMessage(message);
    }
}
