package com.example.moment

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.moment.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        profileViewModel.name.observe(this) { name ->
            binding.editTextName.setText(name)
        }
        profileViewModel.age.observe(this) { age ->
            binding.editTextAge.setText(age)
        }
        profileViewModel.interests.observe(this) { interests ->
            binding.editTextInterests.setText(interests)
        }
    }

    private fun setupClickListeners() {
        binding.buttonSaveProfile.setOnClickListener {
            val name = binding.editTextName.text.toString().trim()
            val age = binding.editTextAge.text.toString().trim()
            val interests = binding.editTextInterests.text.toString().trim()

            if (name.isNotEmpty() && age.isNotEmpty()) {
                profileViewModel.saveProfile(name, age, interests)
                Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                finish() // Close activity after saving
            } else {
                Toast.makeText(this, "Name and Age cannot be empty.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
