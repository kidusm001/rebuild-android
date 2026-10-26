package com.rebuild.domain

/** Fresh-install defaults; existing plans receive only explicit, targeted migrations. */
object Seed {
    fun initialState(): AppState = AppState(planRevision = 1, catalogRevision = ExerciseCatalog.REVISION,
        exercises = exercises, templates = templates)

    val exercises = ExerciseCatalog.exercises

    private fun slot(
        id: String,
        exercise: String,
        baseline: Int,
        upper: Int,
        min: Int,
        max: Int,
        muscle: String,
        isolation: Boolean = false,
        perLeg: Boolean = false,
        alternatives: List<String> = emptyList()
    ) = PlanSlot(id, exercise, alternatives, baseline, upper, min, max,
        if (isolation) 90 else 180, perLeg, muscle)

    val templates = listOf(
        WorkoutTemplate("intro-upper", "Upper", 2, introOnly = true, slots = listOf(
            slot("intro-chest", "chest-machine", 2, 3, 8, 12, "Chest"),
            slot("intro-row", "row-cable", 2, 3, 8, 12, "Back"),
            slot("intro-pulldown", "pulldown", 2, 3, 10, 15, "Back"),
            slot("intro-lateral", "lateral-cable", 3, 4, 12, 20, "Side delts", isolation = true),
            slot("intro-rear", "rear-delt-cable", 2, 4, 12, 20, "Rear delts", isolation = true,
                alternatives = listOf("reverse-fly-db")),
            slot("intro-curl", "curl-cable", 3, 4, 10, 15, "Biceps", isolation = true,
                alternatives = listOf("curl-db")),
            slot("intro-triceps", "triceps-overhead", 3, 4, 10, 15, "Triceps", isolation = true)
        )),
        WorkoutTemplate("push", "Push", 2, establishedOnly = true, slots = listOf(
            slot("push-incline", "incline-db", 2, 3, 8, 12, "Chest",
                alternatives = listOf("incline-machine")),
            slot("push-shoulder", "shoulder-db", 2, 2, 8, 12, "Front delts",
                alternatives = listOf("shoulder-machine")),
            slot("push-lateral", "lateral-cable", 3, 4, 12, 20, "Side delts", isolation = true),
            slot("push-fly", "chest-fly-cable", 2, 2, 10, 15, "Chest", isolation = true),
            slot("push-triceps", "triceps-overhead", 3, 4, 10, 15, "Triceps", isolation = true)
        )),
        WorkoutTemplate("pull", "Pull", 3, establishedOnly = true, slots = listOf(
            slot("pull-vertical", "pulldown", 3, 3, 8, 12, "Back"),
            slot("pull-row", "row-chest-supported", 2, 3, 8, 12, "Back"),
            slot("pull-rear", "rear-delt-cable", 2, 4, 12, 20, "Rear delts", isolation = true,
                alternatives = listOf("reverse-fly-db")),
            slot("pull-curl", "curl-cable", 3, 4, 10, 15, "Biceps", isolation = true,
                alternatives = listOf("curl-db"))
        )),
        WorkoutTemplate("lower-a", "Lower A", 4, slots = listOf(
            slot("lower-a-press", "leg-press", 2, 3, 10, 15, "Quads"),
            slot("lower-a-split", "split-squat", 2, 2, 8, 12, "Quads", perLeg = true),
            slot("lower-a-curl", "curl-seated", 3, 4, 10, 15, "Hamstrings",
                isolation = true, alternatives = listOf("curl-lying")),
            slot("lower-a-calf", "calf-seated", 3, 4, 10, 20, "Calves", isolation = true),
            slot("lower-a-crunch", "crunch-cable", 2, 3, 10, 15, "Abs", isolation = true)
        )),
        WorkoutTemplate("upper", "Upper", 6, slots = listOf(
            slot("upper-bench", "bench-db", 2, 3, 8, 12, "Chest",
                alternatives = listOf("bench-barbell")),
            slot("upper-row", "row-chest-supported", 3, 3, 8, 12, "Back"),
            slot("upper-lateral", "lateral-cable", 3, 4, 12, 20, "Side delts", isolation = true),
            slot("upper-pulldown", "pulldown", 2, 3, 10, 15, "Back"),
            slot("upper-incline", "incline-db", 2, 2, 8, 12, "Chest"),
            slot("upper-curl", "curl-db", 3, 4, 10, 15, "Biceps", isolation = true,
                alternatives = listOf("curl-cable")),
            slot("upper-triceps", "pushdown", 3, 4, 10, 15, "Triceps", isolation = true)
        )),
        WorkoutTemplate("lower-b", "Lower B", 7, slots = listOf(
            slot("lower-b-squat", "squat-hack", 2, 3, 8, 12, "Quads",
                alternatives = listOf("squat-back")),
            slot("lower-b-rdl", "rdl", 3, 3, 8, 10, "Hamstrings"),
            slot("lower-b-curl", "curl-seated", 2, 3, 10, 15, "Hamstrings", isolation = true),
            slot("lower-b-extension", "leg-extension", 2, 2, 10, 15, "Quads", isolation = true),
            slot("lower-b-calf", "calf-standing", 3, 4, 10, 20, "Calves", isolation = true),
            slot("lower-b-crunch", "crunch-cable", 2, 3, 10, 15, "Abs", isolation = true)
        ))
    )
}
