<div align='center'>

# Backuper

<img height="128" src="images/backuper_logo.png" width="128" alt=""/>

## _Simple backup plugin for Paper/Folia_

[![bStats Graph Data](https://bstats.org/signatures/bukkit/Backuper.svg)](https://bstats.org/plugin/bukkit/Backuper)

</div>

---

### Pages

* [<img width="20px" src="https://i.imgur.com/o104U27.png"></img>](https://modrinth.com/plugin/backuper)[ Modrinth](https://modrinth.com/plugin/backuper)
* [<img width="20px" src="https://i.imgur.com/QJnHi37.png"></img>](https://hangar.papermc.io/Collagen/Backuper)[ Hangar](https://hangar.papermc.io/Collagen/Backuper)

---

### How to use

1. Install into your plugins folder
2. Start the server
3. Change config.yml (plugins/Backuper/config.yml)
4. Restart the server

#### By default, backups will be stored in (plugins/Backuper/Backups)

---

### How does automatic backup work

- **If `backupTime` is not set to -1**
    
  - Backups will occur every day at the time specified in `backupTime`, regardless of `backupPeriod`



- **If `backupTime` is set to -1**
    
  - First backup will happen at the server start
  - Next backups will happen after `backupPeriod`

---

### Configuration

* `Auto backup` - **(true/false)** - enables automatic backups once at a specified interval **(when disabled, backups will only run on the `/backup` command)**
* `Backups folder` - **(Path)** - **FULL** path to folder, where backups will be stored
* `backup time` - **(0 - 23)** - **(fixedBackupTime)** Backups will be made at this time every day. (`backupPeriod` will be automatically set to 24 hours). **-1 to disable backup time fixation**
* `Backup period` - **(1 <= Minutes)** - the period after which the server will make backups **(To change this value you need to set `backupTime` to -1)**
* `After backup` - **(NOTHING/STOP/RESTART)** - what will the server do after backup
* `Skip duplicate backup` - **(true/false)** - backup will only occur if the world has been changed since the last backup. If the world has not been changed, this backup cycle will be skipped. `After backup` **will be executed anyway**
* `Max backup number` - **(0 <=)** - maximum number of backups to be kept **(0 - unlimited)**
* `Max backup weight` - **(0 <=)** - maximum weight of backups that will be stored **(MB)** **(0 - unlimited)**
* `Zip Archive` - **(true/false)** - do you want to store backups in ZIP archives?
* `Better logging` - **(true/false)** - enable logging of additional information **(used for debugging, you probably don't need it)**

---

### Commands

* `/backup <stopRestartServer>` - command to backup the server manually, argument means what the server will do after restart **(Argument can be STOP or RESTART, also you can use it without argument)**
* `/backup list` - command to view the list of backups **(click on a backup to open its menu)**
* `/backup menu <backupName>` - command to open the menu of the specified backup **(can be opened by clicking on the specified backup in `/backup list`)**
* `/backup menu <backupName> toZIP` - command to convert the specified backup to a ZIP archive **(can be used by clicking on the `[TO ZIP]` option in `/backup menu <backupName>`)**
* `/backup menu <backupName> unZIP` - command to convert the specified backup from a ZIP archive to a folder **(can be used by clicking on the `[UNZIP]` option in `/backup menu <backupName>`)**
* `/backup menu <backupName> delete` - command to delete the specified backup **(can be used by clicking on the `[DELETE]` option in `/backup menu <backupName>`)**

---

### Permissions

* `backuper.backup` - permission to use `/backup` command
* `backuper.stop` - permission to use `/backup` command with the STOP argument
* `backuper.restart` - permission to use `/backup` command with the RESTART argument
* `backuper.list` - permission to use `/backup list` and `/backup menu` commands
* `backuper.tozip` = permission to convert backups to a ZIP archive
* `backuper.unzip` = permission to convert backups from a ZIP archive to a folder
* `backuper.delete` = permission to delete backups

---

### Notes

* **Please report any issues to** [GitHub](https://github.com/DVDishka/Backuper/issues)
* RESTART option may not work well, so it's better to use STOP with a loop in your start script ([start script](https://flags.sh/) auto restart ON)
* You can reset the backup time if it is broken **and your `backupTime` is set to -1** by changing `lastBackup` to 0. Then the next backup will happen at the server start and the next ones will happen after `backupPeriod`
