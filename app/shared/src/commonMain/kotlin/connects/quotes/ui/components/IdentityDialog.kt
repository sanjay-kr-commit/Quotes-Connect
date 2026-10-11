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
import connects.quotes.client.SessionManager
import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.ui.theme.CloseIcon
import connects.quotes.ui.theme.UserIcon
import connects.quotes.ui.theme.QCRadiusButton
import connects.quotes.ui.theme.QCRadiusCard
import connects.quotes.ui.theme.QCRadiusInput

@Composable
fun IdentityDialog(
    onDismiss: () -> Unit,
    onCreateNew: (String, Boolean) -> Unit,
    onLogin: (Id, String, Boolean) -> Unit,
    onChangePassphrase: (String, String) -> Unit = { _, _ -> }
) {
    val currentIdentity by SessionManager.currentIdentity.collectAsState()
    val isRememberMeActive by SessionManager.isRememberMe.collectAsState()

    var isCreating by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isChangingPass by remember { mutableStateOf(false) }

    var monikerInput by remember { mutableStateOf("") }
    var loginIdInput by remember { mutableStateOf("") }
    var loginPassInput by remember { mutableStateOf("") }
    var rememberMeInput by remember { mutableStateOf(isRememberMeActive) }

    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(QCRadiusCard)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(QCRadiusCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserIcon(tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                        Text(
                            text = "SOVEREIGN IDENTITY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        CloseIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Subtitle / Philosophy
                Text(
                    text = "No accounts. No emails. No algorithm. Identity is simply an Author Passage in the graph holding a cryptographic bearer passphrase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Current Active Identity State
                if (currentIdentity != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE AUTHOR PASSAGE:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentIdentity!!.passageId.value,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "SECRET PASSPHRASE:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentIdentity!!.passphrase,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isChangingPass) {
                        // Change Passphrase Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "UPDATE PASSPHRASE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = oldPassInput,
                                onValueChange = { oldPassInput = it },
                                label = { Text("Current Passphrase") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newPassInput,
                                onValueChange = { newPassInput = it },
                                label = { Text("New Passphrase") },
                                placeholder = { Text("Enter or generate passphrase") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { newPassInput = Credentials.generatePassphrase(32) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(QCRadiusButton)
                            ) {
                                Text("GENERATE STRONG (ALPHANUMERIC)", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isChangingPass = false
                                        oldPassInput = ""
                                        newPassInput = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(QCRadiusButton)
                                ) {
                                    Text("CANCEL", fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = {
                                        if (oldPassInput.isNotBlank() && newPassInput.isNotBlank()) {
                                            onChangePassphrase(oldPassInput.trim(), newPassInput.trim())
                                            isChangingPass = false
                                            oldPassInput = ""
                                            newPassInput = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(QCRadiusButton)
                                ) {
                                    Text("UPDATE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                oldPassInput = currentIdentity!!.passphrase
                                newPassInput = ""
                                isChangingPass = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(QCRadiusButton)
                        ) {
                            Text("CHANGE SECRET PASSPHRASE", fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Remember Me Status / Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Remember me on this device",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isRememberMeActive) "Auto-login on restart enabled" else "Session only (clears on exit)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRememberMeActive,
                            onCheckedChange = { checked ->
                                SessionManager.setRememberMePreference(checked)
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { SessionManager.clearIdentity() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(QCRadiusButton)
                    ) {
                        Text("DISCONNECT IDENTITY (GO ANONYMOUS)", fontFamily = FontFamily.Monospace)
                    }
                } else if (!isCreating && !isLoggingIn) {
                    // Selection choices
                    Button(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(QCRadiusButton)
                    ) {
                        Text("CREATE SOVEREIGN IDENTITY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { isLoggingIn = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(QCRadiusButton)
                    ) {
                        Text("CONNECT EXISTING (UUID OR @ALIAS + PASSPHRASE)", fontFamily = FontFamily.Monospace)
                    }
                } else if (isCreating) {
                    // Create identity form
                    OutlinedTextField(
                        value = monikerInput,
                        onValueChange = { monikerInput = it },
                        label = { Text("Moniker / Public Pseudonym") },
                        placeholder = { Text("e.g. Diogenes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberMeInput = !rememberMeInput }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = rememberMeInput,
                            onCheckedChange = { rememberMeInput = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remember me on this device",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isCreating = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(QCRadiusButton)
                        ) {
                            Text("BACK", fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                if (monikerInput.isNotBlank()) {
                                    onCreateNew(monikerInput.trim(), rememberMeInput)
                                    isCreating = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(QCRadiusButton)
                        ) {
                            Text("GENERATE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isLoggingIn) {
                    // Login form
                    OutlinedTextField(
                        value = loginIdInput,
                        onValueChange = { loginIdInput = it },
                        label = { Text("Author Passage UUID or @Username") },
                        placeholder = { Text("e.g. 550e8400... or @socrates") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = loginPassInput,
                        onValueChange = { loginPassInput = it },
                        label = { Text("Secret Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberMeInput = !rememberMeInput }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = rememberMeInput,
                            onCheckedChange = { rememberMeInput = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remember me on this device",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isLoggingIn = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(QCRadiusButton)
                        ) {
                            Text("BACK", fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                if (loginIdInput.isNotBlank() && loginPassInput.isNotBlank()) {
                                    onLogin(Id(loginIdInput.trim()), loginPassInput.trim(), rememberMeInput)
                                    isLoggingIn = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(QCRadiusButton)
                        ) {
                            Text("CONNECT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
