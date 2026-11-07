package haddad.three.notas.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import haddad.three.notas.R
import haddad.three.notas.ui.common.AppBottomSheet
import haddad.three.notas.ui.components.RememberTextButton

private val PinInputTransformation =
    InputTransformation {
        val filtered = asCharSequence().filter(Char::isDigit).take(6).toString()
        if (filtered != asCharSequence().toString()) {
            replace(0, length, filtered)
        }
    }

@Composable
private fun rememberPinTextFieldState(
    pin: String,
    onPinChange: (String) -> Unit,
): TextFieldState {
    val textFieldState = rememberTextFieldState(initialText = pin)
    LaunchedEffect(pin) {
        val currentPin = textFieldState.text.toString()
        if (currentPin != pin) {
            textFieldState.edit {
                replace(0, length, pin)
                selection = TextRange(pin.length)
            }
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { updatedPin ->
                if (updatedPin != pin) {
                    onPinChange(updatedPin)
                }
            }
    }
    return textFieldState
}

@Composable
fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val pinState =
        rememberPinTextFieldState(
            pin = pin,
            onPinChange = { updatedPin ->
                if (updatedPin.length <= 6) {
                    pin = updatedPin
                }
            },
        )
    val confirmState =
        rememberPinTextFieldState(
            pin = confirm,
            onPinChange = { updatedPin ->
                if (updatedPin.length <= 6) {
                    confirm = updatedPin
                }
            },
        )
    val mismatch = pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm
    val ready = pin.length in 4..6 && pin == confirm

    AppBottomSheet(
        title = stringResource(R.string.pin_setup_title),
        subtitle = stringResource(R.string.pin_setup_subtitle),
        onDismiss = onDismiss,
        actionsImePadding = true,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            RememberTextButton(enabled = ready, onClick = { onConfirm(pin) }) { Text(stringResource(R.string.common_save)) }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedSecureTextField(
                state = pinState,
                label = { Text(stringResource(R.string.pin_label)) },
                inputTransformation = PinInputTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedSecureTextField(
                state = confirmState,
                label = { Text(stringResource(R.string.pin_confirm_label)) },
                isError = mismatch,
                inputTransformation = PinInputTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (mismatch) {
                Text(
                    "PINs don't match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
