package com.sakovich.collegeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sakovich.collegeapp.presentation.auth.LoginFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Загружаем LoginFragment как стартовый экран
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
        }
    }
}