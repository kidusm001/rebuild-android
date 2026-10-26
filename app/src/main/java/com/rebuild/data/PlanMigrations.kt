package com.rebuild.data

import com.rebuild.domain.AppState
import com.rebuild.domain.LoadingType

/** Future templates only: a started workout owns its loading identity and recorded sets. */
internal object PlanMigrations {
    fun apply(state: AppState): AppState {
        if (state.planRevision >= 1) return state
        val legacyIds = setOf("pullup", "pullup-assisted")
        val vertical = state.templates.singleOrNull { it.id == "pull" }
            ?.slots?.singleOrNull { it.id == "pull-vertical" }
        val slotId = vertical?.id
        val templates = state.templates.map { template ->
            if (template.id != "pull") template else template.copy(slots = template.slots.map { slot ->
                if (slot.id != slotId) slot else {
                    val exerciseId = if (slot.exerciseId in legacyIds) "pulldown" else slot.exerciseId
                    slot.copy(exerciseId = exerciseId,
                        alternatives = slot.alternatives.filterNot { it in legacyIds || it == exerciseId })
                }
            })
        }
        val overrides = state.overrides.map {
            if (it.slotId == slotId && it.exerciseId in legacyIds) it.copy(exerciseId = "pulldown") else it
        }
        if (vertical?.exerciseId in legacyIds || overrides != state.overrides) {
            val pulldown = state.exercises.singleOrNull { it.id == "pulldown" }
            SnapshotCodec.ensure(pulldown != null && pulldown.loading == LoadingType.EXTERNAL && !pulldown.perDumbbell,
                "The updated Wednesday plan needs the Lat pulldown external-load definition. " +
                    "This backup has a missing or changed definition; your current data has not been replaced.")
        }
        return state.copy(planRevision = 1, templates = templates, overrides = overrides)
    }
}
