package com.eval.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Plays chess move sounds using SoundPool for low-latency audio.
 * Supports different sounds for regular moves, captures, checks, and castling.
 */
class MoveSoundPlayer(context: Context) {
    private val soundPool: SoundPool
    private var moveSound: Int = 0
    private var captureSound: Int = 0
    private var checkSound: Int = 0
    private var castleSound: Int = 0
    // Per-sample ready flags. Previously a single isLoaded boolean flipped
    // to true after the first successful load callback, which meant playMove
    // could fire for a sound whose sample had not finished decoding yet. It
    // was also latched to false permanently if init threw, silencing audio
    // for the rest of the session. Track each sample separately instead.
    private val ready = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            ready[sampleId] = (status == 0)
            if (status != 0) {
                android.util.Log.w("MoveSoundPlayer", "sample $sampleId failed to load (status=$status)")
            }
        }

        // Load sounds from raw resources (WAV format for broad compatibility).
        // A failure here means the resource is missing/corrupt for this specific
        // sample — other samples still load, and per-sample checks below skip
        // only the broken ones.
        try { moveSound = soundPool.load(context, com.eval.R.raw.move, 1) }
        catch (e: Exception) { android.util.Log.e("MoveSoundPlayer", "move: ${e.message}") }
        try { captureSound = soundPool.load(context, com.eval.R.raw.capture, 1) }
        catch (e: Exception) { android.util.Log.e("MoveSoundPlayer", "capture: ${e.message}") }
        try { checkSound = soundPool.load(context, com.eval.R.raw.check, 1) }
        catch (e: Exception) { android.util.Log.e("MoveSoundPlayer", "check: ${e.message}") }
        try { castleSound = soundPool.load(context, com.eval.R.raw.castle, 1) }
        catch (e: Exception) { android.util.Log.e("MoveSoundPlayer", "castle: ${e.message}") }
    }

    private fun isReady(sampleId: Int): Boolean =
        sampleId != 0 && ready[sampleId] == true

    /**
     * Play the appropriate sound for a move.
     * @param isCapture True if the move captures a piece
     * @param isCheck True if the move gives check
     * @param isCastle True if the move is castling
     */
    fun playMove(isCapture: Boolean = false, isCheck: Boolean = false, isCastle: Boolean = false) {
        val soundId = when {
            isCheck && isReady(checkSound) -> checkSound
            isCapture && isReady(captureSound) -> captureSound
            isCastle && isReady(castleSound) -> castleSound
            isReady(moveSound) -> moveSound
            else -> 0
        }

        if (soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    /**
     * Play a simple move sound (used for navigation).
     */
    fun playMoveSound() {
        if (!isReady(moveSound)) return
        soundPool.play(moveSound, 0.8f, 0.8f, 1, 0, 1.0f)
    }

    /**
     * Release resources when no longer needed.
     */
    fun release() {
        soundPool.release()
    }
}
