package com.example.spotifylyricsproxy.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.spotifylyricsproxy.R

/**
 * Shows the copyright notice every time a screen that stores lyrics is opened;
 * [content] is only composed once the user has agreed.
 */
@Composable
fun CopyrightGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    if (accepted) {
        content()
    } else {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.copyright_gate_title)) },
            text = { Text(stringResource(R.string.copyright_gate_message)) },
            confirmButton = { TextButton(onClick = { accepted = true }) { Text(stringResource(R.string.copyright_gate_ok)) } },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.copyright_gate_back)) } }
        )
    }
}
