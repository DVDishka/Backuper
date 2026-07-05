# AGENTS.md

This repository contains `Backuper`, a Maven-based Paper/Folia plugin for automated backups with local, FTP, SFTP, and Google Drive storage backends.

## Project layout

- `src/main/java/ru/dvdishka/backuper`: production plugin code
- `src/main/resources`: plugin descriptors and default config templates
- `src/test/java`: unit tests
- `src/integration-test/java`: integration tests picked up by Failsafe
- `plugins/Backuper`: local runtime config and generated plugin data for manual testing
- `wiki`: end-user documentation

## Build and test

- Build jar: `mvn package`
- Run unit tests: `mvn test`
- Run integration tests: `mvn verify`

The project targets Java 25. Maven is configured with Surefire for unit tests and Failsafe for `*IT.java` integration tests.
On this workstation, use the Java 25 JDK when invoking Maven manually:
`$env:JAVA_HOME='C:\Users\dimak\.jdks\corretto-25'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn test`.

## Working rules

- Keep changes scoped to the existing package structure and naming conventions.
- Prefer updating existing config/resource templates in `src/main/resources` instead of introducing duplicates.
- Treat `plugins/Backuper` as local runtime state unless the task is explicitly about sample or test data.
- Preserve compatibility assumptions called out in `README.md` and `wiki`.
- Add or update tests when changing shared backup, storage, scheduling, or command behavior.
- When changing `src/main/resources/config.yml`, update `configVersion`, add a backwards-compatibility migration in `ConfigBackwardsCompatibility`, wire it from `ConfigManager`, and cover it in `ConfigTest`.
- Keep standalone storage templates (`local_config.yml`, `ftp_config.yml`, `sftp_config.yml`, `google_drive_config.yml`) consistent with the matching section in `config.yml`.
- Be careful with existing user changes and local runtime files; do not revert unrelated dirty files.

## Storage behavior

- Storage methods should fail loudly when remote/local commands fail. Do not ignore boolean result values from storage APIs.
- For FTP, check boolean results for commands such as `changeWorkingDirectory`, `rename`, `mkd`, `deleteFile`, and `removeDirectory`, and include the server reply in thrown errors.
- After backup upload and rename paths, verify the resulting state: uploaded in-progress object exists, final object exists after rename, and in-progress object no longer exists.
- `exists(...)` should only return `false` for a real "not found" condition; permission, connection, API, or server errors should propagate through the existing storage exception/retry flow.

## Storage protocol logs

- Storage protocol/operation logs are opt-in/out per storage via `debug.protocolLogging`; current default is `true`.
- Logs are written under `plugins/Backuper/logs/<storageId>.log`, rotated at 1MB, and old rotated logs are retained.
- Reuse the common storage log infrastructure rather than adding ad hoc file writers.
- FTP can log raw Commons Net command/reply events; mask passwords (`PASS ***`).
- SFTP can use JSch logging for SSH/JSch diagnostics, but add explicit storage operation logs when exact SFTP paths/actions are needed for troubleshooting.
- Google Drive logs should stay at API-operation level and must not include OAuth tokens, refresh tokens, credential payloads, or HTTP request bodies.
- Local storage logs should describe filesystem operations and paths.

## Notes for agents

- This is a shaded plugin jar; dependency relocation is configured in `pom.xml`.
- MockBukkit and CommandAPI test tooling are already present for plugin-level tests.
- Resource filtering is enabled for `src/main/resources`, so avoid introducing placeholder syntax accidentally.
