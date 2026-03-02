package com.capstone.navicamp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityOfficerActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var prefs: android.content.SharedPreferences
    private var userID: String = ""
    private var currentPosition: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        // 2. Setup Bottom Navigation
        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        userID = prefs.getString("userID", "") ?: ""

        if (userID.isEmpty()) {
            // If no one is logged in, redirect to LoginActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_security_officer)
        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        currentPosition = normalizeRole(prefs.getString("systemRole", ""))
        setupBottomNavVisibility(currentPosition)

        // set default fragment
        if (savedInstanceState == null) {
            loadFragment(OfficerHomeFragment())
        }

        syncUserRoleFromDatabase(userID)

        bottomNav.setOnItemSelectedListener { item ->
            val transaction = supportFragmentManager.beginTransaction()

            when (item.itemId) {
                R.id.nav_officer_home -> {
                    transaction.replace(R.id.officer_fragment_container, OfficerHomeFragment())
                }
                R.id.nav_admin_verify_account -> {
                    transaction.replace(R.id.officer_fragment_container, AccountVerificationFragment())
                }
                R.id.nav_officer_incidents -> {
                    transaction.replace(R.id.officer_fragment_container, OfficerIncidentsFragment())
                }
                R.id.nav_officer_settings -> {
                    transaction.replace(R.id.officer_fragment_container, SettingsMenuFragment())
                }
            }
            transaction.commit()
            true
        }
    }

    private fun syncUserRoleFromDatabase(userID: String) {
        if (userID.isEmpty()) return

        lifecycleScope.launch {
            // Fetch from MySQL using the function we made earlier
            val dbProfile = withContext(Dispatchers.IO) {
                MySQLHelper.getOfficerProfile(userID)
            }

            if (dbProfile != null) {
                val realPositionFromDB = normalizeRole(dbProfile["systemRole"])

                // If the role in DB is different than what Login saved/Activity has:
                if (realPositionFromDB != currentPosition) {
                    currentPosition = realPositionFromDB

                    // Update SharedPreferences locally so we remember this for next time
                    prefs.edit().putString("systemRole", realPositionFromDB).apply()

                    // Update the Bottom Nav instantly
                    withContext(Dispatchers.Main) {
                        setupBottomNavVisibility(realPositionFromDB)
                    }
                }
            }
        }
    }

    private fun setupBottomNavVisibility(position: String?) {
        val verifyItem = bottomNav.menu.findItem(R.id.nav_admin_verify_account)
        verifyItem?.isVisible = normalizeRole(position).contains("admin")
    }

    private fun normalizeRole(role: String?): String {
        return role?.trim()?.lowercase().orEmpty()
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            val cachedRole = normalizeRole(prefs.getString("systemRole", ""))
            if (cachedRole != currentPosition) {
                currentPosition = cachedRole
                setupBottomNavVisibility(currentPosition)
            }
        }
        if (userID.isNotBlank()) {
            syncUserRoleFromDatabase(userID)
        }
    }

    override fun onBackPressed() {
        if (bottomNav.selectedItemId != R.id.nav_officer_home) {
            // If they are NOT on Home, go to Home
            bottomNav.selectedItemId = R.id.nav_officer_home
        } else {
            // If they ARE on Home, close the app as usual
            super.onBackPressed()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.officer_fragment_container, fragment) // Use ONE container
            .commit()
    }
}
