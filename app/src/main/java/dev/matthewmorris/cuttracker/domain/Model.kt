package dev.matthewmorris.cuttracker.domain

enum class GoalStatus { MET, MISSED, UNMARKED }

enum class StatusSource { AUTO, MANUAL }

enum class MeasurementSource { HEALTH_CONNECT, MANUAL }

/** What a body reading measures. Health Connect keeps each of these as a separate record. */
enum class BodyMetric { WEIGHT_KG, BODY_FAT_PCT, LEAN_MASS_KG }

enum class Phase { CUT, BUILD }

object Tags {
    const val ALCOHOL = "alcohol"
    const val TREAT = "treat"
}
