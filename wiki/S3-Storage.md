## Default Configuration

```yaml
storages:
  yourStorageId:
    type: s3
    enabled: true
    autoBackup: true

    bucket: ''
    region: 'us-east-1'
    endpoint: ''
    pathStyleAccess: false

    backupsFolder: backups/

    maxBackupsNumber: 0
    maxBackupsWeight: 0

    zipArchive: true
    zipCompressionLevel: 5

    storageClass: ''

    auth:
      accessKeyId: ''
      secretAccessKey: ''
      sessionToken: ''

    transfer:
      partSizeMb: 8
      bufferUploadsToDisk: false
      requestTimeoutSeconds: 3600

    debug:
      protocolLogging: true
```

## Configuration Options

### Basic Settings

```yaml
type: s3
enabled: true
autoBackup: true
```

- **type**: Must be `s3` for AWS S3 and S3-compatible object storage.
- **enabled**: Controls whether this storage is active. Set to `false` to disable without removing the configuration.
- **autoBackup**: When `true`, automatic backups are saved to this storage. When `false`, only manual backups are allowed.

### Bucket and Connection Settings

```yaml
bucket: 'my-minecraft-backups'
region: 'us-east-1'
endpoint: ''
pathStyleAccess: false
storageClass: ''
```

- **bucket**: Name of the S3 bucket where backups will be stored. The bucket must already exist.
- **region**: AWS or provider region (e.g., `us-east-1`, `eu-central-1`, or `auto` for Cloudflare R2). Default is `us-east-1`.
- **endpoint**: Custom endpoint URL for S3-compatible services (e.g., Cloudflare R2, MinIO, Wasabi, Backblaze B2). Leave empty (`''`) for standard AWS S3.
- **pathStyleAccess**: Controls whether to use path-style URLs (`endpoint/bucket/key`) or virtual-hosted-style URLs (`bucket.endpoint/key`):
  - `false` (default): Virtual-hosted style. Recommended for AWS S3 and Cloudflare R2.
  - `true`: Path style. Required for MinIO, LocalStack, and some private S3-compatible object storage implementations.
- **storageClass**: S3 Storage Class to assign to uploaded objects (e.g., `STANDARD`, `STANDARD_IA`, `ONEZONE_IA`, `GLACIER_IR`, `INTELLIGENT_TIERING`). Leave empty (`''`) to use the bucket's default storage class.
  > **Note:** Leaving this empty is strongly recommended when using S3-compatible providers (such as Cloudflare R2, Wasabi, or MinIO) that do not support AWS-specific storage classes.

### Location

```yaml
backupsFolder: backups/
```

- **backupsFolder**: Key prefix (directory) inside the bucket where backups will be placed. Can be a top-level prefix like `backups/` or nested like `servers/survival/backups/`.

### Backup Limits and Compression

```yaml
maxBackupsNumber: 0
maxBackupsWeight: 0
zipArchive: true
zipCompressionLevel: 5
```

- **maxBackupsNumber**: Maximum number of backups to keep. Oldest backups are automatically deleted when exceeded. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Maximum total size in megabytes of backups in this storage. Oldest backups are automatically deleted when exceeded. Set to `0` for unlimited size.
- **zipArchive**: When `true`, backups are stored as compressed ZIP files. When `false`, backups are stored as folder structures (object prefix trees).
- **zipCompressionLevel**: ZIP compression level from `0` (no compression, fastest) to `9` (maximum compression, slower).

### Authentication

```yaml
auth:
  accessKeyId: 'YOUR_ACCESS_KEY_ID'
  secretAccessKey: 'YOUR_SECRET_ACCESS_KEY'
  sessionToken: ''
```

- **accessKeyId**: S3 Access Key ID.
- **secretAccessKey**: S3 Secret Access Key.
- **sessionToken**: Optional temporary session token. Leave empty unless using temporary credentials (e.g. AWS STS / IAM role assumption).

### Transfer and Upload Performance

```yaml
transfer:
  partSizeMb: 8
  bufferUploadsToDisk: false
  requestTimeoutSeconds: 3600
```

- **partSizeMb**: Size in megabytes of each part in multipart uploads (minimum `5`, default `8`). Backuper streams large backup files using S3 multipart upload without loading the entire archive into memory.
- **bufferUploadsToDisk**: When `true`, backups are staged to a temporary file on the local disk before uploading to S3. Enable this if direct streaming encounters network stability issues or if your environment restricts chunked transfers.
- **requestTimeoutSeconds**: Timeout in seconds for S3 HTTP requests. Default is `3600` (1 hour). Set to `0` to disable the timeout.

### Logging and Debugging

```yaml
debug:
  protocolLogging: true
```

- **protocolLogging**: When `true`, S3 operations are logged to `plugins/Backuper/logs/{storageId}.log`. Access keys, secrets, and object payload data are never logged.

---

## Required Permissions

- `backuper.yourStorageId` - Required for storage to appear in command suggestions and to view backups
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures

---

## Provider Configuration Examples

### AWS S3

```yaml
storages:
  awsS3:
    type: s3
    enabled: true
    autoBackup: true
    bucket: 'my-minecraft-backup-bucket'
    region: 'us-east-1'
    endpoint: ''
    pathStyleAccess: false
    backupsFolder: backups/
    auth:
      accessKeyId: 'AKIAIOSFODNN7EXAMPLE'
      secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
```

### Cloudflare R2

Cloudflare R2 provides zero-egress-fee S3-compatible storage.

```yaml
storages:
  cloudflareR2:
    type: s3
    enabled: true
    autoBackup: true
    bucket: 'minecraft-backups'
    region: 'auto'
    endpoint: 'https://<ACCOUNT_ID>.r2.cloudflarestorage.com'
    pathStyleAccess: false
    backupsFolder: backups/
    storageClass: ''
    auth:
      accessKeyId: '<R2_ACCESS_KEY_ID>'
      secretAccessKey: '<R2_SECRET_ACCESS_KEY>'
```

### MinIO (Self-Hosted)

For MinIO or local S3 instances, enable `pathStyleAccess: true`:

```yaml
storages:
  localMinio:
    type: s3
    enabled: true
    autoBackup: true
    bucket: 'minecraft-backups'
    region: 'us-east-1'
    endpoint: 'http://192.168.1.100:9000'
    pathStyleAccess: true
    backupsFolder: backups/
    auth:
      accessKeyId: 'minioadmin'
      secretAccessKey: 'minioadmin'
```

### Wasabi

```yaml
storages:
  wasabiBackup:
    type: s3
    enabled: true
    autoBackup: true
    bucket: 'my-wasabi-bucket'
    region: 'us-east-1'
    endpoint: 'https://s3.wasabisys.com'
    pathStyleAccess: false
    backupsFolder: backups/
    auth:
      accessKeyId: 'WASABI_ACCESS_KEY'
      secretAccessKey: 'WASABI_SECRET_KEY'
```

### Backblaze B2 (S3-Compatible API)

```yaml
storages:
  backblazeB2:
    type: s3
    enabled: true
    autoBackup: true
    bucket: 'my-b2-bucket'
    region: 'us-west-004'
    endpoint: 'https://s3.us-west-004.backblazeb2.com'
    pathStyleAccess: false
    backupsFolder: backups/
    auth:
      accessKeyId: 'KEY_ID'
      secretAccessKey: 'APPLICATION_KEY'
```
