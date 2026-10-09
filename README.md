<p align="center"><img src="docs/assets/logo.png" width="120" alt="MusicBox logo"></p>

# MusicBox - Feel The Music

An **offline music player for Android** that plays the songs stored on your phone and keeps them
organised for you: artist folders, moods, favourites, most-played charts and smart shuffle.
No account, no ads, no tracking.

> **Status: version 0.1.** The code was written to build with GitHub Actions. If the first build shows a
> red cross, open the failed run, copy the error and send it to whoever maintains the project - it is
> usually a one-line fix.

## What it does

| Area | Details |
|---|---|
| **Auto artist folders** | Reads file names, song tags and parent folders. `Tum Hi Ho by Arijit Singh` goes to the *Arijit Singh* folder. |
| **Others / Unidentified** | Songs by artists that are not in your list go to **Others**. Songs nothing can be read from go to **Unidentified**, where you (or the AI) fix them. |
| **Home screen** | Auto-sliding artist carousel (tap an artist to open the folder), Recently Played, a numbered **Most Played** grid, Favorites, Moods, Devotional, Recently Added, Forgotten Songs, Hidden Gems and your own folders. |
| **Your layout** | Move sections up and down, hide them, add a mood or a folder as a section. |
| **Artist folder** | Drag and drop songs into your own order. **Shuffle** offers *Random*, *Most played* and *My sequence* (pick songs 1, 2, 3... then the rest plays randomly with no repeats). |
| **Folders and moving songs** | Make folders, add any song to them, edit a song's artist/mood to move it to another folder. |
| **All Songs, New Songs, copy and move** | One page with every song and a *Duplicates* filter (copies are tagged **DUP**). New Songs lists what you added recently. Long-press to select songs, then copy or move them to any folder, artist, mood or Favorites - a song can be in many places. |
| **Player** | Background playback, notification and lock-screen controls, queue you can re-order, sleep timer, lyrics (`.lrc` or `.txt`), equalizer, bass and loudness boost. |
| **AI Organizer (optional)** | Suggests real titles, artists, moods and situations for songs. You review every guess before it is applied. Uses Claude or any OpenAI-compatible service with **your own key**. |
| **Backup** | Export and restore play counts, favourites, folders and layout. |

See [docs/FILENAME_RULES.md](docs/FILENAME_RULES.md) for the naming tricks and [docs/ROADMAP.md](docs/ROADMAP.md) for what comes next.

## Get the app (no computer programming needed)

1. Create a repository on GitHub and upload the contents of this folder (see *Uploading* below).
2. Open the **Actions** tab. The workflow **Android CI** starts by itself (about 5-8 minutes the first time).
3. When it turns green, open the run and download **MusicBox-debug-apk** from *Artifacts*.
4. Unzip it, install the `.apk` on your phone (allow "install unknown apps" when asked).
5. Want a download link on the **Releases** page? Add a tag such as `v0.1.0`; the workflow attaches the APK to the release.

### Uploading

* **On a computer:** unzip, then drag *everything inside the folder* (including the hidden `.github` folder) into
  *Add file > Upload files* on your new repository. Or use `git`.
* **On a phone:** GitHub's web uploader does not keep folders. Easiest is a computer, or the free Termux app with `git`.
  If only the `.github` folder is missing, create the file `.github/workflows/android.yml` in the GitHub website
  (*Add file > Create new file*) and paste the text from `docs/ci/android.yml.txt`.

## Build it yourself

* **Android Studio** (Koala or newer): open the folder and press *Run*.
* **Command line:** JDK 17 and Gradle 8.9: `gradle :app:assembleDebug`. The APK is in `app/build/outputs/apk/debug/`.
  (Run `gradle wrapper` once if you want a `./gradlew` script.)

Requirements: Android 7.0 (API 24) or newer. Version 0.1.1 was reviewed by reading the code and by running the parser unit tests; please report anything odd on your phone.

## First run

1. Allow access to music (and notifications, so the player controls show).
2. MusicBox scans your phone. Check **Settings > Library** to skip folders (WhatsApp audio and ringtones are skipped by default).
3. Open **Settings > Artists** to add artists, nicknames and photos. It starts with a long list of Hindi, Bengali and international artists.
4. Open **Unidentified** from the Home carousel or Library to fix leftovers, or try **AI Organizer**.

## AI Organizer setup (optional)

*Settings > AI settings*: choose **Claude (Anthropic)** or **OpenAI-compatible** (OpenAI, Gemini, Groq, OpenRouter, a local server...),
paste your key, press Save. Then *Settings > AI Organizer > Ask AI*.
Only file names and tags are sent - never the audio. Results appear as a list of guesses; tick the ones you trust and press **Apply**.
The app renames songs *inside MusicBox* (title, artist, mood); your files on disk are not touched.

## Privacy

Playback and organising are fully offline. The `INTERNET` permission exists only for the optional AI Organizer.
Your data is kept in one small file inside the app's private storage and can be exported from Settings.

## Project layout

```
app/src/main/java/com/musicbox/app/
  data/       models, MediaStore scanner, saved user data, repository
  organize/   file-name parser, artist and mood matching, folder logic   (unit tested)
  player/     background service, remote controller, cover art, equalizer
  ai/         optional AI Organizer
  ui/         Jetpack Compose screens and components
docs/         roadmap, naming rules, architecture
.github/      CI workflow, issue and pull-request templates
```

## Contributing and license

Please read [CONTRIBUTING.md](CONTRIBUTING.md). MusicBox is released under the [MIT license](LICENSE).
