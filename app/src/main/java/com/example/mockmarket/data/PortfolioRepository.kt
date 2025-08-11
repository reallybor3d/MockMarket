package com.example.mockmarket.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object PortfolioRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    /**
     * quotes: map of SYMBOL -> lastPrice
     */

    fun refreshEquityWithQuotes(
        quotes: Map<String, Double>,
        onDone: () -> Unit,
        onErr: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onErr("Not signed in")
        val userRef = db.collection("users").document(uid)

        userRef.collection("holdings").get()
            .addOnSuccessListener { posSnap ->
                var positionsValue = 0.0
                for (doc in posSnap.documents) {
                    val sym = doc.id
                    val qty = doc.getDouble("qty") ?: 0.0
                    val px = quotes[sym] ?: 0.0
                    positionsValue += qty * px
                }
                userRef.get()
                    .addOnSuccessListener { userSnap ->
                        val cash = userSnap.getDouble("cash") ?: 0.0
                        val equity = cash + positionsValue
                        userRef.update(
                            mapOf(
                                "equity" to equity,
                                "lastEquityAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener { onDone() }
                            .addOnFailureListener { e -> onErr(e.message ?: "Update failed") }
                    }
                    .addOnFailureListener { e -> onErr(e.message ?: "Load user failed") }
            }
            .addOnFailureListener { e -> onErr(e.message ?: "Load positions failed") }
    }
}
