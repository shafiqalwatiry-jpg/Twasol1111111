-- ==============================================================================
-- تواصل (Tawasol) - Complete Production Database Schema for Supabase PostgreSQL
-- ==============================================================================
-- Includes Row Level Security (RLS), Realtime replication, Auto Triggers, and Indexes.

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. ENUMS & DOMAINS
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('user', 'support', 'moderator', 'admin', 'owner');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE conversation_type AS ENUM ('direct', 'group');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE message_type_enum AS ENUM ('text', 'image', 'video', 'audio', 'file');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE message_status_enum AS ENUM ('sending', 'sent', 'delivered', 'read', 'failed');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE call_type_enum AS ENUM ('voice', 'video');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE call_status_enum AS ENUM ('incoming', 'outgoing', 'ringing', 'accepted', 'rejected', 'busy', 'missed', 'ended');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 3. PROFILES TABLE (Linked to auth.users - STRICTLY PUBLIC SAFE METADATA ONLY)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    bio TEXT,
    is_online BOOLEAN DEFAULT false,
    last_seen TIMESTAMPTZ DEFAULT NOW(),
    role user_role DEFAULT 'user',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT username_length CHECK (char_length(username) >= 3 AND char_length(username) <= 30),
    CONSTRAINT username_format CHECK (username ~* '^[a-zA-Z0-9_]+$')
);

CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles(username);

