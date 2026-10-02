package dev.yoanndev90.statusbarhider.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors =
	lightColorScheme(
		primary = Blue80,
		secondary = BlueGrey40
	)

private val DarkColors =
	darkColorScheme(
		primary = Blue40,
		secondary = BlueGrey80
	)

@Composable
fun StatusBarHiderTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColors else LightColors,
		typography = AppTypography,
		content = content
	)
}
