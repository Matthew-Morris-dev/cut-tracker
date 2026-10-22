package dev.matthewmorris.cuttracker.data.food

import dev.matthewmorris.cuttracker.domain.Tags
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
internal data class OffProductResponse(val status: Int = 0, val product: OffProduct? = null)

@Serializable
internal data class OffSearchResponse(val hits: List<OffProduct> = emptyList())

/** Both endpoints' product shape. Open Food Facts is user-entered, so every field is optional. */
@Serializable
internal data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    /** A comma-separated string from the product API, a list from search. */
    val brands: JsonElement? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    @SerialName("product_quantity") val productQuantity: JsonElement? = null,
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    /** Free text such as "51 g" or "330 ml"; search has this but not product_quantity. */
    val quantity: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null,
    val nutriments: JsonObject? = null,
)

internal object OffMapping {
    const val FIELDS =
        "code,product_name,product_name_en,brands,serving_quantity,product_quantity,product_quantity_unit," +
            "quantity,categories_tags,nutriments"

    private const val KJ_PER_KCAL = 4.184

    private val alcoholCategories =
        setOf("en:alcoholic-beverages", "en:beers", "en:wines", "en:spirits", "en:ciders", "en:liqueurs")

    // Deliberately narrow: yogurts and desserts are left out so a cutting staple isn't flagged as a treat.
    private val treatCategories = setOf(
        "en:sweet-snacks", "en:confectioneries", "en:chocolates", "en:candies", "en:biscuits-and-cakes",
        "en:biscuits", "en:cakes", "en:ice-creams", "en:crisps", "en:potato-crisps", "en:chips",
    )

    private val quantityPattern = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(kg|g|ml|cl|l)\b""", RegexOption.IGNORE_CASE)

    /** Null when the product has no usable name or calories, which the app treats as not found. */
    fun toFoodProduct(product: OffProduct): FoodProduct? {
        val name = (product.productName ?: product.productNameEn)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val nutriments = product.nutriments ?: JsonObject(emptyMap())
        val kcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy-kj_100g")?.div(KJ_PER_KCAL)
            ?: nutriments.number("energy_100g")?.div(KJ_PER_KCAL)
            ?: return null
        if (kcal !in 0.0..1000.0) return null
        val details = toDetails(product)
        return FoodProduct(
            barcode = product.code?.takeIf { it.isNotBlank() },
            name = name,
            brand = brand(product.brands),
            kcalPer100g = kcal,
            proteinPer100g = nutriments.number("proteins_100g")?.coerceIn(0.0, 100.0) ?: 0.0,
            servingG = details.servingG,
            packG = details.packG,
            isLiquid = details.isLiquid,
            suggestedTag = details.suggestedTag,
        )
    }

    /** Serving and pack sizes, liquid or not, and the tag; none of it needs the product to have calories. */
    fun toDetails(product: OffProduct): ProductDetails {
        val quantity = parseQuantity(product.quantity)
        return ProductDetails(
            servingG = product.servingQuantity.number()?.takeIf { it in 1.0..2_000.0 },
            packG = product.productQuantity.number()?.takeIf { it in 1.0..10_000.0 } ?: quantity?.first,
            isLiquid = product.productQuantityUnit.equals("ml", ignoreCase = true) || quantity?.second == true,
            suggestedTag = suggestTag(product.categoriesTags.orEmpty(), product.nutriments?.number("alcohol_100g")),
        )
    }

    fun suggestTag(categories: List<String>, alcoholPer100g: Double?): String? = when {
        (alcoholPer100g ?: 0.0) > 0.5 || categories.any { it in alcoholCategories } -> Tags.ALCOHOL
        categories.any { it in treatCategories } -> Tags.TREAT
        else -> null
    }

    /** "51 g" -> 51 g, "0.5 l" -> 500 ml (liquid). Multipacks like "6 x 25 g" aren't parsed. */
    fun parseQuantity(text: String?): Pair<Double, Boolean>? {
        val match = quantityPattern.find(text ?: return null) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val (grams, liquid) = when (match.groupValues[2].lowercase()) {
            "kg" -> amount * 1000 to false
            "g" -> amount to false
            "l" -> amount * 1000 to true
            "cl" -> amount * 10 to true
            else -> amount to true
        }
        return (grams to liquid).takeIf { grams in 1.0..10_000.0 }
    }

    private fun brand(element: JsonElement?): String? = when (element) {
        is JsonArray -> (element.firstOrNull() as? JsonPrimitive)?.content
        is JsonPrimitive -> if (element.isString) element.content.substringBefore(',') else null
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.number(key: String): Double? = get(key).number()

    private fun JsonElement?.number(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.content.replace(',', '.').toDoubleOrNull()
    }
}
