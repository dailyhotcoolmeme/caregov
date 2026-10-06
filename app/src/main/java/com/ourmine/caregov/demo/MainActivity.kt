package com.ourmine.caregov.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ourmine.caregov.demo.updates.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updater: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = DemoSession(this)
        setContent {
            CaregovTheme {
                CaregovApp(session, updater)
            }
        }
    }
}
