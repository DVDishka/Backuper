package ru.dvdishka.backuper.backend.utils;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.io.IOUtils;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.exceptions.NotAuthorizedException;

import java.io.*;
import java.util.*;

public class GoogleDriveUtils {

    private static File tokensFolder;

    private static final String APPLICATION_NAME = "BACKUPER";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> DRIVE_SCOPES = List.of(DriveScopes.DRIVE_FILE);
    private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();
    private static LocalServerReceiver current_receiver = null;

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    public static void init() {
        tokensFolder = Config.getInstance().getGoogleDriveConfig().getTokensFolder();
    }

    public static Credential returnCredentialIfAuthorized(CommandSender sender) {

        try {
            GoogleClientSecrets clientSecrets = JSON_FACTORY
                    .fromString(ObfuscateUtils
                            .decrypt(IOUtils
                                    .toString((Utils.plugin.getResource("google_cred.txt")))),
                            GoogleClientSecrets.class);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensFolder))
                    .setAccessType("offline")
                    .build();

            Credential credential = flow.loadCredential("user");

            if (credential != null
                    && (credential.getRefreshToken() != null
                    || credential.getExpiresInSeconds() == null
                    || credential.getExpiresInSeconds() > 60)) {
                try {

                    Drive service = new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                            .setApplicationName(APPLICATION_NAME)
                            .setHttpRequestInitializer(new HttpRequestInitializer() {
                                @Override
                                public void initialize(HttpRequest httpRequest) throws IOException {
                                    credential.initialize(httpRequest);
                                    httpRequest.setConnectTimeout(300 * 60000);
                                    httpRequest.setReadTimeout(300 * 60000);
                                }
                            })
                            .build();

                    service.files().get("test").execute();

                } catch (GoogleJsonResponseException e) {
                    if (e.getStatusCode() != 404) {
                        return null;
                    }
                } catch (Exception e) {
                    return null;
                }
                return credential;
            }
            return null;

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to authorize user in Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static boolean isAuthorized(CommandSender sender) {
        return returnCredentialIfAuthorized(sender) != null;
    }

