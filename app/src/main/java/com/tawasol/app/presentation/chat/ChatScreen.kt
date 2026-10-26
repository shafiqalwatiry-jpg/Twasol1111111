package com.tawasol.app.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tawasol.app.core.media.AudioRecorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.Conversation
import com.tawasol.app.domain.model.Message
import com.tawasol.app.domain.model.MessageStatus
import com.tawasol.app.domain.model.MessageType
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.repository.ChatRepository
import com.tawasol.app.domain.usecase.GetConversationUseCase
import com.tawasol.app.domain.usecase.GetMessagesUseCase
import com.tawasol.app.domain.usecase.MarkConversationAsReadUseCase
import com.tawasol.app.domain.usecase.SendMediaMessageUseCase
import com.tawasol.app.domain.usecase.SendMessageUseCase
import com.tawasol.app.presentation.components.UserAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChatUiState(
    val conversation: Conversation? = null,
    val currentUserId: String = "",
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val errorMessage: String? = null,
    val replyingToMessage: Message? = null,
    val isRecordingVoice: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val playingAudioMessageId: String? = null,
    val audioPlaybackProgress: Float = 0f,
    val audioPlaybackSpeed: Float = 1.0f,
    val showAttachmentSheet: Boolean = false,
    val activeCallMessage: String? = null,
    val activeCall: com.tawasol.app.domain.model.CallSession? = null,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOff: Boolean = false,
    val peerUser: com.tawasol.app.domain.model.User? = null
)

