package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.StartupBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class StartupPage : AppCompatActivity() {

    private lateinit var binding: StartupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StartupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Auto sign-in on launch
        signInAndProceed()

        // Manual login fallback
        binding.btnLogin.setOnClickListener {
            signInAndProceed()
        }

        binding.btnCreateAccount.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                signIn { goToUsernameScreen() }
            } else {
                goToUsernameScreen()
            }
        }
    }

    private fun signInAndProceed() {
        disableButtons()
        signIn { goNext() }
    }

    private fun signIn(onSuccess: () -> Unit) {
        auth.signInAnonymously()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Auth failed: ${e.message}", Toast.LENGTH_SHORT).show()
                enableButtons()
            }
    }

    private fun goNext() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)

        userRef.get()
            .addOnSuccessListener { snap ->
                val uname = snap.getString("username")
                if (!uname.isNullOrBlank()) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    if (!snap.exists()) {
                        userRef.set(
                            mapOf(
                                "cash" to 100_000.0,
                                "equity" to 100_000.0,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "lastEquityAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener { goToUsernameScreen() }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Setup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                enableButtons()
                            }
                    } else {
                        goToUsernameScreen()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
                enableButtons()
            }
    }

    private fun goToUsernameScreen() {
        startActivity(Intent(this, UsernameActivity::class.java))
        finish()
    }

    private fun disableButtons() {
        binding.btnLogin.isEnabled = false
        binding.btnCreateAccount.isEnabled = false
    }

    private fun enableButtons() {
        binding.btnLogin.isEnabled = true
        binding.btnCreateAccount.isEnabled = true
    }
}

//