package com.example.mockmarket.app_ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mockmarket.R
import com.example.mockmarket.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        binding.btnSignOut.setOnClickListener { signOut() }
        binding.btnClearDemo.setOnClickListener { clearDemoData() }
    }

    private fun signOut() {
        auth.signOut()
        startActivity(Intent(requireContext(), SplashActivity::class.java))
        requireActivity().finish()
    }

    private fun clearDemoData() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            toast("Not signed in")
            return
        }
        setLoading(true)

        val userRef = db.collection("users").document(uid)
        val holdingsRef = userRef.collection("holdings")
        val txRef = userRef.collection("transactions")

        // Read cash
        userRef.get().addOnSuccessListener { userSnap ->
            val cash = userSnap.getDouble("cash") ?: 0.0

            // Fetch holdings
            holdingsRef.get().continueWithTask { holdingsTask ->
                val batch = db.batch()
                holdingsTask.result?.documents?.forEach { batch.delete(it.reference) }

                // Wipe previous transactions
                txRef.get().addOnSuccessListener { txQs ->
                    txQs.documents.forEach { batch.delete(it.reference) }

                    // Reset EVERYTHING
                    batch.update(userRef, mapOf(
                        "equity" to 0.0,
                        "score" to cash,
                        "lastEquityAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ))

                    batch.commit()
                        .addOnSuccessListener {
                            toast("Cleared holdings and reset equity.")
                            setLoading(false)
                        }
                        .addOnFailureListener {
                            toast("Failed to clear: ${it.message}")
                            setLoading(false)
                        }
                }.addOnFailureListener {
                    toast("Failed to read transactions: ${it.message}")
                    setLoading(false)
                }
            }.addOnFailureListener {
                toast("Failed to read holdings: ${it.message}")
                setLoading(false)
            }

        }.addOnFailureListener {
            toast("Failed to read user: ${it.message}")
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignOut.isEnabled = !loading
        binding.btnClearDemo.isEnabled = !loading
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}