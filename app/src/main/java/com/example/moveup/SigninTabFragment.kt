package com.example.moveup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.moveup.databinding.SigninTabFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import splitties.toast.toast

class SigninTabFragment: Fragment() {

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
  =======                    Funktion                   =======
  =============================================================

  In diesem Fragment kann der User sich mit Email und Passwort registrieren
*/

/*
  =============================================================
  =======                   Variablen                   =======
  =============================================================
*/

    private var _binding: SigninTabFragmentBinding? = null
    private val binding get() = _binding!!

    // === Datenbank === //
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var mAuthListener: FirebaseAuth.AuthStateListener


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
        _binding = SigninTabFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // --- E-mail wird an Benutzer geschickt zur Verifizierung seiner Mail --- //
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

        // --- E-Mail und Passwort werden eingelesen und an die register-Funktion übergeben --- //
        binding.buttonRegister.setOnClickListener {
            var email : String
            var password : String

            email = binding.EditViewEmail.text.toString()
            password = binding.EditViewPassword.text.toString()
            register(email, password)
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
            binding.EditViewEmail.error = getString(R.string.required)
            valid = false
        }

        if (password.isEmpty()) {
            binding.EditViewPassword.error = getString(R.string.required)
            valid = false
        }

        return valid
    }

    // === register === //
    private fun register(email: String, password: String) {

        // Überprüfen, ob E-mail und Passwort eingegeben wurden
        if (!validateForm(email, password)) {
            return
        }

        // User wird neu angelegt in der Datenbank
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