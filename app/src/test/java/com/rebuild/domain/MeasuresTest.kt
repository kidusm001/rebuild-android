package com.rebuild.domain

import org.junit.Assert.*
import org.junit.Test

class MeasuresTest {
    private fun library(id: String) = ExerciseCatalog.revisionTwo.single { it.id == id }
    private val plank = library("plank")
    private val carry = library("farmers-carry")
    private val treadmill = library("treadmill")

    private fun entry(
        exercise: Exercise,
        sets: Int? = null,
        goal: MeasuredGoal = MeasuredGoal(),
        efforts: List<Effort> = emptyList()
    ) = WorkoutExercise("entry", null, exercise, sets, null, null, null, null, null, 60, optional = true,
        efforts = efforts).withGoal(goal)

    private fun effort(id: String, seconds: Int? = null, distance: Distance? = null) =
        Effort(id, seconds, distance, null, LoadUnit.KG, null, 1000)

    private fun km(thousandths: Long) = Distance(thousandths, DistanceUnit.KM)
    private fun m(thousandths: Long) = Distance(thousandths, DistanceUnit.M)
    private fun mi(thousandths: Long) = Distance(thousandths, DistanceUnit.MI)

    @Test fun distancesParseExactlyWithEitherDecimalSeparatorAndNeverRound() {
        assertEquals(DistanceEntry.Valid(km(2400)), parseDistance("2.4", DistanceUnit.KM))
        assertEquals(DistanceEntry.Valid(km(2400)), parseDistance(" 2,4 ", DistanceUnit.KM))
        assertEquals(DistanceEntry.Valid(m(40_000)), parseDistance("40", DistanceUnit.M))
        assertEquals(DistanceEntry.Valid(mi(1)), parseDistance("0.001", DistanceUnit.MI))
        assertEquals("Trailing zeros past three decimals are not extra precision",
            DistanceEntry.Valid(km(1500)), parseDistance("1.500000", DistanceUnit.KM))
        assertEquals(DistanceEntry.TooPrecise, parseDistance("0.0005", DistanceUnit.KM))
        assertEquals(DistanceEntry.TooPrecise, parseDistance("2.4001", DistanceUnit.KM))
        assertEquals(DistanceEntry.Blank, parseDistance("  ", DistanceUnit.KM))
        listOf("-1", "1.2.3", "1e3", "+5", "5 km", ".5", "5.", "١٢").forEach {
            assertEquals(it, DistanceEntry.NotANumber, parseDistance(it, DistanceUnit.KM))
        }
        assertEquals(DistanceEntry.OutOfRange, parseDistance("0", DistanceUnit.KM))
        assertEquals(DistanceEntry.OutOfRange, parseDistance("0.000", DistanceUnit.M))
        // Each unit allows 1,000 km (or 1,000 mi) and no more.
        assertEquals(DistanceEntry.Valid(m(1_000_000_000)), parseDistance("1000000", DistanceUnit.M))
        assertEquals(DistanceEntry.OutOfRange, parseDistance("1000000.001", DistanceUnit.M))
        assertEquals(DistanceEntry.Valid(km(1_000_000)), parseDistance("1000", DistanceUnit.KM))
        assertEquals(DistanceEntry.OutOfRange, parseDistance("1000.001", DistanceUnit.KM))
        assertEquals(DistanceEntry.Valid(mi(1_000_000)), parseDistance("1000", DistanceUnit.MI))
        assertEquals(DistanceEntry.OutOfRange, parseDistance("9999999999", DistanceUnit.MI))
    }

    @Test fun durationsAreWholeSecondsFromOneSecondToADayWithMissingNeverZero() {
        assertEquals(DurationEntry.Valid(75), parseDuration("1", "15"))
        assertEquals(DurationEntry.Valid(45), parseDuration("", "45"))
        assertEquals(DurationEntry.Valid(1200), parseDuration("20", ""))
        assertEquals(DurationEntry.Valid(MAX_EFFORT_SECONDS), parseDuration("1440", "0"))
        assertEquals(DurationEntry.OutOfRange, parseDuration("1440", "1"))
        assertEquals(DurationEntry.OutOfRange, parseDuration("0", "0"))
        assertEquals(DurationEntry.OutOfRange, parseDuration("9999999", ""))
        assertEquals(DurationEntry.BadSeconds, parseDuration("1", "60"))
        assertEquals(DurationEntry.Blank, parseDuration(" ", ""))
        listOf("1.5" to "", "" to "4,5", "-1" to "", "" to "1e1").forEach { (min, sec) ->
            assertEquals("$min:$sec", DurationEntry.NotANumber, parseDuration(min, sec))
        }
    }

