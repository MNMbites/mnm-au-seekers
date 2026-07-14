package com.mnm.auseekers.paper

import android.content.Context
import com.google.gson.Gson

class AndroidPaperTradingRepository(
    context: Context,
    private val ledger: PaperTradingLedger = PaperTradingLedger(),
    private val gson: Gson = Gson(),
) {
    private val preferences = context.getSharedPreferences(
        "paper_trading_portfolio",
        Context.MODE_PRIVATE,
    )

    fun load(): PaperPortfolio {
        val serialized = preferences.getString(PORTFOLIO_KEY, null) ?: return PaperPortfolio()
        return runCatching { gson.fromJson(serialized, PaperPortfolio::class.java) }
            .getOrNull()
            ?: PaperPortfolio()
    }

    fun open(request: OpenPaperTradeRequest): PaperPortfolio =
        save(ledger.open(load(), request))

    fun close(positionId: String, bid: Double, ask: Double): PaperPortfolio =
        save(ledger.close(load(), positionId, bid, ask))

    fun reset(): PaperPortfolio = save(ledger.reset(load()))

    fun unrealizedPnl(position: PaperPosition, bid: Double, ask: Double): Double =
        ledger.unrealizedPnl(position, bid, ask)

    private fun save(portfolio: PaperPortfolio): PaperPortfolio {
        preferences.edit().putString(PORTFOLIO_KEY, gson.toJson(portfolio)).apply()
        return portfolio
    }

    private companion object {
        const val PORTFOLIO_KEY = "portfolio"
    }
}
