package com.example.mockmarket.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.round

object PortfolioRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // API KEY
    private const val API_KEY = "d1df3020933a4fdca8b35966e9e0d538"
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.twelvedata.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    private val service by lazy { retrofit.create(StockApiService::class.java) }

    private fun r2(v: Double) = round(v * 100.0) / 100.0

    /**
     * Recompute portfolio equity and score using provided quotes, and
     * fetch prices for any missing symbols from TwelveData.
     *
     * - equity = sum(qty * price) for all holdings (positions only)
     * - score  = cash + equity (for leaderboard ordering)
     */
    fun refreshEquityWithQuotes(
        quotes: Map<String, Double>,
        onDone: () -> Unit,
        onErr: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onErr("Not signed in")
        val userRef = db.collection("users").document(uid)
        val holdingsRef = userRef.collection("holdings")

        // 1) Read user (for cash) and holdings (for symbols/qty)
        userRef.get().addOnSuccessListener { userSnap ->
            val cash = userSnap.getDouble("cash") ?: 0.0

            holdingsRef.get()
                .addOnSuccessListener { qs ->
                    val holdings = qs.documents.mapNotNull { d ->
                        val sym = d.id
                        val qty = d.getDouble("qty") ?: 0.0
                        if (qty > 0) sym to qty else null
                    }

                    if (holdings.isEmpty()) {
                        userRef.update(
                            mapOf(
                                "equity" to 0.0,
                                "score" to r2(cash),
                                "lastEquityAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener { onDone() }
                            .addOnFailureListener { e -> onErr(e.message ?: "Update failed") }
                        return@addOnSuccessListener
                    }

                    // 2) Start with provided quotes; fetch missing symbols
                    val prices = quotes.toMutableMap()
                    val toFetch = holdings.map { it.first }.filter { it !in prices }

                    if (toFetch.isEmpty()) {
                        // 3) All prices known → compute & write
                        val equity = computeEquity(holdings, prices)
                        writeEquity(userRef, cash, equity, onDone, onErr)
                    } else {
                        fetchMissingPrices(
                            symbols = toFetch,
                            onEach = { sym, px -> prices[sym] = px },
                            onAllDone = {
                                val equity = computeEquity(holdings, prices)
                                writeEquity(userRef, cash, equity, onDone, onErr)
                            },
                            onErr = onErr
                        )
                    }
                }
                .addOnFailureListener { e -> onErr(e.message ?: "Failed to read holdings") }
        }.addOnFailureListener { e -> onErr(e.message ?: "Failed to read user") }
    }

    private fun computeEquity(
        holdings: List<Pair<String, Double>>,
        prices: Map<String, Double>
    ): Double {
        var positionsValue = 0.0
        for ((sym, qty) in holdings) {
            val px = prices[sym] ?: 0.0
            positionsValue += qty * px
        }
        return r2(positionsValue)
    }

    private fun writeEquity(
        userRef: com.google.firebase.firestore.DocumentReference,
        cash: Double,
        equity: Double,
        onDone: () -> Unit,
        onErr: (String) -> Unit
    ) {
        userRef.update(
            mapOf(
                "equity" to equity,                 // positions only
                "score" to r2(cash + equity),       // for leaderboard
                "lastEquityAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { onDone() }
            .addOnFailureListener { e -> onErr(e.message ?: "Failed to write equity") }
    }

    /**
     * Fetch latest close price for each missing symbol (fail-soft: 0.0 on error).
     * Uses the same endpoint you already call elsewhere:
     *   service.getTimeSeries(sym, "1day", API_KEY, 1)
     */
    private fun fetchMissingPrices(
        symbols: List<String>,
        onEach: (String, Double) -> Unit,
        onAllDone: () -> Unit,
        onErr: (String) -> Unit
    ) {
        var remaining = symbols.size
        if (remaining == 0) { onAllDone(); return }

        symbols.forEach { sym ->
            service.getTimeSeries(
                symbol = sym,
                interval = "1day",
                apiKey = API_KEY,
                outputSize = 1
            ).enqueue(object : Callback<TwelveDataResponse> {
                override fun onResponse(
                    call: Call<TwelveDataResponse>,
                    response: Response<TwelveDataResponse>
                ) {
                    val px = response.body()
                        ?.takeIf { it.status == "ok" }
                        ?.values?.firstOrNull()
                        ?.close?.toDoubleOrNull() ?: 0.0
                    onEach(sym, px)
                    if (--remaining == 0) onAllDone()
                }

                override fun onFailure(call: Call<TwelveDataResponse>, t: Throwable) {
                    // fail-soft: record 0.0 so flow completes
                    onEach(sym, 0.0)
                    if (--remaining == 0) onAllDone()
                }
            })
        }
    }
}