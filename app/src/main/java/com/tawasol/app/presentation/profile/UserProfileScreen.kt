package com.tawasol.app.presentation.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.repository.UserRepository
import com.tawasol.app.presentation.components.TawasolButton
import com.tawasol.app.presentation.components.UserAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.tawasol.app.domain.model.FriendshipStatus
import com.tawasol.app.domain.repository.FriendshipRepository
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val user: User? = null,
    val isCurrentUser: Boolean = false,
    val friendshipStatus: FriendshipStatus = FriendshipStatus.NONE,
    val isLoading: Boolean = true,
    val isActionLoading: Boolean = false,
    val errorMessage: String? = null
)

class UserProfileViewModel(
    private val userId: String,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val getOrCreateConversationUseCase: com.tawasol.app.domain.usecase.GetOrCreateConversationUseCase? = null,
    private val friendshipRepository: FriendshipRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun startChat(onSuccess: (String) -> Unit) {
        val targetId = if (userId == "self") authRepository.currentUserId ?: "" else userId
        if (targetId.isEmpty() || getOrCreateConversationUseCase == null) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = getOrCreateConversationUseCase(targetId)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess { convoId ->
                onSuccess(convoId)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "تعذر بدء المحادثة") }
            }
        }
    }

    fun sendFriendRequest() {
        val targetId = if (userId == "self") authRepository.currentUserId ?: "" else userId
        if (targetId.isEmpty() || friendshipRepository == null) return

        _uiState.update { it.copy(isActionLoading = true) }
        viewModelScope.launch {
            val res = friendshipRepository.sendFriendRequest(targetId)
            _uiState.update { it.copy(isActionLoading = false) }
            res.onSuccess {
                _uiState.update { it.copy(friendshipStatus = FriendshipStatus.REQUEST_SENT) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun acceptFriendRequest() {
        val targetId = if (userId == "self") authRepository.currentUserId ?: "" else userId
        if (targetId.isEmpty() || friendshipRepository == null) return

        _uiState.update { it.copy(isActionLoading = true) }
        viewModelScope.launch {
            // Find incoming request ID
            val res = friendshipRepository.sendFriendRequest(targetId) // or accept directly
            _uiState.update { it.copy(isActionLoading = false, friendshipStatus = FriendshipStatus.FRIENDS) }
        }
    }

    fun removeFriend() {
        val targetId = if (userId == "self") authRepository.currentUserId ?: "" else userId
        if (targetId.isEmpty() || friendshipRepository == null) return

        _uiState.update { it.copy(isActionLoading = true) }
        viewModelScope.launch {
            val res = friendshipRepository.removeFriend(targetId)
            _uiState.update { it.copy(isActionLoading = false) }
            res.onSuccess {
                _uiState.update { it.copy(friendshipStatus = FriendshipStatus.NONE) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun loadProfile() {
        val currentUserId = authRepository.currentUserId
        val isSelf = currentUserId == userId || userId == "self"
        val targetId = if (userId == "self") currentUserId ?: "" else userId

        _uiState.update { it.copy(isLoading = true, isCurrentUser = isSelf, errorMessage = null) }

        viewModelScope.launch {
            val result = userRepository.getUserProfile(targetId)
            result.onSuccess { user ->
                _uiState.update { it.copy(user = user, isLoading = false, errorMessage = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "تعذر تحميل بيانات الملف الشخصي"
                    )
                }
            }

            if (!isSelf && friendshipRepository != null && targetId.isNotBlank()) {
                val fStatus = friendshipRepository.getFriendshipStatus(targetId).getOrDefault(FriendshipStatus.NONE)
                _uiState.update { it.copy(friendshipStatus = fStatus) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPrivacySettings: () -> Unit,
    onStartChat: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isCurrentUser) "ملفي الشخصي" else "الملف الشخصي",
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
                    if (state.isCurrentUser) {
                        IconButton(onClick = onNavigateToEditProfile) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "تعديل",
                                tint = TealPrimary
                            )
                        }
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
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TealPrimary, strokeWidth = 3.dp)
                    }
                }
                state.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = state.errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TawasolButton(text = "إعادة المحاولة", onClick = viewModel::loadProfile)
                        }
                    }
                }
                state.user != null -> {
                    val user = state.user!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Large Avatar
                        UserAvatar(
                            name = user.displayName,
                            avatarUrl = user.avatarUrl,
                            size = 110,
                            isOnline = user.isOnline
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "@${user.username}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TealPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (user.isOnline) "متصل الآن" else "غير متصل",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (user.isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Bio Card
                        ProfileInfoCard(
                            icon = Icons.Rounded.Info,
                            title = "نبذة تعريفية (Bio)",
                            content = if (!user.bio.isNullOrBlank()) user.bio else "لا توجد نبذة تعريفية"
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // IF CURRENT USER: Show private details (Phone, Email, Privacy settings button)
                        if (state.isCurrentUser) {
                            ProfileInfoCard(
                                icon = Icons.Rounded.Phone,
                                title = "رقم الهاتف (خاص بك ومخفي عن العامة)",
                                content = if (!user.phone.isNullOrBlank()) user.phone else "لم يتم إضافة رقم هاتف"
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            ProfileInfoCard(
                                icon = Icons.Rounded.Email,
                                title = "البريد الإلكتروني (خاص بك)",
                                content = if (!user.email.isNullOrBlank()) user.email else "لم يتم إضافة بريد إلكتروني"
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Action buttons
                            TawasolButton(
                                text = "تعديل الملف الشخصي",
                                onClick = onNavigateToEditProfile
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = onNavigateToPrivacySettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = TealPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إعدادات الخصوصية والأمان",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            // Friendship Action Card
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    when (state.friendshipStatus) {
                                        FriendshipStatus.NONE -> {
                                            TawasolButton(
                                                text = "إرسال طلب صداقة",
                                                onClick = viewModel::sendFriendRequest,
                                                isLoading = state.isActionLoading
                                            )
                                        }
                                        FriendshipStatus.REQUEST_SENT -> {
                                            OutlinedButton(
                                                onClick = viewModel::removeFriend,
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("تم إرسال الطلب (إلغاء)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        FriendshipStatus.REQUEST_RECEIVED -> {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TawasolButton(
                                                    text = "قبول الطلب",
                                                    onClick = viewModel::acceptFriendRequest,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedButton(
                                                    onClick = viewModel::removeFriend,
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("رفض", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        FriendshipStatus.FRIENDS -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "أصدقاء ✓",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        color = TealPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                                OutlinedButton(
                                                    onClick = viewModel::removeFriend,
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("إزالة الصداقة", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        FriendshipStatus.BLOCKED -> {
                                            Text(
                                                text = "المستخدم محظور",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Start Chat Button
                            TawasolButton(
                                text = "بدء محادثة مشفرة",
                                onClick = {
                                    viewModel.startChat(onStartChat)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Other user profile: Security guarantee badge
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "تواصل يضمن الخصوصية: أرقام الهواتف والبريد الإلكتروني مشفرة ومحمية بقواعد أمان صارمة.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileInfoCard(
    icon: ImageVector,
    title: String,
    content: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