    public static Credential authorizeForced(CommandSender sender) throws IOException {

        GoogleClientSecrets clientSecrets = JSON_FACTORY
                .fromString(ObfuscateUtils
                                .decrypt(IOUtils
                                        .toString((Utils.plugin.getResource("google_cred.txt")))),
                        GoogleClientSecrets.class);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensFolder))
                .setAccessType("offline")
                .build();

        try {
            current_receiver.stop();
            Logger.getLogger().log("The previous link for authorization is no longer relevant");
        } catch (Exception ignored) {}

        // PORT MUST BE UNLOCKED
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(Config.getInstance().getGoogleDriveConfig().getAuthPort()).build();
        current_receiver = receiver;

        Credential credential = new MyAuthorizationCodeInstalledApp(flow, receiver).authorize("user", true, sender);

        return credential;
    }

    public static Drive getService(CommandSender sender) throws NotAuthorizedException {

        Credential credential = returnCredentialIfAuthorized(sender);
        if (credential == null) {
            throw new NotAuthorizedException("googleDrive");
        }

        return new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .setHttpRequestInitializer(new HttpRequestInitializer() {
                    @Override
                    public void initialize(HttpRequest httpRequest) throws IOException {
                        credential.initialize(httpRequest);
                        httpRequest.setConnectTimeout(300 * 60000);
                        httpRequest.setReadTimeout(300 * 60000);
                    }
                })
                .build();
    }

    /**
     * @param file Local file to upload
     * @param parentFolderId GoogleDrive parent folder ID or an empty string
     **/
    public static String uploadFile(File file, String parentFolderId, MediaHttpUploaderProgressListener progressListener, CommandSender sender) {

        if (!file.exists()) {
            Logger.getLogger().warn("File does not exist: " + file.getAbsolutePath(), sender);
            return null;
        }

        try {
            Drive service = getService(sender);

            Map<String, String> fileAppProperties = new HashMap<>();
            fileAppProperties.put("backuper", "true");

            com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
            driveFileMeta.setAppProperties(fileAppProperties);
            driveFileMeta.setName(file.getName());
            if (!Objects.equals(parentFolderId, "")) {
                driveFileMeta.setParents(List.of(parentFolderId));
            }
            FileContent driveFileContent = new FileContent("", file);

            Drive.Files.Create driveFileCreate = service.files()
                    .create(driveFileMeta, driveFileContent)
                    .setFields("id, parents, appProperties");
            driveFileCreate.getMediaHttpUploader().setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
            driveFileCreate.getMediaHttpUploader().setProgressListener(progressListener);

            com.google.api.services.drive.model.File driveFile = driveFileCreate.execute();

            return driveFile.getId();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to upload file to Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    /**
     * @param file Local file to upload
     * @param fileName GoogleDrive new file name
     * @param parentFolderId GoogleDrive parent folder ID or an empty string
     **/
    public static String uploadFile(File file, String fileName, String parentFolderId, MediaHttpUploaderProgressListener progressListener, CommandSender sender) {

        if (!file.exists()) {
            Logger.getLogger().warn("File does not exist: " + file.getAbsolutePath(), sender);
            return null;
        }

        try {
            Drive service = getService(sender);

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
                    .setFields("id, parents, appProperties");
            driveFileCreate.getMediaHttpUploader().setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
            driveFileCreate.getMediaHttpUploader().setProgressListener(progressListener);

            com.google.api.services.drive.model.File driveFile = driveFileCreate.execute();

            return driveFile.getId();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to upload file to Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static String createFolder(String folderName, String parentFolderId, CommandSender sender) {

        try {
            Drive service = getService(sender);

            Map<String, String> fileAppProperties = new HashMap<>();
            fileAppProperties.put("backuper", "true");

            com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
            driveFileMeta.setName(folderName);
            driveFileMeta.setAppProperties(fileAppProperties);
            if (!Objects.equals(parentFolderId, "")) {
                driveFileMeta.setParents(List.of(parentFolderId));
            }
            driveFileMeta.setMimeType(FOLDER_MIME_TYPE);

            return service.files().create(driveFileMeta).setFields("appProperties, id, parents").execute().getId();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to create folder in Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static InputStream getDownloadFileStream(String fileId, CommandSender sender) {

        try {
            Drive service = getService(sender);

            return service.files().get(fileId).executeMediaAsInputStream();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to download file from Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static void downloadFile(String fileId, File targetFile, MediaHttpDownloaderProgressListener progressListener, CommandSender sender) {

        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            Drive service = getService(sender);

            Drive.Files.Get getDriveFile = service.files()
                    .get(fileId);
            getDriveFile.getMediaHttpDownloader().setProgressListener(progressListener);

            getDriveFile.executeMediaAndDownloadTo(outputStream);

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to download file from Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
        }
    }

    public static boolean isFolder(String driveFileId, CommandSender sender) throws FileNotFoundException {

        try {
            Drive service = getService(sender);
            return service.files().get(driveFileId)
                    .execute()
                    .getMimeType()
                    .equals(FOLDER_MIME_TYPE);

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get file type from Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            throw new FileNotFoundException();
        }
    }

    private static class MyAuthorizationCodeInstalledApp extends AuthorizationCodeInstalledApp {

        public MyAuthorizationCodeInstalledApp(AuthorizationCodeFlow flow, VerificationCodeReceiver receiver) {
            super(flow, receiver);
        }

        protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl, CommandSender sender) throws IOException {
            String url = authorizationUrl.build();
            Preconditions.checkNotNull(url);

            Component message = Component.empty()
                    .append(Component.text("Login using this link:")
                            .color(TextColor.fromHexString("#129c9b")))
                    .append(Component.text(url)
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, url))
                            .decorate(TextDecoration.UNDERLINED));

            sender.sendMessage(message);
        }

        public Credential authorize(String userId, boolean force, CommandSender sender) throws IOException {
            try {
                if (!force) {
                    Credential credential = getFlow().loadCredential(userId);
                    if (credential != null
                            && (credential.getRefreshToken() != null
                            || credential.getExpiresInSeconds() == null
                            || credential.getExpiresInSeconds() > 60)) {
                        return credential;
                    }
                }

                String redirectUri = getReceiver().getRedirectUri();
                AuthorizationCodeRequestUrl authorizationUrl =
                        getFlow().newAuthorizationUrl().setRedirectUri(redirectUri);
                onAuthorization(authorizationUrl, sender);

                String code = getReceiver().waitForCode();
                TokenResponse response;
                try {
                    response = getFlow().newTokenRequest(code).setRedirectUri(redirectUri).execute();
                } catch (Exception e) {
                    Logger.getLogger().warn("Google authorization failed. If you have just generated new authorization link just ignore this message, it is about the old one");
                    Logger.getLogger().devWarn(GoogleDriveUtils.class, e);
                    return null;
                }

                return getFlow().createAndStoreCredential(response, userId);
            } finally {
                getReceiver().stop();
            }
        }
    }

    /**
     * @param driveFileId Parent Google Drive file ID. "drive" to get all drive files. "" to get all files
     **/
    public static List<com.google.api.services.drive.model.File> ls(String driveFileId, CommandSender sender) {

        try {

            Drive service = getService(sender);
            String pageToken = null;
            ArrayList<com.google.api.services.drive.model.File> driveFiles = new ArrayList<>();

            do {

                Drive.Files.List lsRequest = service.files().list()
                        .setPageToken(pageToken);
                String q = "appProperties has { key='backuper' and value='true' }";

                if (driveFileId != null && driveFileId.equals("drive")) {
                    lsRequest = lsRequest.setSpaces("drive");
                }

                if (driveFileId != null && !driveFileId.isEmpty() && !driveFileId.equals("drive")) {
                    q += " and '" + driveFileId + "'" + " in parents";
                    lsRequest = lsRequest.setQ(q);
                }

                FileList driveFileList = lsRequest.execute();

                driveFiles.addAll(driveFileList.getFiles());

                pageToken = driveFileList.getNextPageToken();

            } while (pageToken != null);

            return driveFiles;

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get files list from Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return List.of();
        }
    }

    public static void renameFile(String fileId, String newName, CommandSender sender) {

        try {
            Drive service = getService(sender);

            service.files().update(fileId, new com.google.api.services.drive.model.File()
                    .setName(newName))
                    .setFields("name")
                    .execute();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to rename Google Drive file", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
        }
    }

    public static void deleteFile(String fileId, CommandSender sender) {

        try {

            Drive service = getService(sender);
            service.files().delete(fileId).execute();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to delete Google Drive file", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
        }
    }

    public static com.google.api.services.drive.model.File getFileByName(String fileName, String parentId, CommandSender sender) {
        try {
            String q = "";

            q += "name = '" + fileName + "'";
            q += " and appProperties has { key='backuper' and value='true' }";

            Drive.Files.List lsRequest = GoogleDriveUtils.getService(sender).files().list();

            if (parentId != null && !parentId.isEmpty()) {
                q += " and '" + Config.getInstance().getGoogleDriveConfig().getBackupsFolderId() + "' in parents";
            }

            lsRequest.setQ(q);

            FileList driveFileList = lsRequest.execute();
            return !driveFileList.getFiles().isEmpty() ? driveFileList.getFiles().get(0) : null;

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get fileId from Google Drive. Check if Google Account is linked", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static long getFileByteSize(String fileId, CommandSender sender) {
        try {

            if (isFolder(fileId, sender)) {

                long size = 0;

                for (com.google.api.services.drive.model.File file : ls(fileId, sender)) {
                    size += getFileByteSize(file.getId(), sender);
                }

                return size;

            } else {
                com.google.api.services.drive.model.File driveFile = getService(sender).files().get(fileId).setFields("size").execute();
                Long size = driveFile.getSize();
                return size != null ? size : 0;
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get file size from Google Drive. Check if Google Drive account is linked", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return 0;
        }
    }

    public static String getFileName(String fileId, CommandSender sender) {
        try {

            return getService(sender).files().get(fileId).setFields("name").execute().getName();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get file name from Google Drive. Check if Google Drive account is linked", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return "";
        }
    }
}
