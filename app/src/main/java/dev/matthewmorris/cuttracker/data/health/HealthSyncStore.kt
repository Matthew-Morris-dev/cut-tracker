package dev.matthewmorris.cuttracker.data.health

import android.content.Context
import androidx.core.content.edit
import dev.matthewmorris.cuttracker.domain.BodyMetric
import java.time.Instant

/** Sync bookkeeping. Tokens are only valid on this phone, so a restored backup just re-imports. */
class HealthSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences("health_connect", Context.MODE_PRIVATE)

    fun token(metric: BodyMetric): String? = prefs.getString("token_${metric.name}", null)

    fun setToken(metric: BodyMetric, token: String) = prefs.edit { putString("token_${metric.name}", token) }

    var lastSyncAt: Instant?
        get() = prefs.getLong("last_sync_at", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit { putLong("last_sync_at", value?.toEpochMilli() ?: 0L) }

    /** Set when a logged food couldn't be written; the next sync re-sends the last 30 days. */
    var nutritionCatchUpNeeded: Boolean
        get() = prefs.getBoolean("nutrition_catch_up", true)
        set(value) = prefs.edit { putBoolean("nutrition_catch_up", value) }

    val pendingNutritionDeletes: Set<String>
        get() = prefs.getStringSet("nutrition_pending_deletes", emptySet()).orEmpty().toSet()

    fun addPendingNutritionDelete(clientId: String) =
        prefs.edit { putStringSet("nutrition_pending_deletes", pendingNutritionDeletes + clientId) }

    fun removePendingNutritionDeletes(clientIds: Set<String>) =
        prefs.edit { putStringSet("nutrition_pending_deletes", pendingNutritionDeletes - clientIds) }
}
