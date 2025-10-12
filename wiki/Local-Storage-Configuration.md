# Local Storage

## Default Configuration

```yaml
storages:
  yourStorageId:
    type: local
    enabled: true
    autoBackup: true
    
    backupsFolder: ./plugins/Backuper/Backups
    
    maxBackupsNumber: 0
    maxBackupsWeight: 0
    
    zipArchive: true
    zipCompressionLevel: 5
```

## Configuration Options

### Basic Settings

```yaml
type: local                  # Storage type - must be 'local' for local file system storage
enabled: true                # Enable/disable this storage (true/false)
autoBackup: true             # Include this storage in automatic backups (true/false)
```

- **type**: Defines the storage implementation. Must be `local` for local file system storage.
- **enabled**: Controls whether this storage is active. Set to `false` to disable without removing configuration.
- **autoBackup**: When `true`, automatic backups will be saved to this storage. When `false`, only manual backups are allowed.

### Location

```yaml
backupsFolder: ./plugins/Backuper/Backups  # Directory where backups will be stored
```

- **backupsFolder**: Full path to the directory where backup files will be stored. Can be relative to server directory or absolute path.

**Path examples:**
- `./plugins/Backuper/Backups` - Relative to server directory
- `/home/minecraft/backups` - Absolute path (Linux/Mac)
- `C:\MinecraftBackups` - Absolute path (Windows)
- `../shared_backups` - Relative path outside server directory

**Path separator:** Automatically detected by the system (no manual configuration needed)

### Backup Limits

```yaml
maxBackupsNumber: 0          # Maximum number of backups to keep (0 = unlimited)
maxBackupsWeight: 0          # Maximum total size of all backups in MB (0 = unlimited)
```

- **maxBackupsNumber**: Limits the number of backup files/folders stored. When exceeded, oldest backups are deleted automatically. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Limits the total size of all backups in megabytes. When exceeded, oldest backups are deleted to free space. Set to `0` for unlimited size.

### Compression

```yaml
zipArchive: true             # Store backups as ZIP files (true/false)
zipCompressionLevel: 5       # ZIP compression level (0-9)
```

- **zipArchive**: When `true`, backups are stored as compressed ZIP files. When `false`, backups are stored as folder structures (faster but larger).
- **zipCompressionLevel**: Controls ZIP compression strength. Higher values create smaller files but take more time:
  - `0` - No compression (fastest, largest files)
  - `1-3` - Low compression (fast, moderate file size)
  - `4-6` - Balanced compression (moderate speed and size)
  - `7-9` - High compression (slower, smallest files)

## Required Permissions

- `backuper.yourStorageId` - Required for storage to appear in command suggestions
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list` - View and manage backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures
