package com.mnm.auseekers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskCalculatorTest {
    private val calculator = RiskCalculator()

    @Test
    fun safeProfileSupportsFifteenDollarBalanceWithoutRoundingRiskUp() {
        val plan = calculator.calculate(
            request(
                balance = 15.0,
                profile = RiskProfile.SAFE,
                stopDistancePoints = 50.0,
                valuePerPointPerLot = 0.10,
            ),
        )

        assertTrue(plan.allowed)
        assertEquals(0.5, plan.effectiveRiskPercent, 0.0001)
        assertEquals(0.075, plan.maximumLoss, 0.0001)
        assertEquals(0.01, plan.suggestedLot!!, 0.0001)
    }

    @Test
    fun rejectsTradeWhenBrokerMinimumLotExceedsRiskLimit() {
        val plan = calculator.calculate(
            request(
                balance = 15.0,
                profile = RiskProfile.SAFE,
                stopDistancePoints = 100.0,
                valuePerPointPerLot = 1.0,
            ),
        )

        assertFalse(plan.allowed)
        assertEquals(null, plan.suggestedLot)
        assertTrue(plan.reason.contains("minimum lot"))
    }

    @Test
    fun rejectsBalanceBelowSafetyFloor() {
        val plan = calculator.calculate(
            request(
                balance = 14.99,
                profile = RiskProfile.AGGRESSIVE,
                stopDistancePoints = 20.0,
                valuePerPointPerLot = 0.10,
            ),
        )

        assertFalse(plan.allowed)
        assertTrue(plan.reason.contains("USD 15"))
    }

    @Test
    fun scalpingModeReducesSelectedRisk() {
        val plan = calculator.calculate(
            request(
                balance = 100.0,
                profile = RiskProfile.SEMI_AGGRESSIVE,
                mode = TradingMode.SCALPING,
                stopDistancePoints = 20.0,
                valuePerPointPerLot = 1.0,
            ),
        )

        assertEquals(0.75, plan.effectiveRiskPercent, 0.0001)
    }

    private fun request(
        balance: Double,
        profile: RiskProfile,
        stopDistancePoints: Double,
        valuePerPointPerLot: Double,
        mode: TradingMode = TradingMode.PRIMARY,
    ) = RiskRequest(
        balance = balance,
        profile = profile,
        mode = mode,
        stopDistancePoints = stopDistancePoints,
        valuePerPointPerLot = valuePerPointPerLot,
    )
}
