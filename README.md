<center>

# Backuper

<img height="128" src="images\backuper_logo.png" width="128"/>

## _Simple backup plugin for spigot_

</center>

---

### How to use

1. Install into your plugins folder
2. Start the server
3. Change config.yml (plugins/Backuper/config.yml)
4. Restart the server

#### Backups will be stored in (plugins/Backuper/Backups)

---

### Configuration

* `Backup time` - time in hours when server will be backed up (0 - 23)
* `Backup period` - the period after which the server will make backups (Hours)
* `After backup` - what will the server do after backup (STOP, RESTART, NOTHING)
* `Max backup number` - maximum number of backups to be kept (0 - unlimited)
* `Max backup weight` - maximum weight of backups that will be stored (MB) (0 - unlimited)

---

### Commands

* `/backup <stopRestartServer>` - command to backup the server manually, argument means what the server will do after restart (Argument can be STOP or RESTART, also you can use it without argument)

---

### Notes

* RESTART option may not work well, so it's better to use STOP with a loop in your start script ([start script](https://flags.sh/) auto restart ON)