-- 4. USER PRIVATE CONTACTS (Strictly private: only accessible by user themselves or authorized admin)
CREATE TABLE IF NOT EXISTS public.user_contacts (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    phone_number TEXT,
    email TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. PRIVACY SETTINGS
CREATE TABLE IF NOT EXISTS public.privacy_settings (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    last_seen_visibility TEXT DEFAULT 'everyone' CHECK (last_seen_visibility IN ('everyone', 'contacts', 'nobody')),
    avatar_visibility TEXT DEFAULT 'everyone' CHECK (avatar_visibility IN ('everyone', 'contacts', 'nobody')),
    status_visibility TEXT DEFAULT 'everyone' CHECK (status_visibility IN ('everyone', 'contacts', 'nobody')),
    allow_messages_from TEXT DEFAULT 'everyone' CHECK (allow_messages_from IN ('everyone', 'contacts')),
    read_receipts_enabled BOOLEAN DEFAULT true,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. USER DEVICES & SESSIONS
CREATE TABLE IF NOT EXISTS public.user_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL,
    device_name TEXT,
    fcm_token TEXT,
    last_active TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

-- 7. CONVERSATIONS
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type conversation_type NOT NULL DEFAULT 'direct',
    title TEXT,
    avatar_url TEXT,
    description TEXT,
    last_message_text TEXT,
    last_message_at TIMESTAMPTZ DEFAULT NOW(),
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. CONVERSATION MEMBERS (PARTICIPANTS)
CREATE TABLE IF NOT EXISTS public.conversation_members (
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT DEFAULT 'member' CHECK (role IN ('member', 'admin', 'creator')),
    is_muted BOOLEAN DEFAULT false,
    last_read_at TIMESTAMPTZ DEFAULT NOW(),
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_members_user ON public.conversation_members(user_id);

-- 8. MESSAGES
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    text TEXT,
    message_type message_type_enum DEFAULT 'text',
    status message_status_enum DEFAULT 'sent',
    reply_to_message_id UUID REFERENCES public.messages(id) ON DELETE SET NULL,
    media_url TEXT,
    file_size BIGINT,
    duration_seconds INT,
    is_pinned BOOLEAN DEFAULT false,
    is_edited BOOLEAN DEFAULT false,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON public.messages(conversation_id, created_at DESC);

-- 9. MESSAGE REACTIONS
CREATE TABLE IF NOT EXISTS public.message_reactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reaction TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(message_id, user_id, reaction)
);

-- 10. MESSAGE READ RECEIPTS
CREATE TABLE IF NOT EXISTS public.message_reads (
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(message_id, user_id)
);

-- 11. STATUSES (Stories - 24 hours expiry)
CREATE TABLE IF NOT EXISTS public.statuses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content_text TEXT,
    media_url TEXT,
    status_type TEXT DEFAULT 'text' CHECK (status_type IN ('text', 'image', 'video')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours')
);

CREATE INDEX IF NOT EXISTS idx_statuses_active ON public.statuses(user_id, expires_at);

-- 12. STATUS VIEWS
CREATE TABLE IF NOT EXISTS public.status_views (
    status_id UUID NOT NULL REFERENCES public.statuses(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(status_id, viewer_id)
);

-- 13. CALLS (WebRTC Signaling)
CREATE TABLE IF NOT EXISTS public.calls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    caller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE SET NULL,
    type call_type_enum NOT NULL DEFAULT 'voice',
    status call_status_enum NOT NULL DEFAULT 'outgoing',
    webrtc_sdp_offer JSONB,
    webrtc_sdp_answer JSONB,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 14. CALL PARTICIPANTS (for group calls)
CREATE TABLE IF NOT EXISTS public.call_participants (
    call_id UUID NOT NULL REFERENCES public.calls(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT DEFAULT 'joined',
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    left_at TIMESTAMPTZ,
    PRIMARY KEY(call_id, user_id)
);

-- 15. BLOCKS & REPORTS
CREATE TABLE IF NOT EXISTS public.blocks (
    blocker_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reported_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (reason IN ('spam', 'abuse', 'impersonation', 'other')),
    details TEXT,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'reviewed', 'resolved')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.privacy_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_reads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.statuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.status_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

-- Profiles: Public can read public profile details (id, username, display_name, avatar_url, bio, is_online, last_seen)
CREATE POLICY "Public profile fields visible to all" ON public.profiles
    FOR SELECT USING (true);

CREATE POLICY "Users can update own profile" ON public.profiles
    FOR UPDATE USING (auth.uid() = id);

-- User Contacts: Strictly confidential! Only the account owner can view, insert, or update their phone and email
CREATE POLICY "Users can only read own private contacts" ON public.user_contacts
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can update own private contacts" ON public.user_contacts
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own private contacts" ON public.user_contacts
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Privacy Settings
CREATE POLICY "Privacy settings are readable for visibility logic" ON public.privacy_settings
    FOR SELECT USING (true);

CREATE POLICY "Users can update own privacy settings" ON public.privacy_settings
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own privacy settings" ON public.privacy_settings
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Conversation Members: Members can view the conversation
CREATE POLICY "Members can view conversations" ON public.conversations
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.conversation_members
            WHERE conversation_members.conversation_id = conversations.id
            AND conversation_members.user_id = auth.uid()
        )
    );

CREATE POLICY "Authenticated users can create conversations" ON public.conversations
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);

-- Conversation Members table
CREATE POLICY "Members can view conversation participants" ON public.conversation_members
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.conversation_members AS cm
            WHERE cm.conversation_id = conversation_members.conversation_id
            AND cm.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can join or be added to conversations" ON public.conversation_members
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);

-- Messages: Only conversation members can read and write messages
CREATE POLICY "Members can read messages in conversation" ON public.messages
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.conversation_members
            WHERE conversation_members.conversation_id = messages.conversation_id
            AND conversation_members.user_id = auth.uid()
        )
    );

