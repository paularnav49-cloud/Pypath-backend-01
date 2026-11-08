package com.pypath.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf

/** Every sound the app can play. File names match app/src/main/res/raw (made by content/build_sounds.py). */
enum class Sfx(val resName: String) {
    CORRECT("sfx_correct"), WRONG("sfx_wrong"), FILL("sfx_fill"), PASS("sfx_pass"), FAIL("sfx_fail"),
    BADGE_L1("badge_l1"), BADGE_L2("badge_l2"), BADGE_L3("badge_l3"), BADGE_L4("badge_l4"),
    BADGE_L5("badge_l5"), BADGE_L6("badge_l6"), BADGE_L7("badge_l7");

    companion object {
        fun badge(levelNumber: Int): Sfx? = entries.firstOrNull { it.resName == "badge_l$levelNumber" }
    }
}

/** What screens use. The default is silent, so previews, Paparazzi and tests need no audio. */
interface SoundPlayer {
    fun play(sfx: Sfx)
}

object SilentSoundPlayer : SoundPlayer {
    override fun play(sfx: Sfx) = Unit
}

val LocalSounds = staticCompositionLocalOf<SoundPlayer> { SilentSoundPlayer }

/**
 * Short effects and badge jingles via one [SoundPool] (all clips are under 4 s and 150 KB).
 *
 * - Loaded once (lazily) by AppContainer, released from MainActivity.onDestroy when the activity finishes.
 * - Uses USAGE_GAME, so it follows the media volume; nothing plays when the ringer is silent or on
 *   vibrate, or when the user turns "Sound effects" off in the ⋮ menu.
 * - Clips are looked up by name, so the app still builds and runs (silently) if the WAV files
 *   have not been generated yet. res/raw/keep.xml stops resource shrinking from removing them.
 * - Never used by the Practical tab.
 */
class SoundManager(private val context: Context) : SoundPlayer {

    @Volatile var enabled: Boolean = true

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Sfx, Int>()
    private val ready = mutableSetOf<Int>()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Synchronized
    fun ensureLoaded() {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(this) { ready += sampleId } }
        for (sfx in Sfx.entries) {
            @Suppress("DiscouragedApi")
            val res = context.resources.getIdentifier(sfx.resName, "raw", context.packageName)
            if (res != 0) ids[sfx] = p.load(context, res, 1) else Log.w(TAG, "missing res/raw/${sfx.resName}.wav")
        }
        pool = p
    }

    override fun play(sfx: Sfx) {
        if (!enabled || isSilenced()) return
        val p = pool ?: return
        val id = ids[sfx] ?: return
        if (synchronized(this) { id !in ready }) return
        val volume = if (sfx.resName.startsWith("badge")) 0.9f else 0.7f
        p.play(id, volume, volume, 1, 0, 1f)
    }

    private fun isSilenced(): Boolean = audio?.ringerMode?.let { it != AudioManager.RINGER_MODE_NORMAL } ?: false

    @Synchronized
    fun release() {
        pool?.release()
        pool = null
        ids.clear()
        ready.clear()
    }

    private companion object {
        const val TAG = "SoundManager"
    }
}
