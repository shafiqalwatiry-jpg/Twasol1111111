package com.tawasol.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.PrivacySettings
import com.tawasol.app.domain.model.VisibilityOption
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.usecase.GetPrivacySettingsUseCase
import com.tawasol.app.domain.usecase.UpdatePrivacySettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacySettingsUiState(
    val settings: PrivacySettings = PrivacySettings(userId = ""),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class PrivacySettingsViewModel(
    private val authRepository: AuthRepository,
    private val getPrivacySettingsUseCase: GetPrivacySettingsUseCase,
    private val updatePrivacySettingsUseCase: UpdatePrivacySettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySettingsUiState())
    val uiState: StateFlow<PrivacySettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val currentUserId = authRepository.currentUserId
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "لم يتم العثور على الجلسة") }
            return
        }

        viewModelScope.launch {
            getPrivacySettingsUseCase(currentUserId).collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings ?: PrivacySettings(userId = currentUserId),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun updateLastSeen(option: VisibilityOption) {
        val current = _uiState.value.settings
        saveSettings(current.copy(lastSeenVisibility = option))
    }

    fun updateOnlineStatus(option: VisibilityOption) {
        val current = _uiState.value.settings
        saveSettings(current.copy(onlineStatusVisibility = option))
    }

    fun updateProfilePhoto(option: VisibilityOption) {
        val current = _uiState.value.settings
        saveSettings(current.copy(profilePhotoVisibility = option))
    }

    fun updateBio(option: VisibilityOption) {
        val current = _uiState.value.settings
        saveSettings(current.copy(bioVisibility = option))
    }

    fun toggleReadReceipts(enabled: Boolean) {
        val current = _uiState.value.settings
        saveSettings(current.copy(readReceiptsEnabled = enabled))
    }

    private fun saveSettings(newSettings: PrivacySettings) {
        _uiState.update { it.copy(settings = newSettings, isSaving = true) }
        viewModelScope.launch {
            updatePrivacySettingsUseCase(newSettings)
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    viewModel: PrivacySettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var activeDialogTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الخصوصية",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TealPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "من يمكنه رؤية معلوماتي الشخصية",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = TealPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        PrivacySettingItem(
                            icon = Icons.Rounded.Schedule,
                            title = "آخر ظهور",
                            currentValue = state.settings.lastSeenVisibility.titleArabic,
                            onClick = { activeDialogTarget = "last_seen" }
                        )

                        PrivacySettingItem(
                            icon = Icons.Rounded.Visibility,
                            title = "حالة الاتصال بالإنترنت",
                            currentValue = state.settings.onlineStatusVisibility.titleArabic,
                            onClick = { activeDialogTarget = "online_status" }
                        )

                        PrivacySettingItem(
                            icon = Icons.Rounded.AccountCircle,
                            title = "الصورة الشخصية",
                            currentValue = state.settings.profilePhotoVisibility.titleArabic,
                            onClick = { activeDialogTarget = "profile_photo" }
                        )

                        PrivacySettingItem(
                            icon = Icons.Rounded.Info,
                            title = "النبذة التعريفية (Bio)",
                            currentValue = state.settings.bioVisibility.titleArabic,
                            onClick = { activeDialogTarget = "bio" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "مؤشرات قراءة الرسائل",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = TealPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مؤشرات قراءة الرسائل (Read Receipts)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "إذا قمت بتعطيل هذا الخيار، فلن تتمكن من معرفة متى قرأ الآخرون رسائلك",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Switch(
                            checked = state.settings.readReceiptsEnabled,
                            onCheckedChange = viewModel::toggleReadReceipts,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TealPrimary,
                                checkedTrackColor = TealPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }

    // Visibility Selector Dialog
    if (activeDialogTarget != null) {
        val title = when (activeDialogTarget) {
            "last_seen" -> "آخر ظهور"
            "online_status" -> "حالة الاتصال"
            "profile_photo" -> "الصورة الشخصية"
            "bio" -> "النبذة التعريفية"
            else -> ""
        }
        val currentOption = when (activeDialogTarget) {
            "last_seen" -> state.settings.lastSeenVisibility
            "online_status" -> state.settings.onlineStatusVisibility
            "profile_photo" -> state.settings.profilePhotoVisibility
            "bio" -> state.settings.bioVisibility
            else -> VisibilityOption.EVERYONE
        }

        AlertDialog(
            onDismissRequest = { activeDialogTarget = null },
            title = {
                Text(
                    text = "من يمكنه رؤية $title؟",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    VisibilityOption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (activeDialogTarget) {
                                        "last_seen" -> viewModel.updateLastSeen(option)
                                        "online_status" -> viewModel.updateOnlineStatus(option)
                                        "profile_photo" -> viewModel.updateProfilePhoto(option)
                                        "bio" -> viewModel.updateBio(option)
                                    }
                                    activeDialogTarget = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentOption == option,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = TealPrimary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option.titleArabic,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (currentOption == option) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialogTarget = null }) {
                    Text("إلغاء", color = TealPrimary)
                }
            }
        )
    }
}

@Composable
fun PrivacySettingItem(
    icon: ImageVector,
    title: String,
    currentValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
