package com.appalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appalarm.ui.AppRoot
import com.appalarm.ui.theme.AppAlarmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppAlarmTheme {
                AppRoot()
            }
        }
    }
}
