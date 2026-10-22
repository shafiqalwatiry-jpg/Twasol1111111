-- ==============================================================================
-- PHASE 9: STATUSES RLS, FRIENDSHIPS SYSTEM, WEBRTC CALLS & RECURSION HARDENING
-- ==============================================================================
-- Sequence: Follows phase8_fix_rls_recursion.sql
-- Safe for execution on top of existing production database with zero data loss.
-- ==============================================================================

-- 1. HELPER FUNCTIONS (SECURITY DEFINER with strict search_path = public)
CREATE OR REPLACE FUNCTION public.is_user_blocked(u1 UUID, u2 UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.blocks
        WHERE (blocker_id = u1 AND blocked_id = u2)
           OR (blocker_id = u2 AND blocked_id = u1)
    );
$$;

CREATE OR REPLACE FUNCTION public.is_conversation_admin(
    p_conversation_id UUID,
    p_user_id UUID
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.conversation_members
        WHERE conversation_id = p_conversation_id
          AND user_id = p_user_id
          AND role IN ('admin', 'creator')
    );
$$;

-- Grant execution to authenticated users
GRANT EXECUTE ON FUNCTION public.is_user_blocked(UUID, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_conversation_admin(UUID, UUID) TO authenticated, service_role;

-- 2. HARDEN MESSAGES DELETE POLICY (Eliminate direct table subquery recursion)
DROP POLICY IF EXISTS "Users can delete own sent messages" ON public.messages;
CREATE POLICY "Users can delete own sent messages"
    ON public.messages
    FOR DELETE
    TO authenticated
    USING (
        sender_id = auth.uid() OR
        public.is_conversation_admin(conversation_id, auth.uid())
    );

-- 3. FRIENDSHIPS & CONTACT REQUESTS SYSTEM
CREATE TABLE IF NOT EXISTS public.friend_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT no_self_friend_request CHECK (sender_id != receiver_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_sender ON public.friend_requests(sender_id);
CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver ON public.friend_requests(receiver_id);
CREATE INDEX IF NOT EXISTS idx_friend_requests_status ON public.friend_requests(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_friendship
    ON public.friend_requests (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id))
    WHERE status IN ('pending', 'accepted');

-- Helper function to check if two users are accepted friends
CREATE OR REPLACE FUNCTION public.are_friends(u1 UUID, u2 UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.friend_requests
        WHERE ((sender_id = u1 AND receiver_id = u2) OR (sender_id = u2 AND receiver_id = u1))
          AND status = 'accepted'
    );
$$;

GRANT EXECUTE ON FUNCTION public.are_friends(UUID, UUID) TO authenticated, service_role;

-- RLS for friend_requests
ALTER TABLE public.friend_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own friend requests" ON public.friend_requests;
CREATE POLICY "Users can view own friend requests"
    ON public.friend_requests
    FOR SELECT
    TO authenticated
    USING (sender_id = auth.uid() OR receiver_id = auth.uid());

DROP POLICY IF EXISTS "Users can send friend requests" ON public.friend_requests;
CREATE POLICY "Users can send friend requests"
    ON public.friend_requests
    FOR INSERT
    TO authenticated
    WITH CHECK (
        sender_id = auth.uid() AND
        NOT public.is_user_blocked(auth.uid(), receiver_id)
    );

DROP POLICY IF EXISTS "Users can update relevant friend requests" ON public.friend_requests;
CREATE POLICY "Users can update relevant friend requests"
    ON public.friend_requests
    FOR UPDATE
    TO authenticated
    USING (sender_id = auth.uid() OR receiver_id = auth.uid())
    WITH CHECK (sender_id = auth.uid() OR receiver_id = auth.uid());

DROP POLICY IF EXISTS "Users can delete own friend requests" ON public.friend_requests;
CREATE POLICY "Users can delete own friend requests"
    ON public.friend_requests
    FOR DELETE
    TO authenticated
    USING (sender_id = auth.uid() OR receiver_id = auth.uid());

-- 4. STATUSES & STORIES PRIVACY & RLS ENFORCEMENT
ALTER TABLE public.statuses ADD COLUMN IF NOT EXISTS background_color TEXT DEFAULT '#005C4B';
ALTER TABLE public.statuses ADD COLUMN IF NOT EXISTS privacy_mode TEXT DEFAULT 'friends' CHECK (privacy_mode IN ('all', 'friends', 'selected', 'except'));
ALTER TABLE public.statuses ADD COLUMN IF NOT EXISTS allowed_viewer_ids UUID[] DEFAULT NULL;

ALTER TABLE public.statuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.status_views ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Statuses visibility policy" ON public.statuses;
CREATE POLICY "Statuses visibility policy"
    ON public.statuses
    FOR SELECT
    TO authenticated
    USING (
        user_id = auth.uid() OR (
            expires_at > NOW() AND
            NOT public.is_user_blocked(auth.uid(), user_id) AND (
                privacy_mode = 'all' OR
                public.are_friends(auth.uid(), user_id) OR
                (privacy_mode = 'selected' AND auth.uid() = ANY(allowed_viewer_ids)) OR
                (privacy_mode = 'except' AND public.are_friends(auth.uid(), user_id) AND NOT (auth.uid() = ANY(allowed_viewer_ids)))
            )
        )
    );

DROP POLICY IF EXISTS "Users can insert own statuses" ON public.statuses;
CREATE POLICY "Users can insert own statuses"
    ON public.statuses
    FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can update own statuses" ON public.statuses;
CREATE POLICY "Users can update own statuses"
    ON public.statuses
    FOR UPDATE
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can delete own statuses" ON public.statuses;
CREATE POLICY "Users can delete own statuses"
    ON public.statuses
    FOR DELETE
    TO authenticated
    USING (user_id = auth.uid());

-- RLS for status_views
DROP POLICY IF EXISTS "Viewers and status owners can view status receipts" ON public.status_views;
CREATE POLICY "Viewers and status owners can view status receipts"
    ON public.status_views
    FOR SELECT
    TO authenticated
    USING (
        viewer_id = auth.uid() OR
        EXISTS (
            SELECT 1 FROM public.statuses s
            WHERE s.id = status_id AND s.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can insert own status view" ON public.status_views;
CREATE POLICY "Users can insert own status view"
    ON public.status_views
    FOR INSERT
    TO authenticated
    WITH CHECK (viewer_id = auth.uid());

-- 5. CALLS SYSTEM & WEBRTC SIGNALING EXTENSIONS
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS caller_ice_candidates JSONB DEFAULT '[]'::jsonb;
ALTER TABLE public.calls ADD COLUMN IF NOT EXISTS receiver_ice_candidates JSONB DEFAULT '[]'::jsonb;

ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Participants can view their calls" ON public.calls;
CREATE POLICY "Participants can view their calls"
    ON public.calls
    FOR SELECT
    TO authenticated
    USING (caller_id = auth.uid() OR receiver_id = auth.uid());

DROP POLICY IF EXISTS "Users can initiate calls" ON public.calls;
CREATE POLICY "Users can initiate calls"
    ON public.calls
    FOR INSERT
    TO authenticated
    WITH CHECK (
        caller_id = auth.uid() AND
        (receiver_id IS NULL OR NOT public.is_user_blocked(auth.uid(), receiver_id))
    );

DROP POLICY IF EXISTS "Participants can update their calls" ON public.calls;
CREATE POLICY "Participants can update their calls"
    ON public.calls
    FOR UPDATE
    TO authenticated
    USING (caller_id = auth.uid() OR receiver_id = auth.uid())
    WITH CHECK (caller_id = auth.uid() OR receiver_id = auth.uid());

DROP POLICY IF EXISTS "Call participants can delete call record" ON public.calls;
CREATE POLICY "Call participants can delete call record"
    ON public.calls
    FOR DELETE
    TO authenticated
    USING (caller_id = auth.uid() OR receiver_id = auth.uid());

-- 6. REALTIME REPLICATION REFRESH
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.friend_requests;
    END IF;
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
