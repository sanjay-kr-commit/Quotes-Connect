package connects.quotes.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import connects.quotes.config.ServerEndpointConfig

@Composable
fun ServerEndpointDialog(
    currentConfig: ServerEndpointConfig,
    isServerAvailable: Boolean,
    onDismiss: () -> Unit,
    onSaveEndpoint: (String) -> Unit
) {
    var endpointInput by remember { mutableStateOf(currentConfig.baseUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SERVER ENDPOINT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Connect this client to a remote Quotes Connect server across your network or internet.",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Current Status Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE ENDPOINT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentConfig.baseUrl,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = if (isServerAvailable) "● ONLINE" else "○ OFFLINE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServerAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                // Channel Transport Security Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRANSPORT SECURITY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (currentConfig.isSecureTransport) "Native TLS / SSL Transport" else "Asymmetric Encrypted Channel (RSA / AES)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = if (currentConfig.isSecureTransport) "🔒 SSL / TLS" else "🔐 ASYM CHANNEL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Input Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "HOST / REMOTE IP & PORT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = {
                            endpointInput = it
                            testResult = null
                        },
                        placeholder = { Text("e.g. 192.168.1.100:8080 or localhost:8080", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                // Quick Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "QUICK PRESETS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "Default" to ServerEndpointConfig.DEFAULT.baseUrl,
                            "LAN Demo" to "http://192.168.1.100:8080",
                            "Android Host" to "http://10.0.2.2:8080"
                        ).forEach { (label, preset) ->
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    .clickable { endpointInput = preset }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                testResult?.let { msg ->
                    Text(
                        text = msg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveEndpoint(endpointInput)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("SAVE & CONNECT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
