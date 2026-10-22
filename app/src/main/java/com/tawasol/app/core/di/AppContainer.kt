package com.tawasol.app.core.di

import android.content.Context
import com.tawasol.app.core.database.TawasolDatabase
import com.tawasol.app.core.network.ConnectivityNetworkMonitor
import com.tawasol.app.core.network.NetworkMonitor
import com.tawasol.app.core.security.E2EEManager
import com.tawasol.app.core.security.KeystoreManager
import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.data.repository.AuthRepositoryImpl
import com.tawasol.app.data.repository.ChatRepositoryImpl
import com.tawasol.app.data.repository.StatusRepositoryImpl
import com.tawasol.app.data.repository.UserRepositoryImpl
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.repository.ChatRepository
import com.tawasol.app.domain.repository.StatusRepository
import com.tawasol.app.domain.repository.UserRepository
import com.tawasol.app.domain.usecase.AddGroupMemberUseCase
import com.tawasol.app.domain.usecase.CreateGroupUseCase
import com.tawasol.app.domain.usecase.EnsureE2EESetupUseCase
import com.tawasol.app.domain.usecase.GetConversationUseCase
import com.tawasol.app.domain.usecase.GetConversationsUseCase
import com.tawasol.app.domain.usecase.GetGroupMembersUseCase
import com.tawasol.app.domain.usecase.GetMessagesUseCase
import com.tawasol.app.domain.usecase.GetOrCreateConversationUseCase
import com.tawasol.app.domain.usecase.GetPrivacySettingsUseCase
import com.tawasol.app.domain.usecase.GetSafetyNumberUseCase
import com.tawasol.app.domain.usecase.LoginUseCase
import com.tawasol.app.domain.usecase.MarkConversationAsReadUseCase
import com.tawasol.app.domain.usecase.RegisterUseCase
import com.tawasol.app.domain.usecase.RemoveGroupMemberUseCase
import com.tawasol.app.domain.usecase.SearchUsersUseCase
import com.tawasol.app.domain.usecase.SendMessageUseCase
import com.tawasol.app.domain.usecase.SendMediaMessageUseCase
import com.tawasol.app.domain.usecase.UpdatePrivacySettingsUseCase
import com.tawasol.app.domain.usecase.UpdateProfileUseCase
import com.tawasol.app.domain.usecase.UploadAvatarUseCase

interface AppContainer {
    val database: TawasolDatabase
    val keystoreManager: KeystoreManager
    val e2eeManager: E2EEManager
    val supabaseProvider: SupabaseClientProvider
    val networkMonitor: NetworkMonitor
    val authRepository: AuthRepository
    val chatRepository: ChatRepository
    val userRepository: UserRepository
    val statusRepository: StatusRepository
    val loginUseCase: LoginUseCase
    val registerUseCase: RegisterUseCase
    val searchUsersUseCase: SearchUsersUseCase
    val updateProfileUseCase: UpdateProfileUseCase
    val uploadAvatarUseCase: UploadAvatarUseCase
    val getPrivacySettingsUseCase: GetPrivacySettingsUseCase
    val updatePrivacySettingsUseCase: UpdatePrivacySettingsUseCase
    val getConversationsUseCase: GetConversationsUseCase
    val getConversationUseCase: GetConversationUseCase
    val getMessagesUseCase: GetMessagesUseCase
    val sendMessageUseCase: SendMessageUseCase
    val sendMediaMessageUseCase: SendMediaMessageUseCase
    val getOrCreateConversationUseCase: GetOrCreateConversationUseCase
    val markConversationAsReadUseCase: MarkConversationAsReadUseCase
    val createGroupUseCase: CreateGroupUseCase
    val addGroupMemberUseCase: AddGroupMemberUseCase
    val removeGroupMemberUseCase: RemoveGroupMemberUseCase
    val getGroupMembersUseCase: GetGroupMembersUseCase
    val ensureE2EESetupUseCase: EnsureE2EESetupUseCase
    val getSafetyNumberUseCase: GetSafetyNumberUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: TawasolDatabase by lazy {
        TawasolDatabase.getInstance(context)
    }

