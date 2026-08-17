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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.program.blindfoldtrainer.R
import com.program.blindfoldtrainer.core.audio.ModelState
import com.program.blindfoldtrainer.core.audio.TRANSLATED_LANGUAGES
import com.program.blindfoldtrainer.core.audio.PHONETIC_FILES
import com.program.blindfoldtrainer.core.audio.VoiceLanguages
import com.program.blindfoldtrainer.core.model.Settings
import com.program.blindfoldtrainer.core.model.Language
import com.program.blindfoldtrainer.core.model.ThemeChoice

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

            EyesFreeSection(enabled = settings.eyesFree, onEnabled = viewModel::onEyesFree)

            LanguageSection(settings = settings, viewModel = viewModel)

            VoiceInputSection(settings = settings, viewModel = viewModel)
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

/** Vežbanje bez gledanja u ekran. */
@Composable
private fun EyesFreeSection(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    SettingsCard(stringResource(R.string.settings_eyes_free)) {
        SwitchRow(
            title = stringResource(R.string.settings_eyes_free_switch),
            description = stringResource(R.string.settings_eyes_free_hint),
            checked = enabled,
            onCheckedChange = onEnabled
        )
    }
}

/**
 * **Jedan jezik za sve** — i za ono što aplikacija govori tebi i za ono što ti
 * govoriš njoj.
 *
 * Dugo su to bila dva odvojena izbora, jer zavise od različitih stvari: glas na
 * uređaju naspram preuzetog paketa. Sa uređaja je stiglo da je to previše: ko
 * vežba zatvorenih očiju i sklapa tablu u glavi ne sme uz to da pamti da sluša
 * jedan jezik a govori drugi, a i sam autor je gubio trag šta je gde podesio.
 *
 * Nudi se jezik koji ima **rečenice** i **glas na uređaju**; paket za slušanje
 * stoji uz njega, jer se preuzima a ne bira. Šta nedostaje piše uz sam jezik.
 */
