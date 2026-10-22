package dev.matthewmorris.cuttracker.data.food

import dev.matthewmorris.cuttracker.data.db.FoodItem
import kotlin.math.roundToInt

/** A food from Open Food Facts, reduced to what the app uses. Values are per 100 g (or ml for drinks). */
data class FoodProduct(
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val servingG: Double?,
    val packG: Double?,
    val isLiquid: Boolean,
    val suggestedTag: String?,
)

/** What a product page adds to a search hit. Kept apart from calories, which a product can lack. */
data class ProductDetails(
    val servingG: Double?,
    val packG: Double?,
    val isLiquid: Boolean,
    val suggestedTag: String?,
)

/** Something logged by amount: a product just found online, or a saved food kept per 100 g. */
data class FoodChoice(
    val itemId: Long?,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val servingG: Double?,
    val packG: Double?,
    val isLiquid: Boolean,
    val lastAmountG: Double?,
    val tag: String?,
    val isFavourite: Boolean,
) {
    fun kcalFor(grams: Double): Int = (kcalPer100g * grams / 100).roundToInt()

    fun proteinFor(grams: Double): Double = proteinPer100g * grams / 100

    /** Last time's amount, else one serving, else 100 g. */
    val defaultAmountG: Double get() = lastAmountG ?: servingG ?: 100.0

    companion object {
        fun of(product: FoodProduct) = FoodChoice(
            itemId = null,
            barcode = product.barcode,
            name = product.name,
            brand = product.brand,
            kcalPer100g = product.kcalPer100g,
            proteinPer100g = product.proteinPer100g,
            servingG = product.servingG,
            packG = product.packG,
            isLiquid = product.isLiquid,
            lastAmountG = null,
            tag = product.suggestedTag,
            isFavourite = false,
        )

        /** Null for presets and quick-add foods, which are logged per serving instead. */
        fun of(item: FoodItem): FoodChoice? {
            val kcal = item.kcalPer100g ?: return null
            return FoodChoice(
                itemId = item.id,
                barcode = item.barcode,
                name = item.name,
                brand = item.brand,
                kcalPer100g = kcal,
                proteinPer100g = item.proteinPer100g ?: 0.0,
                servingG = item.servingG,
                packG = item.packG,
                isLiquid = item.isLiquid,
                lastAmountG = item.lastAmountG,
                tag = item.tags.firstOrNull(),
                isFavourite = item.isFavourite,
            )
        }
    }
}
