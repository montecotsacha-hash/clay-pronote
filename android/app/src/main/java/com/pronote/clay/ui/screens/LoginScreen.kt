package com.pronote.clay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pronote.clay.ui.components.ClayButton
import com.pronote.clay.ui.components.ClayErrorBox
import com.pronote.clay.ui.components.ClayPill
import com.pronote.clay.ui.components.ClaySectionTitle
import com.pronote.clay.ui.components.clay
import com.pronote.clay.ui.components.clayInset
import com.pronote.clay.ui.theme.ClayDimens
import com.pronote.clay.ui.viewmodel.HomeUiState
import com.pronote.clay.ui.viewmodel.HomeViewModel

/**
 * LoginScreen — clay-styled credentials form.
 *
 * The demo connector is prefilled so the app is explorable in one tap; a real
 * connector drops in behind PronoteDataSource without touching this screen.
 */
@Composable
fun LoginScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val error = state as? HomeUiState.Error

    var schoolUrl by remember { mutableStateOf("https://0123456a.index-education.net/pronote/") }
    var username by remember { mutableStateOf("demo") }
    var password by remember { mutableStateOf("demo1234") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = ClayDimens.ScreenPadding),
    ) {
        Spacer(Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(64.dp)
                    .clay(depth = 14.dp, tint = MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Ardoise", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Tes notes, hors ligne, en un clin d'œil.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(34.dp))
        ClaySectionTitle("Connexion")
        Spacer(Modifier.height(14.dp))

        ClayField(
            value = schoolUrl,
            onValueChange = { schoolUrl = it },
            label = "Adresse Pronote de l'établissement",
            leading = { Icon(Icons.Rounded.School, contentDescription = null, Modifier.size(18.dp)) },
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(12.dp))
        ClayField(
            value = username,
            onValueChange = { username = it },
            label = "Identifiant",
            leading = { Icon(Icons.Rounded.Person, contentDescription = null, Modifier.size(18.dp)) },
        )
        Spacer(Modifier.height(12.dp))
        ClayField(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            leading = { Icon(Icons.Rounded.Lock, contentDescription = null, Modifier.size(18.dp)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Masquer" else "Afficher",
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            ClayErrorBox(message = error.message, hint = error.hint)
        }

        Spacer(Modifier.height(22.dp))
        ClayButton(
            text = "Se connecter",
            onClick = { viewModel.login(schoolUrl.trim(), username.trim(), password) {} },
        )

        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            ClayPill(text = "Mode démonstration", tint = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Les identifiants sont préremplis pour explorer l'app tout de suite. " +
                "Ils restent chiffrés sur l'appareil (Keystore Android) et ne sont " +
                "jamais envoyés à un serveur tiers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ClayField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.large,
        colors = colors,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .clayInset(shape = MaterialTheme.shapes.large),
    )
}
