# Roadmap

The long idea lists in `docs/ideas/` were sorted into steps. Everything here keeps the rule: **playback is always offline**.

## 0.1 - done (this release)
Local scan, background playback, auto artist folders, Others / Unidentified, carousel Home, numbered Most Played,
Favorites, moods, devotional section, custom folders, movable sections, drag-and-drop order, Random / Most played /
My sequence shuffle, queue editing, lyrics import, sleep timer, equalizer, AI Organizer, backup.

## 0.2 - polish what people touch every day
- Real file renaming from the Unidentified screen (MediaStore write request)
- ~~Playback speed and fade-out sleep timer~~ (done in 0.1.1); gapless and crossfade settings
- Home-screen widget and better lock-screen artwork
- Multi-select in folders (move many songs at once), "Remember last position" for long mashups
- Tablet / landscape layout with the side menu from the design mockup
- Search filters (artist / mood / album) and search by lyrics text

## 0.3 - organise and clean
- Duplicate finder, broken-file and missing-metadata report
- Tag editor that writes ID3 tags (title, artist, album, artwork)
- Smart playlists with rules (mood = sad AND plays = 0 AND added this month)
- Listening statistics: Music DNA, heatmap, top artists by time, "Time Machine" for a day
- Per-artist or per-song EQ presets, ReplayGain normalisation

## 0.4 - smarter and more beautiful
- Real waveform generated from the audio, beat-reactive visualizer
- Colour theme from album art, more themes (AMOLED, neon, retro cassette)
- Offline mood/energy detection from audio features (tempo, loudness) without any AI key
- Audio fingerprinting (Chromaprint + AcoustID) for identifying files with no tags, optional online lookup
- Android Auto and Wear OS controls

## 1.0
Accessibility pass, translations (Hindi, Bengali), automatic tests on real devices, signed release builds, Play Store listing.

## Ideas parking lot (from the idea lists)
Karaoke mode, lyrics translation field, A-B repeat, per-device (headphone) EQ profiles, Bluetooth battery display, M3U/PLS
import and export, sleep stories, alarm with a song, party mode, workout cadence mode, folder-watch sync between devices over
local Wi-Fi, scheduled backups, app lock, kids profile.

## Not planned
Streaming, accounts, cloud upload of music, ads, tracking. (JioSaavn / Spotify style catalogues are not part of an offline player.)
