package com.tim03.slagalica.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.AuthNavEvent
import com.tim03.slagalica.viewmodel.AuthViewModel

private val srbRegions = listOf(
    "Beograd",
    "Vojvodina",
    "Šumadija",
    "Zapadna Srbija",
    "Južna i Istočna Srbija",
    "Raška oblast",
    "Podunavlje",
    "Braničevo"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf("") }
    var regionExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.navigateTo) {
        if (uiState.navigateTo == AuthNavEvent.EMAIL_VERIFICATION_SENT) {
            showSuccessDialog = true
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = NavyCard,
            title = { Text("Registracija uspešna!", color = White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Na Vašu email adresu je poslat link za verifikaciju. Kliknite na link u emailu da biste aktivirali nalog.",
                    color = LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.clearEvent()
                        onRegisterSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                ) {
                    Text("U redu", color = White)
                }
            }
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryBlueBright, unfocusedBorderColor = MediumGray,
        focusedLabelColor = PrimaryBlueBright, unfocusedLabelColor = LightGray,
        cursorColor = PrimaryBlueBright, focusedTextColor = White, unfocusedTextColor = White,
        focusedContainerColor = NavyCard, unfocusedContainerColor = NavyCard
    )

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, NavyLight)))) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearEvent(); onBackClick() }) {
                            Icon(Icons.Default.ArrowBack, "Nazad", tint = White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Registracija", style = MaterialTheme.typography.headlineMedium, color = White, fontWeight = FontWeight.Bold)
                Text("Kreirajte vaš nalog", style = MaterialTheme.typography.bodyMedium, color = LightGray)

                Spacer(modifier = Modifier.height(24.dp))

                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email, onValueChange = { email = it; viewModel.clearEvent() },
                    label = { Text("Email adresa") },
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = LightGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username, onValueChange = { username = it; viewModel.clearEvent() },
                    label = { Text("Korisničko ime") },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = LightGray) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = regionExpanded,
                    onExpandedChange = { regionExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedRegion,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Region") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = LightGray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = fieldColors, shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false },
                        modifier = Modifier.background(NavyCard)
                    ) {
                        srbRegions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region, color = White) },
                                onClick = { selectedRegion = region; regionExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password, onValueChange = { password = it; viewModel.clearEvent() },
                    label = { Text("Lozinka") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = LightGray) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = LightGray)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword, onValueChange = { confirmPassword = it; viewModel.clearEvent() },
                    label = { Text("Potvrdi lozinku") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = LightGray) },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = LightGray)
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && confirmPassword != password)
                            Text("Lozinke se ne poklapaju", color = ErrorRed)
                    },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { viewModel.register(email, username, selectedRegion, password, confirmPassword) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                    } else {
                        Text("REGISTRUJ SE", style = MaterialTheme.typography.labelLarge, color = White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Već imate nalog? ", color = LightGray, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.clearEvent(); onBackClick() }) {
                        Text("Ulogujte se", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
