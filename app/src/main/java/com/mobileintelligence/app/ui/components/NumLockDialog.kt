package com.mobileintelligence.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Modes for the NumLock dialog.
 */
enum class NumLockMode {
    /** User sets a new PIN (enter + confirm). */
    SET_PIN,
    /** User verifies existing PIN to proceed with a protected action. */
    VERIFY_PIN,
    /** User verifies old PIN, then enters + confirms new PIN. */
    CHANGE_PIN
}

/**
 * A full-screen numeric keypad PIN dialog.
 *
 * @param mode SET_PIN, VERIFY_PIN, or CHANGE_PIN
 * @param title Dialog title text
 * @param onDismiss Called when user cancels
 * @param onPinVerified Called with the PIN when verification/setup succeeds (caller must persist)
 * @param verifyPin Suspend function that checks a PIN against stored hash (for VERIFY/CHANGE modes)
 * @param minPinLength Minimum PIN length (default 4)
 * @param maxPinLength Maximum PIN length (default 8)
 */
@Composable
fun NumLockDialog(
    mode: NumLockMode,
    title: String = when (mode) {
        NumLockMode.SET_PIN -> "Set NumLock PIN"
        NumLockMode.VERIFY_PIN -> "Enter PIN"
        NumLockMode.CHANGE_PIN -> "Change PIN"
    },
    onDismiss: () -> Unit,
    onPinVerified: (String) -> Unit,
    verifyPin: (suspend (String) -> Boolean)? = null,
    minPinLength: Int = 4,
    maxPinLength: Int = 8
) {
    // State machine phases
    var phase by remember {
        mutableStateOf(
            when (mode) {
                NumLockMode.SET_PIN -> PinPhase.ENTER_NEW
                NumLockMode.VERIFY_PIN -> PinPhase.VERIFY
                NumLockMode.CHANGE_PIN -> PinPhase.VERIFY
            }
        )
    }

    var currentPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") } // stored during SET/CHANGE confirm step
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    val subtitle = when (phase) {
        PinPhase.VERIFY -> "Enter your current PIN"
        PinPhase.ENTER_NEW -> if (mode == NumLockMode.CHANGE_PIN) "Enter new PIN" else "Enter a numeric PIN"
        PinPhase.CONFIRM_NEW -> "Confirm your PIN"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // PIN dots
                PinDots(
                    length = currentPin.length,
                    maxLength = maxPinLength,
                    hasError = errorMessage != null
                )

                // Error message
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Numeric keypad
                NumericKeypad(
                    onDigit = { digit ->
                        if (currentPin.length < maxPinLength) {
                            currentPin += digit
                            errorMessage = null
                        }
                    },
                    onBackspace = {
                        if (currentPin.isNotEmpty()) {
                            currentPin = currentPin.dropLast(1)
                            errorMessage = null
                        }
                    },
                    onConfirm = {
                        if (currentPin.length < minPinLength) {
                            errorMessage = "PIN must be at least $minPinLength digits"
                            return@NumericKeypad
                        }
                        when (phase) {
                            PinPhase.VERIFY -> {
                                isVerifying = true
                            }
                            PinPhase.ENTER_NEW -> {
                                firstPin = currentPin
                                currentPin = ""
                                phase = PinPhase.CONFIRM_NEW
                            }
                            PinPhase.CONFIRM_NEW -> {
                                if (currentPin == firstPin) {
                                    onPinVerified(currentPin)
                                } else {
                                    errorMessage = "PINs do not match"
                                    currentPin = ""
                                    firstPin = ""
                                    phase = PinPhase.ENTER_NEW
                                }
                            }
                        }
                    },
                    confirmEnabled = currentPin.length >= minPinLength && !isVerifying
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel button
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }

    // Handle async verification
    if (isVerifying) {
        LaunchedEffect(Unit) {
            val isValid = verifyPin?.invoke(currentPin) ?: false
            if (isValid) {
                if (mode == NumLockMode.CHANGE_PIN) {
                    phase = PinPhase.ENTER_NEW
                    currentPin = ""
                    errorMessage = null
                } else {
                    onPinVerified(currentPin)
                }
            } else {
                errorMessage = "Incorrect PIN"
                currentPin = ""
            }
            isVerifying = false
        }
    }
}

private enum class PinPhase {
    VERIFY,
    ENTER_NEW,
    CONFIRM_NEW
}

// ── PIN Dots ────────────────────────────────────────────────────

@Composable
private fun PinDots(length: Int, maxLength: Int, hasError: Boolean) {
    val dotColor by animateColorAsState(
        targetValue = if (hasError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "dot-color"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { idx ->
            val filled = idx < length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (filled) Modifier.background(dotColor)
                        else Modifier.border(1.5.dp, dotColor, CircleShape)
                    )
            )
        }
    }
}

// ── Numeric Keypad ──────────────────────────────────────────────

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "✓")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        "⌫" -> KeyButton(
                            content = { @Suppress("DEPRECATION") Icon(Icons.Default.Backspace, contentDescription = "Backspace", modifier = Modifier.size(20.dp)) },
                            onClick = onBackspace,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        "✓" -> KeyButton(
                            content = {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (confirmEnabled) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { if (confirmEnabled) onConfirm() },
                            containerColor = if (confirmEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        else -> KeyButton(
                            content = {
                                Text(
                                    key,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = { onDigit(key) },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit,
    containerColor: Color
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}
