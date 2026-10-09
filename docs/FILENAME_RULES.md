# How MusicBox understands your file names

MusicBox looks at three clues, strongest first, and combines them:

1. the **file name**
2. the **artist tag** inside the file (ID3 / MP4 tags)
3. the **parent folder name** (for example `Music/Arijit Singh/song.mp3`)

If a known artist is found, the song goes into that artist's folder (a duet appears in both folders).
If only an unknown artist tag exists, the song goes to **Others**. If nothing is found, it goes to **Unidentified**.

## Names that work

| File name | Result |
|---|---|
| `Tum Hi Ho by Arijit Singh` | artist: Arijit Singh, title: Tum Hi Ho |
| `tum hi ho (Arijit Singh) (romantic)` | artist, title **and** mood Romantic |
| `Arijit Singh - Tum Hi Ho` | artist + title |
| `Tum Hi Ho - Arijit Singh` | artist + title |
| `Kesariya - Brahmastra - Arijit Singh` | title Kesariya (the middle part is ignored) |
| `Lata Mangeshkar & Kishore Kumar - Pyar Hua` | both artist folders |
| `01. Tum Hi Ho - Arijit Singh [PagalWorld.com] 320kbps` | web-site names, bitrate and track numbers are removed |
| `Arijit Singh Tum Hi Ho` | the artist is found inside the text |
| `AR Rahman`, `A R Rahman`, `A.R. Rahman` | all mean the same artist |
| `Jai Ho by Arjit Singh` | small spelling mistakes in longer names are forgiven |

Add your own nicknames in **Settings > Artists** (for example `Arijit`, `Honey Singh`).

## Moods in file names

Put a mood word in brackets: `(sad)`, `(romantic)`, `(motivation)`, `(devotional)`, `(party)`, `(relax)`, `(feel good)`,
`(energise)`, `(sufi)`, `(retro)`, `(focus)` - or any keyword you add in **Settings > Moods**.
Words in the title also count: `Hanuman Chalisa` becomes Devotional, `Love Story` becomes Romantic.

## Changing things without renaming files

Song menu > **Edit info / move to artist** changes the title, artist (folder) and mood inside MusicBox.
Type a known artist to move the song to their folder, or any new name to put it in Others.
Your original files are never renamed or changed.

## Tips

* A leading number without a dash (`07 Tum Hi Ho`) is kept as part of the title.
* If a title really contains the word "by" (`Stand By Me`) and no artist follows it, nothing is split.
* Still wrong? Use *Edit info* once - the choice is remembered.
