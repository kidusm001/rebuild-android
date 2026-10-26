package com.rebuild.ui

import com.rebuild.domain.*
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MeasuredPresentationTest {
    private val locale = Locale.getDefault()

    @Before fun fixLocale() = Locale.setDefault(Locale.US)

    @After fun restoreLocale() = Locale.setDefault(locale)

    private fun library(id: String) = ExerciseCatalog.exercises.single { it.id == id }
    private val plank = library("plank")
    private val weightedPlank = library("plank-weighted")
    private val carry = library("farmers-carry")
    private val treadmill = library("treadmill")
    private val bench = library("incline-db")
    /** A plates-capable hold: an externally loaded cable exercise, timed. */
    private val pallof = Exercise("pallof", "Pallof hold", equipment = Equipment.CABLE, measure = Measure.TIMED, custom = true)

    private fun m(thousandths: Long) = Distance(thousandths, DistanceUnit.M)
    private fun km(thousandths: Long) = Distance(thousandths, DistanceUnit.KM)
    private fun mi(thousandths: Long) = Distance(thousandths, DistanceUnit.MI)

    private fun effort(id: String, seconds: Int? = null, distance: Distance? = null, kg: Double? = null,
        plates: Int? = null, at: Long = 1) =
        Effort(id, seconds, distance, kg, if (plates != null) LoadUnit.PLATES else LoadUnit.KG, plates, at)

    private fun entry(exercise: Exercise, vararg efforts: Effort, sets: Int? = null, goal: MeasuredGoal = MeasuredGoal(),
        id: String = exercise.id) =
        WorkoutExercise(id, null, exercise, sets, null, null, null, null, null, 90, optional = true,
            efforts = efforts.toList(), effortSeconds = goal.effortSeconds, effortDistance = goal.effortDistance,
            totalSeconds = goal.totalSeconds, totalDistance = goal.totalDistance)

    private fun workout(id: String, startedAt: Long, vararg exercises: WorkoutExercise, finished: Boolean = true) =
        Workout(id, null, "Extra", Phase.BASELINE, startedAt, if (finished) startedAt + 1 else null,
            exercises = exercises.toList())

    @Test fun timesAndDistancesShowExactlyWhatWasEntered() {
        assertEquals(listOf("0:45", "12:05", "1:02:03", "24:00:00", "0:00"),
            listOf(45, 725, 3723, 86_400, -5).map { formatDuration(it) })
        assertEquals(listOf("1 hour 5 minutes", "45 seconds", "0 seconds", "1 hour 1 second", "1 minute 30 seconds"),
            listOf(3900, 45, 0, 3601, 90).map { spokenDuration(it) })
        assertEquals(listOf("2.4", "40", "0.001", "1000", "1.05", "12.345"),
            listOf(2400L, 40_000L, 1L, 1_000_000L, 1050L, 12_345L).map(::thousandthsText))
        assertEquals("2.4 km", formatDistance(km(2400)))
        assertEquals("1.243 mi", formatDistance(mi(1243)))
        assertEquals("40 m", formatDistance(m(40_000)))
        assertEquals("1 mile", spokenDistance(mi(1000)))
        assertEquals("2.4 kilometres", spokenDistance(km(2400)))
        assertEquals("What was entered is what comes back to correct it", "1.243", distanceInput(mi(1243)))
        assertEquals("Never shown as more than was covered", "4.999 km",
            formatMicrometres(m(4_999_900).micrometres, DistanceUnit.KM))
        assertEquals("0 km", formatMicrometres(0, DistanceUnit.KM))
        Locale.setDefault(Locale.GERMANY)
        assertEquals("The phone's own decimal separator", "2,4 km", formatDistance(km(2400)))
        assertEquals("2,4", distanceInput(km(2400)))
    }

    @Test fun effortLinesListOnlyTheMeasurementsTaken() {
        assertEquals("0:45", effortText(effort("a", 45), plank))
        assertEquals("0:45 · 10 kg", effortText(effort("a", 45, kg = 10.0), weightedPlank))
        assertEquals("A carry leads with its distance", "40 m · 0:52 · 24 kg/DB",
            effortText(effort("a", 52, m(40_000), 24.0), carry))
        assertEquals("40 m · 24 kg/DB", effortText(effort("a", distance = m(40_000), kg = 24.0), carry))
        assertEquals("25:00 · 5 km", effortText(effort("a", 1500, km(5000)), treadmill))
        assertEquals("5 km", effortText(effort("a", distance = km(5000)), treadmill))
        assertEquals("25:00", effortText(effort("a", 1500), treadmill))
        assertEquals("0:30 · 4 plates/stack", effortText(effort("a", 30, plates = 4), pallof))
        assertEquals("40 metres, 52 seconds, 24 kg/DB", spokenEffort(effort("a", 52, m(40_000), 24.0), carry))
        assertEquals("25 minutes, 5 kilometres", spokenEffort(effort("a", 1500, km(5000)), treadmill))
    }

    @Test fun goalsAndTargetsReadAsEntered() {
        assertEquals("3 timed sets of at least 0:30", measuredGoalText(Measure.TIMED, 3, MeasuredGoal(effortSeconds = 30)))
        assertEquals("2 distance sets of at least 40 m",
            measuredGoalText(Measure.DISTANCE, 2, MeasuredGoal(effortDistance = m(40_000))))
        assertEquals("at least 20:00 and 5 km in total",
            measuredGoalText(Measure.CARDIO, null, MeasuredGoal(totalSeconds = 1200, totalDistance = km(5000))))
        assertEquals("1 timed set of at least 1 minute 30 seconds",
            measuredGoalText(Measure.TIMED, 1, MeasuredGoal(effortSeconds = 90), spoken = true))
        assertNull(measuredGoalText(Measure.TIMED, null, MeasuredGoal()))
        assertNull(measuredGoalText(Measure.CARDIO, null, MeasuredGoal()))
        assertNull(measuredGoalText(Measure.REPS, 3, MeasuredGoal()))

        assertEquals("At least 20:00 in total · rest 1:30 if you start it",
            targetsText(Measure.CARDIO, null, null, null, false, 90, MeasuredGoal(totalSeconds = 1200)))
        assertEquals("3 timed sets of at least 0:30 · rest 1:00",
            targetsText(Measure.TIMED, 3, null, null, false, 60, MeasuredGoal(effortSeconds = 30)))
        assertEquals("No goal · rest 1:30", targetsText(Measure.TIMED, null, null, null, false, 90))
        assertEquals("Rep targets read as before", "3 sets × 8–12 per leg · rest 1:30", targetsText(3, 8, 12, true, 90))

        assertEquals("3 × 0:30", shortTarget(Measure.TIMED, 3, null, null, false, MeasuredGoal(effortSeconds = 30)))
        assertEquals("2 × 40 m", shortTarget(Measure.DISTANCE, 2, null, null, false, MeasuredGoal(effortDistance = m(40_000))))
        assertEquals("20:00 · 5 km",
            shortTarget(Measure.CARDIO, null, null, null, false, MeasuredGoal(totalSeconds = 1200, totalDistance = km(5000))))
        assertNull(shortTarget(Measure.TIMED, null, null, null, false, MeasuredGoal()))
        assertEquals("3 × 8–12", shortTarget(Measure.REPS, 3, 8, 12, false, MeasuredGoal()))
        assertEquals("2 distance sets of at least 40 metres",
            spokenTarget(Measure.DISTANCE, 2, null, null, false, MeasuredGoal(effortDistance = m(40_000))))
    }

    @Test fun countsIncludeOnlyEffortsThatReachedTheMinimum() {
        val holds = entry(plank, effort("a", 45), effort("b", 20), effort("c", 30), effort("d", 31), sets = 2,
            goal = MeasuredGoal(effortSeconds = 30))
        assertEquals("Reached 0:30: 3 of 2 · 1 under, not counted", effortCountText(holds))
        assertEquals(listOf("reaches 0:30", "under 0:30, not counted", "reaches 0:30", "reaches 0:30 · extra"),
            holds.efforts.indices.map { effortNote(holds, it) })
        val short = entry(plank, effort("a", 45), effort("b", 29), sets = 2, goal = MeasuredGoal(effortSeconds = 30))
        assertEquals("Reached 0:30: 1 of 2 · 1 under, not counted", effortCountText(short))

        val noGoal = entry(plank, effort("a", 45))
        assertEquals("Timed sets: 1", effortCountText(noGoal))
        assertNull(effortNote(noGoal, 0))

        // 0.025 mi is 40.2336 m: compared exactly, without changing how either was entered.
        val carries = entry(carry, effort("a", distance = m(40_000), kg = 24.0), effort("b", distance = m(39_999), kg = 24.0),
            effort("c", distance = mi(25), kg = 24.0), sets = 2, goal = MeasuredGoal(effortDistance = m(40_000)))
        assertEquals("Reached 40 m: 2 of 2 · 1 under, not counted", effortCountText(carries))
        assertEquals("under 40 m, not counted", effortNote(carries, 1))

        val rows = entry(treadmill, effort("a", 300, km(1004)), effort("b", 300, km(1000)),
            goal = MeasuredGoal(totalSeconds = 600, totalDistance = km(2000)))
        assertEquals("Logged: 10:00 of 10:00 · 2.004 km of 2 km · 2 efforts", effortCountText(rows))
        assertNull("Cardio has no per-effort minimum", effortNote(rows, 0))
        assertEquals("Logged: 0:00 of 10:00 · 0 km of 2 km · 0 efforts",
            effortCountText(entry(treadmill, goal = MeasuredGoal(totalSeconds = 600, totalDistance = km(2000)))))
        assertEquals("Logged: nothing yet · 0 efforts", effortCountText(entry(treadmill)))
        assertEquals("Without a goal each unit keeps its own total", "Logged: 800 m + 2 km · 2 efforts",
            effortCountText(entry(treadmill, effort("a", distance = m(800_000)), effort("b", distance = km(2000)))))
        assertEquals("Against a goal the exact total is rounded down, never up", "Logged: 1.999 km of 2 km · 2 efforts",
            effortCountText(entry(treadmill, effort("a", distance = m(999_999)), effort("b", distance = km(1000)),
                goal = MeasuredGoal(totalDistance = km(2000)))))
    }

    @Test fun labelsAndStopwatchStatesNameWhatTheyAre() {
        assertEquals(listOf("Timed set 2", "Distance set 1", "Effort 3"),
            listOf(Measure.TIMED to 2, Measure.DISTANCE to 1, Measure.CARDIO to 3).map { effortLabel(it.first, it.second) })
        assertEquals(listOf("timed set", "distance set", "effort"),
            listOf(Measure.TIMED, Measure.DISTANCE, Measure.CARDIO).map(::effortNoun))
        assertEquals(listOf("Extra timed set", "Extra distance set", "Another effort"),
            listOf(Measure.TIMED, Measure.DISTANCE, Measure.CARDIO).map(::extraEffortLabel))
        assertEquals(listOf("kg", "kg/DB", "Plates"), listOf(effortLoadLabel(weightedPlank, LoadUnit.KG),
            effortLoadLabel(carry, LoadUnit.KG), effortLoadLabel(pallof, LoadUnit.PLATES)))
        val watch = Stopwatch("sw", "w", "e", 30_500, null, null, null, false, false, 1, 0)
        assertEquals("paused at 0:30", stopwatchStatus(watch))
        assertEquals("timing", stopwatchStatus(watch.copy(runningSince = 5, session = "p")))
        assertEquals("A partial second is never rounded up", "stopped at 0:46, not logged",
            stopwatchStatus(watch.copy(elapsedMs = 46_999, stopped = true)))
        assertEquals("time to enter", stopwatchStatus(watch.copy(stopped = true, needsConfirmation = true)))
    }

    @Test fun workoutSummariesCountEachKindOfWorkSeparately() {
        val sets = WorkoutExercise("b", null, bench, null, null, null, null, null, null, 90, optional = true, sets = listOf(
            LoggedSet("w1", 10.0, 10, null, true, 1), LoggedSet("s1", 20.0, 10, 2.0, false, 2),
            LoggedSet("s2", 20.0, 9, 1.0, false, 3)))
        val mixed = workout("w", 0, sets, entry(plank, effort("a", 45), effort("b", 40)),
            entry(carry, effort("c", distance = m(40_000), kg = 24.0)), entry(treadmill, effort("d", 1500)))
        assertEquals("2 working sets, 1 warm-up, 2 timed sets, 1 distance set, 1 cardio effort", workoutLoggedText(mixed))
        assertEquals(listOf("2 timed sets", "1 distance set", "1 cardio effort"), effortCounts(mixed))
        assertEquals("nothing logged yet", workoutLoggedText(workout("w", 0, entry(plank), entry(treadmill))))
        assertEquals("1 cardio effort", workoutLoggedText(workout("w", 0, entry(plank), entry(treadmill, effort("d", 60)))))
        assertEquals("A rep-only workout reads as before", "2 working sets, 1 warm-up", workoutLoggedText(workout("w", 0, sets)))
        assertEquals("0 working sets", workoutLoggedText(workout("w", 0, sets.copy(sets = emptyList()))))
    }

    @Test fun historyChartsKeepEachMeasurementAndUnitApart() {
        val workouts = listOf(
            workout("w2", 2_000, entry(treadmill, effort("b", 1200, mi(3000)), effort("c", distance = km(1000)))),
            workout("w1", 1_000, entry(treadmill, effort("a", 1500, km(5000)))),
            workout("w3", 3_000, entry(treadmill, effort("d", 600))),
            workout("w4", 4_000, entry(treadmill)),
            workout("w5", 5_000, entry(treadmill, effort("e", 900)), finished = false)
        )
        val sessions = effortSessions(workouts, treadmill.identity(), null)
        assertEquals("Finished sessions with efforts, oldest first", listOf("w1", "w2", "w3"), sessions.map { it.workout.id })
        val charts = effortCharts(treadmill, LoadUnit.KG, sessions)
        assertEquals("No pace, total, or kilometres converted to miles", listOf("time", "distance-KM", "distance-MI"),
            charts.map { it.key })
        assertEquals(listOf("Time per effort", "Distance per effort · km", "Distance per effort · mi"), charts.map { it.title })
        val (time, kilometres, miles) = charts
        assertEquals(EffortSeries(listOf(listOf(1500.0), listOf(1200.0), listOf(600.0)), listOf(1_000L, 2_000L, 3_000L)), time.series)
        assertEquals("A session without a distance is left out, never zero",
            EffortSeries(listOf(listOf(5000.0), listOf(1000.0)), listOf(1_000L, 2_000L)), kilometres.series)
        assertEquals(EffortSeries(listOf(listOf(3000.0)), listOf(2_000L)), miles.series)
        assertEquals(listOf("25:00", "5 km", "3 mi"),
            listOf(time.format(1500.0), kilometres.format(5000.0), miles.format(3000.0)))

        val holds = listOf(workout("h", 1, entry(weightedPlank, effort("a", 45, kg = 10.0), effort("b", 40, kg = 12.5))))
        val holdCharts = effortCharts(weightedPlank, LoadUnit.KG, effortSessions(holds, weightedPlank.identity(), LoadUnit.KG))
        assertEquals(listOf("time", "load"), holdCharts.map { it.key })
        assertEquals("Weight · kg", holdCharts[1].title)
        assertEquals(listOf(listOf(10.0, 12.5)), holdCharts[1].series.sessions)
        val bodyweight = listOf(workout("p", 1, entry(plank, effort("a", 45))))
        assertEquals(listOf("time"), effortCharts(plank, LoadUnit.KG,
            effortSessions(bodyweight, plank.identity(), LoadUnit.KG)).map { it.key })
        val carries = listOf(workout("c", 1, entry(carry, effort("a", 52, m(40_000), 24.0))))
        val carryCharts = effortCharts(carry, LoadUnit.KG, effortSessions(carries, carry.identity(), LoadUnit.KG))
        assertEquals(listOf("distance-M", "load"), carryCharts.map { it.key })
        assertEquals(listOf("Distance per set · m", "Weight · kg per dumbbell"), carryCharts.map { it.title })

        val mixedUnits = listOf(workout("k", 1, entry(pallof, effort("a", 30, kg = 15.0))),
            workout("p", 2, entry(pallof, effort("b", 30, plates = 4), effort("c", 25, plates = 5))))
        assertEquals(listOf("k"), effortSessions(mixedUnits, pallof.identity(), LoadUnit.KG).map { it.workout.id })
        val plateSessions = effortSessions(mixedUnits, pallof.identity(), LoadUnit.PLATES)
        assertEquals(listOf("p"), plateSessions.map { it.workout.id })
        val plateLoad = effortCharts(pallof, LoadUnit.PLATES, plateSessions).single { it.key == "load" }
        assertEquals("Load · plates per stack", plateLoad.title)
        assertEquals(listOf(listOf(4.0, 5.0)), plateLoad.series.sessions)
    }

    @Test fun freshEffortFieldsNeverOfferATimeOrADistance() {
        fun form(we: WorkoutExercise, offered: Effort?) =
            freshEffortForm(we, offered?.let { it to PrefillSource.LAST_TIME }).let {
                assertEquals(NOT_TYPED, it.typedFor)
                assertTrue(it.effortId.isNotBlank())
                listOf(it.minutes, it.seconds, it.distance, it.unit.name, it.load)
            }
        assertEquals(listOf("", "", "", "M", ""), form(entry(plank), effort("a", 45)))
        assertEquals(listOf("", "", "", "M", "10"), form(entry(weightedPlank), effort("a", 45, kg = 10.0)))
        assertEquals("A carry is offered its load and unit, never a distance", listOf("", "", "", "M", "24"),
            form(entry(carry), effort("a", 52, m(40_000), 24.0)))
        assertEquals("The unit it was last carried in", listOf("", "", "", "KM", "24"),
            form(entry(carry), effort("a", 52, km(1500), 24.0)))
        assertEquals("Its goal's unit, still with no distance", listOf("", "", "", "MI", ""),
            form(entry(carry, goal = MeasuredGoal(effortDistance = mi(50))), null))
        assertEquals(listOf("", "", "", "M", ""), form(entry(carry), null))
        assertEquals("Cardio keeps the last unit but never a distance", listOf("", "", "", "KM", ""),
            form(entry(treadmill), effort("a", 1500, km(5000))))
        assertEquals("A goal's unit when nothing was logged", listOf("", "", "", "MI", ""),
            form(entry(treadmill, goal = MeasuredGoal(totalDistance = mi(3000))), null))
        assertEquals(listOf("", "", "", "KM", ""), form(entry(treadmill), null))
        assertEquals(listOf("", "", "", "M", "4"), form(entry(pallof), effort("a", 30, plates = 4)))

        // Intervals: a time-only effort after one in miles keeps miles, never switching back to the default unit.
        val timeOnly = effort("b", 120)
        val intervals = entry(treadmill, effort("a", distance = mi(1500)), timeOnly)
        assertEquals("The unit of today's last distance", listOf("", "", "", "MI", ""),
            freshEffortForm(intervals, timeOnly to PrefillSource.THIS_WORKOUT)
                .let { listOf(it.minutes, it.seconds, it.distance, it.unit.name, it.load) })
    }

    @Test fun aReviewOffersOnlyATimeThatCanBeLogged() {
        val watch = Stopwatch("sw", "w", "e", 45_999, null, null, null, true, false, 1, 3)
        assertEquals("Whole seconds, rounded down", 45L, reviewSeconds(watch))
        assertEquals(86_400L, reviewSeconds(watch.copy(elapsedMs = 86_400_999)))
        assertNull("Past what one effort can record", reviewSeconds(watch.copy(elapsedMs = 86_401_000)))
        assertNull("Its time couldn't be measured", reviewSeconds(watch.copy(needsConfirmation = true)))
        assertEquals("Stopped at 0:45. Not saved until you log it.",
            reviewNoticeText(watch, 45, typedOver = false))
        assertEquals("The stopwatch measured 0:45. Your typed time is logged instead.",
            reviewNoticeText(watch, 45, typedOver = true))
        val over = watch.copy(elapsedMs = 86_405_000)
        assertEquals("The stopwatch ran 24:00:05, longer than the 24 hours one effort can record. Enter the time to " +
            "log, or discard it.", reviewNoticeText(over, reviewSeconds(over), typedOver = false))
    }

    @Test fun effortFieldsAreCheckedInTheOrderShown() {
        fun check(exercise: Exercise, minutes: String, seconds: String, distance: String = "",
            unit: DistanceUnit = DistanceUnit.M, load: String = "", loadUnit: LoadUnit = LoadUnit.KG) =
            validateEffort(exercise, loadUnit, minutes, seconds, distance, unit, load)
        fun invalid(field: EffortField, message: String) = EffortValidation.Invalid(field, message)
        val duration = EffortField.DURATION
        assertEquals(invalid(duration, "Enter the time: minutes, seconds or both."), check(plank, "", ""))
        assertEquals(EffortValidation.Valid(45, null, null, null), check(plank, "", "45"))
        assertEquals(EffortValidation.Valid(120, null, null, null), check(plank, "2", ""))
        assertEquals(invalid(duration, "Seconds go from 0 to 59."), check(plank, "1", "60"))
        assertEquals(invalid(duration, "Enter a time from 0:01 up to 24 hours."), check(plank, "0", "0"))
        assertEquals(invalid(duration, "Enter a time from 0:01 up to 24 hours."), check(plank, "1440", "1"))
        assertEquals(EffortValidation.Valid(86_400, null, null, null), check(plank, "1440", "0"))
        assertEquals(invalid(duration, "Enter the time in whole minutes and seconds."), check(plank, "1.5", ""))
        assertEquals(invalid(EffortField.LOAD, "Enter the weight in kg."), check(weightedPlank, "0", "45"))
        assertEquals(EffortValidation.Valid(45, null, 10.0, null), check(weightedPlank, "0", "45", load = "10"))

        assertEquals(invalid(EffortField.DISTANCE, "Enter the distance."), check(carry, "", "", load = "24"))
        assertEquals(invalid(EffortField.LOAD, "Enter the weight in kg per dumbbell."), check(carry, "", "", "40"))
        assertEquals("A carry's time is optional", EffortValidation.Valid(null, m(40_000), 24.0, null),
            check(carry, "", "", "40", load = "24"))
        assertEquals(EffortValidation.Valid(52, m(40_500), 24.0, null), check(carry, "0", "52", "40.5", load = "24"))
        assertEquals(invalid(EffortField.DISTANCE, "Use at most three decimals."), check(carry, "0", "52", "40.0005"))
        assertEquals(invalid(EffortField.LOAD, "Weight isn't a number."), check(carry, "", "", "40", load = "heavy"))
        assertEquals("Dumbbells are never plates",
            invalid(EffortField.LOAD, "Enter whole plates per stack, from 1 to 1000, for a cable or stack machine."),
            check(carry, "", "", "40", load = "4", loadUnit = LoadUnit.PLATES))
        assertEquals(EffortValidation.Valid(30, null, null, 4), check(pallof, "", "30", load = "4", loadUnit = LoadUnit.PLATES))

        assertEquals(invalid(duration, "Enter a time, a distance or both."), check(treadmill, "", "", unit = DistanceUnit.KM))
        assertEquals(EffortValidation.Valid(null, km(5000), null, null), check(treadmill, "", "", "5", DistanceUnit.KM))
        assertEquals("Cardio never records a load", EffortValidation.Valid(1500, null, null, null),
            check(treadmill, "25", "", load = "999"))
        assertEquals(invalid(EffortField.DISTANCE, "Enter a distance above 0, up to 1000 km."),
            check(treadmill, "25", "", "1000.001", DistanceUnit.KM))
        assertEquals(invalid(EffortField.DISTANCE, "Enter a distance above 0, up to 1000 mi."),
            check(treadmill, "", "", "0", DistanceUnit.MI))
        Locale.setDefault(Locale.GERMANY)
        assertEquals("A decimal comma is accepted", EffortValidation.Valid(null, km(2400), null, null),
            check(treadmill, "", "", "2,4", DistanceUnit.KM))
    }

    @Test fun goalFieldsNeedWhatEachGoalNeeds() {
        fun text(sets: String = "", minutes: String = "", seconds: String = "", distance: String = "",
            unit: DistanceUnit = DistanceUnit.M, rest: String = "90") = GoalText(sets, minutes, seconds, distance, unit, rest)
        fun error(measure: Measure, text: GoalText, optional: Boolean) = parseMeasuredGoal(measure, text, optional).first
        fun goal(measure: Measure, text: GoalText, optional: Boolean = false) =
            parseMeasuredGoal(measure, text, optional).also { assertNull(it.first) }.second

        assertEquals("Enter how many timed sets and the minimum time for each.", error(Measure.TIMED, text(), false))
        assertEquals("An added exercise may have no goal", ParsedGoal(null, MeasuredGoal(), 90),
            goal(Measure.TIMED, text(), optional = true))
        assertEquals("Enter the minimum time each one needs.", error(Measure.TIMED, text(sets = "3"), false))
        assertEquals("Enter the minimum time each one needs, or leave the goal blank.",
            error(Measure.TIMED, text(sets = "3"), true))
        assertEquals("Enter how many timed sets.", error(Measure.TIMED, text(seconds = "30"), false))
        assertEquals("Enter how many distance sets, or leave the goal blank.",
            error(Measure.DISTANCE, text(distance = "40"), true))
        assertEquals(ParsedGoal(3, MeasuredGoal(effortSeconds = 30), 60), goal(Measure.TIMED, text("3", "0", "30", rest = "60")))
        assertEquals(ParsedGoal(2, MeasuredGoal(effortDistance = m(40_000)), 90), goal(Measure.DISTANCE, text("2", distance = "40")))
        assertEquals("Seconds go from 0 to 59.", error(Measure.TIMED, text("3", "1", "75"), false))

        assertEquals("Enter a total time, a total distance or both.", error(Measure.CARDIO, text(), false))
        assertEquals(ParsedGoal(null, MeasuredGoal(), 90), goal(Measure.CARDIO, text(), optional = true))
        assertEquals(ParsedGoal(null, MeasuredGoal(totalSeconds = 1200, totalDistance = km(5000)), 90),
            goal(Measure.CARDIO, text(minutes = "20", distance = "5", unit = DistanceUnit.KM)))
        assertEquals("Rest must be between 1 and 3600 seconds.", error(Measure.CARDIO, text(minutes = "20", rest = "0"), false))

        val saved = GoalText.of(Measure.CARDIO, null, MeasuredGoal(totalSeconds = 1205, totalDistance = mi(3100)), 90)
        assertEquals(listOf("", "20", "5", "3.1", "MI", "90"),
            listOf(saved.sets, saved.minutes, saved.seconds, saved.distance, saved.unit.name, saved.rest))
        assertEquals("A saved goal reopens to exactly itself",
            ParsedGoal(null, MeasuredGoal(totalSeconds = 1205, totalDistance = mi(3100)), 90), goal(Measure.CARDIO, saved))
        val hold = GoalText.of(Measure.TIMED, 3, MeasuredGoal(effortSeconds = 45), 60)
        assertEquals(ParsedGoal(3, MeasuredGoal(effortSeconds = 45), 60), goal(Measure.TIMED, hold))
        assertEquals("A carry starts in metres", DistanceUnit.M, GoalText.of(Measure.DISTANCE, null, MeasuredGoal(), null).unit)
    }
}
