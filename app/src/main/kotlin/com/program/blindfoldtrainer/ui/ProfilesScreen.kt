package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.model.Profile

/**
 * Biranje profila.
 *
 * Nema lozinke i nema prijave — bira se, kao na televizoru. Zaštita ovde ništa
 * ne bi štitila; ono što treba je **razdvajanje napretka**, jer otac i sin hoće
 * da vide svoj, a ne njihov zbir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.active.collectAsState()

    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.profiles_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    isActive = profile.id == active?.id,
                    canDelete = profiles.size > 1,
                    onActivate = { viewModel.onActivate(profile) },
                    onRename = { viewModel.onRename(profile, it) },
                    onDelete = { viewModel.onDelete(profile) }
                )
            }

            item(key = "new") {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.profiles_new_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            viewModel.onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.profiles_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var renaming by remember(profile.id) { mutableStateOf(false) }
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var confirmingDelete by remember(profile.id) { mutableStateOf(false) }

    Card(
        onClick = onActivate,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.profiles_active)
                    )
                }
            }

            if (renaming) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    TextButton(
                        onClick = {
                            onRename(name)
                            renaming = false
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text(stringResource(R.string.profiles_save))
                    }
                    TextButton(onClick = { renaming = false }) {
                        Text(stringResource(R.string.profiles_cancel))
                    }
                }
                return@Column
            }

            Row {
                TextButton(onClick = { renaming = true }) {
                    Text(stringResource(R.string.profiles_rename))
                }

                // Brisanje profila briše i celu njegovu istoriju, a to je jedino
                // što se u ovoj aplikaciji ne može povratiti — zato dva dodira.
                if (canDelete) {
                    TextButton(
                        onClick = {
                            if (confirmingDelete) onDelete() else confirmingDelete = true
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (confirmingDelete) {
                                    R.string.profiles_delete_confirm
                                } else {
                                    R.string.profiles_delete
                                }
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
