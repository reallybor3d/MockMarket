package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.ActivityUsernameBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction

class UsernameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsernameBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsernameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConfirm.setOnClickListener {
            val raw = binding.etUsername.text.toString().trim()
            val key = raw.lowercase()

            if (raw.length < 3) {
                Toast.makeText(this, "Username too short", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Optional: enforce simple charset
            if (!key.matches(Regex("^[a-z0-9_]{3,30}$"))) {
                Toast.makeText(this, "Use a–z, 0–9, _ (3–30 chars)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            claimUsername(raw, key)
        }
    }

    private fun claimUsername(displayName: String, keyLower: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid
        val unameRef = db.collection("usernames").document(keyLower)
        val userRef  = db.collection("users").document(uid)

        db.runTransaction { tx: Transaction ->
            // Fail if taken
            if (tx.get(unameRef).exists()) throw IllegalStateException("USERNAME_TAKEN")

            // Reserve activity_username.xml -> uid
            tx.set(unameRef, mapOf("uid" to uid))

            // Set on profile (store lowercase, or your original string—your call)
            tx.update(userRef, mapOf("username" to displayName))
            null
        }
            .addOnSuccessListener {
                Toast.makeText(this, "Username set!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                val msg = if (e.message?.contains("USERNAME_TAKEN") == true) {
                    "Username is taken"
                } else {
                    e.message ?: "Failed to set username"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }
}