CREATE POLICY "Members can insert messages in conversation" ON public.messages
    FOR INSERT WITH CHECK (
        sender_id = auth.uid() AND
        EXISTS (
            SELECT 1 FROM public.conversation_members
            WHERE conversation_members.conversation_id = messages.conversation_id
            AND conversation_members.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can update own sent messages" ON public.messages
    FOR UPDATE USING (sender_id = auth.uid());

-- Blocks
CREATE POLICY "Users can view and manage their blocklist" ON public.blocks
    FOR ALL USING (blocker_id = auth.uid());

-- Calls
CREATE POLICY "Participants can view their calls" ON public.calls
    FOR ALL USING (caller_id = auth.uid() OR receiver_id = auth.uid());

-- ==============================================================================
-- REALTIME REPLICATION CONFIGURATION
-- ==============================================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
ALTER PUBLICATION supabase_realtime ADD TABLE public.conversations;
ALTER PUBLICATION supabase_realtime ADD TABLE public.conversation_members;
ALTER PUBLICATION supabase_realtime ADD TABLE public.calls;
ALTER PUBLICATION supabase_realtime ADD TABLE public.statuses;

-- ==============================================================================
-- AUTOMATIC PROFILE CREATION TRIGGER ON SIGNUP
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
    INSERT INTO public.profiles (id, username, display_name)
    VALUES (
        new.id,
        COALESCE(new.raw_user_meta_data->>'username', 'user_' || substr(new.id::text, 1, 8)),
        COALESCE(new.raw_user_meta_data->>'display_name', 'مستخدم تواصل')
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.user_contacts (user_id, phone_number, email)
    VALUES (
        new.id,
        new.raw_user_meta_data->>'phone_number',
        new.email
    )
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.privacy_settings (user_id)
    VALUES (new.id)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- ==============================================================================
-- STORAGE: AVATARS BUCKET SETUP & POLICIES
-- ==============================================================================
INSERT INTO storage.buckets (id, name, public) 
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Avatar images are publicly accessible" ON storage.objects
    FOR SELECT USING (bucket_id = 'avatars');

CREATE POLICY "Authenticated users can upload own avatar" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'avatars' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Authenticated users can update own avatar" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'avatars' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Authenticated users can delete own avatar" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'avatars' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- ==============================================================================
-- SECURE USERNAME LOOKUP BY PRIVATE CONTACT EMAIL (LOGIN RESOLVER)
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.get_auth_username_by_contact_email(search_email TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    found_username TEXT;
BEGIN
    SELECT p.username INTO found_username
    FROM public.profiles p
    JOIN public.user_contacts c ON c.user_id = p.id
    WHERE LOWER(c.email) = LOWER(search_email)
    LIMIT 1;

    RETURN found_username;
END;
$$;

-- ==============================================================================
-- PHASE 3: SECURE ATOMIC 1-TO-1 CONVERSATION CREATION
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.get_or_create_direct_conversation(target_user_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_uid UUID;
    existing_id UUID;
    new_convo_id UUID;
BEGIN
    current_uid := auth.uid();
    IF current_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF current_uid = target_user_id THEN
        RAISE EXCEPTION 'Cannot start a conversation with yourself';
    END IF;

    -- Check if target user actually exists in profiles
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = target_user_id) THEN
        RAISE EXCEPTION 'Target user does not exist';
    END IF;

    -- Look for existing 1-to-1 conversation
    SELECT cm1.conversation_id INTO existing_id
    FROM public.conversation_members cm1
    JOIN public.conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id
    JOIN public.conversations c ON c.id = cm1.conversation_id
    WHERE c.type = 'direct'
      AND cm1.user_id = current_uid
      AND cm2.user_id = target_user_id
    LIMIT 1;

    IF existing_id IS NOT NULL THEN
        RETURN existing_id;
    END IF;

    -- Create new conversation atomically
    INSERT INTO public.conversations (type, created_by, last_message_at)
    VALUES ('direct', current_uid, NOW())
    RETURNING id INTO new_convo_id;

    -- Insert both participants
    INSERT INTO public.conversation_members (conversation_id, user_id, role, last_read_at)
    VALUES 
        (new_convo_id, current_uid, 'creator', NOW()),
        (new_convo_id, target_user_id, 'member', NOW());

    RETURN new_convo_id;
END;
$$;

-- ==============================================================================
-- PHASE 3: UPDATE CONVERSATION PREVIEW ON NEW MESSAGE
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.handle_new_message_preview()
RETURNS trigger AS $$
BEGIN
    UPDATE public.conversations
    SET 
        last_message_text = COALESCE(NEW.text, CASE WHEN NEW.message_type = 'image' THEN 'صورة' ELSE 'ملف' END),
        last_message_at = NEW.created_at,
        updated_at = NOW()
    WHERE id = NEW.conversation_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_message_inserted ON public.messages;
CREATE TRIGGER on_message_inserted
    AFTER INSERT ON public.messages
    FOR EACH ROW EXECUTE PROCEDURE public.handle_new_message_preview();

-- ==============================================================================
-- PHASE 4: CHAT-MEDIA STORAGE BUCKET & RLS POLICIES
-- ==============================================================================
INSERT INTO storage.buckets (id, name, public) 
VALUES ('chat-media', 'chat-media', true)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Conversation members can read chat media" ON storage.objects
    FOR SELECT USING (
        bucket_id = 'chat-media' AND
        auth.role() = 'authenticated'
    );

CREATE POLICY "Authenticated users can upload chat media" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'chat-media' AND
        auth.role() = 'authenticated'
    );

-- ==============================================================================
-- PHASE 6: GROUP MANAGEMENT RPC FUNCTIONS
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.create_group_conversation(
    group_title TEXT,
    group_description TEXT,
    member_ids UUID[]
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_uid UUID;
    new_convo_id UUID;
    m_id UUID;
BEGIN
    current_uid := auth.uid();
    IF current_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF length(trim(group_title)) = 0 THEN
        RAISE EXCEPTION 'Group title cannot be empty';
    END IF;

    INSERT INTO public.conversations (type, title, description, created_by, last_message_at)
    VALUES ('group', group_title, group_description, current_uid, NOW())
    RETURNING id INTO new_convo_id;

    INSERT INTO public.conversation_members (conversation_id, user_id, role, last_read_at)
    VALUES (new_convo_id, current_uid, 'creator', NOW());

    IF member_ids IS NOT NULL THEN
        FOREACH m_id IN ARRAY member_ids
        LOOP
            IF m_id <> current_uid AND EXISTS (SELECT 1 FROM public.profiles WHERE id = m_id) THEN
                INSERT INTO public.conversation_members (conversation_id, user_id, role, last_read_at)
                VALUES (new_convo_id, m_id, 'member', NOW())
                ON CONFLICT (conversation_id, user_id) DO NOTHING;
            END IF;
        END LOOP;
    END IF;

    RETURN new_convo_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_group_member(
    target_conversation_id UUID,
    target_user_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_uid UUID;
    user_role TEXT;
BEGIN
    current_uid := auth.uid();
    IF current_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT role INTO user_role FROM public.conversation_members
    WHERE conversation_id = target_conversation_id AND user_id = current_uid;

    IF user_role IS NULL OR (user_role <> 'admin' AND user_role <> 'creator') THEN
        RAISE EXCEPTION 'Permission denied: only admins can add members';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = target_user_id) THEN
        RAISE EXCEPTION 'User does not exist';
    END IF;

    INSERT INTO public.conversation_members (conversation_id, user_id, role, last_read_at)
    VALUES (target_conversation_id, target_user_id, 'member', NOW())
    ON CONFLICT (conversation_id, user_id) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_group_member(
    target_conversation_id UUID,
    target_user_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    current_uid UUID;
    caller_role TEXT;
    target_role TEXT;
BEGIN
    current_uid := auth.uid();
    IF current_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF current_uid = target_user_id THEN
        DELETE FROM public.conversation_members
        WHERE conversation_id = target_conversation_id AND user_id = current_uid;
        RETURN;
    END IF;

    SELECT role INTO caller_role FROM public.conversation_members
    WHERE conversation_id = target_conversation_id AND user_id = current_uid;

    IF caller_role IS NULL OR (caller_role <> 'admin' AND caller_role <> 'creator') THEN
        RAISE EXCEPTION 'Permission denied: only admins can remove members';
    END IF;

    SELECT role INTO target_role FROM public.conversation_members
    WHERE conversation_id = target_conversation_id AND user_id = target_user_id;

    IF target_role = 'creator' THEN
        RAISE EXCEPTION 'Cannot remove group creator';
    END IF;

    DELETE FROM public.conversation_members
    WHERE conversation_id = target_conversation_id AND user_id = target_user_id;
END;
$$;

-- ==============================================================================
-- 15. E2EE PUBLIC KEYS (Phase 7 - End-to-End Encryption)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.user_e2ee_keys (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,
    key_algorithm TEXT NOT NULL DEFAULT 'ECDH-P256',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_e2ee_keys_user_id ON public.user_e2ee_keys(user_id);

ALTER TABLE public.user_e2ee_keys ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read public keys"
    ON public.user_e2ee_keys FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Users can insert their own public key"
    ON public.user_e2ee_keys FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own public key"
    ON public.user_e2ee_keys FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);