    override val keystoreManager: KeystoreManager by lazy {
        KeystoreManager(context)
    }

    override val e2eeManager: E2EEManager by lazy {
        E2EEManager(keystoreManager)
    }

    override val supabaseProvider: SupabaseClientProvider by lazy {
        SupabaseClientProvider()
    }

    override val networkMonitor: NetworkMonitor by lazy {
        ConnectivityNetworkMonitor(context)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(supabaseProvider, database, keystoreManager)
    }

    override val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(
            database = database,
            supabaseProvider = supabaseProvider,
            networkMonitor = networkMonitor,
            e2eeManager = e2eeManager,
            keystoreManager = keystoreManager,
            currentUserIdProvider = { keystoreManager.getCurrentUserId() }
        )
    }

    override val userRepository: UserRepository by lazy {
        UserRepositoryImpl(database, supabaseProvider, keystoreManager)
    }

    override val statusRepository: StatusRepository by lazy {
        StatusRepositoryImpl(
            supabaseProvider = supabaseProvider,
            currentUserIdProvider = { keystoreManager.getCurrentUserId() }
        )
    }

    override val loginUseCase: LoginUseCase by lazy {
        LoginUseCase(authRepository)
    }

    override val registerUseCase: RegisterUseCase by lazy {
        RegisterUseCase(authRepository)
    }

    override val searchUsersUseCase: SearchUsersUseCase by lazy {
        SearchUsersUseCase(userRepository)
    }

    override val updateProfileUseCase: UpdateProfileUseCase by lazy {
        UpdateProfileUseCase(userRepository)
    }

    override val uploadAvatarUseCase: UploadAvatarUseCase by lazy {
        UploadAvatarUseCase(userRepository)
    }

    override val getPrivacySettingsUseCase: GetPrivacySettingsUseCase by lazy {
        GetPrivacySettingsUseCase(userRepository)
    }

    override val updatePrivacySettingsUseCase: UpdatePrivacySettingsUseCase by lazy {
        UpdatePrivacySettingsUseCase(userRepository)
    }

    override val getConversationsUseCase: GetConversationsUseCase by lazy {
        GetConversationsUseCase(chatRepository)
    }

    override val getConversationUseCase: GetConversationUseCase by lazy {
        GetConversationUseCase(chatRepository)
    }

    override val getMessagesUseCase: GetMessagesUseCase by lazy {
        GetMessagesUseCase(chatRepository)
    }

    override val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(chatRepository)
    }

    override val sendMediaMessageUseCase: SendMediaMessageUseCase by lazy {
        SendMediaMessageUseCase(chatRepository)
    }

    override val getOrCreateConversationUseCase: GetOrCreateConversationUseCase by lazy {
        GetOrCreateConversationUseCase(chatRepository)
    }

    override val markConversationAsReadUseCase: MarkConversationAsReadUseCase by lazy {
        MarkConversationAsReadUseCase(chatRepository)
    }

    override val createGroupUseCase: CreateGroupUseCase by lazy {
        CreateGroupUseCase(chatRepository)
    }

    override val addGroupMemberUseCase: AddGroupMemberUseCase by lazy {
        AddGroupMemberUseCase(chatRepository)
    }

    override val removeGroupMemberUseCase: RemoveGroupMemberUseCase by lazy {
        RemoveGroupMemberUseCase(chatRepository)
    }

    override val getGroupMembersUseCase: GetGroupMembersUseCase by lazy {
        GetGroupMembersUseCase(chatRepository)
    }

    override val ensureE2EESetupUseCase: EnsureE2EESetupUseCase by lazy {
        EnsureE2EESetupUseCase(chatRepository)
    }

    override val getSafetyNumberUseCase: GetSafetyNumberUseCase by lazy {
        GetSafetyNumberUseCase(chatRepository)
    }
}
