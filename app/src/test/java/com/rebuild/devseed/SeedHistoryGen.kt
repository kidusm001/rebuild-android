package com.rebuild.devseed

import com.rebuild.data.SnapshotCodec
import com.rebuild.data.StateChanges
import com.rebuild.domain.*
import org.junit.Test
import java.io.File

/** Local design-review helper (not committed): builds 10 weeks of realistic history and writes the snapshot. */
class SeedHistoryGen {
    @Test
    fun generate() {
        val out = System.getenv("SEED_OUT") ?: return
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        var s = Seed.initialState()
        fun apply(a: Action) { s = StateChanges.apply(s, a) }
        apply(Action.SelectPhase(Phase.INTRO_TWO))
        val templates = listOf("intro-upper", "lower-a", "lower-b", "intro-upper")
        var setN = 0
        for (week in 0 until 10) {
            templates.forEachIndexed { k, t ->
                val start = now - (70 - week * 7 - k * 2) * day + 18 * 3_600_000L
                if (start > now - day) return@forEachIndexed
                apply(Action.StartWorkout(t, start))
                val w = s.activeWorkout!!
                w.exercises.forEachIndexed { i, we ->
                    val base = when (we.exercise.loading) { LoadingType.BODYWEIGHT -> null; else -> 20.0 + i * 7.5 }
                    val n = we.targetSets ?: 2
                    repeat(n) { r ->
                        val load = base?.let { it + week * 1.25 + (if (r == 1) -2.5 else 0.0) }
                        val reps = 8 + ((week + r + i) % 5)
                        apply(Action.SaveSet(w.id, we.id, "s${setN++}", load, reps, (3 - (week % 3)).toDouble(), false,
                            start + (i * 10 + r * 3) * 60_000L))
                    }
                }
                apply(Action.FinishWorkout(start + 70 * 60_000L))
            }
        }
        for (d in 70 downTo 1 step 1) if (d % 3 != 0) {
            val v = 84.0 - (70 - d) * 0.03 + listOf(0.4, -0.3, 0.2, -0.1, 0.0)[d % 5]
            apply(Action.LogBodyweight("bw$d", now - d * day + 7 * 3_600_000L, Math.round(v * 10) / 10.0, MassUnit.KG))
        }
        for (wk in 9 downTo 0) {
            val at = now - (wk * 7 + 1) * day
            apply(Action.SaveMeasurements(listOf(BodyMeasurement("m$wk", at, localDateOf(at).toString(), BodySite.WAIST,
                92.0 - (9 - wk) * 0.35, LengthUnit.CM))))
        }
        File(out).writeText(SnapshotCodec.encode(s))
    }
}
