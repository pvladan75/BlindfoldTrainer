package com.program.blindfoldtrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.program.blindfoldtrainer.core.model.Profile
import com.program.blindfoldtrainer.core.model.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ko vežba na ovom uređaju.
 *
 * Profil se **bira, ne prijavljuje**: podaci su na uređaju, pa lozinka ne bi
 * štitila nego se pretvarala da štiti. Ovde treba razdvajanje napretka, ne
 * zaštita.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val active: StateFlow<Profile?> = repository.active
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onCreate(name: String) = viewModelScope.launch {
        val created = repository.create(name)

        // Nov profil se odmah i preuzima: pravi se zato što neko hoće da vežba,
        // a ne da bi stajao na spisku.
        repository.activate(created.id)
    }

    fun onActivate(profile: Profile) = viewModelScope.launch { repository.activate(profile.id) }

    fun onRename(profile: Profile, name: String) = viewModelScope.launch {
        repository.rename(profile.id, name)
    }

    fun onDelete(profile: Profile) = viewModelScope.launch { repository.delete(profile.id) }
}
