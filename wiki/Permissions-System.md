## Permissions

### Core Permission
```
backuper                            # Base permission for plugin access
```

### Backup
```
backuper.backup.stop                # Allow backup with server stop
backuper.backup.restart             # Allow backup with server restart
backuper.backup_alert               # Receive backup alerts
```

### Config
```
backuper.config                     # Base config permission
backuper.config.reload              # Reload configuration file
```

### Status
```
backuper.status                     # Check task status and progress
```

### Storage Permissions

```
backuper.{storageId}               # Required for storage to appear in command suggestions and to view backups in storage
backuper.{storageId}.backup        # Create backups to specific storage
backuper.{storageId}.list.delete   # Delete backups from storage
backuper.{storageId}.list.tozip    # Convert backups to ZIP (local only)
backuper.{storageId}.list.unzip    # Extract backups from ZIP (local only)
backuper.{storageId}.account       # Link/manage storage accounts (Google Drive, etc.)
```

## Permission Hierarchy

```
backuper
├── backuper.backup
│   ├── backuper.backup.stop
│   ├── backuper.backup.restart
│   └── backuper.backup_alert
├── backuper.config
│   └── backuper.config.reload
├── backuper.status
└── backuper.{storageId}
    ├── backuper.{storageId}.backup
    ├── backuper.{storageId}.list.delete
    ├── backuper.{storageId}.list.tozip
    ├── backuper.{storageId}.list.unzip
    └── backuper.{storageId}.account
```
