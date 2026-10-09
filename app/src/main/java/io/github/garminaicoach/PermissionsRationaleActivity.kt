package io.github.garminaicoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import io.github.garminaicoach.ui.CoachTheme
import io.github.garminaicoach.ui.PrivacyScreen

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CoachTheme { Box(Modifier.safeDrawingPadding()) { PrivacyScreen(onClose = ::finish) } } }
    }
}