@Composable
private fun LanguageSection(settings: Settings, viewModel: SettingsViewModel) {
    val speakable by viewModel.speakableLanguages.collectAsState()

    SettingsCard(stringResource(R.string.settings_language_title)) {
        Text(
            text = stringResource(R.string.settings_language_title_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        SpeechLanguagePicker(
            selected = settings.language,
            speakable = speakable,
            onSelect = viewModel::onLanguage
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.settings_speech_rate, settings.speechRate),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = settings.speechRate,
            onValueChange = viewModel::onSpeechRate,
            valueRange = Settings.MIN_SPEECH_RATE..Settings.MAX_SPEECH_RATE,
            // Deset koraka po 0.1 kroz ceo opseg — finije od toga se ne čuje.
            steps = 9
        )

        Spacer(Modifier.height(12.dp))

        VoicePackage(language = settings.language, viewModel = viewModel)
    }
}

/**
 * Jezik kojim aplikacija govori.
 *
 * Zavisi od glasova **na uređaju**, ne od preuzetog paketa — zato je odvojen od
 * izbora jezika prepoznavanja i zato spisak ume da bude kraći.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechLanguagePicker(
    selected: Language,
    speakable: Set<Language>,
    onSelect: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isMissingVoice = speakable.isNotEmpty() && selected !in speakable

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            isError = isMissingVoice,
            label = { Text(stringResource(R.string.settings_speech_language)) },
            supportingText = {
                Text(
                    stringResource(
                        if (isMissingVoice) {
                            R.string.settings_speech_language_missing
                        } else {
                            R.string.settings_speech_language_hint
                        }
                    )
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Language.entries.forEach { language ->
                // Dok se TTS ne podigne, spisak je prazan i ne zaključavamo ništa.
                val hasVoice = speakable.isEmpty() || language in speakable

                // Jezik bez rečenica nije pola-jezik nego mešavina: engleska
                // rečenica sa tuđim imenom figure u sredini. Zato se ne nudi.
                val isTranslated = language in TRANSLATED_LANGUAGES
                val isOffered = hasVoice && isTranslated

                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(language.labelRes()),
                            color = if (isOffered) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                    },
                    trailingIcon = {
                        // Uvek se kaže **šta** nedostaje. Zatamnjen jezik bez
                        // objašnjenja je nemi otkaz, a njih je ovaj projekat
                        // već skupo platio.
                        val missing = when {
                            !isTranslated -> R.string.settings_speech_not_translated
                            !hasVoice -> R.string.settings_speech_no_voice
                            else -> null
                        }
                        missing?.let {
                            Text(
                                text = stringResource(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    enabled = isOffered,
                    onClick = {
                        expanded = false
                        onSelect(language)
                    }
                )
            }
        }
    }
}

/** Kako se govori aplikaciji. Jezik se ovde više ne bira — bira se gore. */
@Composable
private fun VoiceInputSection(settings: Settings, viewModel: SettingsViewModel) {
    SettingsCard(stringResource(R.string.settings_voice)) {
        Text(
            text = stringResource(R.string.settings_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        SwitchRow(
            title = stringResource(R.string.settings_phonetic),
            description = if (settings.isPhoneticAlphabetAvailable) {
                stringResource(R.string.settings_phonetic_hint)
            } else {
                // Umesto da prekidač nestane, kaže se šta treba uraditi da bi
                // radio: preuzeti engleski model.
                stringResource(R.string.settings_phonetic_english_only)
            },
            checked = settings.usesPhoneticAlphabet,
            enabled = settings.isPhoneticAlphabetAvailable,
            onCheckedChange = viewModel::onPhoneticAlphabet
        )

        // Prekidač bez spiska reči je beskoristan: niko ne zna napamet šta
        // zamenjuje f ili h.
        if (settings.usesPhoneticAlphabet) PhoneticWordList()

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

        // Bez prekidača: imena figura rade uvek kad ih jezik ima, uporedo sa
        // poljima. Prekidač bi lagao da postoji izbor — ali niko ne bi ni
        // pogodio da „rook e two" prolazi, pa mora bar da piše.
        Notice(
            title = stringResource(R.string.settings_piece_names),
            text = stringResource(R.string.settings_piece_names_hint)
        )
    }
}

/** Objašnjenje bez prekidača — za ono što radi samo od sebe. */
@Composable
private fun Notice(title: String, text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Paket za slušanje, uz jezik koji je već izabran.
 *
 * Ranije se ovde birao i jezik, pa je postojao korak „Koristi jezik" — jer bi
 * prelazak na jezik bez paketa nečujno ugasio mikrofon. Sad je jezik jedan i
 * bira se gore, pa je ostalo samo preuzimanje: bez paketa se ne gubi vežba nego
 * samo glasovni unos, i to piše.
 */
@Composable
private fun VoicePackage(language: Language, viewModel: SettingsViewModel) {
    val installed by viewModel.installedLanguages.collectAsState()
    val modelState by viewModel.modelState.collectAsState()

    val candidate = language
    val isCandidateInstalled = candidate in installed
    val busy = modelState as? ModelState.Downloading
    val unpacking = modelState as? ModelState.Unpacking
    val failure = (modelState as? ModelState.Failed)?.takeIf { it.language == candidate }

    Text(
        text = stringResource(R.string.settings_voice_package),
        style = MaterialTheme.typography.bodyLarge
    )

    Spacer(Modifier.height(8.dp))

    when {
        busy?.language == candidate -> {
            Text(
                text = stringResource(R.string.settings_language_downloading),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            val fraction = busy?.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::onCancelInstall) {
                Text(stringResource(R.string.voice_cancel))
            }
        }

        unpacking?.language == candidate -> {
            Text(
                text = stringResource(R.string.settings_language_unpacking),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        else -> {
            failure?.let {
                Text(
                    text = stringResource(R.string.voice_failed, it.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
            }

            if (isCandidateInstalled) {
                Text(
                    text = stringResource(R.string.settings_language_installed),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { viewModel.onDelete(candidate) }) {
                    Text(stringResource(R.string.voice_delete))
                }
            } else {
                // Veličina stoji pre dodira, a ne posle: preuzimanje od četrdesetak
                // megabajta nije nešto što se sazna kad već krene.
                FilledTonalButton(onClick = { viewModel.onInstall(candidate) }) {
                    Text(
                        stringResource(
                            R.string.settings_language_item,
                            stringResource(R.string.settings_language_install),
                            VoiceLanguages.specFor(candidate).downloadMegabytes
                        )
                    )
                }
            }
        }
    }
}

/**
 * Koja reč zamenjuje koje slovo.
 *
 * Reči su engleske i onda kad je izabran drugi jezik — fonetska abeceda je
 * međunarodna, a rečnik koji Vosk sluša pravi se od baš ovih reči.
 */
@Composable
private fun PhoneticWordList() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Sortirano po slovu, da se traži okom a ne pamćenjem.
            PHONETIC_FILES.entries
                .sortedBy { it.value }
                .chunked(4)
                .forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { (word, file) ->
                            Text(
                                text = "$file — $word",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    // Nedostupno podešavanje ostaje vidljivo, ali izbledelo — da se vidi da
    // postoji i da objašnjenje ispod ima kome da se obrati.
    val alpha = if (enabled) 1f else 0.5f

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun ThemeChoice.labelRes(): Int = when (this) {
    ThemeChoice.SYSTEM -> R.string.settings_theme_system
    ThemeChoice.LIGHT -> R.string.settings_theme_light
    ThemeChoice.DARK -> R.string.settings_theme_dark
}

private fun Language.labelRes(): Int = when (this) {
    Language.ENGLISH -> R.string.language_english
    Language.GERMAN -> R.string.language_german
    Language.RUSSIAN -> R.string.language_russian
    Language.FRENCH -> R.string.language_french
    Language.SPANISH -> R.string.language_spanish
    Language.ITALIAN -> R.string.language_italian
    Language.POLISH -> R.string.language_polish
    Language.CZECH -> R.string.language_czech
    Language.TURKISH -> R.string.language_turkish
}
