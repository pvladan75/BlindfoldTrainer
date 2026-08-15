package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.model.SessionResult
import com.program.blindfoldtrainer.core.progress.ProgressRepository
import com.program.blindfoldtrainer.core.progress.ProgressSnapshot
import com.program.blindfoldtrainer.core.progress.SessionReward
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Napredak za celu školjku: meni ga prikazuje, sažetak sesije ga dopunjuje.
 *
 * Drži se na nivou aktivnosti, ne rute — zato preživljava prelaz iz modula u
 * meni i promenu konfiguracije.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: ProgressRepository
) : ViewModel() {

    val snapshot: StateFlow<ProgressSnapshot> = repository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressSnapshot.EMPTY)

    private val _lastReward = MutableStateFlow<SessionReward?>(null)

    /** Šta je donela poslednja upisana sesija; `null` dok je nema. */
    val lastReward: StateFlow<SessionReward?> = _lastReward.asStateFlow()

    private var recorded: SessionResult? = null

    fun record(result: SessionResult) {
        // Ekran modula zove onFinish iz LaunchedEffect-a, a on se posle promene
        // konfiguracije pokreće ponovo sa istim stanjem. ViewModel to preživi,
        // pa se dupli upis iste sesije zaustavlja ovde.
        if (result == recorded) return
        recorded = result

        viewModelScope.launch {
            _lastReward.value = repository.record(result)
        }
    }

    /** Sažetak je zatvoren — sledeća sesija sme ponovo da se upiše. */
    fun onSummaryClosed() {
        recorded = null
        _lastReward.value = null
    }
}
