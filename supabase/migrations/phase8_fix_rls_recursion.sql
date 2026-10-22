-- ==============================================================================
-- PHASE 8: FIX RLS INFINITE RECURSION IN CONVERSATION_MEMBERS & MESSAGES
-- ==============================================================================
-- Description:
-- Fixes PostgreSQL error: "infinite recursion detected in policy for relation conversation_members"
-- on https://wyrkekldegvsvvlztisr.supabase.co/rest/v1/messages
--
-- Cause:
-- The previous RLS policy on conversation_members queried conversation_members itself in a subquery:
--   EXISTS (SELECT 1 FROM public.conversation_members AS cm WHERE ...)
-- When messages RLS queried conversation_members, it triggered the conversation_members policy,
-- which triggered itself endlessly.
--
-- Solution:
-- 1. Create a SECURITY DEFINER helper function: is_conversation_member(UUID, UUID)
--    which checks membership with elevated privileges, bypassing recursive RLS evaluation.
-- 2. Drop the recursive policies on conversations, conversation_members, and messages.
-- 3. Re-create secure, non-recursive policies using is_conversation_member.
-- ==============================================================================

-- 1. Create SECURITY DEFINER helper function (non-recursive, stable, fast)
CREATE OR REPLACE FUNCTION public.is_conversation_member(
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
    );
$$;

-- Grant execution to authenticated users
GRANT EXECUTE ON FUNCTION public.is_conversation_member(UUID, UUID) TO authenticated, service_role;

-- 2. Drop existing recursive policies
DROP POLICY IF EXISTS "Members can view conversations" ON public.conversations;
DROP POLICY IF EXISTS "Members can view conversation participants" ON public.conversation_members;
DROP POLICY IF EXISTS "Users can join or be added to conversations" ON public.conversation_members;
DROP POLICY IF EXISTS "Members can read messages in conversation" ON public.messages;
DROP POLICY IF EXISTS "Members can insert messages in conversation" ON public.messages;
DROP POLICY IF EXISTS "Users can update own sent messages" ON public.messages;
DROP POLICY IF EXISTS "Users can delete own sent messages" ON public.messages;

-- 3. Recreate non-recursive policy for CONVERSATIONS
CREATE POLICY "Members can view conversations"
    ON public.conversations
    FOR SELECT
    TO authenticated
    USING (
        public.is_conversation_member(id, auth.uid())
    );

-- 4. Recreate non-recursive policy for CONVERSATION_MEMBERS
-- A user can see their own membership record, or records of other members in the same conversation
CREATE POLICY "Members can view conversation participants"
    ON public.conversation_members
    FOR SELECT
    TO authenticated
    USING (
        user_id = auth.uid() OR public.is_conversation_member(conversation_id, auth.uid())
    );

CREATE POLICY "Users can join or be added to conversations"
    ON public.conversation_members
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() IS NOT NULL AND (
            user_id = auth.uid() OR public.is_conversation_member(conversation_id, auth.uid())
        )
    );

CREATE POLICY "Admins or self can remove members"
    ON public.conversation_members
    FOR DELETE
    TO authenticated
    USING (
        user_id = auth.uid() OR public.is_conversation_member(conversation_id, auth.uid())
    );

-- 5. Recreate non-recursive policies for MESSAGES
CREATE POLICY "Members can read messages in conversation"
    ON public.messages
    FOR SELECT
    TO authenticated
    USING (
        public.is_conversation_member(conversation_id, auth.uid())
    );

CREATE POLICY "Members can insert messages in conversation"
    ON public.messages
    FOR INSERT
    TO authenticated
    WITH CHECK (
        sender_id = auth.uid() AND
        public.is_conversation_member(conversation_id, auth.uid())
    );

CREATE POLICY "Users can update own sent messages"
    ON public.messages
    FOR UPDATE
    TO authenticated
    USING (sender_id = auth.uid())
    WITH CHECK (sender_id = auth.uid());

CREATE POLICY "Users can delete own sent messages"
    ON public.messages
    FOR DELETE
    TO authenticated
    USING (
        sender_id = auth.uid() OR
        EXISTS (
            SELECT 1 FROM public.conversation_members
            WHERE conversation_id = messages.conversation_id
              AND user_id = auth.uid()
              AND role IN ('admin', 'creator')
        )
    );
