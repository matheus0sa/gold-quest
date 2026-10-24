package com.goldquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.goldquest.ui.GoldQuestRoot
import com.goldquest.ui.theme.GoldQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoldQuestTheme {
                GoldQuestRoot()
            }
        }
    }
}
