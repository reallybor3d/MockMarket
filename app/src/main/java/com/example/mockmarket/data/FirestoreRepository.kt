package com.example.mockmarket.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction

object FirestoreRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun placeOrder(
        symbol: String,
        side: String,           // "BUY" or "SELL"
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

        val userRef = db.collection("users").document(uid)
        val holdingRef = userRef.collection("holdings").document(symbol)
        val txRef = userRef.collection("transactions").document()

        db.runTransaction { txn: Transaction ->
            // User doc
            val userSnap = txn.get(userRef)
            if (!userSnap.exists()) throw IllegalStateException("User doc missing")
            var cash = (userSnap.getDouble("cash") ?: 0.0)

            // Holding doc
            val holdingSnap = txn.get(holdingRef)
            val qtyOld = holdingSnap.getDouble("qty") ?: 0.0
            val avgCostOld = holdingSnap.getDouble("avgCost") ?: 0.0

            if (side == "BUY") {
                val cost = qty * price
                if (cash < cost) throw IllegalStateException("Insufficient cash")

                val newQty = qtyOld + qty
                val totalCost = (avgCostOld * qtyOld) + cost
                val newAvg = if (newQty > 0.0) totalCost / newQty else 0.0

                cash -= cost
                txn.set(holdingRef, mapOf("qty" to newQty, "avgCost" to newAvg))
            } else {
                // SELL
                if (qtyOld < qty) throw IllegalStateException("Insufficient shares")
                val newQty = qtyOld - qty
                val proceeds = qty * price
                cash += proceeds

                if (newQty > 0.0) {
                    txn.update(holdingRef, mapOf("qty" to newQty))
                } else {
                    txn.delete(holdingRef)
                }
            }

            // Update cash and record transaction
            txn.update(userRef, mapOf("cash" to cash))
            txn.set(txRef, mapOf(
                "symbol" to symbol,
                "side" to side,
                "qty" to qty,
                "price" to price,
                "timestamp" to FieldValue.serverTimestamp()
            ))

            null
        }
            .addOnSuccessListener { onOk() }
            .addOnFailureListener { e -> onErr(e.message ?: "Order failed") }
    }
}