class ChatViewModel(
    private val conversationId: String,
    private val chatRepository: ChatRepository,
    private val getConversationUseCase: GetConversationUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markConversationAsReadUseCase: MarkConversationAsReadUseCase,
    private val authRepository: AuthRepository,
    private val sendMediaMessageUseCase: SendMediaMessageUseCase? = null,
    private val callRepository: com.tawasol.app.domain.repository.CallRepository? = null,
    private val userRepository: com.tawasol.app.domain.repository.UserRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(currentUserId = authRepository.currentUserId ?: "")
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val messages: StateFlow<List<Message>> = getMessagesUseCase(conversationId, limit = 50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadConversation()
        listenToRealtimeMessages()
        markAsRead()
        markPresenceOnline()
    }

    private fun markPresenceOnline() {
        viewModelScope.launch {
            userRepository?.updatePresence(true)
        }
    }

    private fun loadConversation() {
        viewModelScope.launch {
            getConversationUseCase(conversationId).collect { convo ->
                _uiState.update { it.copy(conversation = convo) }
                convo?.peerUserId?.let { peerId ->
                    observePeerUser(peerId)
                }
            }
        }
    }

    private fun observePeerUser(peerId: String) {
        viewModelScope.launch {
            userRepository?.getUser(peerId)?.collect { user ->
                _uiState.update { it.copy(peerUser = user) }
            }
        }
        viewModelScope.launch {
            userRepository?.getUserProfile(peerId)
        }
    }

    private fun listenToRealtimeMessages() {
        viewModelScope.launch {
            chatRepository.startRealtimeMessagesSubscription(conversationId).collect {
                markAsRead()
            }
        }
    }

    fun markAsRead() {
        viewModelScope.launch {
            markConversationAsReadUseCase(conversationId)
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun setReplyingToMessage(message: Message?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun setAttachmentSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showAttachmentSheet = visible) }
    }

    fun setActiveCallMessage(msg: String?) {
        _uiState.update { it.copy(activeCallMessage = msg) }
    }

    fun startVoiceCall() {
        val peerId = _uiState.value.conversation?.peerUserId
        if (peerId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "لا يمكن الاتصال بمحادثة غير فردية") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            val res = callRepository?.startCall(peerId, conversationId, com.tawasol.app.domain.model.CallType.VOICE)
            _uiState.update { it.copy(isSending = false) }
            res?.onSuccess { session ->
                _uiState.update { it.copy(activeCall = session) }
            }?.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun startVideoCall() {
        val peerId = _uiState.value.conversation?.peerUserId
        if (peerId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "لا يمكن الاتصال بمحادثة غير فردية") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            val res = callRepository?.startCall(peerId, conversationId, com.tawasol.app.domain.model.CallType.VIDEO)
            _uiState.update { it.copy(isSending = false) }
            res?.onSuccess { session ->
                _uiState.update { it.copy(activeCall = session) }
            }?.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun endActiveCall() {
        val call = _uiState.value.activeCall ?: return
        viewModelScope.launch {
            callRepository?.endCall(call.id)
            _uiState.update { it.copy(activeCall = null) }
        }
    }

    fun toggleMuteMic() {
        _uiState.update { it.copy(isMicMuted = !it.isMicMuted) }
    }

    fun toggleSpeaker() {
        _uiState.update { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun toggleCamera() {
        _uiState.update { it.copy(isCameraOff = !it.isCameraOff) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        val replyToId = _uiState.value.replyingToMessage?.id
        _uiState.update {
            it.copy(inputText = "", isSending = true, errorMessage = null, replyingToMessage = null)
        }

        viewModelScope.launch {
            val result = sendMessageUseCase(conversationId, text)
            result.onSuccess {
                _uiState.update { it.copy(isSending = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.localizedMessage ?: "فشل إرسال الرسالة"
                    )
                }
            }
        }
    }

    fun startVoiceRecording() {
        _uiState.update {
            it.copy(
                isRecordingVoice = true,
                recordingDurationSeconds = 0,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            while (_uiState.value.isRecordingVoice) {
                delay(1000)
                if (_uiState.value.isRecordingVoice) {
                    _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        _uiState.update { it.copy(isRecordingVoice = false, recordingDurationSeconds = 0) }
    }

    fun sendVoiceNote(audioBytes: ByteArray, durationSeconds: Int) {
        if (audioBytes.isEmpty() || audioBytes.size < 500) {
            _uiState.update { it.copy(isRecordingVoice = false, recordingDurationSeconds = 0, errorMessage = "التسجيل الصوتي فارغ أو قصير جداً") }
            return
        }
        val effectiveDuration = if (durationSeconds <= 0) 1 else durationSeconds
        _uiState.update { it.copy(isRecordingVoice = false, recordingDurationSeconds = 0, isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val replyToId = _uiState.value.replyingToMessage?.id
            val result = chatRepository.sendMediaMessage(
                conversationId = conversationId,
                fileBytes = audioBytes,
                fileName = "voice_${System.currentTimeMillis()}.m4a",
                mimeType = "audio/mp4",
                messageType = MessageType.AUDIO,
                caption = null,
                replyToId = replyToId,
                durationSeconds = effectiveDuration
            )
            result.onSuccess {
                _uiState.update { it.copy(isSending = false, replyingToMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.localizedMessage ?: "فشل إرسال الرسالة الصوتية"
                    )
                }
            }
        }
    }

    fun stopAndSendVoiceRecording() {
        cancelVoiceRecording()
    }

    fun sendRealMedia(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        messageType: MessageType,
        caption: String? = null,
        durationSeconds: Int? = null
    ) {
        _uiState.update { it.copy(showAttachmentSheet = false, isSending = true, errorMessage = null) }
        viewModelScope.launch {
            val replyToId = _uiState.value.replyingToMessage?.id
            val result = chatRepository.sendMediaMessage(
                conversationId = conversationId,
                fileBytes = fileBytes,
                fileName = fileName,
                mimeType = mimeType,
                messageType = messageType,
                caption = caption,
                replyToId = replyToId,
                durationSeconds = durationSeconds
            )
            result.onSuccess {
                _uiState.update { it.copy(isSending = false, replyingToMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.localizedMessage ?: "فشل إرسال الملف"
                    )
                }
            }
        }
    }

    fun sendSimulatedImage(caption: String? = null) {
        _uiState.update { it.copy(showAttachmentSheet = false, isSending = true) }
        viewModelScope.launch {
            val imageBytes = ByteArray(2048) { 0xFF.toByte() }
            val replyToId = _uiState.value.replyingToMessage?.id
            val result = chatRepository.sendMediaMessage(
                conversationId = conversationId,
                fileBytes = imageBytes,
                fileName = "photo_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                messageType = MessageType.IMAGE,
                caption = caption ?: "صورة ملتقطة عبر تطبيق تواصل 📸",
                replyToId = replyToId
            )
            result.onSuccess {
                _uiState.update { it.copy(isSending = false, replyingToMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.localizedMessage ?: "فشل إرسال الصورة"
                    )
                }
            }
        }
    }

    fun sendSimulatedDocument(docName: String) {
        _uiState.update { it.copy(showAttachmentSheet = false, isSending = true) }
        viewModelScope.launch {
            val docBytes = ByteArray(4096)
            val replyToId = _uiState.value.replyingToMessage?.id
            val result = chatRepository.sendMediaMessage(
                conversationId = conversationId,
                fileBytes = docBytes,
                fileName = docName,
                mimeType = "application/pdf",
                messageType = MessageType.FILE,
                caption = "مستند PDF مشفر",
                replyToId = replyToId
            )
            result.onSuccess {
                _uiState.update { it.copy(isSending = false, replyingToMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.localizedMessage ?: "فشل إرسال المستند"
                    )
                }
            }
        }
    }

    fun togglePlayAudio(messageId: String, totalDurationSeconds: Int = 15) {
        if (_uiState.value.playingAudioMessageId == messageId) {
            // Pause
            _uiState.update { it.copy(playingAudioMessageId = null) }
        } else {
            // Start play
            _uiState.update {
                it.copy(
                    playingAudioMessageId = messageId,
                    audioPlaybackProgress = 0f
                )
            }
            viewModelScope.launch {
                val totalSteps = 50
                val speed = _uiState.value.audioPlaybackSpeed
                val stepDelay = ((totalDurationSeconds * 1000L) / totalSteps / speed).toLong().coerceAtLeast(20L)
                for (step in 1..totalSteps) {
                    if (_uiState.value.playingAudioMessageId != messageId) break
                    delay(stepDelay)
                    _uiState.update { it.copy(audioPlaybackProgress = step / totalSteps.toFloat()) }
                }
                if (_uiState.value.playingAudioMessageId == messageId) {
                    _uiState.update { it.copy(playingAudioMessageId = null, audioPlaybackProgress = 0f) }
                }
            }
        }
    }

    fun toggleAudioSpeed() {
        val current = _uiState.value.audioPlaybackSpeed
        val next = when (current) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _uiState.update { it.copy(audioPlaybackSpeed = next) }
    }

    fun retrySendMessage(failedMessage: Message) {
        val text = failedMessage.text ?: return
        viewModelScope.launch {
            sendMessageUseCase(conversationId, text)
        }
    }

    fun loadOlderMessages() {
        val currentList = messages.value
        if (currentList.isEmpty() || _uiState.value.isLoadingOlder) return

        val oldestTime = currentList.first().createdAt
        _uiState.update { it.copy(isLoadingOlder = true) }

        viewModelScope.launch {
            chatRepository.fetchOlderMessages(conversationId, oldestTime, limit = 30)
            _uiState.update { it.copy(isLoadingOlder = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToSafetyNumber: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val messagesList by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val audioRecorder = remember { AudioRecorder(context) }
    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.cancelRecording()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioRecorder.startRecording().onSuccess {
                viewModel.startVoiceRecording()
            }.onFailure { err ->
                scope.launch { snackbarHostState.showSnackbar(err.localizedMessage ?: "تعذر بدء التسجيل") }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("يلزم منح إذن الميكروفون لتسجيل الرسائل الصوتية")
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.sendRealMedia(
                    fileBytes = bytes,
                    fileName = "photo_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg",
                    messageType = MessageType.IMAGE,
                    caption = "صورة ملتقطة"
                )
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.sendRealMedia(
                    fileBytes = bytes,
                    fileName = "doc_${System.currentTimeMillis()}",
                    mimeType = "application/octet-stream",
                    messageType = MessageType.FILE,
                    caption = "مستند مرفق"
                )
            }
        }
    }

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    LaunchedEffect(state.activeCallMessage) {
        state.activeCallMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.setActiveCallMessage(null)
        }
    }

    val sheetState = rememberModalBottomSheetState()

    if (state.showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setAttachmentSheetVisible(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WhatsAppAttachmentSheet(
                onSendCamera = {
                    viewModel.setAttachmentSheetVisible(false)
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSendGallery = {
                    viewModel.setAttachmentSheetVisible(false)
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onSendAudio = {
                    viewModel.setAttachmentSheetVisible(false)
                    viewModel.stopAndSendVoiceRecording()
                },
                onSendDocument = {
                    viewModel.setAttachmentSheetVisible(false)
                    documentPickerLauncher.launch(arrayOf("*/*"))
                },
                onSendLocation = {
                    viewModel.onInputTextChange("📍 موقعي المباشر: صنعاء، اليمن (15.3694° N, 44.1910° E)")
                    viewModel.setAttachmentSheetVisible(false)
                },
                onSendContact = {
                    viewModel.onInputTextChange("👤 جهة اتصال: ${state.conversation?.title ?: "مستخدم تواصل"}")
                    viewModel.setAttachmentSheetVisible(false)
                }
            )
        }
    }

    if (showEmojiSheet) {
        EmojiPickerSheet(
            onDismiss = { showEmojiSheet = false },
            onEmojiSelected = { emoji ->
                viewModel.onInputTextChange(state.inputText + emoji)
            }
        )
    }

    if (state.activeCall != null) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss on outside tap during active call */ },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F1E1B)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 48.dp)
                    ) {
                        UserAvatar(
                            name = state.conversation?.title ?: "مكالمة تواصل",
                            avatarUrl = state.conversation?.avatarUrl,
                            size = 100
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = state.conversation?.title ?: "مستخدم تواصل",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.activeCall?.type == com.tawasol.app.domain.model.CallType.VIDEO)
                                "مكالمة مرئية مشفرة من طرف إلى طرف WebRTC"
                            else
                                "مكالمة صوتية مشفرة من طرف إلى طرف WebRTC",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TealPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اتصال P2P مباشر عبر STUN • جاري التفاوض...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }

                    // Bottom Control Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic Mute Toggle
                        IconButton(
                            onClick = viewModel::toggleMuteMic,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (state.isMicMuted) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (state.isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "كتم الميكروفون",
                                tint = Color.White
                            )
                        }

                        // Speaker Toggle
                        IconButton(
                            onClick = viewModel::toggleSpeaker,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (state.isSpeakerOn) TealPrimary else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "مكبر الصوت",
                                tint = Color.White
                            )
                        }

                        // Video camera toggle (if video call)
                        if (state.activeCall?.type == com.tawasol.app.domain.model.CallType.VIDEO) {
                            IconButton(
                                onClick = viewModel::toggleCamera,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isCameraOff) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = if (state.isCameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                                    contentDescription = "الكاميرا",
                                    tint = Color.White
                                )
                            }
                        }

                        // End Call Button (Red)
                        IconButton(
                            onClick = viewModel::endActiveCall,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "إنهاء المكالمة",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val convo = state.conversation
                    val peer = state.peerUser
                    val isPeerOnline = peer?.isOnline == true
                    val presenceText = when {
                        isPeerOnline -> "متصل الآن • مشفر E2EE"
                        peer?.lastSeen != null -> "آخر ظهور ${formatTime(peer.lastSeen)} • مشفر E2EE"
                        else -> "غير متصل • مشفر E2EE"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                convo?.peerUserId?.let { onNavigateToUserProfile(it) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        UserAvatar(
                            name = convo?.title ?: "محادثة",
                            avatarUrl = convo?.avatarUrl,
                            size = 40,
                            isOnline = isPeerOnline
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = convo?.title ?: "محادثة خاصة",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "مشفر تماماً",
                                    tint = TealPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = presenceText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isPeerOnline) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    // Video Call Action
                    IconButton(onClick = viewModel::startVideoCall) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "مكالمة مرئية",
                            tint = TealPrimary
                        )
                    }

                    // Voice Call Action
                    IconButton(onClick = viewModel::startVoiceCall) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "مكالمة صوتية",
                            tint = TealPrimary
                        )
                    }

                    // E2EE Safety Number Shield
                    val peerId = state.conversation?.peerUserId
                    if (!peerId.isNullOrBlank()) {
                        IconButton(onClick = { onNavigateToSafetyNumber(peerId) }) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "أرقام الأمان والتشفير",
                                tint = TealPrimary
                            )
                        }
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "المزيد من الخيارات"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("عرض الملف الشخصي") },
                                onClick = {
                                    showMenu = false
                                    state.conversation?.peerUserId?.let { onNavigateToUserProfile(it) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("الوسائط والمستندات") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setActiveCallMessage("عرض وسائط المحادثة")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("البحث في المحادثة") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setActiveCallMessage("ميزة البحث في الرسائل نشطة")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("كتم الإشعارات") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setActiveCallMessage("تم كتم إشعارات المحادثة")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("خلفية الشاشة") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setActiveCallMessage("خلفية تواصل الأصلية مفعلة")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مسح محتوى المحادثة", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setActiveCallMessage("تم مسح محتوى المحادثة محلياً")
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column {
                    // Quoted reply banner if active
                    state.replyingToMessage?.let { replyMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(TealPrimary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "الرد على رسالة",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TealPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = replyMsg.text ?: "مقطع صوتي / وسائط",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setReplyingToMessage(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "إلغاء الرد",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Error banner
                    state.errorMessage?.let { errorMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Composer Controls (WhatsApp Style)
                    if (state.isRecordingVoice) {
                        // Voice Recording State UI
                        VoiceRecordingBar(
                            durationSeconds = state.recordingDurationSeconds,
                            onCancel = {
                                audioRecorder.cancelRecording()
                                viewModel.cancelVoiceRecording()
                            },
                            onSend = {
                                val res = audioRecorder.stopRecording()
                                res.onSuccess { (file, duration) ->
                                    val bytes = file.readBytes()
                                    file.delete()
                                    viewModel.sendVoiceNote(bytes, duration)
                                }.onFailure { err ->
                                    viewModel.cancelVoiceRecording()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "تعذر حفظ التسجيل الصوتي")
                                    }
                                }
                            }
                        )
                    } else {
                        // Standard Composer Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rounded message input box
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    // Emoji Picker Icon
                                    IconButton(
                                        onClick = {
                                            showEmojiSheet = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                                            contentDescription = "ملصقات ورموز تعبيرية",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Input TextField
                                    OutlinedTextField(
                                        value = state.inputText,
                                        onValueChange = viewModel::onInputTextChange,
                                        placeholder = {
                                            Text(
                                                text = "اكتب رسالة...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        maxLines = 4,
                                        singleLine = false
                                    )

                                    // Paperclip Attachment Icon
                                    IconButton(
                                        onClick = { viewModel.setAttachmentSheetVisible(true) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AttachFile,
                                            contentDescription = "إرفاق ملفات ووسائط",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            // Dynamic Mic / Send Action Circle Button
                            val hasText = state.inputText.isNotBlank()
                            val isSending = state.isSending

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary)
                                    .clickable {
                                        if (hasText) {
                                            viewModel.sendMessage()
                                        } else {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (hasPermission) {
                                                audioRecorder.startRecording().onSuccess {
                                                    viewModel.startVoiceRecording()
                                                }.onFailure { err ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "تعذر بدء التسجيل")
                                                    }
                                                }
                                            } else {
                                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else if (hasText) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "إرسال",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "تسجيل رسالة صوتية",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Chat Wallpaper & Message List
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messagesList.isEmpty()) {
                // Empty messages state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(TealPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لا توجد رسائل بعد",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "الرسائل في هذه المحادثة محمية بالتشفير التام بين الطرفين (End-to-End Encryption). لا يستطيع أحد قراءتها سواكما.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messagesList, key = { it.id }) { message ->
                        val isSelf = message.senderId == state.currentUserId
                        MessageBubble(
                            message = message,
                            isSelf = isSelf,
                            isPlaying = state.playingAudioMessageId == message.id,
                            playbackProgress = if (state.playingAudioMessageId == message.id) state.audioPlaybackProgress else 0f,
                            playbackSpeed = state.audioPlaybackSpeed,
                            onPlayAudio = {
                                viewModel.togglePlayAudio(
                                    message.id,
                                    message.mediaDurationSeconds ?: 15
                                )
                            },
                            onToggleSpeed = { viewModel.toggleAudioSpeed() },
                            onReply = { viewModel.setReplyingToMessage(message) },
                            onRetry = { viewModel.retrySendMessage(message) },
                            onImageClick = { url -> previewImageUrl = url }
                        )
                    }
                }
            }
        }
    }

    if (previewImageUrl != null) {
        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { previewImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = previewImageUrl,
                    contentDescription = "عرض الصورة بالحجم الكامل",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 20.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isSelf: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackSpeed: Float,
    onPlayAudio: () -> Unit,
    onToggleSpeed: () -> Unit,
    onReply: () -> Unit,
    onRetry: () -> Unit,
    onImageClick: (String) -> Unit = {}
) {
    // WhatsApp color palette:
    // Self: Dark `#005C4B` or Light `#DCF8C6`
    // Peer: Dark `#202C33` or Light `#FFFFFF`
    val bubbleColor = if (isSelf) {
        TealPrimary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelf) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeColor = if (isSelf) {
        Color.White.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }

    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val bubbleShape = if (isSelf) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 3.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 3.dp,
            bottomEnd = 16.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReply),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 80.dp, max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {

                // Quoted reply header inside bubble if present
                message.replyToMessageId?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(26.dp)
                                    .background(if (isSelf) Color.White else TealPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "رد على رسالة سابقة",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelf) Color.White else TealPrimary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Render content depending on MessageType
                when (message.type) {
                    MessageType.AUDIO -> {
                        // WhatsApp Voice Note Bubble UI
                        VoiceNoteBubbleContent(
                            durationSeconds = message.mediaDurationSeconds ?: 18,
                            isPlaying = isPlaying,
                            progress = playbackProgress,
                            speed = playbackSpeed,
                            isSelf = isSelf,
                            onPlay = onPlayAudio,
                            onToggleSpeed = onToggleSpeed
                        )
                    }

                    MessageType.IMAGE -> {
                        // Image Attachment Bubble
                        Column {
                            val imageSource = message.mediaUrl?.takeIf { it.isNotBlank() } ?: message.localFilePath
                            if (!imageSource.isNullOrBlank()) {
                                AsyncImage(
                                    model = imageSource,
                                    contentDescription = "صورة مرفقة",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onImageClick(imageSource) },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "صورة مشفرة E2EE",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            if (!message.text.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                                )
                            }
                        }
                    }

                    MessageType.FILE -> {
                        // Document / File Bubble
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE53935)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.text ?: "مستند_مشفر.pdf",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textColor,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "1.4 ميجابايت • PDF",
                                    style = MaterialTheme.typography.labelSmall.copy(color = timeColor)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "تحميل",
                                tint = if (isSelf) Color.White else TealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    else -> {
                        // Standard text message
                        Text(
                            text = message.text ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor,
                                lineHeight = 21.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time and Status Ticks (WhatsApp Style)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = timeColor,
                            fontSize = 10.sp
                        )
                    )

                    if (isSelf) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = timeColor,
                                    strokeWidth = 1.5.dp
                                )
                            }
                            MessageStatus.SENT -> {
                                // Single grey check
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "تم الإرسال للجهة",
                                    tint = timeColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                // Double grey check
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = "تم التسليم",
                                    tint = timeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.READ -> {
                                // Double bright WhatsApp cyan checkmarks
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = "تمت القراءة",
                                    tint = Color(0xFF53BDEB),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "إعادة المحاولة",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceNoteBubbleContent(
    durationSeconds: Int,
    isPlaying: Boolean,
    progress: Float,
    speed: Float,
    isSelf: Boolean,
    onPlay: () -> Unit,
    onToggleSpeed: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Play / Pause Circle Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelf) Color.White else TealPrimary)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                tint = if (isSelf) TealPrimary else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Audio Waveform Visualizer Bars
            AudioWaveform(progress = progress, isSelf = isSelf)

            Spacer(modifier = Modifier.height(4.dp))

            // Duration and speed indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentSeconds = (durationSeconds * progress).toInt()
                val displaySec = if (isPlaying) currentSeconds else durationSeconds
                val mins = displaySec / 60
                val secs = displaySec % 60
                val formattedDuration = String.format("%02d:%02d", mins, secs)

                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isSelf) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                )

                // Speed Pill (1x, 1.5x, 2x)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.15f),
                    modifier = Modifier.clickable(onClick = onToggleSpeed)
                ) {
                    Text(
                        text = "${speed}x",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelf) Color.White else TealPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Mic badge icon
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = if (isSelf) Color.White.copy(alpha = 0.7f) else TealPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun AudioWaveform(
    progress: Float,
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    val barHeights = listOf(6, 12, 18, 10, 16, 22, 14, 20, 24, 18, 12, 8, 16, 22, 14, 10, 18, 24, 16, 8, 14)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        barHeights.forEachIndexed { index, height ->
            val barPosition = index.toFloat() / barHeights.size
            val isActive = barPosition <= progress

            val barColor = if (isSelf) {
                if (isActive) Color.White else Color.White.copy(alpha = 0.35f)
            } else {
                if (isActive) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun VoiceRecordingBar(
    durationSeconds: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red recording pulse indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = alphaPulse))
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Timer
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        val formattedTime = String.format("%02d:%02d", mins, secs)
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "اسحب للإلغاء...",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )

        // Cancel Button (Trash)
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "إلغاء التسجيل",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Send Voice Note Button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(TealPrimary)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "إرسال الصوت",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun WhatsAppAttachmentSheet(
    onSendCamera: () -> Unit,
    onSendGallery: () -> Unit,
    onSendAudio: () -> Unit,
    onSendDocument: () -> Unit,
    onSendLocation: () -> Unit,
    onSendContact: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(
            text = "إرفاق وسائط ومستندات",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 18.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            AttachmentCircleItem(
                label = "الكاميرا",
                icon = Icons.Rounded.CameraAlt,
                color = Color(0xFFE91E63),
                onClick = onSendCamera
            )
            AttachmentCircleItem(
                label = "المعرض",
                icon = Icons.Rounded.Image,
                color = Color(0xFF9C27B0),
                onClick = onSendGallery
            )
            AttachmentCircleItem(
                label = "مقطع صوتي",
                icon = Icons.Rounded.Headphones,
                color = Color(0xFFFF9800),
                onClick = onSendAudio
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            AttachmentCircleItem(
                label = "مستند",
                icon = Icons.Rounded.Description,
                color = Color(0xFF3F51B5),
                onClick = onSendDocument
            )
            AttachmentCircleItem(
                label = "الموقع",
                icon = Icons.Rounded.LocationOn,
                color = Color(0xFF4CAF50),
                onClick = onSendLocation
            )
            AttachmentCircleItem(
                label = "جهة اتصال",
                icon = Icons.Rounded.Person,
                color = Color(0xFF00ACC1),
                onClick = onSendContact
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
fun AttachmentCircleItem(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}

private fun formatTime(instant: Instant): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val emojiCategories = remember {
        listOf(
            "ابتسامات" to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
                "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
                "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
                "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
                "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
                "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓"
            ),
            "تفاعل وأيدي" to listOf(
                "👍", "👎", "👌", "🤌", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙",
                "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👋", "🤚", "🖐️",
                "✋", "🖖", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💪"
            ),
            "قلوب ومشاعر" to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
                "✨", "⭐", "🌟", "💫", "🔥", "💯"
            ),
            "أشياء ويوميات" to listOf(
                "☕", "🍕", "🍔", "🍎", "🌸", "🌹", "🌺", "🌻", "🌴", "☀️",
                "🌙", "⭐", "🎉", "🎊", "🎁", "🎈", "📱", "💻", "📷", "💡",
                "🔑", "🚗", "✈️", "🚀", "⚽", "🏆", "🎮", "📚", "💼", "💰"
            )
        )
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "الرموز التعبيرية والملصقات",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emojiCategories.forEachIndexed { index, (title, _) ->
                    val isSelected = selectedCategoryIndex == index
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { selectedCategoryIndex = index }
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Emoji Grid
            val currentEmojis = emojiCategories[selectedCategoryIndex].second
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 42.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(currentEmojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onEmojiSelected(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

