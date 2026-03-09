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
    private var skipNavListener = false

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

        // Set initial fragment. If launched with map extras (e.g., from notification),
        // open map home focused on that assistance request.
        if (savedInstanceState == null) {
            loadFragment(buildInitialFragmentFromIntent(intent))
        }

        syncUserRoleFromDatabase(userID)

        bottomNav.setOnItemSelectedListener { item ->
            if (skipNavListener) {
                skipNavListener = false
                return@setOnItemSelectedListener true
            }
            val transaction = supportFragmentManager.beginTransaction()

            when (item.itemId) {
                R.id.nav_officer_map_home -> {
                    transaction.replace(R.id.officer_fragment_container, MapHomeFragment())
                }
                R.id.nav_officer_monitoring -> {
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

    private fun buildInitialFragmentFromIntent(launchIntent: Intent?): Fragment {
        val locationID = launchIntent?.getStringExtra("LOCATION_ID")
        val latitude = launchIntent?.extras?.get("LATITUDE") as? Number
        val longitude = launchIntent?.extras?.get("LONGITUDE") as? Number
        val fullName = launchIntent?.getStringExtra("FULL_NAME")
        val floorLevel = launchIntent?.getStringExtra("FLOOR_LEVEL")
        val status = launchIntent?.getStringExtra("STATUS")

        val hasMapContext = !locationID.isNullOrBlank() || (latitude != null && longitude != null)
        if (!hasMapContext) {
            return MapHomeFragment()
        }

        return MapHomeFragment.newInstance(
            locationID = locationID,
            latitude = latitude?.toDouble(),
            longitude = longitude?.toDouble(),
            fullName = fullName,
            floorLevel = floorLevel,
            status = status
        )
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
        if (bottomNav.selectedItemId != R.id.nav_officer_map_home) {
            // If they are NOT on Home, go to Home
            bottomNav.selectedItemId = R.id.nav_officer_map_home
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

    /**
     * Navigate to the map home tab with a specific MapHomeFragment instance.
     * Called from child fragments (e.g., assist card respond button).
     */
    fun navigateToMapHome(mapFragment: MapHomeFragment) {
        loadFragment(mapFragment)
        skipNavListener = true
        bottomNav.selectedItemId = R.id.nav_officer_map_home
    }
}
