package dev.matthewmorris.cuttracker.domain

enum class GoalStatus { MET, MISSED, UNMARKED }

enum class StatusSource { AUTO, MANUAL }

enum class MeasurementSource { HEALTH_CONNECT, MANUAL }

enum class Phase { CUT, BUILD }

object Tags {
    const val ALCOHOL = "alcohol"
    const val TREAT = "treat"
}
