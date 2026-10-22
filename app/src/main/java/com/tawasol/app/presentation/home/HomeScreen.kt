package com.tawasol.app.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.Conversation
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.usecase.GetConversationsUseCase
import com.tawasol.app.presentation.components.TawasolTopAppBar
import com.tawasol.app.presentation.home.tabs.CallsTab
import com.tawasol.app.presentation.home.tabs.ChatsTab
import com.tawasol.app.presentation.home.tabs.SettingsTab
import com.tawasol.app.presentation.home.tabs.StatusTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeTab(val title: String, val icon: ImageVector) {
    CHATS("المحادثات", Icons.Rounded.Chat),
    CALLS("المكالمات", Icons.Rounded.Call),
    STATUS("الحالات", Icons.Rounded.Timeline),
    SETTINGS("الإعدادات", Icons.Rounded.Settings)
}

data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.CHATS,
    val searchQuery: String = "",
    val currentUser: User? = null
)

class HomeViewModel(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val authRepository: AuthRepository,
    private val chatRepository: com.tawasol.app.domain.repository.ChatRepository? = null,
    private val ensureE2EESetupUseCase: com.tawasol.app.domain.usecase.EnsureE2EESetupUseCase? = null,
    val statusRepository: com.tawasol.app.domain.repository.StatusRepository? = null
) : ViewModel() {

    val statusViewModel: com.tawasol.app.presentation.home.tabs.StatusViewModel? =
        statusRepository?.let { com.tawasol.app.presentation.home.tabs.StatusViewModel(it) }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val conversations: StateFlow<List<Conversation>> = getConversationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshCurrentUser()
        syncConversations()
        setupE2EE()
    }

    private fun setupE2EE() {
        viewModelScope.launch {
            try {
                ensureE2EESetupUseCase?.invoke() ?: chatRepository?.ensureE2EESetup()
            } catch (_: Exception) {
                // Keep local offline keystore active
            }
        }
    }

    fun syncConversations() {
        viewModelScope.launch {
            try {
                chatRepository?.syncConversations()
            } catch (e: Exception) {
                // Ignore offline sync errors
            }
        }
    }

    fun refreshCurrentUser() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.update { it.copy(currentUser = user) }
        }
    }

    fun selectTab(tab: HomeTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == HomeTab.SETTINGS) {
            refreshCurrentUser()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onSuccess()
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChatDetail: (String) -> Unit,
    onNavigateToNewChat: () -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPrivacySettings: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentUser()
    }

    Scaffold(
        topBar = {
            TawasolTopAppBar(
                title = when (state.selectedTab) {
                    HomeTab.CHATS -> "تواصل"
                    HomeTab.CALLS -> "المكالمات"
                    HomeTab.STATUS -> "الحالات"
                    HomeTab.SETTINGS -> "الإعدادات"
                },
                actions = {
                    IconButton(onClick = onNavigateToUserSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "بحث عن مستخدمين",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (state.currentUser != null) {
                        IconButton(onClick = { onNavigateToProfile("self") }) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "ملفي الشخصي",
                                tint = TealPrimary
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = TealPrimary,
                            indicatorColor = TealPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.selectedTab == HomeTab.CHATS) {
                FloatingActionButton(
                    onClick = onNavigateToUserSearch,
                    containerColor = TealPrimary,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "محادثة جديدة"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            when (state.selectedTab) {
                HomeTab.CHATS -> ChatsTab(
                    conversations = conversations,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onConversationClick = onNavigateToChatDetail,
                    onNewChatClick = onNavigateToUserSearch
                )
                HomeTab.CALLS -> CallsTab()
                HomeTab.STATUS -> StatusTab(
                    currentUser = state.currentUser,
                    statusViewModel = viewModel.statusViewModel
                )
                HomeTab.SETTINGS -> SettingsTab(
                    currentUser = state.currentUser,
                    onProfileClick = { onNavigateToProfile("self") },
                    onPrivacyClick = onNavigateToPrivacySettings,
                    onEditProfileClick = onNavigateToEditProfile,
                    onLogoutClick = {
                        viewModel.logout(onSuccess = onNavigateToLogin)
                    }
                )
            }
        }
    }
}
