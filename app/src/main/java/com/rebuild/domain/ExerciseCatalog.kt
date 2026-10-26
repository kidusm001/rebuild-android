package com.rebuild.domain

import java.text.Normalizer
import java.util.Locale

/**
 * The built-in library. Revision 1 added common rep-based exercises; revision 2 adds timed holds, loaded carries and
 * cardio. Catalog membership never changes a workout prescription, and an exercise's measurement type never changes.
 */
object ExerciseCatalog {
    const val REVISION = 2

    internal val legacyExercises = listOf(
        Exercise("incline-db", "Incline dumbbell press", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Incline DB bench")),
        Exercise("incline-machine", "Incline machine press",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Incline chest press")),
        Exercise("shoulder-db", "Seated dumbbell shoulder press", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("Seated DB overhead press")),
        Exercise("shoulder-machine", "Seated machine shoulder press",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("Machine overhead press")),
        Exercise("lateral-cable", "Cable lateral raise",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("Cable side raise")),
        Exercise("chest-fly-cable", "Cable chest fly",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Cable crossover", "Cable flyes")),
        Exercise("triceps-overhead", "Overhead cable triceps extension",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.TRICEPS, aliases = listOf("Overhead rope extension")),
        Exercise("pullup", "Pull-up", LoadingType.BODYWEIGHT,
            equipment = Equipment.BODYWEIGHT, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Pullup", "Pull up")),
        Exercise("pullup-assisted", "Assisted pull-up", LoadingType.ASSISTANCE,
            equipment = Equipment.STACK_MACHINE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Assisted pullup")),
        Exercise("pulldown", "Lat pulldown",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Lat pull down", "Cable pulldown")),
        Exercise("row-chest-supported", "Chest-supported row",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Supported machine row")),
        Exercise("rear-delt-cable", "Cable rear-delt fly",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("Cable reverse fly", "Rear delt crossover")),
        Exercise("reverse-fly-db", "Chest-supported dumbbell reverse fly", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("DB rear delt fly")),
        Exercise("curl-cable", "Cable curl",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BICEPS, aliases = listOf("Cable biceps curl")),
        Exercise("curl-db", "Dumbbell curl", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.BICEPS, aliases = listOf("DB biceps curl")),
        Exercise("leg-press", "Leg press",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Seated leg press")),
        Exercise("split-squat", "Bulgarian split squat", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Rear foot elevated split squat", "RFESS")),
        Exercise("curl-seated", "Seated leg curl",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.HAMSTRINGS, aliases = listOf("Seated hamstring curl")),
        Exercise("curl-lying", "Lying leg curl",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.HAMSTRINGS, aliases = listOf("Prone hamstring curl")),
        Exercise("calf-seated", "Seated calf raise",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.CALVES, aliases = listOf("Seated calves")),
        Exercise("crunch-cable", "Cable crunch",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.CORE, aliases = listOf("Kneeling rope crunch", "Cable abs")),
        Exercise("bench-db", "Flat dumbbell bench press", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Flat DB press")),
        Exercise("bench-barbell", "Barbell bench press",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Flat bench")),
        Exercise("pushdown", "Cable triceps pushdown",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.TRICEPS, aliases = listOf("Triceps pressdown")),
        Exercise("squat-hack", "Hack squat",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Machine hack squat")),
        Exercise("squat-back", "Back squat",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Barbell squat")),
        Exercise("rdl", "Romanian deadlift",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.HAMSTRINGS, aliases = listOf("RDL")),
        Exercise("leg-extension", "Leg extension",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Quad extension")),
        Exercise("calf-standing", "Standing calf raise",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.CALVES, aliases = listOf("Standing calves")),
        Exercise("chest-machine", "Machine chest press",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Seated chest press")),
        Exercise("row-cable", "Seated cable row",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Low cable row"))
    )

    /** Everything revision 1 added to a library: the original exercises and common rep-based ones. */
    internal val revisionOne: List<Exercise> = legacyExercises + listOf(
        Exercise("incline-barbell", "Incline barbell bench press",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Incline bench")),
        Exercise("bench-close-grip", "Close-grip bench press",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.TRICEPS, aliases = listOf("Narrow grip bench")),
        Exercise("pushup", "Push-up", LoadingType.BODYWEIGHT,
            equipment = Equipment.BODYWEIGHT, primaryMuscle = MuscleGroup.CHEST, aliases = listOf("Pushup", "Press up")),
        Exercise("dip", "Dip", LoadingType.BODYWEIGHT,
            equipment = Equipment.BODYWEIGHT, primaryMuscle = MuscleGroup.TRICEPS, aliases = listOf("Parallel bar dips")),
        Exercise("chinup", "Chin-up", LoadingType.BODYWEIGHT,
            equipment = Equipment.BODYWEIGHT, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Chinup", "Underhand pullup")),
        Exercise("pulldown-neutral", "Neutral-grip lat pulldown",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Close grip pulldown")),
        Exercise("row-barbell", "Bent-over barbell row",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.BACK, aliases = listOf("BB row")),
        Exercise("row-db", "Single-arm dumbbell row", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.BACK, aliases = listOf("One arm DB row")),
        Exercise("row-stack", "Selectorized seated row",
            equipment = Equipment.STACK_MACHINE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Stack machine row")),
        Exercise("face-pull", "Cable face pull",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("Rope face pull")),
        Exercise("pullover-cable", "Straight-arm cable pulldown",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.BACK, aliases = listOf("Cable pullover", "Lat prayer")),
        Exercise("shoulder-barbell", "Standing barbell overhead press",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("OHP", "Military press")),
        Exercise("lateral-db", "Dumbbell lateral raise", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("DB side raise")),
        Exercise("front-raise-db", "Dumbbell front raise", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("DB front raise")),
        Exercise("reverse-pec-deck", "Reverse pec deck",
            equipment = Equipment.STACK_MACHINE, primaryMuscle = MuscleGroup.SHOULDERS, aliases = listOf("Rear delt machine fly")),
        Exercise("curl-hammer", "Dumbbell hammer curl", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.BICEPS, aliases = listOf("Neutral grip curl")),
        Exercise("curl-preacher", "Barbell preacher curl",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.BICEPS, aliases = listOf("Preacher bench curl")),
        Exercise("curl-incline-db", "Incline dumbbell curl", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.BICEPS, aliases = listOf("Incline DB curl")),
        Exercise("triceps-skullcrusher", "Barbell lying triceps extension",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.TRICEPS, aliases = listOf("Skull crusher", "Skullcrusher")),
        Exercise("triceps-rope", "Rope triceps pushdown",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.TRICEPS, aliases = listOf("Rope pressdown")),
        Exercise("squat-front", "Front squat",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Front rack squat")),
        Exercise("squat-goblet", "Goblet squat",
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Single dumbbell squat")),
        Exercise("lunge-db", "Dumbbell reverse lunge", perDumbbell = true,
            equipment = Equipment.DUMBBELL, primaryMuscle = MuscleGroup.QUADS, aliases = listOf("DB backward lunge")),
        Exercise("deadlift", "Conventional deadlift",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.GLUTES, aliases = listOf("Barbell deadlift")),
        Exercise("hip-thrust", "Barbell hip thrust",
            equipment = Equipment.BARBELL, primaryMuscle = MuscleGroup.GLUTES, aliases = listOf("Hip bridge")),
        Exercise("curl-standing", "Standing cable leg curl",
            equipment = Equipment.CABLE, primaryMuscle = MuscleGroup.HAMSTRINGS, aliases = listOf("Ankle strap hamstring curl")),
        Exercise("calf-leg-press", "Leg press calf raise",
            equipment = Equipment.MACHINE, primaryMuscle = MuscleGroup.CALVES, aliases = listOf("Calf press")),
        Exercise("crunch", "Crunch", LoadingType.BODYWEIGHT,
            equipment = Equipment.BODYWEIGHT, primaryMuscle = MuscleGroup.CORE, aliases = listOf("Abdominal crunch")),
        Exercise("knee-raise", "Hanging knee raise", LoadingType.BODYWEIGHT,
            equipment = Equipment.BODYWEIGHT, primaryMuscle = MuscleGroup.CORE, aliases = listOf("Hanging ab raise"))
    )

    /** Revision 2: timed holds, loaded carries and cardio, each with its measurement type. */
    internal val revisionTwo: List<Exercise> = listOf(
        Exercise("plank", "Plank", LoadingType.BODYWEIGHT, equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.CORE, aliases = listOf("Front plank", "Forearm plank"), measure = Measure.TIMED),
        Exercise("side-plank", "Side plank", LoadingType.BODYWEIGHT, equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.CORE, aliases = listOf("Lateral plank"), measure = Measure.TIMED),
        Exercise("plank-weighted", "Weighted plank", equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.CORE, aliases = listOf("Plate-loaded plank"), measure = Measure.TIMED),
        Exercise("dead-hang", "Dead hang", LoadingType.BODYWEIGHT, equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.BACK, aliases = listOf("Bar hang", "Passive hang"), measure = Measure.TIMED),
        Exercise("wall-sit", "Wall sit", LoadingType.BODYWEIGHT, equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.QUADS, aliases = listOf("Wall squat hold"), measure = Measure.TIMED),
        Exercise("farmers-carry", "Farmer's carry", perDumbbell = true, equipment = Equipment.DUMBBELL,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Farmers walk", "Loaded carry"),
            measure = Measure.DISTANCE),
        Exercise("suitcase-carry", "Suitcase carry", equipment = Equipment.DUMBBELL,
            primaryMuscle = MuscleGroup.CORE, aliases = listOf("One-arm carry", "Single dumbbell carry"),
            measure = Measure.DISTANCE),
        Exercise("treadmill", "Treadmill", LoadingType.BODYWEIGHT, equipment = Equipment.MACHINE,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Treadmill run", "Treadmill walk"),
            measure = Measure.CARDIO),
        Exercise("stationary-bike", "Stationary bike", LoadingType.BODYWEIGHT, equipment = Equipment.MACHINE,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Exercise bike", "Cycling"), measure = Measure.CARDIO),
        Exercise("rowing-machine", "Rowing machine", LoadingType.BODYWEIGHT, equipment = Equipment.MACHINE,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Rower", "Indoor row", "Erg"),
            measure = Measure.CARDIO),
        Exercise("elliptical", "Elliptical", LoadingType.BODYWEIGHT, equipment = Equipment.MACHINE,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Cross trainer"), measure = Measure.CARDIO),
        Exercise("stair-climber", "Stair climber", LoadingType.BODYWEIGHT, equipment = Equipment.MACHINE,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Stair stepper", "Step machine"),
            measure = Measure.CARDIO),
        Exercise("walking", "Walking", LoadingType.BODYWEIGHT, equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Walk"), measure = Measure.CARDIO),
        Exercise("running", "Running", LoadingType.BODYWEIGHT, equipment = Equipment.BODYWEIGHT,
            primaryMuscle = MuscleGroup.FULL_BODY, aliases = listOf("Run", "Jogging"), measure = Measure.CARDIO)
    )

    val exercises: List<Exercise> = revisionOne + revisionTwo
}

fun searchExercises(
    exercises: List<Exercise>,
    query: String,
    muscle: MuscleGroup? = null,
    equipment: Equipment? = null,
    measure: Measure? = null
): List<Exercise> = exercises.searchExercises(query, equipment, muscle, measure = measure)
    .sortedWith(compareBy<Exercise> { it.name.lowercase(Locale.ROOT) }.thenBy { it.name }.thenBy { it.id })

fun AppState.searchExercises(
    query: String = "",
    equipment: Equipment? = null,
    primaryMuscle: MuscleGroup? = null,
    customOnly: Boolean = false,
    measure: Measure? = null
): List<Exercise> = exercises.searchExercises(query, equipment, primaryMuscle, customOnly, measure)

fun Iterable<Exercise>.searchExercises(
    query: String = "",
    equipment: Equipment? = null,
    primaryMuscle: MuscleGroup? = null,
    customOnly: Boolean = false,
    measure: Measure? = null
): List<Exercise> {
    val terms = normalizedSearch(query).split(' ').filter { it.isNotEmpty() }
    val compactQuery = terms.joinToString("")
    return filter { exercise ->
        (equipment == null || exercise.equipment == equipment) &&
            (primaryMuscle == null || exercise.primaryMuscle == primaryMuscle) &&
            (measure == null || exercise.measure == measure) &&
            (!customOnly || exercise.custom) &&
            (listOf(exercise.name) + exercise.aliases).any { name ->
                val normalized = normalizedSearch(name)
                (terms.any { it.length > 1 } && terms.all { it in normalized }) ||
                    containsCompactPhrase(normalized, compactQuery)
            }
    }
}

private fun containsCompactPhrase(value: String, query: String): Boolean {
    if (query.isEmpty()) return true
    val words = value.split(' ').filter { it.isNotEmpty() }
    for (start in words.indices) {
        var matched = 0
        for (index in start until words.size) {
            if (!query.startsWith(words[index], matched)) break
            matched += words[index].length
            if (matched == query.length) return true
        }
    }
    return false
}

private fun normalizedSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
