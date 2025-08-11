package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be before super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Init Firebase
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Anonymous sign-in then proceed
        auth.signInAnonymously()
            .addOnSuccessListener { goNext() }
            .addOnFailureListener {
                // retry once, then give up
                auth.signInAnonymously()
                    .addOnSuccessListener { goNext() }
                    .addOnFailureListener { finish() }
            }
    }

    private fun goNext() {
        val uid = auth.currentUser?.uid ?: run { finish(); return }
        val userRef = db.collection("users").document(uid)

        userRef.get()
            .addOnSuccessListener { snap ->
                val username = snap.getString("username")
                if (!username.isNullOrBlank()) {
                    goMain()
                } else {
                    if (!snap.exists()) {
                        // First launch: create base user doc
                        userRef.set(
                            mapOf(
                                "cash" to 100_000.0,
                                "equity" to 100_000.0,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "lastEquityAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener { goUsername() }
                            .addOnFailureListener { goUsername() } // still allow username flow
                    } else {
                        goUsername()
                    }
                }
            }
            .addOnFailureListener { goUsername() }
    }

    private fun goUsername() {
        startActivity(Intent(this, UsernameActivity::class.java))
        finish()
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
