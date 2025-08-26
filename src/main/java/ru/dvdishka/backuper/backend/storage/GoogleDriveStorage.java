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
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.fluent.Request;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.config.GoogleDriveConfig;
import ru.dvdishka.backuper.backend.config.StorageConfig;
import ru.dvdishka.backuper.backend.util.ObfuscateUtils;
import ru.dvdishka.backuper.backend.util.UIUtils;

import javax.naming.AuthenticationException;
import java.io.*;
import java.util.*;

public class GoogleDriveStorage implements Storage {

    private String id = null;
    private final GoogleDriveConfig config;
    private Drive driveService = null;
    private Credential credential = null;

    private static final String AUTH_SERVICE_URL = "https://auth.backuper-mc.com";
    private static final String APPLICATION_NAME = "BACKUPER";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> DRIVE_SCOPES = List.of(DriveScopes.DRIVE_FILE);
    private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final int RETRIES = 5;

    public GoogleDriveStorage(GoogleDriveConfig config) {
        this.config = config;
    }

    public Credential authorizeForced(CommandSender sender) throws AuthenticationException {
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

            this.credential = new MyAuthorizationCodeInstalledApp(flow).authorize("user", true, sender);
            return credential;
        } catch (Exception e) {
            throw new AuthenticationException("Failed to authorize user in Google Drive");
        }
    }

    private Credential returnCredentialIfAuthorized() throws StorageConnectionException {
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

            credential = flow.loadCredential("user");

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
            throw new StorageConnectionException("Failed to authorize user in Google Drive", e);
        }
    }

    private Drive getClient() throws StorageConnectionException {
        if (driveService != null) {
            try {
                // Test if the connection is still alive by making a simple request
                driveService.files().get("").execute().getName();
                return driveService;
            } catch (Exception ignored) {
                // Connection is dead, we'll create a new one
                driveService = null;
            }
        }

        try {
            Credential credential = returnCredentialIfAuthorized();
            if (credential == null) {
                throw new StorageConnectionException("Not authorized in Google Drive!");
            }

            driveService = new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .setHttpRequestInitializer(httpRequest -> {
                        credential.initialize(httpRequest);
                        httpRequest.setConnectTimeout(300 * 60000);
                        httpRequest.setReadTimeout(300 * 60000);
                    })
                    .build();

            return driveService;
        } catch (Exception e) {
            throw new StorageConnectionException("Not authorized in Google Drive!", e);
        }
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public Backup.StorageType getType() {
        return Backup.StorageType.GOOGLE_DRIVE;
    }

    @Override
    public StorageConfig getConfig() {
        return config;
    }

    @Override
    public boolean checkConnection() {
        return checkConnection(null);
    }

    @Override
    public boolean checkConnection(CommandSender sender) {
        try {
            if (!config.isEnabled()) {
                Backuper.getInstance().getLogManager().warn("Google Drive storage is disabled in config.yml", sender);
                return false;
            }

            getClient();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Not authorized in Google Drive", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" To get all files
     * @return Returns files ids
     **/
    @Override
    public List<String> ls(String driveFileId) throws StorageQuotaExceededException {
        return ls(driveFileId, null);
    }

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" to get all files
     * @param query       Additional parameters to find some file faster. "appProperties has { key='backuper' and value='true' }" will be added in the query string anyway
     * @return Returns files ids
     * */
    public List<String> ls(String driveFileId, String query) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            return ((Retriable<List<String>>) () -> {
                Drive service = getClient();
                String pageToken = null;
                List<String> driveFiles = new ArrayList<>();

                do {
                    Drive.Files.List lsRequest = service.files().list()
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

                    driveFiles.addAll(driveFileList.getFiles().stream().map(com.google.api.services.drive.model.File::getId).toList());

                    pageToken = driveFileList.getNextPageToken();

                } while (pageToken != null);

                return driveFiles;
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get file list from dir \"%s\" using Google Drive connection".formatted(driveFileId), e);
        }
    }

    /***
     @return Returns file's id or null if file doesn't exist
     */
    @Override
    public String resolve(String path, String fileName) {
        com.google.api.services.drive.model.File file = getFileByName(fileName, path);
        if (file == null) {
            return null;
        }
        return file.getId();
    }

    @Override
    public long getDirByteSize(String fileId) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            return ((Retriable<Long>) () -> {
                Drive service = getClient();
                
                if (!isFile(fileId)) {
                    long size = 0;
                    List<String> files = ls(fileId);
                    for (String file : files) {
                        size += getDirByteSize(file);
                    }
                    return size;
                } else {
                    com.google.api.services.drive.model.File driveFile = service.files().get(fileId).setFields("size").execute();
                    Long size = driveFile.getSize();
                    return size != null ? size : 0;
                }
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get file byte size for \"%s\"".formatted(fileId), e);
        }
    }

    @Override
    public boolean isFile(String driveFileId) throws StorageMethodException, StorageConnectionException, StorageQuotaExceededException {
        try {
            return ((Retriable<Boolean>) () -> {
                Drive service = getClient();
                return service.files().get(driveFileId)
                        .setFields("mimeType")
                        .execute()
                        .getMimeType()
                        .equals(FOLDER_MIME_TYPE);
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to check if file is folder on Google Drive", e);
        }
    }

    public com.google.api.services.drive.model.File getFileByName(String fileName, String parentId) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            return ((Retriable<com.google.api.services.drive.model.File>) () -> {
                Drive service = getClient();
                
                String q = "";
                q += "name = '%s'".formatted(fileName);
                q += " and appProperties has { key='backuper' and value='true' }";

                Drive.Files.List lsRequest = service.files().list();

                if (parentId != null && !parentId.isEmpty()) {
                    q += " and '%s' in parents".formatted(parentId);
                }

                lsRequest.setQ(q);

                FileList driveFileList = lsRequest.execute();
                return !driveFileList.getFiles().isEmpty() ? driveFileList.getFiles().getFirst() : null;
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get file by name \"%s\"".formatted(fileName), e);
        }
    }

    public void createDir(String parentFolderId, String folderName) throws StorageQuotaExceededException, StorageLimitException, StorageMethodException, StorageConnectionException {
        createDir(folderName, parentFolderId, new HashMap<>());
    }

    public void createDir(String folderName, String parentFolderId, Map<String, String> properties) throws StorageQuotaExceededException, StorageLimitException, StorageMethodException, StorageConnectionException {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getClient();

                properties.put("backuper", "true");

                com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
                driveFileMeta.setName(folderName);
                driveFileMeta.setAppProperties(properties);
                if (!Objects.equals(parentFolderId, "")) {
                    driveFileMeta.setParents(List.of(parentFolderId));
                }
                driveFileMeta.setMimeType(FOLDER_MIME_TYPE);

                return null;
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to create folder on Google Drive", e);
        }
    }

    /**
     * @param file           Local file to upload
     * @param newFileName       GoogleDrive new file name
     * @param parentDirId GoogleDrive parent folder ID or an empty string
     * @param progressListener Progress listener to update with upload progress
     **/
    @Override
    public void uploadFile(File file, String newFileName, String parentDirId, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException, Storage.StorageLimitException, Storage.StorageQuotaExceededException {
        if (!file.exists()) {
            throw new StorageMethodException("Local file \"%s\" doesn't exist".formatted(file.getAbsolutePath()));
        }

        try {
            ((Retriable<Void>) () -> {
                Drive service = getClient();

                Map<String, String> fileAppProperties = new HashMap<>();
                fileAppProperties.put("backuper", "true");

                com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
                driveFileMeta.setAppProperties(fileAppProperties);
                driveFileMeta.setName(newFileName);
                if (!Objects.equals(parentDirId, "")) {
                    driveFileMeta.setParents(List.of(parentDirId));
                }

                FileContent driveFileContent = new FileContent("", file);

                Drive.Files.Create driveFileCreate = service.files()
                        .create(driveFileMeta, driveFileContent)
                        .setUploadType("resumable")
                        .setFields("id, parents, appProperties");
                driveFileCreate.getMediaHttpUploader().

                        setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                driveFileCreate.getMediaHttpUploader().

                        setProgressListener(new GoogleDriveStorageProgressListener(progressListener));

                return null;
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to upload file to Google Drive", e);
        }
    }

    /**
     * @param sourceStream    inputStream to upload
     * @param newFileName       GoogleDrive new file name
     * @param parentDirId GoogleDrive parent folder ID or an empty string
     **/
    public void uploadFile(InputStream sourceStream, String newFileName, String parentDirId, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException, Storage.StorageLimitException, Storage.StorageQuotaExceededException {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getClient();

                Map<String, String> fileAppProperties = new HashMap<>();
                fileAppProperties.put("backuper", "true");

                com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
                driveFileMeta.setAppProperties(fileAppProperties);
                driveFileMeta.setName(newFileName);
                if (!Objects.equals(parentDirId, "")) {
                    driveFileMeta.setParents(List.of(parentDirId));
                }

                Drive.Files.Create driveFileCreate = service.files()
                        .create(driveFileMeta, new InputStreamContent("", sourceStream))
                        .setUploadType("resumable")
                        .setFields("id, parents, appProperties");
                driveFileCreate.getMediaHttpUploader().setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                driveFileCreate.getMediaHttpUploader().setProgressListener(new GoogleDriveStorageProgressListener(progressListener));

                return null;
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to upload file to Google Drive", e);
        }
    }

    @Override
    public void downloadFile(String fileId, File targetFile, StorageProgressListener progressListener) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            ((Retriable<Void>) () -> {
                try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                    Drive service = getClient();

                    Drive.Files.Get getDriveFile = service.files()
                            .get(fileId);
                    getDriveFile.getMediaHttpDownloader().setProgressListener(new GoogleDriveStorageProgressListener(progressListener));
                    getDriveFile.executeMediaAndDownloadTo(outputStream);
                    return null;
                }
            }).retry(RETRIES);
        } catch (Storage.StorageLimitException | IOException e) {
            throw new StorageMethodException("Failed to download file from Google Drive", e);
        }
    }

    @Override
    public void delete(String id) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getClient();
                service.files().delete(id).execute();
                return null;
            }).retry(RETRIES);
        } catch (IOException | Storage.StorageLimitException e) {
            throw new StorageMethodException("Failed to delete \"%s\" file from Google Drive".formatted(id), e);
        }
    }

    @Override
    public void renameFile(String fileId, String newFileName) throws StorageQuotaExceededException, StorageMethodException, StorageConnectionException {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getClient();

                service.files().update(fileId, new com.google.api.services.drive.model.File()
                                .setName(newFileName))
                        .setFields("name")
                        .execute();
                return null;
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to rename file \"%s\" to \"%s\" using Google Drive connection".formatted(fileId, newFileName), e);
        }
    }

    public void disconnect() {
        try {
            // Google Drive doesn't require explicit disconnection like FTP/SFTP
            // The Drive service will be garbage collected
            driveService = null;
            credential = null;
        } catch (Exception ignored) {
            // Ignore disconnect errors
        }
    }

    @FunctionalInterface
    private interface Retriable<T> {

        int RATE_LIMIT_DELAY_MILLIS = 10000;

        T run() throws IOException, Storage.StorageLimitException, Storage.StorageQuotaExceededException;

        default T retry(int retries) throws Storage.StorageLimitException, Storage.StorageQuotaExceededException, IOException {
            int completedRetries = 0;
            while (completedRetries < retries) {
                try {
                    return run();
                } catch (Exception e) {
                    completedRetries++;

                    if (completedRetries == retries) {
                        if (e instanceof GoogleJsonResponseException googleJsonResponseException) {
                            if (googleJsonResponseException.getDetails().getCode() == 401) {
                                throw new StorageConnectionException("Failed to authorize user in Google Drive");
                            }
                            if (googleJsonResponseException.getDetails().getErrors() != null) {
                                if (googleJsonResponseException.getDetails().getErrors().stream().anyMatch(errorInfo -> errorInfo.getReason().equals("storageQuotaExceeded"))) {
                                    throw new StorageLimitException(Backup.StorageType.GOOGLE_DRIVE);
                                }
                                if (googleJsonResponseException.getDetails().getErrors().stream().anyMatch(errorInfo -> errorInfo.getReason().equals("rateLimitExceeded"))) {
                                    Backuper.getInstance().getLogManager().devWarn("Rate limit exceeded, retry in %s seconds...".formatted(RATE_LIMIT_DELAY_MILLIS / 1000));
                                    try {
                                        Thread.sleep(RATE_LIMIT_DELAY_MILLIS);
                                    } catch (Exception ignored) {
                                        // No need to handle wait interruption
                                    }
                                    throw new StorageQuotaExceededException(Backup.StorageType.GOOGLE_DRIVE);
                                }
                            }
                        }
                        throw e;
                    }

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
            }
            return null; // Never reached
        }
    }

    private static class MyAuthorizationCodeInstalledApp {

        private final AuthorizationCodeFlow flow;

        public MyAuthorizationCodeInstalledApp(AuthorizationCodeFlow flow) {
            this.flow = flow;
        }

        protected void onAuthorization(String id, CommandSender sender) {

            String url = "%s/authgd?id=%s".formatted(AUTH_SERVICE_URL, id);

            Component header = Component.empty()
                    .append(Component.text("Account linking"));

            Component message = Component.empty()
                    .append(Component.text("Log in to your Google Account:")
                            .color(TextColor.fromHexString("#129c9b")))
                    .append(Component.space())
                    .append(Component.text(url)
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, url))
                            .decorate(TextDecoration.UNDERLINED));

            UIUtils.sendFramedMessage(header, message, sender);
        }

        public Credential authorize(String userId, boolean force, CommandSender sender) throws AuthenticationException {

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

            String id = generateId();
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
                throw new AuthenticationException("Failed to get authGD server response");
            }
            if (t >= 300) {
                throw new AuthenticationException("Failed to get authGD server response");
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
                throw new RuntimeException("Failed to save Google Drive credentials", e);
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
