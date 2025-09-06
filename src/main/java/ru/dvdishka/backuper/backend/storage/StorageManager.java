package ru.dvdishka.backuper.backend.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.jorel.commandapi.executors.CommandArguments;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.list.ListCommand;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public class StorageManager {

    private final HashMap<String, Storage> storages = new HashMap<>();

    private final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

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

    public List<Storage> getStorages() {
        return new ArrayList<>(storages.values());
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

        for (Storage storage : storages.values()) {
            if (storage.getConfig().isEnabled()){
                CompletableFuture<Void> indexStorageFuture = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                        try {
                            Backuper.getInstance().getLogManager().devLog("Indexing local storage...");
                            new ListCommand(storage.getId(), false, null, new CommandArguments(new Objects[]{}, new HashMap<String, Object>(), new String[]{}, new HashMap<String, String>(), "")).execute();
                            Backuper.getInstance().getLogManager().devLog("Local storage has been indexed");
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
            if (storage.getConfig().isEnabled() && storage.checkConnection()){
                Backuper.getInstance().getLogManager().log("Connection with %s storage established successfully".formatted(storage.getId()));
            }
        }
    }
}
