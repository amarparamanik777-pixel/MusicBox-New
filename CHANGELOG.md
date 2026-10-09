# Changelog

All notable changes to MusicBox are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com) and the project uses semantic versioning.

## [Unreleased] - songs everywhere

### Added
- **All Songs** page (Library > All songs): every song with search, multi-select (long-press) and a **Duplicates** filter.
- **New Songs** page and Home section: songs added in the last 14 days that you have not sorted yet. Move them into folders or tap the tick to mark all as seen.
- **Copy to... / Move to...** in every song menu. Destinations: your folders (or a new one), Favorites, any artist, any mood. A song can live in as many places as you like.
- Songs stored more than once (same title and artist) get a pink **DUP x2** tag everywhere; the Duplicates filter also shows where each file is stored.
- MusicBox tells you when a new song is already in your library, and when a song is already in the folder you add it to.

### Changed
- Nothing existing was removed. "Add to folder" works as before and now also says when the song was already there.
- Backup files now include which new songs you have dealt with (older backups still restore).

## [0.1.1] - bug fixes and polish

### Fixed
- Now Playing covered the screen but let taps through to the song list and navigation bar underneath.
- One damaged or deleted file no longer stops the queue: playback jumps to the next song.
- Play counts and favourites are saved when the app goes to the background; an unreadable save file is kept as `userdata.json.bad` instead of being overwritten.
- The default skip word "call" hid songs such as "Call Me Maybe" (existing installs are migrated).
- Permission screen: "Try again" and "Open app settings" after a refusal, and the app notices when you grant access in Settings.
- Changes to your music made during a scan trigger one more scan; Home says "Scanning" instead of "No songs found" at start-up.
- Search is much faster on big libraries (search text is prepared once).
- Cover-art cache keeps screen-sized pictures instead of full embedded images; one failing audio effect no longer disables the others.
- Back goes up one screen, then to Home, before leaving the app. Home keeps its scroll position.

### Added
- Animations: screen slide/fade transitions, staggered Home sections, carousel parallax, press-bounce on cards and the play button, cover cross-fades, a record-style spinning cover in the mini player, a cover that shrinks on pause, bouncing bars around the seek bar playhead, colour glow that follows the cover.
- Swipe the cover left or right to change song.
- Playback speed (0.75x - 2x) in the Now Playing menu; the sleep timer fades the volume out and shows the minutes left.
- More unit tests (text helpers, default data).

## [0.1.0] - first release

### Added
- Plays songs from local storage with background playback, notification and lock-screen controls.
- Home screen with an auto-sliding artist carousel, Recently Played, a numbered Most Played grid,
  Favorites, Moods, Devotional, Recently Added, Forgotten Songs and Hidden Gems.
- Automatic artist folders from file names ("Tum Hi Ho by Arijit Singh", "tum hi ho (Arijit Singh) (romantic)"),
  ID3 tags and parent folder names, plus **Others** and **Unidentified** folders.
- Artist folders with drag-and-drop ordering and three shuffle modes: Random, Most played, My sequence.
- Custom folders, home sections you can reorder, hide and add, and moving songs between folders.
- Moods with editable keywords, favorites, play counts and a queue you can re-order.
- Now Playing screen with wave seek bar, lyrics (.lrc / .txt), sleep timer, equalizer, bass and loudness boost.
- Optional AI Organizer (Claude or any OpenAI-compatible service) that suggests titles, artists and moods for review.
- Backup and restore of everything you changed.
