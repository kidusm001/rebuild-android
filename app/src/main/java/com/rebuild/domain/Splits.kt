package com.rebuild.domain

/**
 * A weekly training split: which workouts, on which days, with which exercises. Splits prescribe exercises, sets,
 * reps and rest, never a load. Sets per exercise come from the phase (1, 2, 2, then each slot's baseline), so every
 * split ramps the same way.
 */
data class Split(
    val id: String,
    /** The plain name people see: no jargon. */
    val name: String,
    /** Training days a week; the original plan's is its established count (it starts at 4, see [rampsDays]). */
    val days: Int,
    /** One line on what the week does and who it suits. */
    val reason: String,
    /** What it can be done with. The original plan needs a gym. */
    val gear: Set<Gear>,
    val templates: List<WorkoutTemplate>,
    /** Only the original plan: 4 days in the first phases, 5 from Two-set start. */
    val rampsDays: Boolean = false
) {
    /** Slots this gear leaves out (no dumbbell or bodyweight match), across the week. */
    fun droppedFor(with: Gear): Int = templates.sumOf { t -> t.slots.count { it.exerciseFor(with) == null } }

    /** Slots whose exercise differs from the gym one with this gear, across the week. */
    fun swappedFor(with: Gear): Int = if (with == Gear.GYM) 0 else
        templates.sumOf { t -> t.slots.count { it.exerciseFor(with).let { id -> id != null && id != it.exerciseId } } }
}

