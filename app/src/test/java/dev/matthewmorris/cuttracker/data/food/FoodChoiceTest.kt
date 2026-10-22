package dev.matthewmorris.cuttracker.data.food

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodChoiceTest {
    private val yogurt = FoodChoice.of(
        FoodProduct("1", "Greek yogurt", "Asda", 66.0, 7.0, servingG = 125.0, packG = 500.0, isLiquid = false, suggestedTag = null),
    )

    @Test fun `amounts scale the per-100 g values`() {
        assertEquals(99, yogurt.kcalFor(150.0))
        assertEquals(10.5, yogurt.proteinFor(150.0), 1e-9)
    }

    @Test fun `the default amount is last time's, then a serving, then 100 g`() {
        assertEquals(170.0, yogurt.copy(lastAmountG = 170.0).defaultAmountG, 1e-9)
        assertEquals(125.0, yogurt.defaultAmountG, 1e-9)
        assertEquals(100.0, yogurt.copy(servingG = null).defaultAmountG, 1e-9)
    }
}
