-- ==============================================================================
-- تواصل (Tawasol) - المرحلة السابعة: التشفير التام بين الطرفين (End-to-End Encryption)
-- ==============================================================================

-- 1. جدول المفاتيح العامة للتشفير التام بين الطرفين (ECDH Public Keys)
CREATE TABLE IF NOT EXISTS public.user_e2ee_keys (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,
    key_algorithm TEXT NOT NULL DEFAULT 'ECDH-P256',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- فهارس لتحسين سرعة الاستعلام عن المفاتيح العامة
CREATE INDEX IF NOT EXISTS idx_user_e2ee_keys_user_id ON public.user_e2ee_keys(user_id);

-- 2. سياسات الأمان على مستوى الصفوف (Row Level Security - RLS)
ALTER TABLE public.user_e2ee_keys ENABLE ROW LEVEL SECURITY;

-- أي مستخدم مسجل الدخول يستطيع قراءة المفتاح العام لأي مستخدم آخر لتشفير الرسائل الموجهة إليه
CREATE POLICY "Authenticated users can read public keys"
    ON public.user_e2ee_keys FOR SELECT
    TO authenticated
    USING (true);

-- المستخدم فقط يستطيع إضافة أو تحديث مفتاحه العام الخاص به
CREATE POLICY "Users can insert their own public key"
    ON public.user_e2ee_keys FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own public key"
    ON public.user_e2ee_keys FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 3. دالة لتحديث تاريخ آخر تعديل للمفتاح
CREATE OR REPLACE FUNCTION public.handle_e2ee_key_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER on_e2ee_key_updated
    BEFORE UPDATE ON public.user_e2ee_keys
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_e2ee_key_updated_at();
