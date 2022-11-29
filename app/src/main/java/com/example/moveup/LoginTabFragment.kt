package com.example.moveup

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.LoginTabFragmentBinding

class LoginTabFragment: Fragment() {

    private var _binding: LoginTabFragmentBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

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

            val intent = Intent (getActivity(), MainActivity::class.java)
            getActivity()?.startActivity(intent)
        }
    }

}