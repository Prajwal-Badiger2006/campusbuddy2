package com.example.campusbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.navigation.CampusBuddyNavHost
import com.example.campusbuddy.ui.theme.CampusbuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repository = remember { CampusBuddyRepository() }

            CampusbuddyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CampusBuddyNavHost(repository = repository)
                }
            }
        }
    }
}
