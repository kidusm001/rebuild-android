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
        val alternatives: List<String> = emptyList(),
        val dumbbellAlternatives: List<String> = emptyList(),
        val bodyweightAlternatives: List<String> = emptyList()
    )

    private fun b(
        key: String, gym: String, dumbbell: String?, bodyweight: String?, sets: Int, reps: IntRange, muscle: String,
        isolation: Boolean = false, perLeg: Boolean = false, bodyweightReps: IntRange? = null,
        alternatives: List<String> = emptyList()
    ) = Blueprint(key, gym, dumbbell, bodyweight, sets, reps, muscle, isolation, perLeg, bodyweightReps, alternatives,
        dumbbellAlternatives(dumbbell), bodyweightAlternatives(bodyweight))

    /** Easier or neighbouring versions offered as swaps, by the option they stand beside. */
    private fun dumbbellAlternatives(id: String?): List<String> = when (id) {
        "bench-db" -> listOf("pushup")
        "row-db" -> listOf("row-db-chest-supported")
        "row-db-chest-supported" -> listOf("row-db")
        "split-squat" -> listOf("lunge-db", "split-squat-bodyweight")
        else -> emptyList()
    }

    private fun bodyweightAlternatives(id: String?): List<String> = when (id) {
        "pushup" -> listOf("pushup-incline")
        "pushup-incline" -> listOf("pushup")
        // A negative (jump up, lower slowly) is the way to a first pull-up, and never a day's own exercise.
        "pullup" -> listOf("pullup-negative", "inverted-row", "chinup")
        "chinup" -> listOf("pullup-negative", "inverted-row")
        "inverted-row" -> listOf("pullup")
        "pike-pushup" -> listOf("pushup")
        "pushup-close" -> listOf("pushup-incline")
        "split-squat-bodyweight" -> listOf("squat-bodyweight")
        else -> emptyList()
    }

    // Rep ranges go by movement: power lifts 5–8, big compound lifts 6–10, other compounds 8–12, isolation 10–15,
    // shoulder raises 12–20, calves 10–20. Bodyweight sets can't add load, so they get wider ranges of their own.
    private val power = 5..8
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
        b("pull", "pulldown", "row-db", "inverted-row", 3, compound, "Back", bodyweightReps = 6..15,
            alternatives = listOf("pulldown-neutral")),
        b("hamstrings", "curl-seated", "leg-curl-slider", "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
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
        b("hamstrings", "curl-lying", "leg-curl-slider", "leg-curl-slider", 2, isolation, "Hamstrings", isolation = true,
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
        b("pulldown", "pulldown", "row-db-chest-supported", "pullup", 2, compound, "Back", bodyweightReps = 3..10),
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
        b("hamstrings", "curl-seated", "leg-curl-slider", "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
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
        b("row", "row-cable", "row-db-chest-supported", "inverted-row", 2, compound, "Back", bodyweightReps = 6..15),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("rear-delts", "rear-delt-cable", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true),
        b("biceps", "curl-cable", "curl-hammer", null, 2, isolation, "Biceps", isolation = true),
        b("triceps", "triceps-overhead", "triceps-overhead-db", "pushup-close", 2, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15)
    )
    private val lowerB = listOf(
        b("legs", "leg-press", "squat-goblet", "squat-bodyweight", 3, 10..15, "Quads", bodyweightReps = 12..20),
        b("split-squat", "split-squat", "split-squat", "split-squat-bodyweight", 2, compound, "Quads", perLeg = true,
            bodyweightReps = 10..15),
        b("glutes", "hip-thrust", "glute-bridge", "glute-bridge", 2, compound, "Glutes", bodyweightReps = 12..20),
        b("hamstrings", "curl-lying", "leg-curl-slider", "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-seated")),
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
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true),
        b("triceps", "pushdown", "triceps-overhead-db", "pushup-close", 3, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15)
    )
    private val pull = listOf(
        b("pulldown", "pulldown", "row-db-chest-supported", "pullup", 3, compound, "Back", bodyweightReps = 3..10,
            alternatives = listOf("pulldown-neutral")),
        b("row", "row-chest-supported", "row-db", "inverted-row", 3, compound, "Back", bodyweightReps = 6..15),
        b("rear-delts", "face-pull", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true),
        b("biceps", "curl-db", "curl-db", "chinup", 3, isolation, "Biceps", isolation = true, bodyweightReps = 3..10),
        b("hammer", "curl-hammer", "curl-hammer", null, 1, isolation, "Biceps", isolation = true)
    )
    private val legs = listOf(
        b("squat", "squat-hack", "squat-goblet", "squat-bodyweight", 3, bigCompound, "Quads", bodyweightReps = 12..20,
            alternatives = listOf("squat-back")),
        b("hinge", "rdl-db", "rdl-db", "glute-bridge", 3, compound, "Hamstrings", bodyweightReps = 12..20,
            alternatives = listOf("rdl")),
        b("split-squat", "split-squat", "split-squat", "split-squat-bodyweight", 2, compound, "Quads", perLeg = true,
            bodyweightReps = 10..15),
        b("hamstrings", "curl-seated", "leg-curl-slider", "leg-curl-slider", 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("extension", "leg-extension", null, null, 2, isolation, "Quads", isolation = true),
        b("calves", "calf-standing", "calf-raise-bodyweight", "calf-raise-bodyweight", 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("core", "crunch-cable", "crunch", "crunch", 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val upperPower = listOf(
        b("press", "bench-barbell", "bench-db", null, 3, power, "Chest",
            alternatives = listOf("bench-db", "chest-machine")),
        // Chest-supported row spares the lower back ahead of Tuesday's squats and RDLs; barbell row stays an option.
        b("row", "row-chest-supported", "row-db", null, 3, power, "Back",
            alternatives = listOf("row-barbell", "row-cable")),
        b("incline", "incline-db", "incline-db", null, 3, bigCompound, "Chest",
            alternatives = listOf("incline-machine", "incline-barbell")),
        b("pulldown", "pulldown", "row-db-chest-supported", null, 3, bigCompound, "Back",
            alternatives = listOf("pulldown-neutral")),
        b("shoulder", "shoulder-barbell", "shoulder-db", null, 2, power, "Front delts",
            alternatives = listOf("shoulder-db", "shoulder-machine")),
        b("biceps", "curl-preacher", "curl-db", null, 2, bigCompound, "Biceps", isolation = true,
            alternatives = listOf("curl-db", "curl-cable")),
        b("triceps", "triceps-skullcrusher", "triceps-overhead-db", null, 2, bigCompound, "Triceps",
            isolation = true, alternatives = listOf("pushdown", "bench-close-grip"))
    )
    private val lowerPower = listOf(
        b("squat", "squat-back", "squat-goblet", null, 3, power, "Quads",
            alternatives = listOf("squat-hack", "squat-front")),
        b("hinge", "rdl", "rdl-db", null, 3, power, "Hamstrings",
            alternatives = listOf("rdl-db", "deadlift")),
        b("split-squat", "split-squat", "split-squat", null, 3, bigCompound, "Quads", perLeg = true),
        b("hamstrings", "curl-lying", "leg-curl-slider", null, 3, compound, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-seated")),
        b("calves", "calf-standing", "calf-raise-bodyweight", null, 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("core", "crunch-cable", "crunch", null, 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val upperHypertrophy = listOf(
        b("incline", "incline-db", "incline-db", null, 3, compound, "Chest",
            alternatives = listOf("incline-machine", "incline-barbell")),
        b("row", "row-cable", "row-db", null, 3, compound, "Back",
            alternatives = listOf("row-chest-supported", "row-stack")),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("pulldown", "pulldown-neutral", "row-db-chest-supported", null, 3, compound, "Back",
            alternatives = listOf("pulldown")),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true,
            alternatives = listOf("lateral-db")),
        b("biceps", "curl-incline-db", "curl-incline-db", null, 2, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-cable", "curl-db")),
        b("triceps", "triceps-overhead", "triceps-overhead-db", null, 2, isolation, "Triceps",
            isolation = true, alternatives = listOf("pushdown", "triceps-rope"))
    )
    private val lowerHypertrophy = listOf(
        b("squat", "squat-hack", "squat-goblet", null, 3, compound, "Quads",
            alternatives = listOf("leg-press", "squat-front")),
        b("hinge", "rdl-db", "rdl-db", null, 3, compound, "Hamstrings",
            alternatives = listOf("rdl")),
        b("lunge", "lunge-db", "lunge-db", null, 3, compound, "Quads", perLeg = true),
        b("extension", "leg-extension", null, null, 2, isolation, "Quads", isolation = true),
        b("hamstrings", "curl-seated", "leg-curl-slider", null, 3, isolation, "Hamstrings",
            isolation = true, bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("calves", "calf-seated", "calf-raise-bodyweight", null, 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25, alternatives = listOf("calf-leg-press")),
        b("core", "knee-raise", "crunch", null, 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val torsoA = listOf(
        b("press", "bench-db", "bench-db", null, 3, bigCompound, "Chest",
            alternatives = listOf("chest-machine", "bench-barbell")),
        b("row", "row-chest-supported", "row-db", null, 3, compound, "Back",
            alternatives = listOf("row-barbell")),
        b("incline", "incline-db", "incline-db", null, 2, compound, "Chest", alternatives = listOf("incline-machine")),
        b("pulldown", "pulldown", "row-db-chest-supported", null, 3, compound, "Back",
            alternatives = listOf("pulldown-neutral")),
        b("shoulder", "shoulder-db", "shoulder-db", null, 3, compound, "Front delts",
            alternatives = listOf("shoulder-machine")),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true,
            alternatives = listOf("lateral-db")),
        b("core", "crunch-cable", "crunch", null, 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val limbsA = listOf(
        b("squat", "squat-hack", "squat-goblet", null, 3, bigCompound, "Quads",
            alternatives = listOf("squat-back")),
        b("hinge", "rdl-db", "rdl-db", null, 3, compound, "Hamstrings",
            alternatives = listOf("rdl")),
        b("extension", "leg-extension", null, null, 2, isolation, "Quads", isolation = true),
        b("hamstrings", "curl-seated", "leg-curl-slider", null, 3, isolation, "Hamstrings",
            isolation = true, bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("calves", "calf-standing", "calf-raise-bodyweight", null, 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("biceps", "curl-db", "curl-db", null, 3, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-cable")),
        b("triceps", "pushdown", "triceps-overhead-db", null, 3, isolation, "Triceps", isolation = true,
            alternatives = listOf("triceps-rope"))
    )
    private val torsoB = listOf(
        b("incline", "incline-db", "incline-db", null, 3, compound, "Chest",
            alternatives = listOf("incline-machine", "incline-barbell")),
        b("pulldown", "pulldown-neutral", "row-db", null, 3, compound, "Back",
            alternatives = listOf("pulldown")),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("row", "row-cable", "row-db-chest-supported", null, 3, compound, "Back",
            alternatives = listOf("row-chest-supported")),
        b("lateral", "lateral-cable", "lateral-db", null, 2, raise, "Side delts", isolation = true,
            alternatives = listOf("lateral-db")),
        b("rear-delts", "rear-delt-cable", "reverse-fly-db", null, 3, raise, "Rear delts", isolation = true,
            alternatives = listOf("face-pull")),
        b("core", "knee-raise", "crunch", null, 2, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val limbsB = listOf(
        b("legs", "leg-press", "squat-goblet", null, 3, 10..15, "Quads"),
        b("split-squat", "split-squat", "split-squat", null, 3, compound, "Quads", perLeg = true),
        b("glutes", "hip-thrust", "glute-bridge", null, 2, compound, "Glutes", bodyweightReps = 12..20),
        b("hamstrings", "curl-lying", "leg-curl-slider", null, 3, isolation, "Hamstrings",
            isolation = true, bodyweightReps = 8..15, alternatives = listOf("curl-seated")),
        b("calves", "calf-seated", "calf-raise-bodyweight", null, 3, calves, "Calves",
            isolation = true, bodyweightReps = 15..25),
        b("biceps", "curl-cable", "curl-hammer", null, 3, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-hammer", "curl-incline-db")),
        b("triceps", "triceps-overhead", "triceps-overhead-db", null, 3, isolation, "Triceps",
            isolation = true, alternatives = listOf("pushdown"))
    )
    private val broChest = listOf(
        b("press", "bench-barbell", "bench-db", null, 3, bigCompound, "Chest",
            alternatives = listOf("bench-db", "chest-machine")),
        b("incline", "incline-db", "incline-db", null, 3, compound, "Chest",
            alternatives = listOf("incline-machine", "incline-barbell")),
        b("machine", "chest-machine", "pushup", null, 3, compound, "Chest", bodyweightReps = 6..20,
            alternatives = listOf("bench-db", "pushup")),
        b("fly", "chest-fly-cable", null, null, 3, isolation, "Chest", isolation = true),
        b("core", "crunch-cable", "crunch", null, 3, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val broBack = listOf(
        b("pulldown", "pulldown", "row-db-chest-supported", null, 3, compound, "Back",
            alternatives = listOf("pulldown-neutral", "pullup-assisted")),
        b("row", "row-barbell", "row-db", null, 3, bigCompound, "Back", alternatives = listOf("row-chest-supported")),
        b("cable-row", "row-cable", null, null, 3, compound, "Back", alternatives = listOf("row-stack")),
        b("pullover", "pullover-cable", null, null, 3, isolation, "Back", isolation = true,
            alternatives = listOf("pulldown-neutral")),
        b("rear-delts", "face-pull", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true,
            alternatives = listOf("rear-delt-cable"))
    )
    private val broShoulders = listOf(
        b("press", "shoulder-db", "shoulder-db", null, 3, compound, "Front delts",
            alternatives = listOf("shoulder-barbell", "shoulder-machine")),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true,
            alternatives = listOf("lateral-db")),
        b("rear-delts", "rear-delt-cable", "reverse-fly-db", null, 3, raise, "Rear delts", isolation = true,
            alternatives = listOf("reverse-pec-deck", "face-pull")),
        b("core", "knee-raise", "crunch", null, 3, isolation, "Abs", isolation = true, bodyweightReps = 12..20)
    )
    private val broLegs = listOf(
        b("squat", "squat-hack", "squat-goblet", null, 3, bigCompound, "Quads", alternatives = listOf("squat-back")),
        b("hinge", "rdl-db", "rdl-db", null, 3, compound, "Hamstrings", alternatives = listOf("rdl")),
        b("split-squat", "split-squat", "split-squat", null, 3, compound, "Quads", perLeg = true),
        b("hamstrings", "curl-seated", "leg-curl-slider", null, 3, isolation, "Hamstrings", isolation = true,
            bodyweightReps = 8..15, alternatives = listOf("curl-lying")),
        b("extension", "leg-extension", null, null, 3, isolation, "Quads", isolation = true),
        b("calves", "calf-standing", "calf-raise-bodyweight", null, 3, calves, "Calves", isolation = true,
            bodyweightReps = 15..25)
    )
    private val broArms = listOf(
        b("curl", "curl-db", "curl-db", null, 3, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-preacher")),
        b("pushdown", "pushdown", "triceps-overhead-db", null, 3, isolation, "Triceps", isolation = true,
            alternatives = listOf("triceps-rope")),
        b("incline-curl", "curl-incline-db", "curl-incline-db", null, 3, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-cable")),
        b("overhead", "triceps-overhead", "pushup-close", null, 3, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15, alternatives = listOf("triceps-skullcrusher")),
        b("hammer", "curl-hammer", "curl-hammer", null, 3, isolation, "Biceps", isolation = true),
        b("skullcrusher", "triceps-skullcrusher", null, null, 2, isolation, "Triceps", isolation = true,
            alternatives = listOf("bench-close-grip"))
    )
    private val arnoldChestBackA = listOf(
        b("press", "bench-db", "bench-db", null, 3, bigCompound, "Chest",
            alternatives = listOf("bench-barbell", "chest-machine")),
        b("pulldown", "pulldown", "row-db-chest-supported", null, 3, compound, "Back",
            alternatives = listOf("pulldown-neutral", "pullup-assisted")),
        b("incline", "incline-db", "incline-db", null, 2, compound, "Chest",
            alternatives = listOf("incline-machine", "incline-barbell")),
        b("row", "row-chest-supported", "row-db", null, 2, compound, "Back",
            alternatives = listOf("row-barbell")),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("cable-row", "row-cable", null, null, 2, compound, "Back", alternatives = listOf("row-stack"))
    )
    private val arnoldChestBackB = listOf(
        b("incline", "incline-db", "incline-db", null, 3, compound, "Chest",
            alternatives = listOf("incline-machine", "incline-barbell")),
        b("pulldown", "pulldown-neutral", "row-db", null, 3, compound, "Back",
            alternatives = listOf("pulldown", "pullup-assisted")),
        b("press", "bench-db", "bench-db", null, 2, bigCompound, "Chest",
            alternatives = listOf("bench-barbell", "chest-machine")),
        b("row", "row-cable", "row-db-chest-supported", null, 2, compound, "Back",
            alternatives = listOf("row-stack")),
        b("fly", "chest-fly-cable", null, null, 2, isolation, "Chest", isolation = true),
        b("supported-row", "row-chest-supported", null, null, 2, compound, "Back",
            alternatives = listOf("row-barbell"))
    )
    private val arnoldShouldersArmsA = listOf(
        b("shoulder", "shoulder-db", "shoulder-db", null, 3, compound, "Front delts",
            alternatives = listOf("shoulder-barbell", "shoulder-machine")),
        b("lateral", "lateral-cable", "lateral-db", null, 3, raise, "Side delts", isolation = true,
            alternatives = listOf("lateral-db")),
        b("rear-delts", "rear-delt-cable", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true,
            alternatives = listOf("face-pull", "reverse-pec-deck")),
        b("biceps", "curl-db", "curl-db", null, 3, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-cable", "curl-preacher")),
        b("triceps", "pushdown", "triceps-overhead-db", null, 3, isolation, "Triceps", isolation = true,
            alternatives = listOf("triceps-rope")),
        b("hammer", "curl-hammer", "curl-hammer", null, 2, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-incline-db")),
        b("overhead", "triceps-overhead", null, null, 2, isolation, "Triceps", isolation = true,
            alternatives = listOf("triceps-skullcrusher"))
    )
    private val arnoldShouldersArmsB = listOf(
        b("shoulder", "shoulder-machine", "shoulder-db", null, 3, compound, "Front delts",
            alternatives = listOf("shoulder-barbell", "shoulder-db")),
        b("lateral", "lateral-db", "lateral-db", null, 3, raise, "Side delts", isolation = true,
            alternatives = listOf("lateral-cable")),
        b("rear-delts", "face-pull", "reverse-fly-db", null, 2, raise, "Rear delts", isolation = true,
            alternatives = listOf("rear-delt-cable", "reverse-pec-deck")),
        b("biceps", "curl-incline-db", "curl-incline-db", null, 3, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-db", "curl-preacher")),
        b("triceps", "triceps-overhead", "triceps-overhead-db", null, 3, isolation, "Triceps", isolation = true,
            alternatives = listOf("triceps-skullcrusher")),
        b("cable-curl", "curl-cable", "curl-hammer", null, 2, isolation, "Biceps", isolation = true,
            alternatives = listOf("curl-hammer")),
        b("pushdown", "pushdown", "pushup-close", null, 2, isolation, "Triceps", isolation = true,
            bodyweightReps = 6..15, alternatives = listOf("triceps-rope"))
    )

    private fun build(splitId: String, days: List<Triple<String, String, List<Blueprint>>>): List<WorkoutTemplate> {
        val weekdays = weekdays(days.size)
        return days.mapIndexed { i, (key, name, blueprints) ->
            val templateId = "$splitId-$key"
            // A swap never offers an exercise the same day already asks for with that gear.
            val dayGym = blueprints.map { it.gym }.toSet()
            val dayDumbbell = blueprints.mapNotNull { it.dumbbell }.toSet()
            val dayBodyweight = blueprints.mapNotNull { it.bodyweight }.toSet()
            WorkoutTemplate(templateId, name, weekdays[i], slots = blueprints.map { bp ->
                PlanSlot(
                    id = "$templateId-${bp.key}",
                    exerciseId = bp.gym,
                    alternatives = bp.alternatives.filter { it !in dayGym },
                    baselineSets = bp.sets,
                    upperSets = bp.sets + 1,
                    minReps = bp.reps.first,
                    maxReps = bp.reps.last,
                    restSeconds = if (bp.isolation) isolationRest else compoundRest,
                    perLeg = bp.perLeg,
                    muscle = bp.muscle,
                    gear = SlotGear(bp.dumbbell, bp.bodyweight, bp.bodyweightReps?.first, bp.bodyweightReps?.last,
                        bp.dumbbellAlternatives.filter { it !in dayDumbbell },
                        bp.bodyweightAlternatives.filter { it !in dayBodyweight })
                )
            }, splitId = splitId)
        }
    }

    const val FULL_BODY_2 = "full-body-2"
    const val FULL_BODY_3 = "full-body-3"
    const val UPPER_LOWER = "upper-lower"
    const val PHUL = "phul"
    const val TORSO_LIMBS = "torso-limbs"
    const val PUSH_PULL_LEGS = "push-pull-legs"
    const val PUSH_PULL_LEGS_2 = "push-pull-legs-2"
    const val UPPER_LOWER_PPL = "upper-lower-ppl"
    const val BRO_SPLIT = "bro-split"
    const val ARNOLD_SPLIT = "arnold-split"

    private val anyGear = setOf(Gear.GYM, Gear.DUMBBELLS, Gear.BODYWEIGHT)
    private val weightedGear = setOf(Gear.GYM, Gear.DUMBBELLS)

    val all: List<Split> = listOf(
        Split(FULL_BODY_2, "Full body, 2 days", 2,
            "Every workout trains your whole body, so your main muscles work twice a week.",
            anyGear, build(FULL_BODY_2, listOf(Triple("a", "Full body A", fullA), Triple("b", "Full body B", fullB)))),
        Split(FULL_BODY_3, "Full body, 3 days", 3,
            "Every workout trains your whole body, so you practise squatting, pressing and pulling three times a week. A good way to start.",
            anyGear, build(FULL_BODY_3, listOf(Triple("a", "Full body A", fullA), Triple("b", "Full body B", fullB),
                Triple("c", "Full body C", fullC)))),
        Split(UPPER_LOWER, "Upper and lower", 4,
            "Upper and lower body take turns, so each muscle gets two workouts a week with rest between them.",
            anyGear, build(UPPER_LOWER, listOf(Triple("upper-a", "Upper A", upperA), Triple("lower-a", "Lower A", lowerA),
                Triple("upper-b", "Upper B", upperB), Triple("lower-b", "Lower B", lowerB)))),
        Split(PHUL, "Power and hypertrophy", 4,
            "Upper and lower body twice a week: two heavier strength days, then two higher-rep days.",
            weightedGear, build(PHUL, listOf(Triple("upper-power", "Upper power", upperPower),
                Triple("lower-power", "Lower power", lowerPower),
                Triple("upper-hypertrophy", "Upper hypertrophy", upperHypertrophy),
                Triple("lower-hypertrophy", "Lower hypertrophy", lowerHypertrophy)))),
        Split(TORSO_LIMBS, "Torso and limbs", 4,
            "Chest, back and shoulders on torso days; legs and arms on limb days, twice a week.",
            weightedGear, build(TORSO_LIMBS, listOf(Triple("torso-a", "Torso A", torsoA), Triple("limbs-a", "Limbs A", limbsA),
                Triple("torso-b", "Torso B", torsoB), Triple("limbs-b", "Limbs B", limbsB)))),
        Split(PUSH_PULL_LEGS, "Push, pull, legs", 3,
            "Pushing muscles, pulling muscles, then legs. Each muscle gets one longer workout a week.",
            anyGear, build(PUSH_PULL_LEGS, listOf(Triple("push", "Push", push), Triple("pull", "Pull", pull),
                Triple("legs", "Legs", legs)))),
        Split(UPPER_LOWER_PPL, "Upper, lower, push, pull, legs", 5,
            "An upper and a lower day, then push, pull and legs, so each muscle gets two workouts a week.",
            anyGear, build(UPPER_LOWER_PPL, listOf(Triple("upper", "Upper", upperA), Triple("lower", "Lower", lowerB),
                Triple("push", "Push", push), Triple("pull", "Pull", pull), Triple("legs", "Legs", legs)))),
        Split(BRO_SPLIT, "Bro split", 5,
            "One muscle group a day: chest, back, legs, shoulders and arms.",
            weightedGear, build(BRO_SPLIT, listOf(Triple("chest", "Chest", broChest), Triple("back", "Back", broBack),
                Triple("legs", "Legs", broLegs), Triple("shoulders", "Shoulders", broShoulders),
                Triple("arms", "Arms", broArms)))),
        Split(PUSH_PULL_LEGS_2, "Push, pull, legs twice", 6,
            "Push, pull and legs, twice through, so each muscle gets two workouts a week and Sunday is rest.",
            anyGear, build(PUSH_PULL_LEGS_2, listOf(Triple("push-a", "Push A", push), Triple("pull-a", "Pull A", pull),
                Triple("legs-a", "Legs A", legs), Triple("push-b", "Push B", push), Triple("pull-b", "Pull B", pull),
                Triple("legs-b", "Legs B", legs)))),
        Split(ARNOLD_SPLIT, "Arnold split", 6,
            "Chest and back, shoulders and arms, then legs, twice through the week.",
            weightedGear, build(ARNOLD_SPLIT, listOf(Triple("chest-back-a", "Chest and back A", arnoldChestBackA),
                Triple("shoulders-arms-a", "Shoulders and arms A", arnoldShouldersArmsA), Triple("legs-a", "Legs A", legs),
                Triple("chest-back-b", "Chest and back B", arnoldChestBackB),
                Triple("shoulders-arms-b", "Shoulders and arms B", arnoldShouldersArmsB), Triple("legs-b", "Legs B", lowerB)))),
        Split(ORIGINAL_SPLIT, "Rebuild's gym plan", 5,
            "Upper, lower, push and pull days, mostly on machines and dumbbells. A gentle start that grows to 5 days.",
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
