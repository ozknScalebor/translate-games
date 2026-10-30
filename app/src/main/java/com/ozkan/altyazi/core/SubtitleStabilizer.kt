package com.ozkan.altyazi.core

/**
 * Turns a stream of per-frame OCR reads into real subtitle changes.
 *
 * - Reads of the same line are collected over [requiredHits] consecutive frames before committing.
 * - The committed text is chosen by voting: the read that is both well-scored and most similar to
 *   the other reads wins, so a single garbled frame cannot spoil the result.
 * - A commit that is nearly identical to the shown subtitle is suppressed (OCR jitter).
 * - Typewriter-style subtitles that keep growing are treated as updates.
 * - After a few empty frames the state resets, so a repeated line is translated again.
 *
 * Not thread-safe by design: it is only called from the single camera analysis thread.
 */
class SubtitleStabilizer(
    requiredHits: Int = 2,
    private val sameThreshold: Double = 0.80,
    private val changeThreshold: Double = 0.80,
    private val emptyFramesToClear: Int = 5,
) {
    sealed interface Event {
        data class Commit(val text: String) : Event
        data object Cleared : Event
    }

    var requiredHits: Int = requiredHits.coerceAtLeast(1)
        set(value) { field = value.coerceAtLeast(1) }

    private val reads = ArrayDeque<Read>()
    private var candidate = ""
    private var candidateHandled = false
    private var committed: String? = null
    private var emptyFrames = 0

    fun reset() {
        reads.clear(); candidate = ""; candidateHandled = false; committed = null; emptyFrames = 0
    }

    fun accept(text: String): Event? = accept(Read.of(text))

    fun accept(read: Read): Event? {
        if (read.text.isBlank()) {
            emptyFrames++
            if (emptyFrames == emptyFramesToClear && committed != null) {
                reset()
                emptyFrames = emptyFramesToClear
                return Event.Cleared
            }
            return null
        }
        emptyFrames = 0

        if (candidate.isNotEmpty() && TextSimilarity.ratio(read.text, candidate) >= sameThreshold) {
            if (!candidateHandled) {
                reads.addLast(read)
                if (reads.size > MAX_READS) reads.removeFirst()
                candidate = vote()
            } else {
                reads.addLast(read) // count the frame, keep the committed choice
                if (reads.size > MAX_READS) reads.removeFirst()
            }
        } else {
            reads.clear()
            reads.addLast(read)
            candidate = read.text
            candidateHandled = false
        }

        if (candidateHandled || reads.size < requiredHits) return null
        candidateHandled = true

        val previous = committed
        val isNew = previous == null ||
            TextSimilarity.ratio(candidate, previous) < changeThreshold ||
            isExtension(previous, candidate)
        if (!isNew) return null

        committed = candidate
        return Event.Commit(candidate)
    }

    /** Medoid vote: own score plus average agreement with the other reads. */
    private fun vote(): String = reads.maxBy { r ->
        val agreement = reads.sumOf { other -> TextSimilarity.ratio(r.text, other.text) } / reads.size
        r.score + agreement
    }.text

    private fun isExtension(previous: String, next: String): Boolean {
        if (next.length < previous.length + 6) return false
        val prefix = previous.take((previous.length * 0.8).toInt().coerceAtLeast(4))
        return next.startsWith(prefix, ignoreCase = true)
    }

    private companion object {
        const val MAX_READS = 8
    }
}
