package ru.dvdishka.backuper.backend.storage;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.fluent.Request;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.GoogleDriveConfig;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.exception.StorageQuotaExceededException;
import ru.dvdishka.backuper.backend.storage.util.Retriable;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressInputStream;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.ObfuscateUtils;
import ru.dvdishka.backuper.backend.util.UIUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GoogleDriveStorage implements UserAuthStorage {

    @Setter
    private String id = null;
    private final GoogleDriveConfig config;
    private final BackupManager backupManager;

    private Credential credential = null;

    private final GoogleDriveClientProvider mainClient;

    private static final String AUTH_SERVICE_URL = "https://auth.backuper-mc.com";
    private static final String APPLICATION_NAME = "BACKUPER";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> DRIVE_SCOPES = List.of(DriveScopes.DRIVE_FILE);
    private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    Cache<Pair<String, String>, List<File>> cacheLs = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .expireAfterAccess(5, TimeUnit.SECONDS)
            .build();

    private final ru.dvdishka.backuper.backend.storage.util.Retriable.RetriableExceptionHandler retriableExceptionHandler = new ru.dvdishka.backuper.backend.storage.util.Retriable.RetriableExceptionHandler() {

        final int RATE_LIMIT_DELAY_MILLIS = 10000;

        @Override
        public void handleRegularException(Exception e) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
            if (e instanceof GoogleJsonResponseException googleJsonResponseException) {
                if (googleJsonResponseException.getDetails().getErrors() != null && googleJsonResponseException.getDetails().getErrors().stream().anyMatch(errorInfo -> errorInfo.getReason().equals("rateLimitExceeded"))) {
                    Backuper.getInstance().getLogManager().devWarn("Rate limit exceeded, retry in %s seconds...".formatted(RATE_LIMIT_DELAY_MILLIS / 1000));
                    try {
                        Thread.sleep(RATE_LIMIT_DELAY_MILLIS);
                    } catch (Exception ignored) {
                        // No need to handle wait interruption
                    }
                }
            }
        }

        @Override
        public RuntimeException handleFinalException(Exception e) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
            if (e instanceof GoogleJsonResponseException googleJsonResponseException) {
                if (googleJsonResponseException.getDetails().getCode() == 401) {
                    return new StorageConnectionException(getStorage(), "Failed to authorize user in Google Drive");
                }
                if (googleJsonResponseException.getDetails().getErrors() != null) {
                    if (googleJsonResponseException.getDetails().getErrors().stream().anyMatch(errorInfo -> errorInfo.getReason().equals("storageQuotaExceeded"))) {
                        return new StorageLimitException(getStorage(), "Storage limit exceeded");
                    }
                    if (googleJsonResponseException.getDetails().getErrors().stream().anyMatch(errorInfo -> errorInfo.getReason().equals("rateLimitExceeded"))) {
                        Backuper.getInstance().getLogManager().devWarn("Rate limit exceeded, retry in %s seconds...".formatted(RATE_LIMIT_DELAY_MILLIS / 1000));
                        try {
                            Thread.sleep(RATE_LIMIT_DELAY_MILLIS);
                        } catch (Exception ignored) {
                            // No need to handle wait interruption
                        }
                        return new StorageQuotaExceededException(getStorage(), "Storage quota limit exceeded");
                    }
                }
            }
            return new StorageMethodException(getStorage(), e.getMessage(), e);
        }

        public Storage getStorage() {
            return GoogleDriveStorage.this;
        }
    };

    public GoogleDriveStorage(GoogleDriveConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
        this.mainClient = new GoogleDriveClientProvider(this);
    }

    public void authorizeForced(CommandSender sender) throws StorageConnectionException {
        try {
            this.credential = null;

            GoogleClientSecrets clientSecrets = JSON_FACTORY
                    .fromString(ObfuscateUtils
                                    .decrypt(IOUtils
                                            .toString((Backuper.getInstance().getResource("google_cred.txt")))),
                            GoogleClientSecrets.class);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(config.getTokenFolder()))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            this.credential = new MyAuthorizationCodeInstalledApp(this, flow).authorize(id, true, sender);
            if (this.credential == null) throw new StorageConnectionException(this, "Failed to authorize user in %s storage".formatted(id));

            Component header = Component.empty()
                    .append(Component.text("Account linking"));

            Component message = Component.empty()
                    .append(Component.text("Account has been successfully linked to %s storage".formatted(id)));

            UIUtils.sendFramedMessage(header, message, sender);

        } catch (Exception e) {
            Component header = Component.empty()
                    .append(Component.text("Account linking"));

            Component message = Component.empty()
                    .append(Component.text("Failed to link account to %s storage:".formatted(this.id))
                            .color(NamedTextColor.RED));

            UIUtils.sendFramedMessage(header, message, sender);
            throw new StorageConnectionException(this, "Failed to authorize user in %s storage".formatted(id), e);
        }
    }

    Credential returnCredentialIfAuthorized() throws StorageConnectionException {
        try {
            boolean checkConnection = credential == null;

            GoogleClientSecrets clientSecrets = JSON_FACTORY
                    .fromString(ObfuscateUtils
                            .decrypt(IOUtils
                                    .toString((Backuper.getInstance().getResource("google_cred.txt")))),
                            GoogleClientSecrets.class);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(config.getTokenFolder()))
                    .setApprovalPrompt("force")
                    .setAccessType("offline")
                    .build();

            credential = flow.loadCredential(id);

            if (credential != null
                    && (credential.getRefreshToken() != null
                    || credential.getExpiresInSeconds() == null
                    || credential.getExpiresInSeconds() > 60)) {

                if (checkConnection) {
                    try {
                        Drive service = new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                                .setApplicationName(APPLICATION_NAME)
                                .setHttpRequestInitializer(httpRequest -> {
                                    credential.initialize(httpRequest);
                                    httpRequest.setConnectTimeout(300 * 60000);
                                    httpRequest.setReadTimeout(300 * 60000);
                                })
                                .build();

                        com.google.api.services.drive.model.File driveFile = service.files().get("").execute();
                        driveFile.getName(); // To check if everything works well

                    } catch (GoogleJsonResponseException e) {
                        if (e.getStatusCode() != 404) {
                            credential = null;
                            return null;
                        }
                    } catch (Exception e) {
                        credential = null;
                        return null;
                    }
                }

                return credential;
            }
            credential = null;
            return null;

        } catch (Exception e) {
            credential = null;
            throw new StorageConnectionException(this, "Failed to authorize user in Google Drive", e);
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public StorageType getType() {
        return StorageType.GOOGLE_DRIVE;
    }

    @Override
    public GoogleDriveConfig getConfig() {
        return config;
    }

    @Override
    public BackupManager getBackupManager() {
        return backupManager;
    }

    @Override
    public boolean checkConnection() {
        return checkConnection(null);
    }

    @Override
    public boolean checkConnection(CommandSender sender) {
        try {
            mainClient.getClient();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Not authorized in Google Drive", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    public void addProperty(String fileId, String key, String value) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            Drive service = mainClient.getClient();
            Map<String, String> appProperties = service.files().get(fileId).setFields("appProperties").execute().getAppProperties();
            appProperties.put(key, value);
            service.files().update(fileId, new com.google.api.services.drive.model.File()
                            .setAppProperties(appProperties))
                    .setFields("appProperties")
                    .execute();
            cacheLs.invalidateAll();
            return null;
        }).retry(retriableExceptionHandler);
    }

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" To get all files
     * @return Returns files names
     **/
    @Override
    public List<String> ls(String driveFileId) throws StorageQuotaExceededException {
        return ls(driveFileId, null).stream().map(com.google.api.services.drive.model.File::getName).distinct().toList();
    }

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" to get all files
     * @param query       Additional parameters to find some file faster. "appProperties has { key='backuper' and value='true' }" will be added in the query string anyway
     * @return Returns List of File objects that contains only the name and id of the file
     * */
    public List<com.google.api.services.drive.model.File> ls(String driveFileId, String query) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            return cacheLs.get(Pair.of(driveFileId, query), () -> {
                return ((Retriable<List<com.google.api.services.drive.model.File>>) () -> {
                    String pageToken = null;
                    List<com.google.api.services.drive.model.File> driveFiles = new ArrayList<>();
                    Drive service = mainClient.getClient();

                    do {
                        Drive.Files.List lsRequest = service.files().list()
                                .setFields("nextPageToken, files(id, name)")
                                .setPageSize(1000)
                                .setPageToken(pageToken);
                        String q = "appProperties has { key='backuper' and value='true' }";
                        if (query != null) {
                            q = "%s and %s".formatted(q, query);
                        }

                        if (driveFileId != null && driveFileId.equals("drive")) {
                            lsRequest = lsRequest.setSpaces("drive");
                        }

                        if (driveFileId != null && !driveFileId.isEmpty() && !driveFileId.equals("drive")) {
                            q = "%s and '%s' in parents".formatted(q, driveFileId);
                        }
                        lsRequest = lsRequest.setQ(q);

                        FileList driveFileList = lsRequest.execute();

                        driveFiles.addAll(driveFileList.getFiles());

                        pageToken = driveFileList.getNextPageToken();

                    } while (pageToken != null);

                    return driveFiles;
                }).retry(retriableExceptionHandler);
            });
        } catch (ExecutionException e) {
            throw new StorageMethodException(this, "Execution exception on ls", e);
        }
    }

    /***
     @return Returns a file's id or null if a file doesn't exist
     */
    @Override
    public String resolve(String path, String fileName) {
        if (fileName.isEmpty()) return path;
        com.google.api.services.drive.model.File file = getFileByName(fileName, path);
        if (file == null) {
            return null;
        }
        return file.getId();
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        try {
            Drive service = mainClient.getClient();
            service.files().get(path)
                    .setFields("mimeType")
                    .execute()
                    .getMimeType()
                    .equals(FOLDER_MIME_TYPE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getDirByteSize(String path) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        return ((Retriable<Long>) () -> {
            if (isDir(path)) {
                long size = 0;
                List<String> files = ls(path);
                for (String file : files) {
                    size += getDirByteSize(getFileByName(file, path).getId());
                }
                return size;
            } else {
                Drive service = mainClient.getClient();
                com.google.api.services.drive.model.File driveFile = service.files().get(path).setFields("size").execute();
                Long size = driveFile.getSize();
                return size != null ? size : 0;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public boolean isFile(String driveFileId) throws StorageMethodException, StorageConnectionException, StorageQuotaExceededException {
        return ((Retriable<Boolean>) () -> {
            Drive service = mainClient.getClient();
            return !service.files().get(driveFileId)
                    .setFields("mimeType")
                    .execute()
                    .getMimeType()
                    .equals(FOLDER_MIME_TYPE);
        }).retry(retriableExceptionHandler);
    }

    @Override
    public String getFileNameFromPath(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<String>) () -> {
            return mainClient.getClient().files().get(path).setFields("name").execute().getName();
        }).retry(retriableExceptionHandler);

    }

    @Override
    public String getParentPath(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<String>) () -> {
            return mainClient.getClient().files().get(path).setFields("parents").execute().getParents().getFirst();
        }).retry(retriableExceptionHandler);

    }

    /***
     * Returns file object that contain mimeType, size, name, id, parents, appProperties
     */
    public com.google.api.services.drive.model.File getFileByName(String fileName, String parentId) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        return ((Retriable<com.google.api.services.drive.model.File>) () -> {
            Drive service = mainClient.getClient();

            String q = "";
            q += "name = '%s'".formatted(fileName);
            q += " and appProperties has { key='backuper' and value='true' }";

            Drive.Files.List lsRequest = service.files().list();

            if (parentId != null && !parentId.isEmpty()) {
                q += " and '%s' in parents".formatted(parentId);
            }

            lsRequest.setQ(q);
            lsRequest.setFields("files(mimeType, size, name, id, parents, appProperties)");

            FileList driveFileList = lsRequest.execute();
            return !driveFileList.getFiles().isEmpty() ? driveFileList.getFiles().getFirst() : null;
        }).retry(retriableExceptionHandler);
    }

    public void createDir(String folderName, String parentFolderId) throws StorageQuotaExceededException, StorageLimitException, StorageMethodException, StorageConnectionException {
        createDir(folderName, parentFolderId, new HashMap<>());
    }

    public void createDir(String folderName, String parentFolderId, Map<String, String> properties) throws StorageQuotaExceededException, StorageLimitException, StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            Drive service = mainClient.getClient();

            properties.put("backuper", "true");

            com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
            driveFileMeta.setName(folderName);
            driveFileMeta.setAppProperties(properties);
            if (!Objects.equals(parentFolderId, "")) {
                driveFileMeta.setParents(List.of(parentFolderId));
            }
            driveFileMeta.setMimeType(FOLDER_MIME_TYPE);
            service.files().create(driveFileMeta).execute();
            cacheLs.invalidateAll();

            return null;
        }).retry(retriableExceptionHandler);
    }

    /**
     * @param sourceStream    inputStream to upload
     * @param newFileName       GoogleDrive new file name
     * @param targetParentDir GoogleDrive parent folder ID or an empty string
     **/
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
        ((Retriable<Void>) () -> {
            Drive service = mainClient.getClient();

            Map<String, String> fileAppProperties = new HashMap<>();
            fileAppProperties.put("backuper", "true");

            com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
            driveFileMeta.setAppProperties(fileAppProperties);
            driveFileMeta.setName(newFileName);
            if (!Objects.equals(targetParentDir, "")) {
                driveFileMeta.setParents(List.of(targetParentDir));
            }

            // getMediaHttpUploader().setProgressListener() doesn't work so we should just wrap input stream to track its progress
            com.google.api.client.http.InputStreamContent contentStream = new InputStreamContent("", new StorageProgressInputStream(sourceStream, progressListener));
            contentStream.setCloseInputStream(false);
            Drive.Files.Create driveFileCreate = service.files()
                    .create(driveFileMeta, contentStream)
                    .setUploadType("resumable")
                    .setFields("id, parents, appProperties");
            driveFileCreate.getMediaHttpUploader().setChunkSize(MediaHttpUploader.DEFAULT_CHUNK_SIZE);

            driveFileCreate.execute();
            cacheLs.invalidateAll();
            return null;
        }).retry(retriableExceptionHandler);
    }

    @Override
    public InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        return ((Retriable<InputStream>) () -> {
            Drive service = mainClient.getClient();

            Drive.Files.Get getDriveFile = service.files().get(sourcePath);
            getDriveFile.getMediaHttpDownloader().setChunkSize(MediaHttpDownloader.MAXIMUM_CHUNK_SIZE);
            // getMediaHttpDownloader().setProgressListener() doesn't work so we should just wrap input stream to track its progress
            return new StorageProgressInputStream(getDriveFile.executeMediaAsInputStream(), progressListener);
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void delete(String id) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            Drive service = mainClient.getClient();
            service.files().delete(id).execute();
            cacheLs.invalidateAll();
            return null;
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void renameFile(String fileId, String newFileName) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            Drive service = mainClient.getClient();

            service.files().update(fileId, new com.google.api.services.drive.model.File()
                            .setName(newFileName))
                    .setFields("name")
                    .execute();
            cacheLs.invalidateAll();
            return null;
        }).retry(retriableExceptionHandler);
    }

    @Override
    public int getStorageSpeedMultiplier() {
        return 15;
    }

    @Override
    public void destroy() {
        mainClient.disconnect();
        credential = null;
    }

    @Override
    public void downloadCompleted() throws StorageMethodException, StorageConnectionException {
        // No actions required
    }

    private static class MyAuthorizationCodeInstalledApp {

        private final Storage storage;
        private final AuthorizationCodeFlow flow;

        public MyAuthorizationCodeInstalledApp(Storage storage, AuthorizationCodeFlow flow) {
            this.storage = storage;
            this.flow = flow;
        }

        protected void onAuthorization(String id, CommandSender sender) {

            String url = "%s/authgd?id=%s".formatted(AUTH_SERVICE_URL, id);

            Component header = Component.empty()
                    .append(Component.text("Account linking"));

            Component message = Component.empty()
                    .append(Component.space())
                    .append(Component.text(url)
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, url))
                            .decorate(TextDecoration.UNDERLINED));

            UIUtils.sendFramedMessage(header, message, sender);
        }

        public Credential authorize(String userId, boolean force, CommandSender sender) {

            if (!force) {
                try {
                    Credential credential = flow.loadCredential(userId);
                    if (credential != null
                            && (credential.getRefreshToken() != null
                            || credential.getExpiresInSeconds() == null
                            || credential.getExpiresInSeconds() > 60)) {
                        return credential;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load Google Drive credentials", e);
                }
            }

            String id = userId + generateId();
            onAuthorization(id, sender);
            String response = null;

            // Get token from AuthGD service
            int t = 0;
            try {
                while (t < 300) {

                    String result;
                    // Check if AuthGD is down
                    try {
                        result = Request.Get("%s/getgd?id=%s".formatted(AUTH_SERVICE_URL, id)).execute().returnContent().asString();
                    } catch (Exception e) {
                        Backuper.getInstance().getLogManager().warn("Google authentication failed. Probably backuper-mc.com is down, inform developer on GitHub", sender);
                        Backuper.getInstance().getLogManager().devWarn(e);
                        return null;
                    }

                    if (!result.equals("null") && !result.equals("wrong")) {
                        response = result;
                        break;
                    }

                    Thread.sleep(1000);
                    t++;
                }
            } catch (Exception e) {
                throw new StorageConnectionException(storage, "Failed to get authGD server response", e);
            }
            if (t >= 300) {
                throw new StorageConnectionException(storage, "Failed to get authGD server response");
            }

            Gson gson = new GsonBuilder().create();
            HashMap<String, Object> responseJson = gson.fromJson(response, HashMap.class);

            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.setAccessToken((String) responseJson.get("access_token"));
            tokenResponse.setScope((String) responseJson.get("scope"));
            tokenResponse.setTokenType((String) responseJson.get("token_type"));
            tokenResponse.setTokenType((String) responseJson.get("token_type"));
            tokenResponse.setExpiresInSeconds(((Double) responseJson.get("expires_in")).longValue());
            tokenResponse.setRefreshToken((String) responseJson.get("refresh_token"));

            try {
                return flow.createAndStoreCredential(tokenResponse, userId);
            } catch (IOException e) {
                throw new StorageConnectionException(storage, "Failed to save Google Drive credentials", e);
            }
        }

        private String generateId() {

            StringBuilder id = new StringBuilder();
            Random rand = new Random();
            for (int i = 0; i < 16; i++) {
                int r = rand.nextInt(0, 62);

                if (r < 10) {
                    id.append((char) ('0' + r));
                } else if (r - 10 < 26) {
                    id.append((char) ('A' + r - 10));
                } else {
                    id.append((char) ('a' + r - 36));
                }
            }
            return id.toString();
        }
    }

    private static class GoogleDriveStorageProgressListener implements MediaHttpUploaderProgressListener, MediaHttpDownloaderProgressListener {

        private final StorageProgressListener progressListener;
        long progress = 0;

        public GoogleDriveStorageProgressListener(StorageProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void progressChanged(MediaHttpUploader mediaHttpUploader) {
            progressListener.incrementProgress(mediaHttpUploader.getNumBytesUploaded() - progress);
            progress = mediaHttpUploader.getNumBytesUploaded();
        }

        @Override
        public void progressChanged(MediaHttpDownloader mediaHttpDownloader) {
            progressListener.incrementProgress(mediaHttpDownloader.getNumBytesDownloaded() - progress);
            progress = mediaHttpDownloader.getNumBytesDownloaded();
        }
    }
}
