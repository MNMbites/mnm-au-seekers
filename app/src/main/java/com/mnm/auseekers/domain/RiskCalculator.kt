package com.mnm.auseekers.domain

import kotlin.math.floor
import kotlin.math.min

class RiskCalculator {
    fun calculate(request: RiskRequest): RiskPlan {
        val modeFactor = if (request.mode == TradingMode.SCALPING) 0.75 else 1.0
        val effectiveRiskPercent = request.profile.baseRiskPercent * modeFactor
        val maximumLoss = request.balance * effectiveRiskPercent / 100.0

        val validationError = validate(request)
        if (validationError != null) {
            return RiskPlan(false, effectiveRiskPercent, maximumLoss, null, validationError)
        }

        val lossAtOneLot = request.stopDistancePoints * request.valuePerPointPerLot
        val rawLot = maximumLoss / lossAtOneLot
        if (rawLot < request.brokerMinimumLot) {
            return RiskPlan(
                allowed = false,
                effectiveRiskPercent = effectiveRiskPercent,
                maximumLoss = maximumLoss,
                suggestedLot = null,
                reason = "Broker minimum lot would exceed the selected risk. Skip the trade or use a suitable cent account.",
            )
        }

        val steppedLot = floor((rawLot + EPSILON) / request.brokerLotStep) * request.brokerLotStep
        val suggestedLot = min(steppedLot, request.brokerMaximumLot)
        return RiskPlan(
            allowed = suggestedLot >= request.brokerMinimumLot,
            effectiveRiskPercent = effectiveRiskPercent,
            maximumLoss = maximumLoss,
            suggestedLot = suggestedLot,
            reason = "Position stays within the selected maximum-loss limit.",
        )
    }

    private fun validate(request: RiskRequest): String? = when {
        request.balance < MINIMUM_SUPPORTED_BALANCE ->
            "Balance is below the USD 15 safety floor. No position is suggested."
        request.stopDistancePoints <= 0.0 -> "Stop distance must be greater than zero."
        request.valuePerPointPerLot <= 0.0 -> "Point value must be greater than zero."
        request.brokerMinimumLot <= 0.0 -> "Broker minimum lot must be greater than zero."
        request.brokerLotStep <= 0.0 -> "Broker lot step must be greater than zero."
        request.brokerMaximumLot < request.brokerMinimumLot -> "Broker lot limits are invalid."
        else -> null
    }

    private companion object {
        const val MINIMUM_SUPPORTED_BALANCE = 15.0
        const val EPSILON = 1e-9
    }
}
