package com.capstone.navicamp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.ActionBarDrawerToggle
import android.widget.TextView
import androidx.compose.runtime.Composable
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

val supabase = createSupabaseClient(
    supabaseUrl = "https://dulyrxwwhvwaqvqfepvw.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR1bHlyeHd3aHZ3YXF2cWZlcHZ3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzY5MTY5MTEsImV4cCI6MjA1MjQ5MjkxMX0.3qEMlGM2Oj3gHKEUkaSG-WaY39yVWW6KYz7rV_XjIsk"
) {
    install(Postgrest)
}

class SecurityOfficerActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var registeredLDUserCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_officer)

        // Set up the Toolbar as the Action Bar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up the DrawerLayout and ActionBarDrawerToggle
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        registeredLDUserCount = findViewById(R.id.registeredLDUserCount)

        fetchRegisteredUserCount()

    }

    @Serializable
    data class Monitoring (
        val userID: Int,
        val userType: String
    )

    private fun fetchRegisteredUserCount() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Query the user table to count rows with user_type "student", "personnel", or "visitor"
                val response = supabase.postgrest
                    .from("user_table")
                    .select(columns = Columns.list("id, name"))
                    .or("userType.eq.Student, userType.eq.Personnel, userType.eq.Visitor") // Use correct "or" syntax
                    .count("exact") // Use the correct method to get exact row count
                    .execute()

                if (response.status == 200 && response.count != null) {
                    val count = response.count

                    withContext(Dispatchers.Main) {
                        // Update the TextView with the count
                        registeredLDUserCount.text = count.toString()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        registeredLDUserCount.text = "Error: ${response.error?.message ?: "Unknown error"}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    registeredLDUserCount.text = "Error"
                }
                e.printStackTrace()
            }
        }
    }
}