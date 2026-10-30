package com.ozkan.altyazi.core

/**
 * Turns a stream of per-frame OCR reads into real subtitle changes.
 *
 * - A read is only committed after [requiredHits] consecutive matching frames (blur, fade animations).
 * - A commit that is nearly identical to the shown subtitle is suppressed (OCR jitter).
 * - Typewriter-style subtitles that keep growing are treated as updates.
 * - After a few empty frames the state resets, so a repeated line is translated again.
 *
 * Not thread-safe by design: it is only called from the single camera analysis thread.
 */
class SubtitleStabilizer(
    requiredHits: Int = 2,
    private val sameThreshold: Double = 0.88,
    private val changeThreshold: Double = 0.80,
    private val emptyFramesToClear: Int = 5,
) {
    sealed interface Event {
        data class Commit(val text: String) : Event
        data object Cleared : Event
    }

    var requiredHits: Int = requiredHits.coerceAtLeast(1)
        set(value) { field = value.coerceAtLeast(1) }

    private var candidate = ""
    private var hits = 0
    private var candidateHandled = false
    private var committed: String? = null
    private var emptyFrames = 0

    fun reset() {
        candidate = ""; hits = 0; candidateHandled = false; committed = null; emptyFrames = 0
    }

    fun accept(text: String): Event? {
        if (text.isBlank()) {
            emptyFrames++
            if (emptyFrames == emptyFramesToClear && committed != null) {
                reset()
                emptyFrames = emptyFramesToClear
                return Event.Cleared
            }
            return null
        }
        emptyFrames = 0

        if (candidate.isNotEmpty() && TextSimilarity.ratio(text, candidate) >= sameThreshold) {
            hits++
            if (!candidateHandled) candidate = text // keep the latest (usually sharpest) read
        } else {
            candidate = text
            hits = 1
            candidateHandled = false
        }

        if (candidateHandled || hits < requiredHits) return null
        candidateHandled = true

        val previous = committed
        val isNew = previous == null ||
            TextSimilarity.ratio(candidate, previous) < changeThreshold ||
            isExtension(previous, candidate)
        if (!isNew) return null

        committed = candidate
        return Event.Commit(candidate)
    }

    private fun isExtension(previous: String, next: String): Boolean {
        if (next.length < previous.length + 6) return false
        val prefix = previous.take((previous.length * 0.8).toInt().coerceAtLeast(4))
        return next.startsWith(prefix, ignoreCase = true)
    }
}
