package com.capstone.navicamp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientSingleton {
    val supabase = createSupabaseClient(
        supabaseUrl = "https://dulyrxwwhvwaqvqfepvw.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR1bHlyeHd3aHZ3YXF2cWZlcHZ3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzY5MTY5MTEsImV4cCI6MjA1MjQ5MjkxMX0.3qEMlGM2Oj3gHKEUkaSG-WaY39yVWW6KYz7rV_XjIsk"
    ) {
        install(Postgrest)
    }
}