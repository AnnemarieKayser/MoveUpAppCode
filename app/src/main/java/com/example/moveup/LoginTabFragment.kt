package com.example.moveup

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.LoginTabFragmentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

import splitties.toast.toast

class LoginTabFragment: Fragment() {

    private var _binding: LoginTabFragmentBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = LoginTabFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.email.translationX = 800F
        binding.password.translationX = 800F
        binding.buttonLogin.translationX = 800F
        binding.textViewPasswortVergessen.translationX = 800F

        binding.email.alpha = 0F
        binding.password.alpha = 0F
        binding.buttonLogin.alpha =0F
        binding.textViewPasswortVergessen.alpha = 0F

        binding.email.animate().translationX(0F).alpha(1F).setDuration(800).setStartDelay(300).start()
        binding.password.animate().translationX(0F).alpha(1F).setDuration(800).setStartDelay(500).start()
        binding.buttonLogin.animate().translationX(0F).alpha(1F).setDuration(800).setStartDelay(500).start()
        binding.textViewPasswortVergessen.animate().translationX(0F).alpha(1F).setDuration(800).setStartDelay(700).start()


        binding.buttonLogin.setOnClickListener {
            var email : String
            var password : String

            email = binding.email.text.toString()
            password = binding.password.text.toString()
            signIn(email, password)
        }

        binding.textViewPasswortVergessen.setOnClickListener {
            sendResetPw()
        }


        mRunnable = Runnable {
            if (mFirebaseAuth.currentUser == null) {
                //txtLogStatus.text = getString(R.string.logged_out)
            } else {
                //txtLogStatus.text = getString(R.string.logged_in)
                val intent = Intent (getActivity(), MainActivity::class.java)
                getActivity()?.startActivity(intent)
            }
        }
        mHandler.postDelayed(mRunnable, 2000)
    }

    private fun validateForm(email: String, password: String): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.email.error = getString(R.string.required)
            valid = false
        }

        if (password.isEmpty()) {
            binding.password.error = getString(R.string.required)
            valid = false
        }

        return valid
    }

    private fun signIn(email: String, password: String) {

        if (!validateForm(email, password)) {
            return
        }

        mFirebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    if (mFirebaseAuth.currentUser!!.isEmailVerified) {
                        toast(R.string.login_success)

                        val intent = Intent (getActivity(), MainActivity::class.java)
                        getActivity()?.startActivity(intent)
                    } else {
                        toast(R.string.reminder_verify)
                    }
                } else {
                    toast(it.exception!!.message.toString())
                }
            }
    }

    private fun sendResetPw() {

        val editTextView = EditText(activity)
        editTextView.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(resources.getString(R.string.forgotPw_title))
                .setView(editTextView)
                .setMessage(getString(R.string.forgotPw_msg))

                .setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->
                    val mail = editTextView.text.toString().trim()
                    if (mail.isEmpty()) {
                        toast(R.string.fill_out)
                        //it.dismiss()
                    } else {
                        sendMail(mail)
                    }
                }

                .setNegativeButton(getString(R.string.button_cancel)) { dialog, which ->

                }
                .show()
        }
    }

    private fun sendMail(mail: String) {
        mFirebaseAuth.sendPasswordResetEmail(mail)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    toast(R.string.reset_pw)
                } else {
                    toast(it.exception!!.message.toString())
                }
            }
    }

}