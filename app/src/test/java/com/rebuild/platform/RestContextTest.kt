package com.rebuild.platform

import com.rebuild.data.StateChanges
import com.rebuild.domain.Action
import com.rebuild.domain.AppState
import com.rebuild.domain.Phase
import com.rebuild.domain.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RestContextTest {
    private val now = 1_800_000_000_000L

    private fun started(): AppState {
        var s = Seed.initialState()
        s = StateChanges.apply(s, Action.SelectPhase(Phase.INTRO_TWO))
        return StateChanges.apply(s, Action.StartWorkout("intro-upper", now))
    }

    @Test fun afterASetItNamesTheExerciseAndTheNextSetOfItsTarget() {
        var s = started()
        val w = s.activeWorkout!!
        val first = w.exercises.first()
        s = StateChanges.apply(s, Action.SaveSet(w.id, first.id, "a", 40.0, 10, 2.0, false, now + 60_000))
        val timer = assertNotNull(s.restTimer).let { s.restTimer!! }
        val about = restContext(s, timer)!!
        assertEquals(first.exercise.name, about.exercise)
        assertEquals("Next: set 2 of ${first.targetSets}", about.next)
    }

    @Test fun onceAnExerciseIsDoneItNamesTheNextExercise() {
        var s = started()
        val w = s.activeWorkout!!
        val first = w.exercises.first()
        repeat(first.targetSets!!) { i ->
            s = StateChanges.apply(s, Action.SaveSet(w.id, first.id, "s$i", 40.0, 10, 2.0, false, now + (i + 1) * 60_000L))
        }
        val about = restContext(s, s.restTimer!!)!!
        assertEquals("Next: ${w.exercises[1].exercise.name}", about.next)
    }

    @Test fun warmUpsDoNotCountAsWorkingSets() {
        var s = started()
        val w = s.activeWorkout!!
        val first = w.exercises.first()
        s = StateChanges.apply(s, Action.SaveSet(w.id, first.id, "w", 20.0, 10, null, true, now + 60_000))
        val timer = s.restTimer ?: return // a warm-up may not start a rest; then there is nothing to describe
        assertEquals("Next: set 1 of ${first.targetSets}", restContext(s, timer)!!.next)
    }

    @Test fun theTakeoverAndCardSayTheNextSetBigAndWhatItBelongsTo() {
        val set = RestContext("Bench press", nextSet = 2, ofSets = 3)
        assertEquals("Set 2", set.headline)
        assertEquals("of 3 · Bench press", set.detail)
        assertEquals("Next: set 2 of 3", set.next)
        val untargeted = RestContext("Plank", nextSet = 4)
        assertEquals("Plank", untargeted.detail)
        assertEquals("Next: set 4", untargeted.next)
        val moving = RestContext("Bench press", nextExercise = "Seated cable row")
        assertEquals("Seated cable row", moving.headline)
        assertEquals("after Bench press", moving.detail)
        assertEquals("All sets done", RestContext("Bench press").headline)
        assertEquals("Go again", RestContext("Bench press", shortPause = true).headline)
    }
}
