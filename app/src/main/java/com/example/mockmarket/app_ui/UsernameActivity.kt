package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mockmarket.databinding.ActivityUsernameBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestoreException

class UsernameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsernameBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MM_DBG", "projectId=" + com.google.firebase.FirebaseApp.getInstance().options.projectId)
        android.util.Log.d("MM_DBG", "uid=" + FirebaseAuth.getInstance().currentUser?.uid)
        binding = ActivityUsernameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConfirm.setOnClickListener {
            val raw = binding.etUsername.text.toString().trim()
            val key = raw.lowercase()

            if (raw.length < 3) {
                toast("Username too short"); return@setOnClickListener
            }
            if (!key.matches(Regex("^[a-z0-9_]{3,30}$"))) {
                toast("Use a–z, 0–9, _ (3–30 chars)"); return@setOnClickListener
            }
            claimUsername(raw, key)
        }
    }

    private fun claimUsername(displayName: String, keyLower: String) {
        val user = auth.currentUser ?: run {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show(); return
        }
        val uid = user.uid
        val unameRef = db.collection("usernames").document(keyLower)
        val userRef  = db.collection("users").document(uid)

        binding.btnConfirm.isEnabled = false

        unameRef.get().addOnSuccessListener { s ->
            if (s.exists()) {
                Toast.makeText(this, "Username is taken", Toast.LENGTH_SHORT).show()
                binding.btnConfirm.isEnabled = true
                return@addOnSuccessListener
            }

            unameRef.set(mapOf("uid" to uid))
                .addOnSuccessListener {
                    userRef.set(mapOf("username" to displayName), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Toast.makeText(this, "Username set!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("MM_DBG", "FAIL users/$uid : ${e.message}", e)
                            Toast.makeText(this, "Failed updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                            binding.btnConfirm.isEnabled = true
                        }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MM_DBG", "FAIL usernames/$keyLower : ${e.message}", e)
                    Toast.makeText(this, "Failed reserving username: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnConfirm.isEnabled = true
                }
        }.addOnFailureListener { e ->
            android.util.Log.e("MM_DBG", "FAIL check usernames/$keyLower : ${e.message}", e)
            Toast.makeText(this, "Failed checking username: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.btnConfirm.isEnabled = true
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}