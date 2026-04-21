package com.aegisgatekeeper.app.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RuleCombinator { ANY, ALL }

enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

enum class ContentSource { YOUTUBE, SOUNDCLOUD, SUBSTACK, GENERIC }

enum class ContentType { VIDEO, AUDIO, READING }

enum class FrictionGame { GAUNTLET, HOLD_STEADY }

enum class Emotion { HAPPY, ANXIOUS, DRAINED, SKIPPED }

// 1. Color Palette
private val Charcoal = Color(0xFF121212)
private val SafetyOrange = Color(0xFFFF9800)
private val TerminalGreen = Color(0xFF4AF626)
private val DarkCharcoal = Color(0xFF1A1A1A)
private val OffWhite = Color(0xFFE0E0E0)
private val MidGray = Color(0xFF888888)

private val IndustrialColorScheme =
    darkColorScheme(
        primary = TerminalGreen,
        secondary = SafetyOrange,
        background = Charcoal,
        surface = DarkCharcoal,
        onPrimary = Charcoal,
        onSecondary = Charcoal,
        onBackground = OffWhite,
        onSurface = OffWhite,
        onSurfaceVariant = MidGray,
        error = Color(0xFFCF6679),
        onError = Color.Black,
    )

// 2. Typography
private val IndustrialTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 57.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default, // Keep body text readable
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
    )

// 3. Shapes
private val IndustrialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(4.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )

// 4. Master Theme Composable
@Suppress("FunctionName")
@Composable
fun GatekeeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialColorScheme,
        typography = IndustrialTypography,
        shapes = IndustrialShapes,
        content = content,
    )
}

private fun SemanticsPropertyReceiver.setOriginalText(value: String) {
    this.text = AnnotatedString(value)
}

@Suppress("FunctionName")
@Composable
fun IndustrialButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    isWarning: Boolean = false,
    enabled: Boolean = true,
    invertEnabledColor: Boolean = false,
) {
    val backgroundColor =
        when {
            isWarning -> MaterialTheme.colorScheme.secondary
            invertEnabledColor -> if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
            !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.primary
        }
    val contentColor =
        when {
            isWarning -> MaterialTheme.colorScheme.onSecondary
            invertEnabledColor -> if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.onPrimary
        }
    val borderColor = if (enabled) backgroundColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    val buttonText = text
    Box(
        modifier =
            modifier
                .shadow(elevation = 4.dp, shape = MaterialTheme.shapes.small)
                .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
                .background(color = backgroundColor, shape = MaterialTheme.shapes.small)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buttonText.uppercase(),
            modifier =
                Modifier.clearAndSetSemantics {
                    setOriginalText(buttonText)
                },
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Suppress("FunctionName")
@Composable
fun IndustrialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors =
            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
    )
}
