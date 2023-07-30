<div align='center'>

# Backuper

<img height="128" src="images/backuper_logo.png" width="128"/>

## _Simple backup plugin for Paper/Folia_

</div>

---

### Pages

* [Hangar](https://hangar.papermc.io/Collagen/Backuper)
* [Modrinth](https://modrinth.com/plugin/backuper)
* [bStats](https://bstats.org/plugin/bukkit/Backuper/17735)

---

### How to use

1. Install into your plugins folder
2. Start the server
3. Change config.yml (plugins/Backuper/config.yml)
4. Restart the server

#### By default, backups will be stored in (plugins/Backuper/Backups)

---

### Configuration

* `Auto backup` - **(true/false)** - enables automatic backups once at a specified interval (when disabled, backups will only run on the `/backup` command)
* `Backups folder` - **(Path)** - **FULL** path to folder, where backups will be stored
* `First backup time` - **(0 -23)** - time in hours when server will be backed up first time
* `Fixed backup time` - **(true/false)** - all backups will take place at a certain time, specified in the `firstBackupTime`. When enabled, the `backupPeriod` automatically becomes 24 hours
* `Backup period` - **(1 <= Hours)** - the period after which the server will make backups
* `After backup` - **(NOTHING/STOP/RESTART)** - what will the server do after backup
* `Max backup number` - **(0 <=)** - maximum number of backups to be kept **(0 - unlimited)**
* `Max backup weight` - **(0 <=)** - maximum weight of backups that will be stored **(MB)**
* `Better logging` - **(true/false)** - enable logging of additional information (used for debugging, you probably don't need it)

---

### Commands

* `/backup <stopRestartServer>` - command to backup the server manually, argument means what the server will do after restart (Argument can be STOP or RESTART, also you can use it without argument)

---

### Permissions

* `backuper.backup` - permission to use `/backup` command
* `backuper.stop` - permission to use `/backup` command with the STOP argument
* `backuper.restart` - permission to use `/backup` command with the RESTART argument

---

### Notes

* **Please report any issues to** [GitHub](https://github.com/DVDishka/Backuper/issues)
* RESTART option may not work well, so it's better to use STOP with a loop in your start script ([start script](https://flags.sh/) auto restart ON)
* You can reset the backup time if it is broken and you don't use `fixedBackupTime` by changing `lastBackup` to 0. Then the next backup will happen at `firstBackupTime` and the next ones will happen after `backupPeriod`
