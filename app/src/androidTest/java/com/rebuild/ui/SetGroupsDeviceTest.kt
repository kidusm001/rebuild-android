package com.rebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rebuild.data.RebuildRepository
import com.rebuild.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drop and rest-pause sets through the real reducer: every stage saved as logged, one working set when finished. */
@RunWith(AndroidJUnit4::class)
class SetGroupsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** The cable row (kg or plates per stack) with room for four working sets. */
    private fun cableRepository(): RebuildRepository = libraryRepository(activeCable = true).also { repo ->
        runBlocking { repo.execute(Action.EditToday(row(repo).id, "row-cable", 4, 180, allowAboveCap = true)) }
    }

    private fun row(repo: AppRepository) = repo.state.value.activeWorkout!!.exercises.first()

    private fun text(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun awaitIdleRunner() {
        rule.waitUntil(5_000) { SemanticsProperties.Disabled !in rule.onNodeWithTag("log-set").fetchSemanticsNode().config }
    }

    /** The type menu, like Log, is disabled for the cooldown after a save; a tap then would be ignored. */
    /** Opens the set-type menu once the button is enabled again (it waits out the save cooldown) and the menu is up. */
    private fun openSetTypeMenu() {
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("set-type") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        rule.onNodeWithTag("set-type").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("set-type-normal").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun chooseType(option: String) {
        openSetTypeMenu()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("set-type-$option").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("set-type-$option").performClick()
        rule.waitForIdle()
    }

    /** Like Log, the short pause is disabled for the post-save cooldown, so an early tap is ignored by design. */
    private fun clickWhenEnabled(tag: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    /**
     * The latest-session card and the session list both show [text]. The list is lazy: its rows below the charts
     * are composed only once scrolled to, so each is found where it is rather than assumed to be composed at once.
     */
    private fun assertLatestAndSessionRowShow(text: String) {
        val latest = hasText("Latest · ", substring = true) and hasText(text)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(latest)
        rule.onNode(latest).assertIsDisplayed()
        val sessionRow = hasText(text) and !hasText("Latest · ", substring = true)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(sessionRow)
        rule.onNode(sessionRow).assertIsDisplayed()
    }

    private fun logEntry(repo: AppRepository, load: String?, reps: String, loadTag: String = "entry-kg") {
        val before = row(repo).sets.size
        awaitIdleRunner()
        load?.let { rule.onNodeWithTag(loadTag).performScrollTo().performTextReplacement(it) }
        rule.onNodeWithTag("entry-reps").performScrollTo().performTextReplacement(reps)
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.waitUntil(5_000) { row(repo).sets.size == before + 1 }
        rule.waitForIdle()
    }

    private fun finishSet(repo: AppRepository) {
        val open = row(repo).openGroup()!!.id
        awaitIdleRunner()
        rule.onNodeWithTag("finish-group").performScrollTo().performClick()
        rule.waitUntil(5_000) { row(repo).setGroups.single { it.id == open }.finishedAt != null }
        rule.waitForIdle()
    }

    private fun switchUnit(unit: LoadUnit) {
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("choose-load-unit") and isEnabled()).fetchSemanticsNodes().size == 1
        }
        rule.onNodeWithTag("choose-load-unit").performScrollTo().performClick()
        rule.onNodeWithTag("unit-${unit.name}").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("unit-${unit.name}").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
    }

    @Test fun dropsJoinTheirSetInKgAndPlatesAndRestPauseRestsOnlyAfterFinish() {
        val repo = cableRepository()
        val ui = MainThreadCollection(repo)
        rule.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()

        // A normal set is unchanged: values, then one Log tap, then the between-set rest.
        rule.onNodeWithTag("log-set").assertTextEquals("Log set")
        logEntry(repo, "40", "10")
        val normalRest = requireNotNull(repo.state.value.restTimer)

        // A drop is part of the set just logged: never a set of its own, so the menu offers no "Drop set".
        openSetTypeMenu()
        rule.onNodeWithTag("set-type-drop").assertDoesNotExist()
        rule.onNodeWithTag("set-type-add-drop").assertTextContains("Add drop to set 1", substring = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("log-set").assertTextEquals("Log drop")
        logEntry(repo, "30", "8")
        logEntry(repo, "20", "6")
        val group = row(repo).setGroups.single()
        assertEquals(SetMethod.DROP, group.method)
        assertNotNull("A drop never leaves a set open", group.finishedAt)
        val dropRest = requireNotNull(repo.state.value.restTimer)
        assertNotEquals("The set got longer, so its rest starts again", normalRest.id, dropRest.id)
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 1 of 4", substring = true)
        rule.onNode(hasTestTag("group-row") and hasContentDescription(
            "Set 1: 40 kg × 10, with 2 drops: 30 kg × 8; 20 kg × 6.", substring = true)).performScrollTo().assertIsDisplayed()

        // Plates per stack: a set and its drop, in plates.
        switchUnit(LoadUnit.PLATES)
        openSetTypeMenu()
        rule.onNodeWithTag("set-type-normal").performClick()
        logEntry(repo, "8", "10", "entry-plates")
        chooseType("add-drop")
        logEntry(repo, "6", "8", "entry-plates")
        val platesGroup = row(repo).setGroups[1]
        val platesStages = row(repo).stages(platesGroup.id)
        assertEquals(listOf(8, 6), platesStages.map { it.plates })
        assertTrue(platesStages.all { it.loadUnit == LoadUnit.PLATES && it.kg == null && !it.warmup })
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 2 of 4", substring = true)

        chooseType("rest_pause")
        logEntry(repo, "6", "10", "entry-plates")
        clickWhenEnabled("short-pause")
        rule.waitUntil(5_000) { repo.state.value.restTimer?.shortPause == true }
        assertEquals(FIXED_NOW + SHORT_PAUSE_SECONDS * 1000L, repo.state.value.restTimer!!.deadline)
        // A mini-set offers this set's load only; its reps are whatever was done.
        rule.onNodeWithTag("log-set").assertTextEquals("Add mini-set")
        assertEquals("6", text("entry-plates"))
        assertEquals("", text("entry-reps"))
        logEntry(repo, null, "4", "entry-plates")
        assertNull("The pause ends with the mini-set; it isn't a rest between sets", repo.state.value.restTimer)
        finishSet(repo)
        assertFalse(requireNotNull(repo.state.value.restTimer).shortPause)

        val we = row(repo)
        assertEquals("two sets with drops and one rest-pause set", 3, we.workingCount())
        assertEquals(3 + 2 + 2, we.sets.size)
        assertEquals(we.sets.size, we.sets.map { it.id }.toSet().size)
        assertEquals(listOf(SetMethod.DROP, SetMethod.DROP, SetMethod.REST_PAUSE), we.setGroups.map { it.method })
        assertTrue(we.setGroups.all { it.finishedAt != null })
        assertEquals(LoadUnit.KG, we.sets.first().loadUnit)
        assertEquals(40.0, we.sets.first().kg)
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: 3 of 4", substring = true)
    }

    @Test fun correctingAStageKeepsTheOthersAndStartsNoTimer() {
        val repo = cableRepository()
        val workout = repo.state.value.activeWorkout!!
        val we = row(repo)
        runBlocking {
            listOf(40.0 to 10, 30.0 to 8, 20.0 to 6).forEachIndexed { i, (kg, reps) ->
                repo.execute(Action.LogStage(workout.id, we.id, "drop", SetMethod.DROP, "stage-$i", kg, reps, null,
                    FIXED_NOW + i * 30_000L, LoadUnit.KG, null))
            }
            repo.execute(Action.FinishGroup(workout.id, we.id, "drop", FIXED_NOW + 90_000))
        }
        val before = row(repo).sets
        val timer = requireNotNull(repo.state.value.restTimer)
        val ui = MainThreadCollection(repo)
        rule.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW + 100_000 }) }
        rule.onNodeWithText("Resume workout").performClick()
        rule.onNodeWithTag("group-row").performScrollTo().performClick()
        rule.onAllNodesWithTag("stage-row").assertCountEquals(3)
        rule.onAllNodesWithTag("stage-row")[1].assertContentDescriptionEquals("Drop 1: 30 kg × 8").performClick()
        rule.onNodeWithText("Edit drop 1").assertIsDisplayed()
        rule.onNodeWithTag("edit-kg").performTextReplacement("32.5")
        rule.onNodeWithText("Save", substring = false).performClick()
        rule.waitUntil(5_000) { row(repo).sets.single { it.id == "stage-1" }.kg == 32.5 }
        rule.waitForIdle()
        val after = row(repo).sets
        assertEquals(before.filterNot { it.id == "stage-1" }, after.filterNot { it.id == "stage-1" })
        assertEquals(before.single { it.id == "stage-1" }.copy(kg = 32.5), after.single { it.id == "stage-1" })
        assertEquals("Corrections never start or restart a rest", timer, repo.state.value.restTimer)

        // A missed stage goes after the last one, in that stage's unit, and the set still counts once.
        rule.onNodeWithTag("add-missed-stage").performScrollTo().performClick()
        // The dialog's title; the button behind it says the same and is the clickable one.
        rule.onNode(hasText("Add a missed drop") and !hasClickAction()).assertIsDisplayed()
        rule.onNodeWithTag("edit-kg").performTextReplacement("10")
        rule.onNodeWithTag("edit-reps").performTextReplacement("5")
        rule.onNodeWithText("Save", substring = false).performClick()
        rule.waitUntil(5_000) { row(repo).sets.size == 4 }
        rule.waitForIdle()
        assertEquals(10.0, row(repo).sets.last().kg)
        assertEquals("drop", row(repo).sets.last().groupId)
        assertEquals(1, row(repo).workingCount())
        assertEquals(timer, repo.state.value.restTimer)

        rule.onNodeWithText("Rest-pause").performScrollTo().performClick()
        rule.waitUntil(5_000) { row(repo).setGroups.single().method == SetMethod.REST_PAUSE }
        assertEquals(before.map { it.id } + row(repo).sets.last().id, row(repo).sets.map { it.id })

        rule.onNodeWithText("Delete this set").performScrollTo().performClick()
        rule.onNodeWithText("Delete set").performClick()
        rule.waitUntil(5_000) { row(repo).sets.isEmpty() }
        assertTrue(row(repo).setGroups.isEmpty())
        rule.onNodeWithTag("group-row").assertDoesNotExist()
    }

    @Test fun openSetSurvivesRecreationAndAnotherExerciseOffersGoToOrFinish() {
        val repo = cableRepository()
        val tester = StateRestorationTester(rule)
        val ui = MainThreadCollection(repo)
        tester.setContent { RebuildApp(ui, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("Resume workout").performClick()
        chooseType("rest_pause")
        logEntry(repo, "40", "10")
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextReplacement("30")
        rule.onNodeWithTag("entry-reps").performTextReplacement("8")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("log-set").assertTextEquals("Add mini-set")
        assertEquals("30", text("entry-kg"))
        assertEquals("8", text("entry-reps"))
        assertEquals(1, row(repo).sets.size)
        logEntry(repo, null, "8")
        val stages = row(repo).sets
        assertEquals(2, stages.size)
        assertEquals("A restored draft never reuses a saved stage's id", 2, stages.map { it.id }.toSet().size)
        assertEquals(listOf(40.0, 30.0), stages.map { it.kg })

        rule.onNodeWithTag("exercise-chip-1").performClick()
        rule.onNodeWithTag("open-group-elsewhere").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("log-set").assertDoesNotExist()
        rule.onNodeWithTag("go-to-open-group").performScrollTo().performClick()
        rule.onNodeWithTag("exercise-chip-0").assertIsSelected()
        rule.onNodeWithTag("log-set").assertTextEquals("Add mini-set")

        rule.onNodeWithTag("exercise-chip-1").performClick()
        rule.onNodeWithTag("finish-open-group").performScrollTo().performClick()
        rule.waitUntil(5_000) { row(repo).setGroups.single().finishedAt != null }
        rule.waitForIdle()
        assertEquals(row(repo).id, requireNotNull(repo.state.value.restTimer).exerciseId)
        rule.onNodeWithTag("exercise-chip-1").assertIsSelected()
        rule.onNodeWithTag("log-set").performScrollTo().assertTextEquals("Log set")
        assertEquals(2, row(repo).sets.size)
    }

    @Test fun aSecondTapWhileSavingNeverLogsAStageTwice() {
        val repo = FakeRepository(seedState(Phase.INTRO_ONE))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        chooseType("rest_pause")
        rule.onNodeWithTag("entry-kg").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("entry-reps").performTextInput("8")
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        rule.onNodeWithTag("log-set").performScrollTo().performClick()
        rule.onNodeWithTag("log-set").assertIsNotEnabled().performClick()
        gate.complete(Unit)
        repo.gate = null
        rule.waitForIdle()
        assertEquals(1, repo.actions.filterIsInstance<Action.LogStage>().size)
        assertEquals(1, row(repo).sets.size)
        logEntry(repo, "30", "6")
        val sets = row(repo).sets
        assertEquals(2, sets.size)
        assertEquals(1, row(repo).setGroups.size)
        assertEquals(listOf(40.0, 30.0), sets.map { it.kg })
        assertTrue(repo.actions.none { it is Action.SaveSet })
    }

    @Test fun aSetWithDropsIsListedAsThatSetAndChartedByItsOwnStageOnly() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        fun logGroup(workout: Workout, we: WorkoutExercise, id: String, method: SetMethod, at: Long, vararg stages: Pair<Double, Int>) =
            runBlocking {
                stages.forEachIndexed { i, (kg, reps) ->
                    repo.execute(Action.LogStage(workout.id, we.id, id, method, "$id-$i", kg, reps, null, at + i * 30_000L,
                        LoadUnit.KG, null))
                }
                repo.execute(Action.FinishGroup(workout.id, we.id, id, at + stages.size * 30_000L))
            }
        runBlocking { repo.execute(Action.StartWorkout("upper-a", FIXED_NOW - 3_600_000)) }
        repo.state.value.activeWorkout!!.let { w ->
            logGroup(w, w.exercises.first(), "drop", SetMethod.DROP, FIXED_NOW - 3_500_000, 40.0 to 10, 30.0 to 8, 20.0 to 6)
            logGroup(w, w.exercises.first(), "rp", SetMethod.REST_PAUSE, FIXED_NOW - 3_000_000, 30.0 to 8, 30.0 to 3)
        }
        runBlocking { repo.execute(Action.FinishWorkout(FIXED_NOW - 2_400_000)) }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").performClick()
        // Five stages are two working sets, each one row: the set, then its drops or pauses as part of it.
        rule.onAllNodesWithTag("working-count")[0].assertTextEquals("Working sets: 2 of 2")
        rule.onNode(hasTestTag("group-row") and hasContentDescription(
            "Set 1: 40 kg × 10, with 2 drops: 30 kg × 8; 20 kg × 6.")).assertExists()
        rule.onNode(hasTestTag("group-row") and hasContentDescription(
            "Set 2: 30 kg × 8, with 1 pause: 30 kg × 3.")).assertExists()
        rule.onAllNodesWithTag("set-row").assertCountEquals(0)

        rule.onNodeWithContentDescription("Bench press, ", substring = true).performScrollTo().performClick()
        rule.onNodeWithTag("charts-normal-only").assertTextEquals(
            "Charts plot each set as lifted; its drops are listed with it below.")
        rule.onNodeWithText("No working sets logged yet.").assertDoesNotExist()
        // Each set is charted by what it was (40 × 10, 30 × 8); the drops after it never become points.
        rule.onNode(hasContentDescription("Weight · kg chart: 1 sessions, values from 30 to 40,", substring = true))
            .performScrollTo().assertExists()
        rule.onNode(hasContentDescription("Reps chart: 1 sessions, values from 8 to 10,", substring = true))
            .performScrollTo().assertExists()
        // The latest-session summary and the session list both show every stage, in its unit.
        assertLatestAndSessionRowShow("40 kg × 10, drop 30 kg × 8, drop 20 kg × 6")
        assertLatestAndSessionRowShow("30 kg × 8, pause 30 kg × 3")

        // A later session adds a normal set and another set with a drop: each is plotted once, by its own stage.
        runBlocking { repo.execute(Action.StartWorkout("upper-a", FIXED_NOW - 1_800_000)) }
        repo.state.value.activeWorkout!!.let { w ->
            runBlocking {
                repo.execute(Action.SaveSet(w.id, w.exercises.first().id, "normal", 42.5, 7, null, false, FIXED_NOW - 1_700_000))
            }
            logGroup(w, w.exercises.first(), "drop-2", SetMethod.DROP, FIXED_NOW - 1_600_000, 35.0 to 6, 25.0 to 6)
        }
        runBlocking { repo.execute(Action.FinishWorkout(FIXED_NOW - 600_000)) }
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Weight · kg chart: 2 sessions, values from 30 to 42.5,", substring = true))
            .performScrollTo().assertExists()
        rule.onNode(hasContentDescription("Reps chart: 2 sessions, values from 6 to 10,", substring = true))
            .performScrollTo().assertExists()
        assertLatestAndSessionRowShow("35 kg × 6, drop 25 kg × 6")
    }

    @Test fun finishingTheWorkoutClosesTheOpenSetAndCreditIsAnExplicitChoice() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        chooseType("rest_pause")
        logEntry(repo, "40", "10")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("Finish") and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1
        }
        rule.onNodeWithText("Finish", substring = false).performClick()
        // The dialog takes focus from the reps field, so it settles once the keyboard has closed.
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Finish partial workout").assertIsDisplayed() }.isSuccess }
        rule.onNodeWithTag("finish-open-sets")
            .assertTextEquals("Bench press: the rest-pause in progress is finished with its 1 logged stage.")
        rule.onNodeWithTag("confirm-button").assertTextEquals("Finish set and workout")
        rule.onNodeWithTag("count-for-week").performScrollTo().assertIsOff().performClick()
        rule.onNodeWithTag("confirm-button").performClick()
        rule.waitUntil(5_000) { repo.state.value.activeWorkoutId == null }
        rule.waitForIdle()
        val finish = repo.actions.filterIsInstance<Action.FinishWorkout>().single()
        assertTrue(finish.closeOpenGroups)
        assertTrue(finish.countForWeek)
        val workout = repo.state.value.workouts.single()
        val bench = workout.exercises.first()
        assertEquals(FIXED_NOW, bench.setGroups.single().finishedAt)
        assertEquals(1, bench.sets.size)
        assertEquals(1, workout.workingSetCount())
        assertTrue(workout.countedForWeek)
        // Counted by choice: Upper A is done this week, so Today offers the next unfinished workout.
        rule.onNode(hasText("Done · Tue 22 Sep") and hasAnyAncestor(hasTestTag("week-row-upper-a"))).assertExists()
        rule.onNodeWithTag("start-suggested").performScrollTo().assertTextContains("Start Lower A")
    }

    @Test fun dropsAfterTheTargetNeverCountOrWarnAndTheNextEntryStaysADrop() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithTag("start-scheduled").performClick()
        val target = requireNotNull(row(repo).targetSets)
        repeat(target) { logEntry(repo, "40", "${10 - it}") }
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: $target of $target", substring = true)
        // The target-done panel's own button, the way it's used after the last set.
        rule.onNodeWithText("Add drop to set $target").performScrollTo().performClick()
        rule.waitForIdle()
        logEntry(repo, "30", "6")
        // The next entry is still a drop, with empty fields: a second drop never becomes a new set.
        rule.onNodeWithTag("log-set").assertTextEquals("Log drop")
        assertEquals("", text("entry-kg"))
        assertEquals("", text("entry-reps"))
        logEntry(repo, "20", "5")
        assertEquals(listOf(30.0, 20.0), (row(repo).workEntries().last() as WorkEntry.Group).followUps.map { it.kg })
        assertEquals(target, row(repo).workingCount())
        rule.onNodeWithTag("working-count").assertTextContains("Working sets: $target of $target", substring = true)
        rule.onNode(hasText("Above the advisory upper", substring = true)).assertDoesNotExist()
        rule.onNode(hasText("extra", substring = true) and hasAnyAncestor(hasTestTag("group-row"))).assertDoesNotExist()
        rule.onNode(hasTestTag("group-row") and hasContentDescription("Set $target: 40 kg ×", substring = true)).assertExists()
    }

    @Test fun anOldDropSetIsMadeDropsOfTheSetBeforeItFromHistory() {
        val repo = FakeRepository(seedState(Phase.INTRO_TWO))
        runBlocking {
            repo.execute(Action.StartWorkout("upper-a", FIXED_NOW - 3_600_000))
            val w = repo.state.value.activeWorkout!!
            val we = w.exercises.first()
            repo.execute(Action.SaveSet(w.id, we.id, "s1", 40.0, 10, null, false, FIXED_NOW - 3_500_000))
            repo.execute(Action.SaveSet(w.id, we.id, "s2", 40.0, 9, null, false, FIXED_NOW - 3_400_000))
            // The old "Drop set" type: the drop logged as a third set.
            repo.execute(Action.LogStage(w.id, we.id, "old", SetMethod.DROP, "old-0", 30.0, 8, null, FIXED_NOW - 3_300_000))
            repo.execute(Action.FinishGroup(w.id, we.id, "old", FIXED_NOW - 3_290_000))
            repo.execute(Action.FinishWorkout(FIXED_NOW - 3_000_000))
        }
        rule.setContent { RebuildApp(repo, FakePlatform(), clock = { FIXED_NOW }) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Upper A").performClick()
        rule.onAllNodesWithTag("working-count")[0].assertTextContains("Working sets: 3", substring = true)
        rule.onNode(hasTestTag("group-row") and hasContentDescription("Set 3:", substring = true)).performScrollTo().performClick()
        rule.onNodeWithTag("join-drops").performScrollTo().assertTextEquals("Make them drops of set 2").performClick()
        rule.waitUntil(5_000) { repo.state.value.workouts.single().exercises.first().workingCount() == 2 }
        rule.waitForIdle()
        rule.onAllNodesWithTag("working-count")[0].assertTextContains("Working sets: 2", substring = true)
        rule.onNode(hasTestTag("group-row") and hasContentDescription(
            "Set 2: 40 kg × 9, with 1 drop: 30 kg × 8.", substring = true)).assertExists()
        assertEquals(listOf("s1", "s2", "old-0"), repo.state.value.workouts.single().exercises.first().sets.map { it.id })
    }
}
