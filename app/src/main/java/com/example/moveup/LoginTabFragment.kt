package com.example.moveup
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.moveup.databinding.LoginTabFragmentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import splitties.toast.toast

class LoginTabFragment: Fragment() {

/*
   ======================================================================================
   ==========================           Einleitung             ==========================
   ======================================================================================
   Projektname: moveUP
   Autor: Annemarie Kayser
   Anwendung: Tragbares sensorbasiertes Messsystem zur Kontrolle des Sitzverhaltens;
              Ausgabe eines Hinweises, wenn eine krumme Haltung eingenommen oder sich lange Zeit nicht
              bewegt wurde, in Form von Vibration am Rücken. Messung des dynamischen und statischen
              Sitzverhaltens mithilfe von Gyroskopwerten.
   Bauteile: Verwendung des 6-Achsen-Beschleunigungssensors MPU 6050 in Verbindung mit dem Esp32 Thing;
             Datenübertragung zwischen dem Esp32 Thing und der App erfolgt via Bluetooth Low Energy.
             Ein Vibrationsmotor am Rücken gibt den Hinweis auf eine krumme Haltung oder sich zubewegen.
             Die Sensorik wurde in einem kleinen Gehäuse befestigt, welches mit einem Clip am Oberteil befestigt werden kann.
   Letztes Update: 18.02.2023

  ======================================================================================
*/


/*
  =============================================================
  =======                  Funktion                     =======
  =============================================================

  In diesem Fragment kann der User sich mit Email und Passwort einloggen
*/

/*
  =============================================================
  =======                   Variablen                   =======
  =============================================================
*/

    private var _binding: LoginTabFragmentBinding? = null
    private val binding get() = _binding!!

    // === Datenbank === //
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }


/*
  =============================================================
  =======                                               =======
  =======         onCreateView & onViewCreated          =======
  =======                                               =======
  =============================================================
*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = LoginTabFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Animation beim Öffnen des Fragments --- //
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


        // --- E-Mail und Passwort werden eingelesen und an die signIn-Funktion übergeben --- //
        binding.buttonLogin.setOnClickListener {
            val email : String
            val password : String

            email = binding.email.text.toString()
            password = binding.password.text.toString()
            signIn(email, password)
        }

        // --- Passwort zurücksetzen --- //
        binding.textViewPasswortVergessen.setOnClickListener {
            sendResetPw()
        }
    }

/*
  =============================================================
  =======                                               =======
  =======                   Funktionen                  =======
  =======                                               =======
  =============================================================
*/

    // === validateForm === //
    // Überprüfen, ob E-Mail und Passwort eingegeben wurden
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

    // === signIn === //
    private fun signIn(email: String, password: String) {

        // Überprüfen, ob E-mail und Passwort eingegeben wurden
        if (!validateForm(email, password)) {
            return
        }

        mFirebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    // User wird eingeloggt, wenn E-Mail bestätigt wurde
                    if (mFirebaseAuth.currentUser!!.isEmailVerified) {
                        toast(R.string.login_success)
                        // Starten der MainActivity
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

    // === sendResetPw === //
    private fun sendResetPw() {

        // AlertDialog mit Eingabefeld
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

    // === sendMail === //
    // Versenden einer Mail, um das Passwort zurückzusetzen
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