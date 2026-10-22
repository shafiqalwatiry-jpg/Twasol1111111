package com.tawasol.app.presentation.home.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.model.UserStatus
import com.tawasol.app.domain.repository.StatusRepository
import com.tawasol.app.presentation.components.UserAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatusUiState(
    val isPosting: Boolean = false,
    val errorMessage: String? = null,
    val showCreateMenu: Boolean = false,
    val showTextStatusDialog: Boolean = false,
    val previewingStatus: UserStatus? = null
)

class StatusViewModel(
    private val statusRepository: StatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    val statuses: StateFlow<List<UserStatus>> = statusRepository.getActiveStatuses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setShowCreateMenu(show: Boolean) {
        _uiState.update { it.copy(showCreateMenu = show) }
    }

    fun setShowTextStatusDialog(show: Boolean) {
        _uiState.update { it.copy(showTextStatusDialog = show, showCreateMenu = false) }
    }

    fun setPreviewingStatus(status: UserStatus?) {
        _uiState.update { it.copy(previewingStatus = status) }
        if (status != null && !status.isSelf) {
            viewModelScope.launch {
                statusRepository.markStatusViewed(status.id)
            }
        }
    }

    fun postTextStatus(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(isPosting = true, errorMessage = null, showTextStatusDialog = false) }
        viewModelScope.launch {
            val result = statusRepository.postTextStatus(text)
            result.onSuccess {
                _uiState.update { it.copy(isPosting = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isPosting = false, errorMessage = err.localizedMessage) }
            }
        }
    }

    fun postImageStatus(imageBytes: ByteArray, caption: String?) {
        _uiState.update { it.copy(isPosting = true, errorMessage = null, showCreateMenu = false) }
        viewModelScope.launch {
            val result = statusRepository.postMediaStatus(imageBytes, caption)
            result.onSuccess {
                _uiState.update { it.copy(isPosting = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isPosting = false, errorMessage = err.localizedMessage) }
            }
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            statusRepository.deleteStatus(statusId)
            _uiState.update { it.copy(previewingStatus = null) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTab(
    currentUser: User?,
    statusViewModel: StatusViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = statusViewModel?.uiState?.collectAsState()?.value ?: StatusUiState()
    val allStatuses = statusViewModel?.statuses?.collectAsState()?.value ?: emptyList()

    val myStatus = allStatuses.firstOrNull { it.isSelf }
    val othersStatuses = allStatuses.filter { !it.isSelf }

    var textInput by remember { mutableStateOf("") }

    // Android Photo Picker Launcher for status images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && statusViewModel != null) {
                statusViewModel.postImageStatus(bytes, "حالة جديدة")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // My Status item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (myStatus != null) {
                        statusViewModel?.setPreviewingStatus(myStatus)
                    } else {
                        statusViewModel?.setShowCreateMenu(true)
                    }
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TealPrimary.copy(alpha = 0.2f))
                        .border(
                            width = if (myStatus != null) 2.5.dp else 1.dp,
                            color = TealPrimary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (myStatus?.mediaUrl != null) {
                        AsyncImage(
                            model = myStatus.mediaUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = TealPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(TealPrimary)
                        .clickable { statusViewModel?.setShowCreateMenu(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (myStatus != null) Icons.Rounded.Edit else Icons.Rounded.Add,
                        contentDescription = "إضافة حالة",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "حالتي",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (myStatus != null) {
                        myStatus.text ?: "انقر لعرض حالتك النشطة"
                    } else {
                        "انقر لإضافة حالة نصية أو صورة تختفي بعد 24 ساعة"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }

            if (state.isPosting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = TealPrimary,
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "آخر التحديثات",
            style = MaterialTheme.typography.labelLarge.copy(
                color = TealPrimary,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (othersStatuses.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد تحديثات حالات حديثة من جهات اتصالك",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(othersStatuses, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { statusViewModel?.setPreviewingStatus(item) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.dp, TealPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.mediaUrl != null) {
                                AsyncImage(
                                    model = item.mediaUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                UserAvatar(
                                    name = item.userDisplayName,
                                    avatarUrl = item.userAvatarUrl,
                                    size = 48
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.userDisplayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = item.text ?: "حالة جديدة",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for creating status
    if (state.showCreateMenu) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { statusViewModel?.setShowCreateMenu(false) },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "نشر حالة جديدة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            statusViewModel?.setShowTextStatusDialog(true)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "كتابة حالة نصية",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            statusViewModel?.setShowCreateMenu(false)
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "اختيار صورة من المعرض",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    // Dialog for writing text status
    if (state.showTextStatusDialog) {
        AlertDialog(
            onDismissRequest = { statusViewModel?.setShowTextStatusDialog(false) },
            title = {
                Text(
                    text = "كتابة حالة نصية جديدة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("اكتب ما يدور في ذهنك...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        cursorColor = TealPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            statusViewModel?.postTextStatus(textInput)
                            textInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("نشر", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { statusViewModel?.setShowTextStatusDialog(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Fullscreen Status Viewer Dialog
    state.previewingStatus?.let { status ->
        AlertDialog(
            onDismissRequest = { statusViewModel?.setPreviewingStatus(null) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (status.isSelf) "حالتي" else status.userDisplayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (status.isSelf) {
                        IconButton(onClick = { statusViewModel?.deleteStatus(status.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "حذف الحالة",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (status.mediaUrl != null) {
                        AsyncImage(
                            model = status.mediaUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (!status.text.isNullOrBlank()) {
                        Text(
                            text = status.text,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { statusViewModel?.setPreviewingStatus(null) }) {
                    Text("إغلاق", color = TealPrimary)
                }
            }
        )
    }
}
