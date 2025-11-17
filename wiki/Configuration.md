## Backup Section

### Automatic Backup
```yaml
backup:
  autoBackup: true
  autoBackupPeriod: 1440
  autoBackupCron: ''
```

- **autoBackup**: Controls whether automatic backups are enabled. Set to `false` to disable automatic backups completely.
- **autoBackupPeriod**: Time between automatic backups in minutes. Only used if `autoBackupCron` is empty.
- **autoBackupCron**: Cron expression for advanced scheduling. When set, overrides `autoBackupPeriod`. Use http://www.cronmaker.com to generate expressions.

### File Management
```yaml
backup:
  backupFileNameFormat: 'dd-MM-yyyy HH-mm-ss'
  addDirectoryToBackup: []
  excludeDirectoryFromBackup: []
```

- **backupFileNameFormat**: Java time format for backup file names. Must include both date and time information.
- **addDirectoryToBackup**: List of additional directories/files to backup beyond worlds (e.g., `plugins`, `config`). Use `*` for all files in the server directory (doesn't work for other directories).
- **excludeDirectoryFromBackup**: List of directories/files to exclude from being backed up.

### Backup Behavior
```yaml
backup:
  deleteBrokenBackups: true
  skipDuplicateBackup: true
  afterBackup: NOTHING
  setWorldsReadOnly: false
```

- **deleteBrokenBackups**: When `true`, failed or corrupted backups are automatically deleted.
- **skipDuplicateBackup**: When `true`, skips backup if no changes detected since last backup.
- **afterBackup**: Action to take after automatic backup completes.
- **setWorldsReadOnly**: Prevents world modification during backup to avoid corruption.

## Server Section

### Alerts
```yaml
server:
  alertTimeBeforeRestart: 60
  alertOnlyServerRestart: true
  alertBackupMessage: 'Server will be backed up in %d second(s)'
  alertBackupRestartMessage: 'Server will be backed up and restarted in %d second(s)'
```

- **alertTimeBeforeRestart**: Warning time in seconds before server restart. Set to `-1` to disable alerts.
- **alertOnlyServerRestart**: When `true`, alerts only shown if server will restart/stop after backup.
- **alertBackupMessage**: Message shown to players before backup. `%d` is replaced with seconds.
- **alertBackupRestartMessage**: Message shown before backup with restart. `%d` is replaced with seconds.

### Performance
```yaml
server:
  sizeCacheFile: './plugins/Backuper/sizeCache.json'
  threadNumber: 0
```

- **sizeCacheFile**: Path to file that caches backup size calculations for performance.
- **threadNumber**: Number of threads for parallel operations. `0` automatically uses optimal number.

### Updates
```yaml
server:
  checkUpdates: true
  betterLogging: false
```

- **checkUpdates**: When `true`, checks for new Backuper versions on startup.
- **betterLogging**: Enables detailed logging for debugging. Not recommended for production.

## Configuration Info

### System Settings
```yaml
configVersion: 13.0
lastBackup: 0
lastChange: 0
```

These values are managed automatically by the plugin and should not be modified manually.

## Storages Section

For storage configuration, see [[Storages Section Configuration|Storages-Section-Configuration]].
