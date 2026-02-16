package com.capstone.navicamp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class SetupActivity : AppCompatActivity() {
    
    lateinit var viewPager: ViewPager2
    private lateinit var lineIndicatorLayout: LinearLayout
    private lateinit var btnBack: ImageButton
    
    private val fragments = listOf(
        WelcomeFragment(),
        InternetConnectionFragment(),
        NotificationPermissionFragment(),
        BatteryOptimizationFragment(),
        SetupCompleteFragment()
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        
        initViews()
        setupViewPager()
        setupNavigation()
    }
    
    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        lineIndicatorLayout = findViewById(R.id.lineIndicatorLayout)
        btnBack = findViewById(R.id.btnBack)
    }
    
    private fun setupViewPager() {
        val adapter = SetupPagerAdapter(this)
        viewPager.adapter = adapter
        
        addIndicators()
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                updateIndicators(position)
            }
        })
        
        // Set initial state
        btnBack.visibility = View.INVISIBLE
        updateIndicators(0)
    }
    
    private fun addIndicators() {
        val indicators = arrayOfNulls<ImageView>(fragments.size)
        val layoutParams = LinearLayout.LayoutParams(
            (32 * resources.displayMetrics.density).toInt(), // width
            (4 * resources.displayMetrics.density).toInt()  // height
        )
        layoutParams.setMargins(
            (4 * resources.displayMetrics.density).toInt(), 0,
            (4 * resources.displayMetrics.density).toInt(), 0
        )
        
        for (i in indicators.indices) {
            indicators[i] = ImageView(this)
            indicators[i]?.layoutParams = layoutParams
            lineIndicatorLayout.addView(indicators[i])
        }
    }
    
    private fun updateIndicators(position: Int) {
        for (i in 0 until lineIndicatorLayout.childCount) {
            val indicator = lineIndicatorLayout.getChildAt(i) as ImageView
            if (i == position) {
                indicator.setImageResource(R.drawable.line_indicator_active)
            } else {
                indicator.setImageResource(R.drawable.line_indicator_inactive)
            }
        }
    }
    
    private fun setupNavigation() {
        btnBack.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem = viewPager.currentItem - 1
            }
        }
    }
    
    fun moveToNextPage() {
        if (viewPager.currentItem < fragments.size - 1) {
            viewPager.currentItem = viewPager.currentItem + 1
        }
    }
    
    fun finishSetup() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("setup_completed", true).apply()
        
        // Check if we should return to main activity (from device setup button)
        val returnToMain = intent.getBooleanExtra("RETURN_TO_MAIN", false)
        
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isLoggedIn = userPrefs.getBoolean("isLoggedIn", false)
        val systemRole = userPrefs.getString("systemRole", null)
        val userType = userPrefs.getString("userType", null)
        
        val intent = if (isLoggedIn && userType != null) {
            val normalizedRole = systemRole?.trim()?.lowercase()
            when {
                normalizedRole?.contains("safety") == true ||
                    normalizedRole?.contains("security") == true ||
                    normalizedRole?.contains("officer") == true ->
                    Intent(this, SecurityOfficerActivity::class.java)
                normalizedRole?.contains("disabled") == true ->
                    Intent(this, LocomotorDisabilityActivity::class.java)
                else -> when (userType) {
                    "Safety Officer", "Security Officer" -> Intent(this, SecurityOfficerActivity::class.java)
                    "Temporarily Disabled", "Permanently Disabled" -> Intent(this, LocomotorDisabilityActivity::class.java)
                    else -> Intent(this, MainActivity::class.java)
                }
            }
        } else {
            Intent(this, MainActivity::class.java)
        }
        
        // If returning to main, clear the activity stack to prevent going back to setup
        if (returnToMain) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        startActivity(intent)
        finish()
    }
    
    private inner class SetupPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
    }
} 