package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import com.program.blindfoldtrainer.core.audio.ModelState
import com.program.blindfoldtrainer.core.audio.VoskModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Jezički model za glasovni unos, iz ugla menija.
 *
 * Preuzimanje pokreće **korisnik**, ne aplikacija: model je 39 MB i nema smisla
 * da ga plaća onaj ko vežba dodirom.
 */
@HiltViewModel
class VoiceModelViewModel @Inject constructor(
    private val store: VoskModelStore
) : ViewModel() {

    val state: StateFlow<ModelState> = store.state

    fun download() = store.download()

    fun cancel() = store.cancel()

    fun delete() = store.delete()
}
