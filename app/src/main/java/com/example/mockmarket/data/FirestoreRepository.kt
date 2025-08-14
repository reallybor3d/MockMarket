package com.example.mockmarket.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlin.math.round

object FirestoreRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // small rounding helpers
    private fun r2(v: Double) = round(v * 100.0) / 100.0
    private fun r4(v: Double) = round(v * 10000.0) / 10000.0

    fun placeOrder(
        symbol: String,
        side: String,
        qty: Double,
        price: Double,
        onOk: () -> Unit,
        onErr: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onErr("Not signed in")
            return
        }
        if (qty <= 0.0 || price <= 0.0) {
            onErr("Invalid qty/price")
            return
        }

        val userRef = db.collection("users").document(uid)
        val holdingRef = userRef.collection("holdings").document(symbol)
        val txRef = userRef.collection("transactions").document()

        db.runTransaction { txn: Transaction ->
            // User
            val userSnap = txn.get(userRef)
            if (!userSnap.exists()) throw IllegalStateException("User doc missing")
            var cash = userSnap.getDouble("cash") ?: 0.0

            // Holding
            val holdingSnap = txn.get(holdingRef)
            val qtyOld = holdingSnap.getDouble("qty") ?: 0.0
            val avgCostOld = holdingSnap.getDouble("avgCost") ?: 0.0

            val dir = side.uppercase()
            if (dir == "BUY") {
                val cost = qty * price
                if (cash + 1e-6 < cost) throw IllegalStateException("Insufficient cash")

                val newQty = qtyOld + qty
                val newAvg = if (newQty > 0.0) ((avgCostOld * qtyOld) + cost) / newQty else 0.0
                cash -= cost

                txn.set(
                    holdingRef,
                    mapOf("qty" to r4(newQty), "avgCost" to r4(newAvg)),
                    SetOptions.merge()
                )
            } else if (dir == "SELL") {
                if (qtyOld + 1e-6 < qty) throw IllegalStateException("Insufficient shares")
                val newQty = qtyOld - qty
                val proceeds = qty * price
                cash += proceeds

                if (newQty > 0.0) {
                    txn.update(holdingRef, mapOf("qty" to r4(newQty)))
                } else {
                    txn.delete(holdingRef)
                }
            } else {
                throw IllegalArgumentException("Invalid side")
            }

            // Update cash
            txn.update(userRef, mapOf("cash" to r2(cash)))

            // Log transaction
            txn.set(
                txRef,
                mapOf(
                    "symbol" to symbol,
                    "side" to dir,
                    "qty" to r4(qty),
                    "price" to r4(price),
                    "notional" to r2(qty * price),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            null
        }
            .addOnSuccessListener { onOk() }
            .addOnFailureListener { e -> onErr(e.message ?: "Order failed") }
    }
}
