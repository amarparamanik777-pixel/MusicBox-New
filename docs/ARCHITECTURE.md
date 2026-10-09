# Architecture

```
MediaStore --scan--> Song list --+
                                 +--> Organizer.build() --> LibraryIndex --> Compose screens
User data (JSON file) -----------+          ^
 artists, moods, folders, layout,           |
 play counts, overrides                     +-- ArtistMatcher, MoodMatcher, FilenameParser
```

* **Scanning** (`data/MediaScanner`): reads audio rows from Android's MediaStore. Nothing leaves the phone.
* **User data** (`data/UserDataStore`): one JSON file (`userdata.json`) with a debounced, atomic save.
  Everything the user changes - favourites, plays, manual orders, overrides - lives here, keyed by MediaStore id.
* **Organizer** (`organize/Organizer`): pure function `(songs, userData) -> LibraryIndex`. It runs on a background
  dispatcher whenever songs, artists, moods or per-song overrides change.
* **Folders** (`organize/Folders`): turns a folder (artist, mood, favourites, custom, ...) into an ordered list.
* **Playback** (`player/`): a Media3 `MediaSessionService` owns the ExoPlayer, so music keeps playing with the screen off.
  `PlayerController` is the app's remote control (a `MediaController`) and publishes `PlayerState` flows.
  Play counts are recorded after 30 seconds (or half of a short song).
* **Cover art** (`player/ArtCache`): extracts embedded pictures once, caches them, and serves them to the notification
  through a tiny `ContentProvider`.
* **AI** (`ai/AiOrganizer`): batches of 15 songs, strict JSON answers, validated before being shown. Suggestions are only
  applied after the user confirms.
* **UI** (`ui/`): Jetpack Compose, Material 3 dark theme taken from the logo colours, one `MainViewModel` for navigation.

## Why these choices

* *MediaStore + permission* instead of raw file access: it works on every modern Android version and respects scoped storage.
* *No database library*: the data is small and a JSON file keeps the build simple and the backup trivial.
* *Virtual renaming*: Android 11+ asks for permission for every file rename, so MusicBox stores titles and artists itself.
  Real file renaming is on the roadmap (it needs `MediaStore.createWriteRequest`).
