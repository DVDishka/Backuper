package ru.dvdishka.backuper.backend.storage;

import ru.dvdishka.backuper.backend.config.PathStorageConfig;

public interface PathStorage extends Storage {

    PathStorageConfig getConfig();

    @Override
    default String getFileNameFromPath(String path) throws StorageMethodException, StorageConnectionException {
        return path.substring(path.lastIndexOf(getConfig().getPathSeparatorSymbol()) + 1);
    }

    @Override
    default String getParentPath(String path) throws StorageMethodException, StorageConnectionException {
        return path.substring(0, path.lastIndexOf(getConfig().getPathSeparatorSymbol()) == -1 ? 0 : path.lastIndexOf(getConfig().getPathSeparatorSymbol()));
    }
}
