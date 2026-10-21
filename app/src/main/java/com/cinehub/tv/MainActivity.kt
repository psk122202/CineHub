package com.cinehub.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cinehub.tv.ui.AppRoot
import com.cinehub.tv.ui.CineHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CineHubApp).container
        setContent {
            CineHubTheme {
                AppRoot(container)
            }
        }
    }
}
