package ru.dvdishka.backuper.backend.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

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

    public String getSizeCacheJson() {

        HashMap<String, ConcurrentMap<String, Long>> jsonedCache = new HashMap<>();

        for (Storage storage : storages.values()) {
            jsonedCache.put(storage.getId(), storage.getBackupManager().getSizeCache());
        }

        String json = gson.toJson(jsonedCache);
        return json;
    }

    public void loadSizeCache(String json) {
        if (json.isEmpty()) {
            return;
        }

        Type typeToken = new TypeToken<HashMap<String, HashMap<String, Long>>>() {}.getType();
        HashMap<String, HashMap<String, Long>> jsonedCache = gson.fromJson(json, typeToken);

        for (Storage storage : storages.values()) {
            if (!jsonedCache.containsKey(storage.getId())) {
                continue;
            }
            storage.getBackupManager().loadSizeCache(jsonedCache.get(storage.getId()));
        }
    }
}
