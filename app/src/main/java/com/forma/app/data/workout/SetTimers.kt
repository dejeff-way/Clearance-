package com.forma.app.data.workout

import com.forma.app.data.repo.WorkoutRepository
import com.forma.app.data.seed.ExerciseKind
import com.forma.app.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hold lengths and rest recommendations worked out from a slot's target text ("20–40 s", "45 s / side", "5 min"). */
object TimerAdvice {
    private val RANGE = Regex("""(\d+(?:\.\d+)?)\s*(?:[–-]\s*(\d+(?:\.\d+)?))?\s*(s|sec|secs|second|seconds|min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

    /** (low, high) in seconds, or null if the target isn't a time. */
    fun holdRange(target: String): Pair<Int, Int>? {
        val m = RANGE.find(target) ?: return null
        val mult = if (m.groupValues[3].lowercase().startsWith("m")) 60.0 else 1.0
        val lo = (m.groupValues[1].toDouble() * mult).toInt()
        val hi = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { (it.toDouble() * mult).toInt() } ?: lo
        return if (lo <= 0) null else lo to maxOf(lo, hi)
    }

    /** "45 s / side", "30 s / leg", "each side" → hold once per side. */
    fun perSide(target: String) = Regex("""\b(side|leg|arm|each)\b""", RegexOption.IGNORE_CASE).containsMatchIn(target)

    /** Holds up to 10 minutes get a countdown; longer timed work (treadmill etc.) is logged in minutes. */
    fun hasHoldTimer(timed: Boolean, kind: ExerciseKind, target: String) =
        timed && kind != ExerciseKind.CARDIO && (holdRange(target)?.second ?: Int.MAX_VALUE) <= 600

    /**
     * Rest between sets. The workout's own rest wins; otherwise a sensible default:
     * loaded strength 90 s, bodyweight strength 60 s, core 45 s, holds/mobility/balance 30 s.
     */
    fun recommendedRest(slotRest: Int, kind: ExerciseKind, tracksWeight: Boolean, timed: Boolean): Int = when {
        slotRest > 0 -> slotRest
        timed -> 30
        kind == ExerciseKind.STRENGTH && tracksWeight -> 90
        kind == ExerciseKind.STRENGTH -> 60
        kind == ExerciseKind.CORE -> 45
        else -> 30
    }

    /** Starting hold: what you held last time for this set, else the low end of the range. */
    fun defaultHold(target: String, lastSeconds: Int?): Int {
        val (lo, hi) = holdRange(target) ?: return lastSeconds ?: 30
        return (lastSeconds ?: lo).coerceIn(5, maxOf(hi * 2, lo))
    }

    fun label(seconds: Int): String = if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} min"
        else if (seconds >= 60) "${seconds / 60}:${"%02d".format(seconds % 60)}" else "$seconds s"
}

enum class TimerKind { HOLD, REST }

/** One step of a timer. [holding] phases are the ones that count as hold time. */
data class TimerPhase(val label: String, val seconds: Int, val holding: Boolean)

data class SetTimer(
    val kind: TimerKind,
    val sessionId: Long,
    val slotKey: String,
    val exerciseName: String,
    val setIndex: Int,
    val totalSets: Int,
    val phases: List<TimerPhase>,
    val startedAt: Long,
    /** Rest started automatically when a hold finishes (0 = none). */
    val restAfter: Int = 0,
) {
    val isLastSet get() = setIndex >= totalSets - 1
    val totalMs get() = phases.sumOf { it.seconds } * 1000L

    fun at(now: Long): TimerTick {
        var elapsed = (now - startedAt).coerceAtLeast(0)
        var held = 0L
        phases.forEachIndexed { i, p ->
            val len = p.seconds * 1000L
            if (elapsed < len) {
                if (p.holding) held += elapsed
                return TimerTick(i, p, len - elapsed, done = false, heldSeconds = (held / 1000).toInt(), totalRemainingMs = totalMs - (now - startedAt))
            }
            if (p.holding) held += len
            elapsed -= len
        }
        return TimerTick(phases.size, null, 0, done = true, heldSeconds = (held / 1000).toInt(), totalRemainingMs = 0)
    }

    companion object {
        const val GET_READY = 3
        const val SWITCH = 5

        fun hold(sessionId: Long, slotKey: String, name: String, setIndex: Int, totalSets: Int, seconds: Int, perSide: Boolean, restAfter: Int, now: Long) =
            SetTimer(
                TimerKind.HOLD, sessionId, slotKey, name, setIndex, totalSets,
                buildList {
                    add(TimerPhase("Get ready", GET_READY, false))
                    if (perSide) {
                        add(TimerPhase("First side", seconds, true))
                        add(TimerPhase("Switch sides", SWITCH, false))
                        add(TimerPhase("Second side", seconds, true))
                    } else add(TimerPhase("Hold", seconds, true))
                },
                now, restAfter,
            )

        fun rest(sessionId: Long, slotKey: String, name: String, setIndex: Int, totalSets: Int, seconds: Int, now: Long) =
            SetTimer(TimerKind.REST, sessionId, slotKey, name, setIndex, totalSets, listOf(TimerPhase("Rest", seconds, false)), now)
    }
}

data class TimerTick(val phaseIndex: Int, val phase: TimerPhase?, val remainingMs: Long, val done: Boolean, val heldSeconds: Int, val totalRemainingMs: Long)

enum class TimerAlert { PHASE, COUNTDOWN, DONE }

/**
 * Runs the hold and rest timers. Times come from the wall clock, so a timer keeps the right time when the
 * screen is left or the app is in the background. A finished hold is logged as that set's time; the next
 * set's rest starts on its own.
 */
class SetTimerController(
    private val time: TimeProvider,
    private val workouts: WorkoutRepository,
    private val scope: CoroutineScope,
    private val alert: (TimerAlert) -> Unit,
) {
    private val _current = MutableStateFlow<SetTimer?>(null)
    val current: StateFlow<SetTimer?> = _current
    private val _tick = MutableStateFlow<TimerTick?>(null)
    val tick: StateFlow<TimerTick?> = _tick
    private var job: Job? = null
    private var lastPhase = -1
    private var lastWholeSecond = -1L
    private val lock = Mutex()

    fun startHold(sessionId: Long, slotKey: String, name: String, setIndex: Int, totalSets: Int, seconds: Int, perSide: Boolean, restAfter: Int) =
        begin(SetTimer.hold(sessionId, slotKey, name, setIndex, totalSets, seconds.coerceIn(5, 600), perSide, restAfter, time.nowMillis()))

    fun startRest(sessionId: Long, slotKey: String, name: String, setIndex: Int, totalSets: Int, seconds: Int) =
        begin(SetTimer.rest(sessionId, slotKey, name, setIndex, totalSets, seconds.coerceIn(5, 600), time.nowMillis()))

    /** Add or take away rest time (never below 5 s left). */
    fun adjustRest(deltaSeconds: Int) {
        val t = _current.value?.takeIf { it.kind == TimerKind.REST } ?: return
        val elapsed = ((time.nowMillis() - t.startedAt) / 1000).toInt()
        val secs = (t.phases[0].seconds + deltaSeconds).coerceIn(elapsed + 5, 600)
        _current.value = t.copy(phases = listOf(t.phases[0].copy(seconds = secs)))
        _tick.value = _current.value!!.at(time.nowMillis())
    }

    /** Stop now. A hold stopped early still logs the time actually held (if any); rest just ends. */
    fun stop() = scope.launch {
        lock.withLock {
            val t = _current.value ?: return@withLock
            clear()
            if (t.kind == TimerKind.HOLD) {
                val k = t.at(time.nowMillis())
                val perSide = t.phases.count { it.holding }
                val held = k.heldSeconds / perSide
                if (held > 0) workouts.logSet(t.sessionId, t.slotKey, t.setIndex, null, null, held / 60.0)
            }
        }
    }

    /** Throws the timer away without logging anything. */
    fun cancel() = clear()

    private fun begin(t: SetTimer) {
        _current.value = t
        lastPhase = -1; lastWholeSecond = -1
        _tick.value = t.at(time.nowMillis())
        if (job?.isActive != true) job = scope.launch {
            while (isActive && _current.value != null) { poll(); delay(200) }
        }
    }

    private fun clear() { _current.value = null; _tick.value = null; lastPhase = -1 }

    /** Advances the timer. Public so tests can drive it without waiting. */
    suspend fun poll() = lock.withLock {
        val t = _current.value ?: return@withLock
        val k = t.at(time.nowMillis())
        _tick.value = k
        if (!k.done) {
            if (lastPhase != -1 && k.phaseIndex != lastPhase) alert(TimerAlert.PHASE)
            lastPhase = k.phaseIndex
            val whole = (k.remainingMs + 999) / 1000
            if (whole in 1..3 && whole != lastWholeSecond) alert(TimerAlert.COUNTDOWN)
            lastWholeSecond = whole
            return@withLock
        }
        clear()
        alert(TimerAlert.DONE)
        if (t.kind == TimerKind.HOLD) {
            val held = t.phases.first { it.holding }.seconds
            workouts.logSet(t.sessionId, t.slotKey, t.setIndex, null, null, held / 60.0)
            if (t.isLastSet) workouts.setCompleted(t.sessionId, t.slotKey, true)
            else if (t.restAfter > 0) begin(SetTimer.rest(t.sessionId, t.slotKey, t.exerciseName, t.setIndex, t.totalSets, t.restAfter, time.nowMillis()))
        }
    }
}
