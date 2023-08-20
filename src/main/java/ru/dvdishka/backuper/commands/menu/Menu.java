package ru.dvdishka.backuper.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.common.Common;

public class Menu implements CommandInterface {

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        String backupName = (String) args.get("backupName");

        if (!Common.checkBackupExistanceByName(backupName)) {
            cancelButtonSound(sender);
            returnFailure("Backup does not exist!", sender);
            return;
        }

        normalButtonSound(sender);

        long backupSize = Common.getBackupMBSizeByName(backupName);
        String zipOrFolder = Common.zipOrFolderBackupByName(backupName);

        Component message = Component.empty();

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)))
                .appendNewline();

        message = message
                .append(Component.text(backupName)
                        .hoverEvent(HoverEvent.showText(Component.text(zipOrFolder + " " + backupSize + " MB"))))
                .appendNewline();

        message = message
                .append(Component.text("[SET]")
                        .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\"" + " set"))
                        .hoverEvent(HoverEvent.showText(Component.text("SET IT UP ON-THE SERVER")))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0x5DB1)))
                .appendSpace();

        message = message
                .append(Component.text("[DELETE]")
                        .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\"" + " deleteConfirmation"))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)))
                .appendNewline();

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)));

        sender.sendMessage(message);
    }
}
