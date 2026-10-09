# Offline Music Player Ideas

This document consolidates the ideas from this chat for a GitHub project: a local-only offline music player that plays songs stored on the user's device.

## Product definition

- Local-only playback
- No streaming
- No account required
- No cloud upload of music
- Offline library scanning
- Songs, artists, albums, genres, folders
- Playlists, favorites, queue, search
- Background playback
- Lyrics, artwork, equalizer, sleep timer
- Privacy-focused design

## Consolidated ideas

The chat generated thousands of ideas across these areas:

1. Core playback: play, pause, next, previous, seek, shuffle, repeat, queue, resume position, gapless playback, crossfade, playback speed, audio focus, headphone controls, lock-screen controls, notification controls, background playback.
2. Local library: folder scanning, storage selection, SD-card support, supported-format detection, metadata reading, artwork, lyrics, duplicate detection, corrupted-file detection, excluded folders, scan history, scan logs, rescan, moved-file detection, deleted-file detection.
3. Browsing: songs, artists, albums, genres, folders, years, formats, bitrate, sample rate, duration, recently added, recently played, most played, never played, favorites, unfinished songs, forgotten songs.
4. Search: title, artist, album, album artist, genre, folder, composer, year, filename, lyrics, notes, rating, play count, duration, bitrate, format, date added, last played, artwork color, saved filters, multi-filter search.
5. Metadata: edit title, artist, album, album artist, genre, year, track number, disc number, composer, producer, lyrics, BPM, key, copyright, comment, custom tags, batch editing, capitalization fixes, duplicate metadata cleanup, metadata backup and undo.
6. Artwork: embedded art, folder art, custom art, crop, rotate, resize, compress, alternate art, artwork per playlist, artwork per folder, artwork in notifications, widgets, lock screen, car mode, artwork collage, waveform art, lyric art, missing-art detection.
7. Lyrics: embedded lyrics, local LRC files, local TXT files, synchronized lyrics, lyric timing editor, timing shift, font controls, color controls, bookmarks, annotations, offline translation files, karaoke mode, lyric practice mode, lyric export and backup.
8. Playlists: manual playlists, smart playlists, folder playlists, favorites playlists, recent playlists, most-played playlists, forgotten-song playlists, playlist folders, playlist artwork, playlist descriptions, reorder, merge, split, duplicate removal, import, export, backup, restore, version history.
9. Queue: save queue, restore queue, queue history, reorder, remove, play next, add to end, duplicate prevention, artist limits, album limits, duration limits, queue search, queue filters, queue backup, queue export, queue lock.
10. Audio: equalizer, bass booster, treble booster, vocal booster, podcast clarity, compressor, limiter, stereo width, mono mode, balance, channel swap, phase inversion, reverb, echo, room simulation, custom presets, per-song presets, per-device presets, spectrum analyzer, frequency analyzer.
11. Device support: wired headphones, Bluetooth headphones, Bluetooth speakers, car audio, USB audio, device-specific volume, equalizer, playlists, queue, artwork, lyrics, crossfade, volume limits, codec display, Bluetooth battery, reconnect last device, output testing.
12. Sleep and timers: minute timer, song-count timer, album timer, playlist timer, clock timer, fade-out, stop after current item, bedtime schedule, wake-up playlist, gradual alarm volume, alarm song, snooze sound, sleep history, timer widget.
13. Statistics: total listening time, daily/weekly/monthly/yearly listening, play counts, skip counts, completion percentage, favorite artists, favorite albums, favorite genres, sessions, average session length, listening by hour, weekday, folder, device, format, playlist, streaks, library usage, export and reset.
14. Privacy: no internet permission, no account, no tracking, no ads, local database, permission audit, revoke folder access, pause indexing, clear cache, clear history, complete local reset, privacy dashboard, local-only documentation, private playlists, app lock, biometric lock, hidden history, encrypted local backup.
15. File tools: rename, move, copy, delete, restore, create folder, compress, extract, convert format, change bitrate, change sample rate, mono/stereo conversion, trim, clip, loop, fade, normalize, replay gain, embed artwork, embed lyrics, embed metadata, checksums, duplicate detection, corruption detection.
16. Accessibility: screen-reader labels, voice search, voice playlist creation, large controls, large text, high contrast, color-blind themes, reduced motion, reduced transparency, haptic controls, custom gestures, keyboard navigation, switch access, dyslexia-friendly font, mono audio, spoken metadata, accessible widgets and car mode.
17. UI and themes: light theme, dark theme, AMOLED theme, Material theme, album-color theme, minimal interface, retro interface, vinyl interface, cassette interface, radio interface, grid/list layouts, landscape mode, tablet layout, foldable layout, one-handed mode, custom icons, fonts, widgets, mini-player, visualizer.
18. GitHub project: README, LICENSE, CONTRIBUTING, CODE_OF_CONDUCT, CHANGELOG, architecture documentation, setup documentation, screenshots, issue templates, pull-request template, GitHub Actions, unit tests, integration tests, playback tests, labels, milestones, releases, semantic versioning.
19. Suggested MVP: scan local files, display songs, play/pause, next/previous, seek, background playback, notification controls, albums, artists, folders, search, playlists, favorites, queue, shuffle, repeat, dark theme, local lyrics, basic equalizer, sleep timer.
20. Testing: empty library, large library, duplicate files, deleted files, moved files, unsupported formats, corrupted files, missing artwork, missing lyrics, SD-card removal, permission denial, screen-off playback, headphone disconnect, Bluetooth switching, calls, low battery, low storage, rotation, process restart, backup restore.

## Suggested project identity

A privacy-focused, local-first offline music player for GitHub. It should play only songs stored on the device, keep user data local, avoid streaming features, and remain useful without internet access.

## Suggested repository structure

```text
offline-music-player/
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── CHANGELOG.md
├── .gitignore
├── docs/
├── app/
├── assets/
├── tests/
└── .github/workflows/build.yml
```

## Suggested release plan

### Version 0.1
- Project setup
- Permission handling
- Local audio scanning
- Song list
- Basic playback

### Version 0.2
- Albums
- Artists
- Folders
- Search
- Background playback
- Notifications

### Version 0.3
- Playlists
- Favorites
- Queue
- Shuffle
- Repeat
- Play counts

### Version 0.4
- Lyrics
- Artwork
- Equalizer
- Sleep timer
- Dark theme
- Widgets

### Version 1.0
- Backup and restore
- Import/export playlists
- Accessibility improvements
- Automated tests
- Documentation
- Stable release build

## README description

> A privacy-focused offline music player that plays only audio files stored locally on the user's device. No streaming, no account, no cloud upload, and no internet required for playback.
