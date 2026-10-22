package dev.matthewmorris.cuttracker.data.food

import dev.matthewmorris.cuttracker.domain.Tags
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures follow the shapes the live API returned when this was written (October 2026). */
class OffMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun product(body: String) = OffMapping.toFoodProduct(json.decodeFromString(OffProduct.serializer(), body))

    @Test fun `product API Mars bar keeps per-100 g values and the pack size`() {
        val mars = product(
            """
            {"code":"5000159407236","product_name":"Mars","brands":"Mars, Mars Wrigley",
             "serving_quantity":100,"serving_size":"100 g","product_quantity":"51","quantity":"51 g",
             "categories_tags":["en:snacks","en:sweet-snacks","en:confectioneries"],
             "nutriments":{"energy-kcal_100g":450,"energy_100g":1890,"proteins_100g":4.04}}
            """,
        )!!
        assertEquals("Mars", mars.name)
        assertEquals("Mars", mars.brand)
        assertEquals(450.0, mars.kcalPer100g, 1e-9)
        assertEquals(4.04, mars.proteinPer100g, 1e-9)
        assertEquals(100.0, mars.servingG!!, 1e-9)
        assertEquals(51.0, mars.packG!!, 1e-9)
        assertEquals(Tags.TREAT, mars.suggestedTag)
        assertFalse(mars.isLiquid)
    }

    @Test fun `search hits have a list of brands and only a text quantity`() {
        val hit = product(
            """{"code":"5000159407236","product_name":"Mars","brands":["Mars"],"quantity":"51 g",
                "nutriments":{"energy-kcal_100g":450,"proteins_100g":4.04}}""",
        )!!
        assertEquals("Mars", hit.brand)
        assertEquals(51.0, hit.packG!!, 1e-9)
        assertNull(hit.servingG)
    }

    @Test fun `energy given only in kJ is converted to kcal`() {
        val food = product("""{"product_name":"Oats","nutriments":{"energy_100g":1600,"proteins_100g":11}}""")!!
        assertEquals(1600 / 4.184, food.kcalPer100g, 1e-6)
    }

    @Test fun `a beer is tagged alcohol and measured in ml`() {
        val beer = product(
            """{"product_name":"Lager","quantity":"330 ml","categories_tags":["en:beverages"],
                "nutriments":{"energy-kcal_100g":43,"alcohol_100g":4.6}}""",
        )!!
        assertEquals(Tags.ALCOHOL, beer.suggestedTag)
        assertTrue(beer.isLiquid)
        assertEquals(330.0, beer.packG!!, 1e-9)
        assertEquals(0.0, beer.proteinPer100g, 1e-9)
    }

    @Test fun `greek yogurt is not a treat`() {
        val yogurt = product(
            """{"product_name":"Greek yogurt","brands":["Asda"],
                "categories_tags":["en:dairies","en:fermented-milk-products","en:yogurts","en:greek-style-yogurts"],
                "nutriments":{"energy-kcal_100g":66,"proteins_100g":7}}""",
        )!!
        assertNull(yogurt.suggestedTag)
    }

    @Test fun `products without a name or calories are skipped`() {
        assertNull(product("""{"product_name":"Mystery","nutriments":{"proteins_100g":3}}"""))
        assertNull(product("""{"product_name":"  ","nutriments":{"energy-kcal_100g":100}}"""))
        assertNull(product("""{"product_name":"Typo","nutriments":{"energy-kcal_100g":45000}}"""))
    }

    @Test fun `implausible serving and pack sizes are dropped, numbers sent as text are read`() {
        val food = product(
            """{"product_name":"Bar","serving_quantity":"0","product_quantity":"250000",
                "nutriments":{"energy-kcal_100g":"400"}}""",
        )!!
        assertNull(food.servingG)
        assertNull(food.packG)
        assertEquals(400.0, food.kcalPer100g, 1e-9)
    }

    @Test fun `quantities parse common units and skip multipacks`() {
        assertEquals(500.0 to true, OffMapping.parseQuantity("0.5 l"))
        assertEquals(330.0 to true, OffMapping.parseQuantity("33cl"))
        assertEquals(1000.0 to false, OffMapping.parseQuantity("1 kg"))
        assertEquals(51.0 to false, OffMapping.parseQuantity("51 g"))
        assertNull(OffMapping.parseQuantity("6 x 25 g"))
        assertNull(OffMapping.parseQuantity(null))
    }
}
