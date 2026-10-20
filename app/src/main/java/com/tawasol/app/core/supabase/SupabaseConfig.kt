package com.tawasol.app.core.supabase

import com.tawasol.app.BuildConfig

object SupabaseConfig {
    // Loaded from BuildConfig or configured at runtime.
    // Replace with your actual Supabase project URL and anon public key.
    // NEVER put the secret service_role key here!
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    const val PROFILES_TABLE = "profiles"
    const val USER_CONTACTS_TABLE = "user_contacts"
    const val PRIVACY_SETTINGS_TABLE = "privacy_settings"
    const val CONVERSATIONS_TABLE = "conversations"
    const val CONVERSATION_MEMBERS_TABLE = "conversation_members"
    const val MESSAGES_TABLE = "messages"
    const val ATTACHMENTS_TABLE = "message_attachments"
    const val STATUSES_TABLE = "statuses"
    const val CALLS_TABLE = "calls"
    const val BLOCKS_TABLE = "blocks"
    const val REPORTS_TABLE = "reports"

    const val AVATARS_BUCKET = "avatars"
}
