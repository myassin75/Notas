package three.haddad.smartnotas.ui.settings

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.LockPrefs

@Composable
internal fun LockSection(
    lockState: LockPrefs.State,
    lockPrefs: LockPrefs,
    biometricAvailable: Boolean,
    deviceCredentialAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val resources = LocalResources.current
    val isAppLockEnabled = deviceCredentialAvailable || lockState.enabled
    val isBiometricEnabled = biometricAvailable && lockState.enabled

    SettingsSectionCard {
        // App Lock row
        SettingsRowItem(
            materialSymbolName = "lock",
            title = stringResource(R.string.settings_app_lock_title),
            subtitle =
                when {
                    !deviceCredentialAvailable -> stringResource(R.string.settings_app_lock_no_device_lock)
                    lockState.enabled -> stringResource(R.string.settings_app_lock_enabled)
                    else -> stringResource(R.string.settings_app_lock_disabled)
                },
            enabled = isAppLockEnabled,
            onClick = {
                val want = !lockState.enabled
                if (want) {
                    scope.launch {
                        if (deviceCredentialAvailable) {
                            lockPrefs.enableDeviceCredential()
                        } else {
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_app_lock_no_device_lock),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                } else {
                    scope.launch { lockPrefs.disable() }
                }
            },
            trailingContent = {
                SettingsCustomSwitch(
                    checked = lockState.enabled,
                    enabled = isAppLockEnabled,
                    onCheckedChange = { want ->
                        if (want) {
                            scope.launch {
                                if (deviceCredentialAvailable) {
                                    lockPrefs.enableDeviceCredential()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        message = resources.getString(R.string.settings_app_lock_no_device_lock),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
                        } else {
                            scope.launch { lockPrefs.disable() }
                        }
                    },
                )
            },
        )

        SettingsDivider()

        // Biometric Unlock row
        SettingsRowItem(
            materialSymbolName = "fingerprint",
            title = stringResource(R.string.settings_biometric_title),
            subtitle =
                when {
                    !biometricAvailable -> stringResource(R.string.settings_biometric_no_hardware)
                    !lockState.enabled -> stringResource(R.string.settings_biometric_need_lock)
                    lockState.biometric -> stringResource(R.string.settings_biometric_enabled)
                    else -> stringResource(R.string.settings_biometric_disabled)
                },
            enabled = isBiometricEnabled,
            onClick =
                if (isBiometricEnabled) {
                    { scope.launch { lockPrefs.setBiometric(!lockState.biometric) } }
                } else {
                    null
                },
            trailingContent = {
                SettingsCustomSwitch(
                    checked = lockState.biometric,
                    enabled = isBiometricEnabled,
                    onCheckedChange = { scope.launch { lockPrefs.setBiometric(it) } },
                )
            },
        )
    }
}
