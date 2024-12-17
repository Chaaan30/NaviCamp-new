package com.capstone.navicamp

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val showRegisterButton = findViewById<Button>(R.id.show_register_button)
        showRegisterButton.setOnClickListener {
            val registerBottomSheet = RegisterBottomSheet()
            registerBottomSheet.show(supportFragmentManager, "RegisterBottomSheet")
        }

        val showLoginButton = findViewById<Button>(R.id.show_login_button)
        showLoginButton.setOnClickListener {
            val loginBottomSheet = LoginBottomSheet()
            loginBottomSheet.show(supportFragmentManager, "LoginBottomSheet")
        }
    }
}