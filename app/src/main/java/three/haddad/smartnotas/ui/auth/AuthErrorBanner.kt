package three.haddad.smartnotas.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.theme.NotasTypography

/**
 * Modern, clean, and elegant error banner for Auth screens.
 * Uses a valid Material Symbols icon ("warning") with a soft tinted background and subtle border.
 */
@Composable
fun AuthErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSemanticPalette.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.error.copy(alpha = 0.08f),
        ),
        border = BorderStroke(1.dp, palette.error.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "warning",
                size = 20.dp,
                tint = palette.error,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = NotasTypography.BodyMedium,
                color = palette.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