    @Test fun measuredTimeRoundsDownToWholeSeconds() {
        assertEquals(29L, wholeSeconds(29_999))
        assertEquals(30L, wholeSeconds(30_000))
        assertEquals("Under a second is never a second", 0L, wholeSeconds(999))
        assertEquals(0L, wholeSeconds(-5))
    }

    @Test fun timedAndDistanceSetsCountOnlyEffortsReachingTheMinimumExactly() {
        val hold = entry(plank, sets = 2, goal = MeasuredGoal(effortSeconds = 30), efforts = listOf(
            effort("a", seconds = 29), effort("b", seconds = 30), effort("c", seconds = 95)))
        assertEquals(listOf(false, true, true), hold.efforts.map(hold::qualifies))
        assertEquals(2, hold.qualifyingCount())
        assertTrue(hold.goalReached())
        assertFalse("One second short is short", hold.copy(efforts = hold.efforts.take(2)).goalReached())
        assertFalse("No goal is never reached", hold.copy(targetSets = null, effortSeconds = null).goalReached())
        // A mile is exactly 1,609.344 m: equal reaches the minimum, a thousandth of a metre less doesn't.
        val carries = entry(carry, sets = 1, goal = MeasuredGoal(effortDistance = mi(1000)))
        assertTrue(carries.qualifies(effort("x", distance = m(1_609_344))))
        assertFalse(carries.qualifies(effort("y", distance = m(1_609_343))))
        assertTrue(carries.qualifies(effort("z", distance = km(1610))))
        assertFalse("A carry without its distance never counts", carries.qualifies(effort("t", seconds = 300)))
        assertEquals(3, entry(carry, efforts = List(3) { effort("e$it", distance = m(1000)) }).performedCount())
    }

    @Test fun cardioTotalsAddExactlyAcrossUnitsAndMissingMeasurementsAddNothing() {
        // 3 km + 1.243 mi = 5,000.414592 m, which reaches 5 km; 1.242 mi falls 1.194752 m short.
        val goal = MeasuredGoal(totalDistance = km(5000))
        val reached = entry(treadmill, goal = goal, efforts = listOf(effort("a", distance = km(3000)),
            effort("b", seconds = 600, distance = mi(1243))))
        assertEquals(5_000_414_592L, reached.loggedMicrometres())
        assertTrue(reached.goalReached())
        val short = reached.copy(efforts = listOf(reached.efforts[0], effort("b", seconds = 600, distance = mi(1242))))
        assertEquals(4_998_805_248L, short.loggedMicrometres())
        assertFalse(short.goalReached())
        // Time only counts recorded durations: an effort with just a distance is unknown time, never zero.
        val both = entry(treadmill, goal = MeasuredGoal(totalSeconds = 1200, totalDistance = km(5000)), efforts =
            listOf(effort("a", seconds = 600, distance = km(2500)), effort("b", distance = km(2500))))
        assertEquals(600L, both.loggedSeconds())
        assertEquals(5_000_000_000L, both.loggedMicrometres())
        assertFalse("Both totals are needed", both.goalReached())
        assertTrue(both.copy(efforts = both.efforts + effort("c", seconds = 600)).goalReached())
        assertTrue(entry(treadmill, goal = MeasuredGoal(totalSeconds = 1200),
            efforts = listOf(effort("a", seconds = 1200))).goalReached())
        assertFalse("Cardio without a total has no goal", entry(treadmill, efforts = listOf(effort("a", seconds = 9))).goalReached())
        assertFalse(entry(treadmill, efforts = listOf(effort("a", seconds = 9))).hasGoal())
    }

    @Test fun largestStoredDistancesAddWithoutOverflowAndImpossibleOnesThrowRatherThanWrap() {
        val longest = mi(DistanceUnit.MI.maxThousandths)
        assertEquals(1_609_344_000_000L, longest.micrometres)
        val many = entry(treadmill, efforts = List(MAX_EFFORTS) { effort("e$it", seconds = MAX_EFFORT_SECONDS, distance = longest) })
        assertEquals(1_609_344_000_000L * MAX_EFFORTS, many.loggedMicrometres())
        assertEquals(MAX_EFFORT_SECONDS.toLong() * MAX_EFFORTS, many.loggedSeconds())
        assertTrue(longest.inRange())
        assertFalse(mi(DistanceUnit.MI.maxThousandths + 1).inRange())
        assertFalse(m(0).inRange())
        assertThrows(ArithmeticException::class.java) { Distance(Long.MAX_VALUE / 2, DistanceUnit.MI).micrometres }
    }

