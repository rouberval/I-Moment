package com.example.moment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _name = MutableLiveData<String>()
    val name: LiveData<String> = _name

    private val _age = MutableLiveData<String>() // Stored as String for EditText binding
    val age: LiveData<String> = _age

    private val _interests = MutableLiveData<String>()
    val interests: LiveData<String> = _interests

    init {
        loadProfile()
    }

    private fun loadProfile() {
        _name.value = sharedPreferences.getString(KEY_NAME, "") ?: ""
        _age.value = sharedPreferences.getString(KEY_AGE, "") ?: ""
        _interests.value = sharedPreferences.getString(KEY_INTERESTS, "") ?: ""
    }

    fun saveProfile(name: String, age: String, interests: String) {
        sharedPreferences.edit()
            .putString(KEY_NAME, name)
            .putString(KEY_AGE, age)
            .putString(KEY_INTERESTS, interests)
            .apply()
        // Update LiveData after saving
        _name.value = name
        _age.value = age
        _interests.value = interests
    }

    companion object {
        const val KEY_NAME = "profile_name"
        const val KEY_AGE = "profile_age"
        const val KEY_INTERESTS = "profile_interests"
    }
}
