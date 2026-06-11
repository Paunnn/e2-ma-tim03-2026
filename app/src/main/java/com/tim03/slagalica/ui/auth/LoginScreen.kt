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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.AuthNavEvent
import com.tim03.slagalica.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    onGuestClick: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.navigateTo) {
        when (uiState.navigateTo) {
            AuthNavEvent.HOME -> { viewModel.clearEvent(); onLoginSuccess() }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Brush.verticalGradient(listOf(Navy, NavyLight, NavyCard)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = PrimaryBlue, modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("SLAGALICA", style = MaterialTheme.typography.headlineLarge, color = Gold, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            Text("Kviz za pametne", style = MaterialTheme.typography.bodyMedium, color = LightGray)

            Spacer(modifier = Modifier.height(48.dp))

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

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlueBright, unfocusedBorderColor = MediumGray,
                focusedLabelColor = PrimaryBlueBright, unfocusedLabelColor = LightGray,
                cursorColor = PrimaryBlueBright, focusedTextColor = White, unfocusedTextColor = White
            )

            OutlinedTextField(
                value = identifier, onValueChange = { identifier = it; viewModel.clearEvent() },
                label = { Text("Email ili korisničko ime") },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = LightGray) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = fieldColors, shape = RoundedCornerShape(12.dp)
            )

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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.login(identifier, password) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                } else {
                    Text("ULOGUJ SE", style = MaterialTheme.typography.labelLarge, color = White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Divider(modifier = Modifier.weight(1f), color = MediumGray)
                Text("  ili  ", color = LightGray, style = MaterialTheme.typography.bodySmall)
                Divider(modifier = Modifier.weight(1f), color = MediumGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onGuestClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
            ) {
                Text("IGRAJ BEZ NALOGA", style = MaterialTheme.typography.labelLarge, color = LightGray)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Nemate nalog? ", color = LightGray, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRegisterClick) {
                    Text("Registrujte se", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
