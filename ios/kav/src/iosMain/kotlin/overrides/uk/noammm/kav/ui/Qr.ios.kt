//@cmp
// iOS override of android/app/src/main/java/uk/noammm/kav/ui/Qr.kt @ ede72bd. Keep public API identical.
//
// Paying for rides needs a camera QR reader (AVFoundation) and a QR encoder for the ticket. Both come with the payments
// phase (ios/MIGRATION_PLAN.md, Phase 6); until then these show a plain notice in the same space.
package uk.noammm.kav.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun QrScanner(modifier: Modifier, onFail: () -> Unit = {}, onCode: (String) -> Unit) {
    Box(modifier.background(K.surface1), contentAlignment = Alignment.Center) {
        Text(
            T("Scanning the bus's QR code isn't available on iPhone yet.", "סריקת קוד ה-QR באוטובוס עדיין לא זמינה באייפון."),
            fontSize = 13.sp, color = K.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun QrCode(text: String, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f).background(Color.White), contentAlignment = Alignment.Center) {
        Text(
            T("The ticket's QR code isn't available on iPhone yet.", "קוד ה-QR של הכרטיס עדיין לא זמין באייפון."),
            fontSize = 13.sp, color = Color.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
        )
    }
}