    private fun watch(
        elapsedMs: Long = 4_000,
        runningSince: Long? = 10_000,
        boot: Int? = 3,
        session: String? = "process-a",
        stopped: Boolean = false,
        needsConfirmation: Boolean = false
    ) = Stopwatch("w", "workout", "entry", elapsedMs, runningSince, boot, session, stopped, needsConfirmation, 500, 0)

    @Test fun stopwatchTrustsOnlyAKnownMatchingBootOrTheSameProcess() {
        assertEquals("Same process", StopwatchTime.Measured(29_500L),
            watch().timeAt(Monotonic(3, "process-a", 35_500)))
        assertEquals("Same boot after the app restarted", StopwatchTime.Measured(29_500L),
            watch().timeAt(Monotonic(3, "process-b", 35_500)))
        assertEquals("Same process even if the boot can't be read", StopwatchTime.Measured(29_500L),
            watch(boot = null).timeAt(Monotonic(null, "process-a", 35_500)))
        assertEquals("Two unknown boots never match", StopwatchTime.Untrusted,
            watch(boot = null).timeAt(Monotonic(null, "process-b", 35_500)))
        assertEquals("The phone restarted", StopwatchTime.Untrusted, watch().timeAt(Monotonic(4, "process-b", 35_500)))
        assertEquals("A reading from before the start in another process", StopwatchTime.Untrusted,
            watch().timeAt(Monotonic(3, "process-b", 9_000)))
        assertEquals("A late reading in this process", StopwatchTime.Stale,
            watch().timeAt(Monotonic(3, "process-a", 9_000)))
        assertEquals("Up to the limit", StopwatchTime.Measured(MAX_STOPWATCH_MS),
            watch().timeAt(Monotonic(3, "process-a", 10_000 + MAX_STOPWATCH_MS - 4_000)))
        assertEquals("Impossibly long", StopwatchTime.Untrusted,
            watch().timeAt(Monotonic(3, "process-a", 10_000 + MAX_STOPWATCH_MS)))
        assertEquals("Paused time is never added", StopwatchTime.Measured(4_000L),
            watch(runningSince = null, boot = null, session = null).timeAt(Monotonic(9, "other", 99_999_999)))
        assertEquals(StopwatchTime.Untrusted, watch(runningSince = null, boot = null, session = null, stopped = true,
            needsConfirmation = true).timeAt(Monotonic(3, "process-a", 35_500)))
    }

    @Test fun stopwatchStepsCountEveryChangeAndStartAgainAtTheLimit() {
        assertEquals(1, watch().nextStep())
        assertEquals(MAX_STOPWATCH_STEP, watch().copy(step = MAX_STOPWATCH_STEP - 1).nextStep())
        assertEquals("No overflow, ever", 0, watch().copy(step = MAX_STOPWATCH_STEP).nextStep())
    }

    @Test fun effortSuggestionsOfferTodayThenLastTimeInTheSameUnitOnly() {
        val today = entry(carry, efforts = listOf(Effort("t1", null, m(40_000), 24.0, LoadUnit.KG, null, 1)))
        val previous = entry(carry, efforts = listOf(Effort("p1", null, m(30_000), 20.0, LoadUnit.KG, null, 1),
            Effort("p2", null, m(35_000), 22.0, LoadUnit.KG, null, 2)))
        assertEquals(today.efforts[0] to PrefillSource.THIS_WORKOUT, nextEffortSuggestion(today, previous, LoadUnit.KG))
        assertEquals("Same position last time", previous.efforts[0] to PrefillSource.LAST_TIME,
            nextEffortSuggestion(today.copy(efforts = emptyList()), previous, LoadUnit.KG))
        assertEquals("Else last time's last", previous.efforts[1] to PrefillSource.LAST_TIME,
            nextEffortSuggestion(entry(carry, efforts = List(5) { effort("x$it", distance = m(1)) }.map {
                it.copy(loadUnit = LoadUnit.PLATES, plates = 3) }), previous, LoadUnit.KG))
        assertNull("Never another unit's load", nextEffortSuggestion(today.copy(efforts = emptyList()), previous,
            LoadUnit.PLATES))
    }
}
