## Default Configuration

```yaml
storages:
  yourStorageId:
    type: webdav
    enabled: true
    autoBackup: true

    backupsFolder: ./

    maxBackupsNumber: 0
    maxBackupsWeight: 0

    zipArchive: true
    zipCompressionLevel: 5

    auth:
      url: 'https://cloud.example.com/remote.php/dav/files/username/'
      username: 'username'
      password: 'app-password'
      allowInsecureHttp: false

    http:
      requestTimeoutSeconds: 3600
      deleteConfirmationTimeoutSeconds: 60
      bufferUploadsToDisk: false
```

## Configuration Options

### Basic Settings

```yaml
type: webdav
enabled: true
autoBackup: true
```

- **type**: Must be `webdav` for WebDAV storage.
- **enabled**: Controls whether this storage is active.
- **autoBackup**: When `true`, automatic backups are saved to this storage.

### Location

```yaml
backupsFolder: ./
```

- **backupsFolder**: Directory relative to `auth.url`. The directory must already exist. Backuper creates backup subdirectories and files inside it.
- `auth.url` is the WebDAV collection used as the storage root. It may include a path, for example a Nextcloud user's DAV endpoint.
- Paths are encoded per URI segment, so spaces and non-ASCII file names are supported.

### Authentication

```yaml
auth:
  url: 'https://cloud.example.com/remote.php/dav/files/username/'
  username: 'username'
  password: 'app-password'
  allowInsecureHttp: false
```

- **url**: Full HTTP(S) URL of the WebDAV root. Do not include credentials, query parameters, fragments, `.` or `..` path segments, or percent-encoded path separators.
- **username** and **password**: Credentials for HTTP Basic authentication. The username cannot contain `:`. Use an app password when the provider supports one. Leave `username` empty for anonymous WebDAV.
- **allowInsecureHttp**: Allows Basic credentials over unencrypted HTTP. This is disabled by default because Basic credentials can be recovered from intercepted traffic. Prefer HTTPS. Enable it only when another trusted transport, such as a private tunnel, protects the connection.

### HTTP Compatibility

```yaml
http:
  requestTimeoutSeconds: 3600
  deleteConfirmationTimeoutSeconds: 60
  bufferUploadsToDisk: false
```

- **requestTimeoutSeconds**: Maximum duration of one HTTP request. Increase it for very large backups or slow links. Set to `0` to disable the request timeout.
- **deleteConfirmationTimeoutSeconds**: Maximum time to poll for completion after a server accepts deletion with HTTP `202 Accepted`. Set to `0` to treat `202` as success without waiting for confirmation.
- **bufferUploadsToDisk**: By default, uploads stream directly and may use HTTP/1.1 chunked transfer encoding. Enable this option if the WebDAV server returns HTTP `411 Length Required` or rejects chunked PUT requests. Backuper then writes each upload to a temporary file before sending it with a fixed `Content-Length`, so sufficient temporary disk space is required.
- Redirects are followed only when they stay on the same scheme, host, port, and configured WebDAV root. Credentials are never forwarded to another origin.
- Redirect and PROPFIND paths containing relative segments or percent-encoded path separators are rejected to preserve the configured root boundary.

### Backup Limits and Compression

```yaml
maxBackupsNumber: 0
maxBackupsWeight: 0
zipArchive: true
zipCompressionLevel: 5
```

- **maxBackupsNumber**: Maximum number of backups to keep. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Maximum total backup size in megabytes. Set to `0` for unlimited size.
- **zipArchive**: Stores backups as ZIP files when enabled, or as directory trees when disabled.
- **zipCompressionLevel**: ZIP compression level from `0` to `9`.

## Required Permissions

- `backuper.yourStorageId` - View this storage and its backups
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures

## Provider Notes

- **Nextcloud**: A typical URL is `https://host/remote.php/dav/files/USERNAME/`. An app password is recommended.
- **ownCloud**: Use the DAV files endpoint shown by the account's WebDAV settings.
- **Reverse proxies**: Allow the WebDAV methods `PROPFIND`, `MKCOL`, `PUT`, `GET`, `DELETE`, and `MOVE`, and permit request bodies large enough for backups.
