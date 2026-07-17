package com.bluedeck.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bluedeck.viewmodel.ServicePinPromptState

@Composable
fun ServicePinPromptDialog(
    prompt: ServicePinPromptState,
    onDismiss: () -> Unit,
    onConfirm: (pin: String, savePin: Boolean) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var savePin by remember { mutableStateOf(true) }
    val canSubmit = pinInput.length == 4

    fun submit() {
        if (!canSubmit) return
        onConfirm(pinInput, savePin)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Bluelink PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A 4-digit Bluelink PIN is required to ${prompt.commandLabel.lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { value ->
                        pinInput = value.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = savePin,
                        onCheckedChange = { savePin = it }
                    )
                    Text(
                        "Save PIN for future commands",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = canSubmit) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
