package ru.dvdishka.backuper.backend.util;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
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
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.exception.StorageQuotaExceededException;

import javax.naming.AuthenticationException;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.*;

public class GoogleDriveUtils {

    private static File tokensFolder;
    private static Credential credential = null;

    private static final String AUTO_SERVICE_URL = "https://auth.backuper-mc.com";

    private static final String APPLICATION_NAME = "BACKUPER";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> DRIVE_SCOPES = List.of(DriveScopes.DRIVE_FILE);
    private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final int RETRIES = 5;

    public static void init() {
        tokensFolder = Config.getInstance().getGoogleDriveConfig().getTokenFolder();
    }

    public static Credential returnCredentialIfAuthorized() throws AuthenticationException {
        try {
            boolean checkConnection = credential == null;

            GoogleClientSecrets clientSecrets = JSON_FACTORY
                    .fromString(ObfuscateUtils
                            .decrypt(IOUtils
                                    .toString((Backuper.getInstance().getResource("google_cred.txt")))),
                            GoogleClientSecrets.class);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensFolder))
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
            throw new AuthenticationException("Failed to authorize user in Google Drive");
        }
    }

    /***\
     * Checks if the Google Drive is available. Sends warning to the console and sender if not
     */
    public static boolean checkConnection() {
        return checkConnection(null);
    }

    /***\
     * Checks if the Google Drive is available. Sends warning to the console and sender if not
     */
    public static boolean checkConnection(CommandSender sender) {
        if (!Config.getInstance().getGoogleDriveConfig().isEnabled()) {
            Backuper.getInstance().getLogManager().warn("Google Drive storage is disabled in config.yml", sender);
            return false;
        }
        try {
            if (returnCredentialIfAuthorized() == null) {
                Backuper.getInstance().getLogManager().warn("Failed to establish connection to the Google Drive", sender);
                return false;
            }
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the Google Drive", sender);
            return false;
        }
    }

    public static Credential authorizeForced(CommandSender sender) throws AuthenticationException {
        try {
            credential = null;

            GoogleClientSecrets clientSecrets = JSON_FACTORY
                    .fromString(ObfuscateUtils
                                    .decrypt(IOUtils
                                            .toString((Backuper.getInstance().getResource("google_cred.txt")))),
                            GoogleClientSecrets.class);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensFolder))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            credential = new MyAuthorizationCodeInstalledApp(flow).authorize("user", true, sender);
            return credential;
        } catch (Exception e) {
            throw new AuthenticationException("Failed to authorize user in Google Drive");
        }
    }

    private static Drive drive = null;

    /**
     * @return Drive service or null if Google Drive is not authorized. It caches the first taken service and returns the cached value for next invocations
     */
    public static Drive getService() {
        if (drive != null) {
            return drive;
        }

        Credential credential;
        try {
            credential = returnCredentialIfAuthorized();
        } catch (AuthenticationException e) {
            throw new RuntimeException(e);
        }
        if (credential == null) {
            throw new RuntimeException("Not authorized in Google Drive!");
        }

        drive = new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .setHttpRequestInitializer(httpRequest -> {
                    credential.initialize(httpRequest);
                    httpRequest.setConnectTimeout(300 * 60000);
                    httpRequest.setReadTimeout(300 * 60000);
                })
                .build();

        return drive;
    }

    public static void addProperty(String fileId, String key, String value) {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getService();
                Map<String, String> appProperties = service.files().get(fileId).setFields("appProperties").execute().getAppProperties();
                appProperties.put(key, value);

                service.files().update(fileId, new com.google.api.services.drive.model.File()
                                .setAppProperties(appProperties))
                        .setFields("appProperties")
                        .execute();
                return null;
            }).retry(RETRIES);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add property to Google Drive file", e);
        }
    }

    /**
     * @param file           Local file to upload
     * @param parentFolderId GoogleDrive parent folder ID or an empty string
     * @return Returns new file's id
     **/
    public static String uploadFile(File file, String parentFolderId, MediaHttpUploaderProgressListener progressListener) throws StorageLimitException, StorageQuotaExceededException {
        return uploadFile(file, file.getName(), parentFolderId, progressListener); // No need to make it retriable because nested uploadFile is already retriable
    }

    /**
     * @param file           Local file to upload
     * @param fileName       GoogleDrive new file name
     * @param parentFolderId GoogleDrive parent folder ID or an empty string
     * @return Returns new file's id
     **/
    public static String uploadFile(File file, String fileName, String parentFolderId, MediaHttpUploaderProgressListener progressListener) throws StorageLimitException, StorageQuotaExceededException {
        if (!file.exists()) {
            throw new RuntimeException("Failed to upload file to Google Drive", new NoSuchFileException(file.getAbsolutePath()));
        }

        try {
            return ((Retriable<String>) () -> {
                Drive service = getService();

                Map<String, String> fileAppProperties = new HashMap<>();
                fileAppProperties.put("backuper", "true");

                com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
                driveFileMeta.setAppProperties(fileAppProperties);
                driveFileMeta.setName(fileName);
                if (!Objects.equals(parentFolderId, "")) {
                    driveFileMeta.setParents(List.of(parentFolderId));
                }

                FileContent driveFileContent = new FileContent("", file);

                Drive.Files.Create driveFileCreate = service.files()
                        .create(driveFileMeta, driveFileContent)
                        .setUploadType("resumable")
                        .setFields("id, parents, appProperties");
                driveFileCreate.getMediaHttpUploader().

                        setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                driveFileCreate.getMediaHttpUploader().

                        setProgressListener(progressListener);

                return driveFileCreate.execute().getId();
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Google Drive", e);
        }
    }

    /**
     * @param inputStream    inputStream to upload
     * @param fileName       GoogleDrive new file name
     * @param parentFolderId GoogleDrive parent folder ID or an empty string
     * @return Returns new file's id
     **/
    public static String uploadFile(InputStream inputStream, String fileName, String parentFolderId, MediaHttpUploaderProgressListener progressListener) throws StorageLimitException, StorageQuotaExceededException {
        try {
            return ((Retriable<String>) () -> {
                Drive service = getService();

                Map<String, String> fileAppProperties = new HashMap<>();
                fileAppProperties.put("backuper", "true");

                com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
                driveFileMeta.setAppProperties(fileAppProperties);
                driveFileMeta.setName(fileName);
                if (!Objects.equals(parentFolderId, "")) {
                    driveFileMeta.setParents(List.of(parentFolderId));
                }

                Drive.Files.Create driveFileCreate = service.files()
                        .create(driveFileMeta, new InputStreamContent("", inputStream))
                        .setUploadType("resumable")
                        .setFields("id, parents, appProperties");
                driveFileCreate.getMediaHttpUploader().setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                driveFileCreate.getMediaHttpUploader().setProgressListener(progressListener);

                return driveFileCreate.execute().getId();
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Google Drive", e);
        }
    }

    /***
     * @return Returns new folder's id
     */
    public static String createFolder(String folderName, String parentFolderId, Map<String, String> properties) throws StorageLimitException, StorageQuotaExceededException {
        try {
            return ((Retriable<String>) () -> {
                Drive service = getService();

                properties.put("backuper", "true");

                com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
                driveFileMeta.setName(folderName);
                driveFileMeta.setAppProperties(properties);
                if (!Objects.equals(parentFolderId, "")) {
                    driveFileMeta.setParents(List.of(parentFolderId));
                }
                driveFileMeta.setMimeType(FOLDER_MIME_TYPE);

                return service.files().create(driveFileMeta).setFields("appProperties, id, parents").execute().getId();
            }).retry(RETRIES);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create folder on Google Drive", e);
        }
    }

    /***
     * @return Returns new folder's id
     */
    public static String createFolder(String folderName, String parentFolderId) throws StorageLimitException, StorageQuotaExceededException {
        return createFolder(folderName, parentFolderId, new HashMap<>());
    }

    public static void downloadFile(String fileId, File targetFile, MediaHttpDownloaderProgressListener progressListener) throws StorageQuotaExceededException {
        try {
            ((Retriable<Void>) () -> {
                try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                    Drive service = getService();

                    Drive.Files.Get getDriveFile = service.files()
                            .get(fileId);
                    getDriveFile.getMediaHttpDownloader().setProgressListener(progressListener);
                    getDriveFile.executeMediaAndDownloadTo(outputStream);
                    return null;
                }
            }).retry(RETRIES);
        } catch (StorageLimitException | IOException e) {
            throw new RuntimeException("Failed to download file from Google Drive", e);
        }
    }

    public static boolean isFolder(String driveFileId) throws StorageQuotaExceededException {
        try {
            return ((Retriable<Boolean>) () -> {
                Drive service = getService();
                return service.files().get(driveFileId)
                        .setFields("mimeType")
                        .execute()
                        .getMimeType()
                        .equals(FOLDER_MIME_TYPE);
            }).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to check if file is folder on Google Drive", e);
        }
    }

    private static class MyAuthorizationCodeInstalledApp {

        private final AuthorizationCodeFlow flow;

        public MyAuthorizationCodeInstalledApp(AuthorizationCodeFlow flow) {
            this.flow = flow;
        }

        protected void onAuthorization(String id, CommandSender sender) {

            String url = AUTO_SERVICE_URL + "/authgd?id=" + id;

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
                        result = Request.Get("%s/getgd?id=%s".formatted(AUTO_SERVICE_URL, id)).execute().returnContent().asString();
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

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" to get all files
     * @param query       Additional parameters to find some file faster. "appProperties has { key='backuper' and value='true' }" will be added in the query string anyway
     **/
    public static List<com.google.api.services.drive.model.File> ls(String driveFileId, String query) throws StorageQuotaExceededException {

        try {
            return ((Retriable<List<com.google.api.services.drive.model.File>>) () -> {
                Drive service = getService();
                String pageToken = null;
                ArrayList<com.google.api.services.drive.model.File> driveFiles = new ArrayList<>();

                do {

                    Drive.Files.List lsRequest = service.files().list()
                            .setPageToken(pageToken);
                    String q = "appProperties has { key='backuper' and value='true' }";
                    if (query != null) {
                        q += " and " + query;
                    }

                    if (driveFileId != null && driveFileId.equals("drive")) {
                        lsRequest = lsRequest.setSpaces("drive");
                    }

                    if (driveFileId != null && !driveFileId.isEmpty() && !driveFileId.equals("drive")) {
                        q += " and '" + driveFileId + "'" + " in parents";
                    }
                    lsRequest = lsRequest.setQ(q);

                    FileList driveFileList = lsRequest.execute();

                    driveFiles.addAll(driveFileList.getFiles());

                    pageToken = driveFileList.getNextPageToken();

                } while (pageToken != null);

                return driveFiles;
            }).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to get Google Drive credentials", e);
        }
    }

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" To get all files
     **/
    public static List<com.google.api.services.drive.model.File> ls(String driveFileId) throws StorageQuotaExceededException {
        return ls(driveFileId, null);
    }

    public static void renameFile(String fileId, String newName) throws StorageQuotaExceededException {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getService();
                service.files().update(fileId, new com.google.api.services.drive.model.File()
                                .setName(newName))
                        .setFields("name")
                        .execute();
                return null;
            }).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to rename file on Google Drive", e);
        }
    }

    public static void deleteFile(String fileId) throws StorageQuotaExceededException {
        try {
            ((Retriable<Void>) () -> {
                Drive service = getService();
                service.files().delete(fileId).execute();
                return null;
            }).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to delete file on Google Drive", e);
        }
    }

    public static com.google.api.services.drive.model.File getFileByName(String fileName, String parentId) throws StorageQuotaExceededException {
        try {
            return ((Retriable<com.google.api.services.drive.model.File>) () -> {
                String q = "";
                q += "name = '" + fileName + "'";
                q += " and appProperties has { key='backuper' and value='true' }";

                Drive.Files.List lsRequest = GoogleDriveUtils.getService().files().list();

                if (parentId != null && !parentId.isEmpty()) {
                    q += " and '" + Config.getInstance().getGoogleDriveConfig().getBackupsFolderId() + "' in parents";
                }

                lsRequest.setQ(q);

                FileList driveFileList = lsRequest.execute();
                return !driveFileList.getFiles().isEmpty() ? driveFileList.getFiles().getFirst() : null;
            }).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to get Google Drive credentials", e);
        }
    }

    public static long getFileByteSize(String fileId) throws StorageQuotaExceededException {
        try {
            return ((Retriable<Long>) () -> {
                if (isFolder(fileId)) {

                    long size = 0;

                    for (com.google.api.services.drive.model.File file : ls(fileId)) {
                        size += getFileByteSize(file.getId());
                    }

                    return size;

                } else {
                    com.google.api.services.drive.model.File driveFile = getService().files().get(fileId).setFields("size").execute();
                    Long size = driveFile.getSize();
                    return size != null ? size : 0;
                }
            }).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to get Google Drive credentials", e);
        }
    }

    public static String getFileName(String fileId) throws StorageQuotaExceededException {
        try {
            return ((Retriable<String>) () -> getService().files().get(fileId).setFields("name").execute().getName()).retry(RETRIES);
        } catch (IOException | StorageLimitException e) {
            throw new RuntimeException("Failed to get Google Drive credentials", e);
        }
    }

    @FunctionalInterface
    private interface Retriable<T> {

        int RATE_LIMIT_DELAY_MILLIS = 10000;

        T run() throws IOException, StorageLimitException, StorageQuotaExceededException;

        default T retry(int retries) throws StorageLimitException, StorageQuotaExceededException, IOException {
            int completedRetries = 0;
            while (completedRetries < retries) {
                try {
                    return run();
                } catch (Exception e) {
                    completedRetries++;

                    if (completedRetries == retries) {
                        if (e instanceof GoogleJsonResponseException googleJsonResponseException) {
                            if (googleJsonResponseException.getDetails().getCode() == 401) {
                                credential = null;
                                throw new RuntimeException(new AuthenticationException("Failed to authorize user in Google Drive"));
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
}
