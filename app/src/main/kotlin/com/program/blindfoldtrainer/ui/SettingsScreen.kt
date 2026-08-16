package com.program.blindfoldtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.program.blindfoldtrainer.core.audio.VoiceLanguages
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.ThemeChoice
import com.program.blindfoldtrainer.core.model.VoiceLanguage

/**
 * Podešavanja.
 *
 * Ovde stoji samo ono što zavisi od korisnika, a ne od toga šta je objektivno
 * bolje — pre svega glasovne opcije, gde ispravan izbor zavisi od izgovora.
 * Sve podrazumevano stoji na zatečenom ponašanju.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemeSection(theme = settings.theme, onTheme = viewModel::onTheme)

            SpeechSection(rate = settings.speechRate, onRate = viewModel::onSpeechRate)

            VoiceSection(settings = settings, viewModel = viewModel)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSection(theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit) {
    SettingsCard(stringResource(R.string.settings_theme)) {
        val choices = ThemeChoice.entries

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == theme,
                    onClick = { onTheme(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size)
                ) {
                    Text(stringResource(choice.labelRes()))
                }
            }
        }
    }
}

@Composable
private fun SpeechSection(rate: Float, onRate: (Float) -> Unit) {
    SettingsCard(stringResource(R.string.settings_speech)) {
        Text(
            text = stringResource(R.string.settings_speech_rate, rate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = rate,
            onValueChange = onRate,
            valueRange = Settings.MIN_SPEECH_RATE..Settings.MAX_SPEECH_RATE,
            // Deset koraka po 0.1 kroz ceo opseg — finije od toga se ne čuje.
            steps = 9
        )
        Text(
            text = stringResource(R.string.settings_speech_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceSection(settings: Settings, viewModel: SettingsViewModel) {
    SettingsCard(stringResource(R.string.settings_voice)) {
        Text(
            text = stringResource(R.string.settings_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        LanguagePicker(
            selected = settings.voiceLanguage,
            onSelect = viewModel::onVoiceLanguage
        )

        Spacer(Modifier.height(4.dp))

        SwitchRow(
            title = stringResource(R.string.settings_nato),
            description = stringResource(R.string.settings_nato_hint),
            checked = settings.natoAlphabet,
            onCheckedChange = viewModel::onNatoAlphabet
        )

        SwitchRow(
            title = stringResource(R.string.settings_whole_move),
            description = stringResource(R.string.settings_whole_move_hint),
            checked = settings.listenWholeMove,
            onCheckedChange = viewModel::onListenWholeMove
        )

        SwitchRow(
            title = stringResource(R.string.settings_separate),
            description = stringResource(R.string.settings_separate_hint),
            checked = settings.separateLetterAndNumber,
            onCheckedChange = viewModel::onSeparateLetterAndNumber
        )
    }
}

/**
 * Jezik glasovnog unosa.
 *
 * Uz jezik stoji i veličina preuzimanja, jer promena jezika znači nov model —
 * to je podatak koji treba da se vidi **pre** dodira, a ne posle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(selected: VoiceLanguage, onSelect: (VoiceLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val spec = VoiceLanguages.specFor(selected)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_language)) },
            supportingText = {
                Text(stringResource(R.string.settings_language_size, spec.downloadMegabytes))
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VoiceLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.settings_language_item,
                                stringResource(language.labelRes()),
                                VoiceLanguages.specFor(language).downloadMegabytes
                            )
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    }
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ThemeChoice.labelRes(): Int = when (this) {
    ThemeChoice.SYSTEM -> R.string.settings_theme_system
    ThemeChoice.LIGHT -> R.string.settings_theme_light
    ThemeChoice.DARK -> R.string.settings_theme_dark
}

private fun VoiceLanguage.labelRes(): Int = when (this) {
    VoiceLanguage.ENGLISH -> R.string.language_english
    VoiceLanguage.GERMAN -> R.string.language_german
    VoiceLanguage.RUSSIAN -> R.string.language_russian
    VoiceLanguage.FRENCH -> R.string.language_french
    VoiceLanguage.SPANISH -> R.string.language_spanish
    VoiceLanguage.ITALIAN -> R.string.language_italian
    VoiceLanguage.POLISH -> R.string.language_polish
    VoiceLanguage.CZECH -> R.string.language_czech
    VoiceLanguage.TURKISH -> R.string.language_turkish
}