object Splits {
    /** Training days spread across the week, Monday first, so rest days fall between them where they can. */
    fun weekdays(days: Int): List<Int> = when (days) {
        2 -> listOf(1, 4)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 2, 4, 5)
        5 -> listOf(1, 2, 3, 5, 6)
        6 -> listOf(1, 2, 3, 4, 5, 6)
        else -> throw IllegalArgumentException("A split trains 2 to 6 days a week.")
    }

    private val compoundRest = 180
    private val isolationRest = 90

    /** One exercise position in a day blueprint, before it gets its split-specific id. */
    private class Blueprint(
        val key: String,
        val gym: String,
        val dumbbell: String?,
        val bodyweight: String?,
        val sets: Int,
        val reps: IntRange,
        val muscle: String,
        val isolation: Boolean = false,
        val perLeg: Boolean = false,
        val bodyweightReps: IntRange? = null,
        val alternatives: List<String> = emptyList()
    )

    private fun b(
        key: String, gym: String, dumbbell: String?, bodyweight: String?, sets: Int, reps: IntRange, muscle: String,
        isolation: Boolean = false, perLeg: Boolean = false, bodyweightReps: IntRange? = null,
        alternatives: List<String> = emptyList()
    ) = Blueprint(key, gym, dumbbell, bodyweight, sets, reps, muscle, isolation, perLeg, bodyweightReps, alternatives)

    // Rep ranges go by movement: big compound lifts 6–10, other compounds 8–12, isolation 10–15, shoulder raises
    // 12–20, calves 10–20. Bodyweight sets can't add load, so they get wider ranges of their own.
    private val bigCompound = 6..10
    private val compound = 8..12
    private val isolation = 10..15
    private val raise = 12..20
    private val calves = 10..20

    // Day blueprints, shared by the splits. Machine and dumbbell lifts come first so a new lifter starts on them;
    // barbell versions are listed as alternatives.
    private val fullA = listOf(
        b("legs", "leg-press", "squat-goblet", "squat-bodyweight", 3, 10..15, "Quads", bodyweightReps = 12..20),
        b("press", "bench-db", "bench-db", "pushup", 3, bigCompound, "Chest", bodyweightReps = 6..20,
            alternatives = listOf("chest-machine", "bench-barbell")),
        b("pull", "pulldown", "row-db", "pullup", 3, compound, "Back", bodyweightReps = 3..10,
            alternatives = listOf("pulldown-neutral")),
        b("hamstrings", "curl-seated", "rdl-db", "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("lateral", "lateral-cable", "lateral-db", null, 2, raise, "Side delts", isolation = true),
        b("core", "crunch-cable", "crunch", "crunch", 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val fullB = listOf(
        b("hinge", "rdl-db", "rdl-db", "glute-bridge", 3, compound, "Hamstrings", bodyweightReps = 12..20,
            alternatives = listOf("rdl")),
        b("split-squat", "split-squat", "split-squat", "split-squat-bodyweight", 3, compound, "Quads", perLeg = true,
            bodyweightReps = 10..15),
        b("incline", "incline-db", "incline-db", "pushup", 3, compound, "Chest", bodyweightReps = 6..20,
            alternatives = listOf("incline-machine")),
        b("row", "row-chest-supported", "row-db", "inverted-row", 3, compound, "Back", bodyweightReps = 6..15),
        b("triceps", "pushdown", "triceps-overhead-db", "pushup-close", 2, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15),
        b("biceps", "curl-db", "curl-db", null, 2, isolation, "Biceps", isolation = true),
        b("calves", "calf-standing", "calf-raise-bodyweight", "calf-raise-bodyweight", 2, calves, "Calves",
            isolation = true, bodyweightReps = 15..25)
    )
    private val fullC = listOf(
        b("squat", "squat-hack", "squat-goblet", "squat-bodyweight", 3, bigCompound, "Quads", bodyweightReps = 12..20,
            alternatives = listOf("squat-back")),
        b("shoulder", "shoulder-db", "shoulder-db", "pike-pushup", 3, compound, "Front delts", bodyweightReps = 6..12,
            alternatives = listOf("shoulder-machine")),
        b("row", "row-cable", "row-db", "inverted-row", 3, compound, "Back", bodyweightReps = 6..15),
        b("hamstrings", "curl-lying", "rdl-db", "leg-curl-slider", 2, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-seated")),
        b("rear-delts", "rear-delt-cable", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true),
        b("biceps", "curl-cable", "curl-hammer", "chinup", 2, isolation, "Biceps", isolation = true,
            bodyweightReps = 3..10),
        b("core", "knee-raise", "crunch", "crunch", 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val upperA = listOf(
        b("press", "bench-db", "bench-db", "pushup", 3, bigCompound, "Chest", bodyweightReps = 6..20,
            alternatives = listOf("chest-machine", "bench-barbell")),
        b("row", "row-chest-supported", "row-db", "inverted-row", 3, compound, "Back", bodyweightReps = 6..15),
        b("shoulder", "shoulder-db", "shoulder-db", "pike-pushup", 2, compound, "Front delts", bodyweightReps = 6..12,
            alternatives = listOf("shoulder-machine")),
        b("pulldown", "pulldown", null, "pullup", 2, compound, "Back", bodyweightReps = 3..10),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true),
        b("biceps", "curl-db", "curl-db", null, 2, isolation, "Biceps", isolation = true),
        b("triceps", "pushdown", "triceps-overhead-db", "pushup-close", 2, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15)
    )
    private val lowerA = listOf(
        b("squat", "squat-hack", "squat-goblet", "squat-bodyweight", 3, bigCompound, "Quads", bodyweightReps = 12..20,
            alternatives = listOf("squat-back")),
        b("hinge", "rdl-db", "rdl-db", "glute-bridge", 3, compound, "Hamstrings", bodyweightReps = 12..20,
            alternatives = listOf("rdl")),
        b("extension", "leg-extension", null, null, 2, isolation, "Quads", isolation = true),
        b("hamstrings", "curl-seated", null, "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("calves", "calf-standing", "calf-raise-bodyweight", "calf-raise-bodyweight", 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("core", "crunch-cable", "crunch", "crunch", 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val upperB = listOf(
        b("incline", "incline-db", "incline-db", "pushup", 3, compound, "Chest", bodyweightReps = 6..20,
            alternatives = listOf("incline-machine")),
        b("pulldown", "pulldown-neutral", "row-db", "pullup", 3, compound, "Back", bodyweightReps = 3..10,
            alternatives = listOf("pulldown")),
        b("row", "row-cable", null, "inverted-row", 2, compound, "Back", bodyweightReps = 6..15),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("rear-delts", "rear-delt-cable", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true),
        b("biceps", "curl-cable", "curl-hammer", null, 2, isolation, "Biceps", isolation = true),
        b("triceps", "triceps-overhead", "triceps-overhead-db", "dip", 2, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..12)
    )
    private val lowerB = listOf(
        b("legs", "leg-press", "squat-goblet", "squat-bodyweight", 3, 10..15, "Quads", bodyweightReps = 12..20),
        b("split-squat", "split-squat", "split-squat", "split-squat-bodyweight", 2, compound, "Quads", perLeg = true,
            bodyweightReps = 10..15),
        b("hamstrings", "curl-lying", "rdl-db", "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-seated")),
        b("glutes", "hip-thrust", "glute-bridge", "glute-bridge", 2, compound, "Glutes", bodyweightReps = 12..20),
        b("calves", "calf-seated", "calf-raise-bodyweight", "calf-raise-bodyweight", 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("core", "knee-raise", "crunch", "crunch", 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val push = listOf(
        b("press", "bench-db", "bench-db", "pushup", 3, bigCompound, "Chest", bodyweightReps = 6..20,
            alternatives = listOf("chest-machine", "bench-barbell")),
        b("incline", "incline-db", "incline-db", null, 2, compound, "Chest", alternatives = listOf("incline-machine")),
        b("shoulder", "shoulder-db", "shoulder-db", "pike-pushup", 3, compound, "Front delts", bodyweightReps = 6..12,
            alternatives = listOf("shoulder-machine")),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("triceps", "pushdown", "triceps-overhead-db", "pushup-close", 2, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15)
    )
    private val pull = listOf(
        b("pulldown", "pulldown", null, "pullup", 3, compound, "Back", bodyweightReps = 3..10,
            alternatives = listOf("pulldown-neutral")),
        b("row", "row-chest-supported", "row-db", "inverted-row", 3, compound, "Back", bodyweightReps = 6..15),
        b("rear-delts", "face-pull", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true),
        b("biceps", "curl-db", "curl-db", "chinup", 3, isolation, "Biceps", isolation = true, bodyweightReps = 3..10),
        b("hammer", "curl-hammer", "curl-hammer", null, 2, isolation, "Biceps", isolation = true)
    )
    private val legs = listOf(
        b("squat", "squat-hack", "squat-goblet", "squat-bodyweight", 3, bigCompound, "Quads", bodyweightReps = 12..20,
            alternatives = listOf("squat-back")),
        b("hinge", "rdl-db", "rdl-db", "glute-bridge", 3, compound, "Hamstrings", bodyweightReps = 12..20,
            alternatives = listOf("rdl")),
        b("split-squat", "split-squat", "split-squat", "split-squat-bodyweight", 2, compound, "Quads", perLeg = true,
            bodyweightReps = 10..15),
        b("hamstrings", "curl-seated", null, "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("extension", "leg-extension", null, null, 2, isolation, "Quads", isolation = true),
        b("calves", "calf-standing", "calf-raise-bodyweight", "calf-raise-bodyweight", 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("core", "crunch-cable", "crunch", "crunch", 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )

    private fun build(splitId: String, days: List<Triple<String, String, List<Blueprint>>>): List<WorkoutTemplate> {
        val weekdays = weekdays(days.size)
        return days.mapIndexed { i, (key, name, blueprints) ->
            val templateId = "$splitId-$key"
            WorkoutTemplate(templateId, name, weekdays[i], slots = blueprints.map { bp ->
                PlanSlot(
                    id = "$templateId-${bp.key}",
                    exerciseId = bp.gym,
                    alternatives = bp.alternatives,
                    baselineSets = bp.sets,
                    upperSets = bp.sets + 1,
                    minReps = bp.reps.first,
                    maxReps = bp.reps.last,
                    restSeconds = if (bp.isolation) isolationRest else compoundRest,
                    perLeg = bp.perLeg,
                    muscle = bp.muscle,
                    gear = SlotGear(bp.dumbbell, bp.bodyweight, bp.bodyweightReps?.first, bp.bodyweightReps?.last)
                )
            }, splitId = splitId)
        }
    }

    const val FULL_BODY_2 = "full-body-2"
    const val FULL_BODY_3 = "full-body-3"
    const val UPPER_LOWER = "upper-lower"
    const val PUSH_PULL_LEGS = "push-pull-legs"
    const val PUSH_PULL_LEGS_2 = "push-pull-legs-2"
    const val UPPER_LOWER_PPL = "upper-lower-ppl"

    private val anyGear = setOf(Gear.GYM, Gear.DUMBBELLS, Gear.BODYWEIGHT)

    val all: List<Split> = listOf(
        Split(FULL_BODY_2, "Full body, 2 days", 2,
            "2 full-body days. Every workout trains your whole body, so each muscle works twice a week.",
            anyGear, build(FULL_BODY_2, listOf(Triple("a", "Full body A", fullA), Triple("b", "Full body B", fullB)))),
        Split(FULL_BODY_3, "Full body, 3 days", 3,
            "3 full-body days. Every workout trains your whole body, so you practise each lift often. A good way to start.",
            anyGear, build(FULL_BODY_3, listOf(Triple("a", "Full body A", fullA), Triple("b", "Full body B", fullB),
                Triple("c", "Full body C", fullC)))),
        Split(UPPER_LOWER, "Upper and lower", 4,
            "4 days that alternate upper and lower body. Each muscle gets 2 workouts a week, with rest between them.",
            anyGear, build(UPPER_LOWER, listOf(Triple("upper-a", "Upper A", upperA), Triple("lower-a", "Lower A", lowerA),
                Triple("upper-b", "Upper B", upperB), Triple("lower-b", "Lower B", lowerB)))),
        Split(PUSH_PULL_LEGS, "Push, pull, legs", 3,
            "3 days: pushing muscles, pulling muscles, then legs. Each workout has room for more sets per muscle.",
            anyGear, build(PUSH_PULL_LEGS, listOf(Triple("push", "Push", push), Triple("pull", "Pull", pull),
                Triple("legs", "Legs", legs)))),
        Split(UPPER_LOWER_PPL, "Upper, lower, push, pull, legs", 5,
            "5 days: an upper and a lower day, then push, pull and legs. Each muscle gets 2 workouts a week.",
            anyGear, build(UPPER_LOWER_PPL, listOf(Triple("upper", "Upper", upperA), Triple("lower", "Lower", lowerA),
                Triple("push", "Push", push), Triple("pull", "Pull", pull), Triple("legs", "Legs", legs)))),
        Split(PUSH_PULL_LEGS_2, "Push, pull, legs twice", 6,
            "6 days: push, pull and legs, twice a week. Each muscle gets 2 workouts a week, one rest day.",
            anyGear, build(PUSH_PULL_LEGS_2, listOf(Triple("push-a", "Push A", push), Triple("pull-a", "Pull A", pull),
                Triple("legs-a", "Legs A", legs), Triple("push-b", "Push B", push), Triple("pull-b", "Pull B", pull),
                Triple("legs-b", "Legs B", legs)))),
        Split(ORIGINAL_SPLIT, "Rebuild's gym plan", 5,
            "Upper, lower, push and pull days on machines and dumbbells. 4 days now, 5 when you move to Two-set start.",
            setOf(Gear.GYM), Seed.templates, rampsDays = true)
    )

    fun byId(id: String): Split = all.single { it.id == id }

    /** Splits that work with [with], for choosing one yourself. */
    fun forGear(with: Gear): List<Split> = all.filter { with in it.gear }

    /**
     * The one split the quiz recommends: days narrow it, experience picks within. New and returning lifters keep to
     * full body at 2–3 days and Upper/Lower at 4; at 5 days they get the original plan, which starts on 4 days (it
     * needs a gym; otherwise Upper/Lower/Push/Pull/Legs).
     */
    fun recommend(experience: Experience, days: Int, with: Gear): Split {
        val id = when (days) {
            2 -> FULL_BODY_2
            3 -> if (experience == Experience.EXPERIENCED) PUSH_PULL_LEGS else FULL_BODY_3
            4 -> UPPER_LOWER
            5 -> if ((experience == Experience.NEW || experience == Experience.RETURNING) && with == Gear.GYM)
                ORIGINAL_SPLIT else UPPER_LOWER_PPL
            6 -> PUSH_PULL_LEGS_2
            else -> throw IllegalArgumentException("A split trains 2 to 6 days a week.")
        }
        return byId(id)
    }

    /** The phase a quiz answer starts in. Experience only ever picks the starting phase, never a later one. */
    fun startingPhase(experience: Experience): Phase = when (experience) {
        Experience.NEW, Experience.RETURNING -> Phase.INTRO_ONE
        Experience.REGULAR -> Phase.INTRO_TWO
        Experience.EXPERIENCED -> Phase.FIVE_TWO
    }

    /** True when a beginner chose more days than they need to start with. */
    fun manyDaysForABeginner(experience: Experience, days: Int): Boolean =
        experience == Experience.NEW && days >= 5
}
