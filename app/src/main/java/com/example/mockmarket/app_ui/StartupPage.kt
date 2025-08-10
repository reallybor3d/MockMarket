package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.StartupBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StartupPage : AppCompatActivity() {

    private lateinit var binding: StartupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StartupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // firebase is on
        FirebaseApp.initializeApp(this)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnLogin.setOnClickListener {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user!!.uid
                    val userRef = db.collection("users").document(uid)
                    userRef.get().addOnSuccessListener { snap ->
                        if (!snap.exists()) {
                            userRef.set(
                                mapOf(
                                    "cash" to 100_000.0,
                                    "equity" to 100_000.0,
                                    "username" to null,
                                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                    "lastEquityAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                            ).addOnSuccessListener { goNext() }
                        } else {
                            goNext()
                        }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Read failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Auth failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        binding.btnCreateAccount.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { goToUsernameScreen() }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Auth failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                goToUsernameScreen()
            }
        }
    }

    private fun goNext() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val uname = snap.get("username") as? String
                val hasUsername = !uname.isNullOrBlank()
                if (hasUsername) {
                    startActivity(Intent(this, DashboardActivity::class.java))
                } else {
                    goToUsernameScreen()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun goToUsernameScreen() {
        startActivity(Intent(this, UsernameActivity::class.java))
    }
}
