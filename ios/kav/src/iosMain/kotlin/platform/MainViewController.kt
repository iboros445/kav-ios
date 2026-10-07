package uk.noammm.kav.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// Entry point called from Swift (KavApp.swift). Phase 0 shows a placeholder; Phase 2 replaces the
// body with the upstream MainActivity wrapper (see ios/MIGRATION_PLAN.md).
fun MainViewController(): UIViewController = ComposeUIViewController {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Kav for iPhone")
    }
}
