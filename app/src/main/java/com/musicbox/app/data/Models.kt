package com.musicbox.app.data

import android.net.Uri
import kotlinx.serialization.Serializable

/** One audio file found on the device (read from MediaStore). */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artistTag: String?,
    val album: String?,
    val durationMs: Long,
    val dateAddedSec: Long,
    val fileName: String,
    val path: String,
)

@Serializable
data class ArtistDef(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val photo: String? = null,
)

@Serializable
data class MoodDef(
    val id: String,
    val name: String,
    val emoji: String,
    val keywords: List<String> = emptyList(),
    val colorA: Long = 0xFF2563EB,
    val colorB: Long = 0xFF7C3AED,
)

/** Everything the user (or the AI) has said about one song. Keyed by MediaStore id. */
@Serializable
data class SongState(
    val plays: Int = 0,
    val lastPlayed: Long = 0L,
    val favorite: Boolean = false,
    val favoriteAt: Long = 0L,
    val titleOverride: String? = null,
    val artistIds: List<String>? = null,
    val otherArtist: String? = null,
    val moodsOverride: List<String>? = null,
    val aiMoods: List<String> = emptyList(),
    val situations: List<String> = emptyList(),
)

@Serializable
data class CustomFolder(
    val id: String,
    val name: String,
    val songIds: List<Long> = emptyList(),
)

@Serializable
data class HomeSection(
    val id: String,
    val type: String,
    val title: String,
    val visible: Boolean = true,
    val param: String = "",
)

@Serializable
data class AiSettings(
    val provider: String = "anthropic",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "claude-haiku-4-5-20251001",
    val webSearch: Boolean = false,
)

@Serializable
data class AudioSettings(
    val eqEnabled: Boolean = false,
    val bands: List<Int> = emptyList(),
    val bass: Int = 0,
    val loudness: Int = 0,
)

@Serializable
data class AppSettings(
    val excludedWords: List<String> = listOf("whatsapp", "ringtone", "notification", "alarm", "recording"),
    val minDurationSec: Int = 30,
    val ai: AiSettings = AiSettings(),
    val audio: AudioSettings = AudioSettings(),
)

@Serializable
data class UserData(
    val version: Int = 3,
    val artists: List<ArtistDef> = emptyList(),
    val moods: List<MoodDef> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val folders: List<CustomFolder> = emptyList(),
    val songState: Map<String, SongState> = emptyMap(),
    val manualOrder: Map<String, List<Long>> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    /** New songs the user has dealt with (moved out of "New Songs" or marked as seen). */
    val seenNew: List<Long> = emptyList(),
)

object SectionType {
    const val ARTISTS = "ARTISTS"
    const val RECENT = "RECENT"
    const val MOST_PLAYED = "MOST_PLAYED"
    const val FAVORITES = "FAVORITES"
    const val MOODS = "MOODS"
    const val MOOD = "MOOD"
    const val FOLDER = "FOLDER"
    const val FOLDERS = "FOLDERS"
    const val RECENT_ADDED = "RECENT_ADDED"
    const val FORGOTTEN = "FORGOTTEN"
    const val NEVER_PLAYED = "NEVER_PLAYED"
    const val LONG = "LONG"
    const val NEW_SONGS = "NEW_SONGS"
}
