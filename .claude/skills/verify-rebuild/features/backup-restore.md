# Backup and restore

More › Backup and restore exports everything to one JSON file the user saves where they like, shares it, or restores one. A restore previews what's in the file, makes the user save a backup of the current data first, then replaces everything.

## Sub-features

- `backup-export` "Export backup file" saves through the system file picker; the More screen's "Last backup" updates.
- `backup-restore` "Choose backup to restore" previews the file, asks for a safety backup, then replaces all data.
- `backup-first-run` "Restore a backup" on the first-run screen goes through the same restore.

## How to get to it (user POV)

- More › Backup and restore.
- The first-run screen's "Restore a backup".

## Driving it with ui.sh

Preconditions:

- For restore: a backup file on the device. `flows.sh seed-history` generates one at `/sdcard/Download/rebuild-seed.json`.

- **Restore from first run.** Run `$S/emu.sh reset` and `$S/flows.sh seed-history`. The preview shows "39 workouts", the safety-backup save, then "Replace all current data?"; after Replace, "Restore complete" (dismissed with OK), then Today shows the seeded week and History's calendar is full.
- **Export.** Run `$S/ui.sh tap More`, `$S/ui.sh tap "Backup and restore"`, `$S/ui.sh tap "Export backup file"`, then `$S/ui.sh tap SAVE` in the picker. Back on More, "Last backup" shows today's date.
- **Side effect.** Run `adb shell ls -la /sdcard/Download/`. The exported `rebuild-backup-*.json` is there.
- **Proof.** Shots of the restore preview and of More with "Last backup".

## Gotchas

- The file picker is the system's DocumentsUI; its labels ("Show roots", "Downloads", "SAVE") are this API 34 image's and may differ elsewhere.
- Restoring replaces all data on the device; never run it on a device this run didn't set up.
