package ru.dvdishka.backuper.backend.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.LocalConfig;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.list.ListCommand;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public class StorageManager implements Listener {

    private final HashMap<String, Storage> storages = new HashMap<>();

    private final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    public StorageManager() {
        // Register default local storage that'll be used to create backups
        LocalConfig config = new LocalConfig();
        LocalStorage storage = new LocalStorage(config.load(config.getDefaultConfig()));
        registerStorage("backuper", storage);
    }

    public void registerStorage(String id, Storage storage) throws RuntimeException {
        if (storages.containsKey(id)) {
            throw new RuntimeException("\"%s\" id is already used for some storage".formatted(id));
        }
        storage.setId(id);
        storages.put(id, storage);
    }

    /***
     *
     * @return Returns storage or null if there is no such registered storage
     */
    public Storage getStorage(String id) {
        return storages.get(id);
    }

    /***
     * @return Doesn't contain disabled storages and "backuper" storage
     */
    public List<Storage> getStorages() {
        return new ArrayList<>(storages.values().stream().filter(storage -> !storage.getId().equals("backuper")).toList());
    }

    public void saveSizeCache() {
        try {
            File sizeCachceFile = Backuper.getInstance().getConfigManager().getServerConfig().getSizeCacheFile();

            FileWriter writer = new FileWriter(sizeCachceFile);
            HashMap<String, ConcurrentMap<String, Long>> jsonedCache = new HashMap<>();
            for (Storage storage : storages.values()) {
                jsonedCache.put(storage.getId(), storage.getBackupManager().getSizeCache());
            }
            String json = gson.toJson(jsonedCache);
            writer.write(json);
            writer.close();
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to save size cache to disk!");
            Backuper.getInstance().getLogManager().warn(e);
        }
    }

    public void loadSizeCache() {

        try {
            File sizeCacheFile = Backuper.getInstance().getConfigManager().getServerConfig().getSizeCacheFile();
            try {
                if (!sizeCacheFile.exists() && !sizeCacheFile.createNewFile()) {
                    Backuper.getInstance().getLogManager().warn("Unable to create %s file!".formatted(sizeCacheFile.getPath()));
                }
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("Unable to create %s file!".formatted(sizeCacheFile.getPath()));
            }

            FileReader reader = new FileReader(sizeCacheFile);
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                json.append(new String(buffer, 0, length));
            }
            reader.close();
            String jsonString = json.toString();
            if (jsonString.isEmpty()) return;

            Type typeToken = new TypeToken<HashMap<String, HashMap<String, Long>>>() {}.getType();
            HashMap<String, HashMap<String, Long>> jsonedCache = gson.fromJson(jsonString, typeToken);

            for (Storage storage : storages.values()) {
                if (!jsonedCache.containsKey(storage.getId())) {
                    continue;
                }
                storage.getBackupManager().loadSizeCache(jsonedCache.get(storage.getId()));
            }
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to load backups size cache");
            Backuper.getInstance().getLogManager().warn(e);
        }
    }

    public void indexStorages() {
        Backuper.getInstance().getLogManager().log("Indexing storages...");
        List<CompletableFuture<Void>> indexStorageFutures = new ArrayList<>();

        for (Storage storage : getStorages()) {
            if (storage.getConfig().isEnabled()){
                CompletableFuture<Void> indexStorageFuture = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                        try {
                            Backuper.getInstance().getLogManager().devLog("Indexing %s storage...".formatted(storage.getId()));
                            new ListCommand(false, Bukkit.getConsoleSender(), new CommandArguments(
                                    new Object[]{storage.getId()},
                                    new HashMap<>(){{put("storage", storage.getId());}},
                                    new String[]{storage.getId()},
                                    new HashMap<>(){{put("storage", storage.getId());}},
                                    "/backuper list %s".formatted(storage.getId())))
                                    .execute();
                            Backuper.getInstance().getLogManager().devLog("%s storage has been indexed".formatted(storage.getId()));
                        } catch (Exception e) {
                            Backuper.getInstance().getLogManager().warn("Failed to index storage %s".formatted(storage.getId()));
                            Backuper.getInstance().getLogManager().warn(e);
                        }
                    });
                });
                indexStorageFutures.add(indexStorageFuture);
            }
        }

        try {
            CompletableFuture.allOf(indexStorageFutures.toArray(new CompletableFuture[0])).get();
        } catch (ExecutionException e) {
            Backuper.getInstance().getLogManager().warn("Failed to index storages");
            Backuper.getInstance().getLogManager().warn(e);
        } catch (InterruptedException ignored) {
            // There is no problem if indexing was interrupted by ScheduleManager
        }
        Backuper.getInstance().getLogManager().log("Storages indexing completed");
    }

    public void checkStoragesConnection() {
        for (Storage storage : storages.values()) {
            if (storage.checkConnection()){
                Backuper.getInstance().getLogManager().log("Connection with %s storage established successfully".formatted(storage.getId()));
            } else {
                Backuper.getInstance().getLogManager().warn("Failed to establish connection with %s storage".formatted(storage.getId()));
            }
        }
        sendUserAuthStoragesCheckResult(Bukkit.getConsoleSender());
    }

    public void destroy() {
        for (Storage storage : storages.values()) {
            storage.destroy();
        }
    }

    private void sendUserAuthStoragesCheckResult(CommandSender sender) {
        for (Storage storage : Backuper.getInstance().getStorageManager().getStorages()) {
            if (!(storage instanceof UserAuthStorage)) continue;

            if (sender.isOp() && !storage.checkConnection()) {
                Component header = Component.empty();
                header = header
                        .append(Component.text("%s storage account".formatted(storage.getId()))
                                .decorate(TextDecoration.BOLD)
                                .color(NamedTextColor.RED));

                Component message = Component.empty();
                message = message
                        .append(Component.text("%s storage is enabled, but account is not linked!")
                                .decorate(TextDecoration.BOLD)
                                .color(NamedTextColor.RED))
                        .append(Component.newline())
                        .append(Component.text("Use ")
                                .decorate(TextDecoration.BOLD)
                                .color(NamedTextColor.RED))
                        .append(Component.text("/backuper account %s link".formatted(storage.getId()))
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand("/backuper account %s link".formatted(storage.getId()))));

                UIUtils.sendFramedMessage(header, message, sender);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendUserAuthStoragesCheckResult(event.getPlayer());
    }
}
