# FTP Storage

## Default Configuration

```yaml
storages:
  yourStorageId:
    type: ftp
    enabled: true
    autoBackup: true
    
    backupsFolder: ./
    pathSeparatorSymbol: /
    
    maxBackupsNumber: 0
    maxBackupsWeight: 0
    
    zipArchive: true
    zipCompressionLevel: 5
    
    auth:
      address: ''
      port: 21
      username: ''
      password: ''
```

## Configuration Options

### Basic Settings

```yaml
type: ftp                    # Storage type - must be 'ftp' for FTP server storage
enabled: true                # Enable/disable this storage (true/false)
autoBackup: true             # Include this storage in automatic backups (true/false)
```

- **type**: Defines the storage implementation. Must be `ftp` for FTP server storage.
- **enabled**: Controls whether this storage is active. Set to `false` to disable without removing configuration.
- **autoBackup**: When `true`, automatic backups will be saved to this storage. When `false`, only manual backups are allowed.

### Location

```yaml
backupsFolder: ./            # Directory on FTP server where backups will be stored
pathSeparatorSymbol: /       # Path separator symbol used on FTP server
```

- **backupsFolder**: Directory path on the FTP server where backup files will be stored. Can be relative to user's home directory or absolute path.
- **pathSeparatorSymbol**: Path separator character used by the FTP server operating system:
  - `/` - Use for most FTP servers (Linux/Unix)
  - `\` - Use only if FTP server is Windows and requires it
  - When in doubt, try `/` first

### Backup Limits

```yaml
maxBackupsNumber: 0          # Maximum number of backups to keep (0 = unlimited)
maxBackupsWeight: 0          # Maximum total size of all backups in MB (0 = unlimited)
```

- **maxBackupsNumber**: Limits the number of backup files stored on FTP server. When exceeded, oldest backups are deleted automatically. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Limits the total size of all backups in megabytes. When exceeded, oldest backups are deleted to free space. Set to `0` for unlimited size.

### Compression

```yaml
zipArchive: true             # Store backups as ZIP files (true/false)
zipCompressionLevel: 5       # ZIP compression level (0-9)
```

- **zipArchive**: When `true`, backups are stored as compressed ZIP files. When `false`, backups are stored as folder structures (faster upload but larger transfer size).
- **zipCompressionLevel**: Controls ZIP compression strength. Higher values create smaller files but take more time:
  - `0` - No compression (fastest, largest files)
  - `1-3` - Low compression (fast, moderate file size)
  - `4-6` - Balanced compression (moderate speed and size)
  - `7-9` - High compression (slower, smallest files)

### Authentication

```yaml
auth:
  address: ''                # FTP server hostname or IP address
  port: 21                   # FTP server port number
  username: ''               # FTP server username
  password: ''               # FTP server password
```

- **address**: Hostname or IP address of the FTP server (e.g., `ftp.example.com` or `192.168.1.100`).
- **port**: Port number for FTP connection. Standard FTP uses port `21`.
- **username**: Username for FTP server authentication.
- **password**: Password for FTP server authentication.

## Required Permissions

- `backuper.yourStorageId` - Required for storage to appear in command suggestions
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list` - View and manage backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures
