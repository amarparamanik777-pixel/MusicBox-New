# Contributing to MusicBox

Thanks for helping! A few simple rules keep the project friendly and easy to maintain.

1. **Open an issue first** for anything bigger than a small fix, so we can agree on the idea.
2. **Keep it offline-first.** Playing music must never need the internet. Online features must be optional and clearly explained.
3. **Privacy matters.** No analytics, no ads, no tracking, no uploading of audio files.
4. **Small pull requests** are reviewed faster. One idea per pull request.
5. **Try it on a phone** before sending a pull request, and say which Android version you tested.

## Building

- Push to GitHub and download the APK from the *Actions* tab, or
- open the folder in Android Studio (Koala or newer) and press Run, or
- run `gradle :app:assembleDebug` with Gradle 8.9 and JDK 17.

## Code style

Kotlin official style, Jetpack Compose for UI. The file-name parser lives in `app/src/main/java/com/musicbox/app/organize`
and has unit tests in `app/src/test`; please add a test when you change how names are understood.
