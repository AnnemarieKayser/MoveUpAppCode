package com.example.moveup

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.SigninTabFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import splitties.toast.toast

class SigninTabFragment: Fragment() {

    private var _binding: SigninTabFragmentBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var mAuthListener: FirebaseAuth.AuthStateListener



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = SigninTabFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        mAuthListener = FirebaseAuth.AuthStateListener {
            val user = mFirebaseAuth.currentUser

            if (user != null) {
                user.sendEmailVerification()
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            FirebaseAuth.getInstance().signOut()
                            toast(R.string.verify_mail)
                        } else {
                            toast(it.exception!!.message.toString())
                        }
                    }
            }
        }

        binding.buttonRegister.setOnClickListener {
            var email : String
            var password : String

            email = binding.EditViewEmail.text.toString()
            password = binding.EditViewPassword.text.toString()
            register(email, password)
        }

    }

    private fun validateForm(email: String, password: String): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.EditViewEmail.error = getString(R.string.required)
            valid = false
        }

        if (password.isEmpty()) {
            binding.EditViewPassword.error = getString(R.string.required)
            valid = false
        }


        return valid
    }

    private fun register(email: String, password: String) {

        if (!validateForm(email, password)) {
            return
        }

        getActivity()?.let {
            mFirebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(it) { task ->
                    if (task.isSuccessful) {
                        mAuthListener.onAuthStateChanged(mFirebaseAuth)
                    } else {
                        toast(task.exception!!.message.toString())
                    }
                }
        }
    }

}