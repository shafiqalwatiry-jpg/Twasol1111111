package com.tawasol.app.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.repository.UserRepository
import com.tawasol.app.domain.usecase.UpdateProfileUseCase
import com.tawasol.app.domain.usecase.UploadAvatarUseCase
import com.tawasol.app.presentation.components.TawasolButton
import com.tawasol.app.presentation.components.TawasolTextField
import com.tawasol.app.presentation.components.UserAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val userId: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        val currentUserId = authRepository.currentUserId
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "لم يتم العثور على الجلسة") }
            return
        }

        _uiState.update { it.copy(userId = currentUserId, isLoading = true) }

        viewModelScope.launch {
            val result = userRepository.getUserProfile(currentUserId)
            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        userId = user.id,
                        displayName = user.displayName,
                        bio = user.bio ?: "",
                        avatarUrl = user.avatarUrl,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "تعذر استرجاع بيانات الملف"
                    )
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, errorMessage = null) }
    }

    fun onBioChange(value: String) {
        _uiState.update { it.copy(bio = value, errorMessage = null) }
    }

    fun uploadAvatarBytes(bytes: ByteArray, mimeType: String) {
        val userId = _uiState.value.userId
        if (userId.isEmpty() || bytes.isEmpty()) return

        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }

        viewModelScope.launch {
            val result = uploadAvatarUseCase(userId, bytes, mimeType)
            result.onSuccess { newAvatarUrl ->
                _uiState.update {
                    it.copy(
                        avatarUrl = newAvatarUrl,
                        isUploadingAvatar = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = error.localizedMessage ?: "فشل رفع الصورة الشخصية"
                    )
                }
            }
        }
    }

    fun onUploadError(error: String) {
        _uiState.update { it.copy(isUploadingAvatar = false, errorMessage = error) }
    }

    fun saveChanges() {
        val state = _uiState.value
        if (state.displayName.trim().isEmpty()) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال الاسم الظاهر") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val result = updateProfileUseCase(state.userId, state.displayName, state.bio)
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.localizedMessage ?: "حدث خطأ أثناء حفظ التعديلات"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes() ?: byteArrayOf()
                inputStream?.close()
                if (bytes.isNotEmpty()) {
                    viewModel.uploadAvatarBytes(bytes, "image/jpeg")
                } else {
                    viewModel.onUploadError("الملف المختار فارغ أو لا يمكن قراءته")
                }
            } catch (e: Exception) {
                viewModel.onUploadError("تعذر قراءة الصورة المحددة: ${e.localizedMessage ?: "خطأ في الصلاحيات"}")
            }
        }
    }

    if (state.isSuccess) {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تعديل الملف الشخصي",
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
                    IconButton(
                        onClick = viewModel::saveChanges,
                        enabled = !state.isSaving && !state.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "حفظ",
                            tint = TealPrimary
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
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealPrimary, strokeWidth = 3.dp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Avatar with edit button overlay
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserAvatar(
                            name = state.displayName.ifEmpty { "ت" },
                            avatarUrl = state.avatarUrl,
                            size = 110
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TealPrimary)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isUploadingAvatar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "تغيير الصورة",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "اضغط على الكاميرا لتغيير صورتك الشخصية",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Display Name Field
                    TawasolTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayNameChange,
                        label = "الاسم الظاهر",
                        placeholder = "مثال: شفيق الوتيري"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Bio Field
                    TawasolTextField(
                        value = state.bio,
                        onValueChange = viewModel::onBioChange,
                        label = "النبذة التعريفية (Bio)",
                        placeholder = "اكتب بضع كلمات عنك..."
                    )

                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    TawasolButton(
                        text = "حفظ التعديلات",
                        onClick = viewModel::saveChanges,
                        isLoading = state.isSaving,
                        enabled = state.displayName.isNotBlank()
                    )
                }
            }
        }
    }
}
