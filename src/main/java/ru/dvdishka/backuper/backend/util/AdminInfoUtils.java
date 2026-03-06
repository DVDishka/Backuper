package ru.dvdishka.backuper.backend.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class AdminInfoUtils {

    public static void sendIssueToGitHub(CommandSender sender) {
        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            if (!sender.isOp()) return;

            Component header = Component.empty();
            header = header
                    .append(Component.text("Issue tracking")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED));

            Component message = Component.empty();
            message = message
                    .append(Component.text("Please, if you find any issues related to the Backuper"))
                    .append(Component.newline())
                    .append(Component.text("Create an issue using the link:"))
                    .append(Component.space())
                    .append(Component.text("https://github.com/DVDishka/Backuper/issues")
                            .clickEvent(ClickEvent.openUrl("https://github.com/DVDishka/Backuper/issues"))
                            .decorate(TextDecoration.UNDERLINED));

            UIUtils.sendFramedMessage(header, message, sender);
        });
    }

    public static void sendPluginVersionCheck(CommandSender sender) {
        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            if (sender.isOp() && !checkPluginVersion()) {
                Component header = Component.empty();
                header = header
                        .append(Component.text("Backuper is outdated")
                                .decorate(TextDecoration.BOLD)
                                .color(NamedTextColor.RED));

                Component message = Component.empty();
                message = message
                        .append(Component.text("You are using an outdated version of Backuper!\nPlease update it to the latest and check the changelist!"));

                int downloadLinkNumber = 0;
                for (String downloadLink : Utils.downloadLinks) {
                    message = message.append(Component.newline());
                    message = message
                            .append(Component.text("Download link:"))
                            .append(Component.space())
                            .append(Component.text(sender instanceof ConsoleCommandSender ? downloadLink : Utils.downloadLinksName.get(downloadLinkNumber))
                                    .clickEvent(ClickEvent.openUrl(downloadLink))
                                    .decorate(TextDecoration.UNDERLINED));
                    downloadLinkNumber++;
                }
                UIUtils.sendFramedMessage(header, message, sender);
            }
        });
    }

    /***
     * May lock down the thread so don't call it in the main thread
     */
    private static boolean checkPluginVersion() {

        if (!Backuper.getInstance().getConfigManager().getServerConfig().isCheckUpdates()) return true;

        try {
            HttpURLConnection connection = (HttpURLConnection) Utils.getLatestVersionURL.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String input;
            StringBuilder response = new StringBuilder();

            while ((input = in.readLine()) != null) {
                response.append(input);
            }
            in.close();

            return response.toString().equals(Utils.getProperty("version"));
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to check the Backuper updates!");
            Backuper.getInstance().getLogManager().warn(e);
            return true; // We shouldn't say that the plugin should be updated if there is some problem during the check
        }
    }
}
