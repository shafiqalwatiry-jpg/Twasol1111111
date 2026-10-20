# وثيقة العقود المشتركة لمنصة «تواصل» (Tawasol Shared Contracts)
## الإصدار: 1.0.0 — Android Native & Web Platform

تحدد هذه الوثيقة القواعد الهندسية المشتركة والموحدة بين تطبيق **Android Native (Kotlin / Jetpack Compose)** ومنصة **الويب (React / TypeScript)** لضمان تطابق السلوك وتكاملهما عبر بنية **Supabase** المشتركة.

---

## 1. هوية المستخدم واسم المستخدم (User Identity & Username)
* **المعرف الأساسي في النظام:** `id: UUID` (مطابق لـ `auth.users.id`).
* **اسم المستخدم الفريد (`username`):**
  - الصيغة البرمجية المعتمدة: التعبير النمطي `^[a-zA-Z0-9_]{3,30}$` (من 3 إلى 30 حرفاً، أحرف إنجليزية وأرقام وشرطة سفلية فقط).
  - لا يُسمح بالمسافات أو الرموز الخاصة أو الأحرف العربية في اسم المستخدم (المعرف التقني).
  - الفحص الفريد يتم عبر جدول `public.profiles(username)` غير الحساس لحالة الأحرف (Case-Insensitive).
* **الاسم الظاهر (`displayName`):**
  - يقبل النصوص العربية والإنجليزية والرموز التعبيرية (Emoji).
  - الطول: من 2 إلى 50 حرفاً.
* **النبذة التعريفية (`bio`):**
  - نص اختياري بحد أقصى 150 حرفاً.

---

## 2. التسجيل، تسجيل الدخول، والمصادقة (Auth Flow & Session)
* **التسجيل (Registration):**
  1. التحقق من فرادة `username` في جدول `profiles`.
  2. إنشاء حساب في Supabase Auth عبر البريد الإلكتروني أو توليد بريد افتراضي معرف للمستخدم (`username@tawasol.local` إذا لم يُدخل بريداً).
  3. إنشاء سجل الملف العام في `public.profiles` (`id, username, display_name, avatar_url, bio`).
  4. حفظ البيانات الحساسة (رقم الهاتف والبريد الفعلي) حصراً داخل جدول `public.user_contacts` المحمي بسياسات RLS، ولا يمكن لأي مستخدم آخر الاستعلام عنه.
  5. إنشاء إعدادات الخصوصية الافتراضية في `public.privacy_settings`.
* **تسجيل الدخول (Login):**
  - يدعم تسجيل الدخول عبر `username` أو البريد الإلكتروني، مصحوباً بكلمة المرور (8 أحرف على الأقل).
  - عند استخدام `username`، يتم أولاً جلب البريد المرتبط به والمصادقة الآمنة عبر `signInWithPassword`.
* **إدارة الجلسات (Session Management):**
  - **Android:** حفظ الرمز المميز (Session Tokens) بأمان في `EncryptedSharedPreferences` بمفاتيح مدارة عبر `Android Keystore`.
  - **Web:** إدارة الجلسة تلقائياً عبر `supabase.auth.getSession()` و تخزين `localStorage` الآمن بـ HTTP-only context عند النشر.
* **تسجيل الخروج (Logout):**
  - استدعاء `supabase.auth.signOut()`.
  - تحديث حالة الاتصال `is_online = false` و `last_seen = NOW()`.
  - تنظيف الذاكرة المؤقتة المحلية.

---

## 3. بنية المحادثات والرسائل (Conversations & Messages)
* **أنواع المحادثات (`conversation_type`):**
  - `direct`: محادثة ثنائية فردية بين مستخدمين اثنين.
  - `group`: محادثة جماعية يديرها منشئ المجموعة والمشرفون.
* **إنشاء المحادثات الفردية (Direct Conversations):**
  - يتم استدعاء دالة RPC المشتركة: `get_or_create_direct_conversation(target_user_id: UUID)`.
  - تضمن الدالة ذرية الإنشاء وعدم تكرار المحادثات بين الطرفين.
* **إنشاء وإدارة المجموعات (Groups):**
  - دالة `create_group_conversation(group_title, group_description, member_ids)`.
  - دوال الإدارة: `add_group_member` و `remove_group_member`.
* **بنية الرسالة (`Message`):**
  - `id: UUID`
  - `conversation_id: UUID`
  - `sender_id: UUID`
  - `text: String?` (مطلوب إذا لم يكن هناك وسائط)
  - `message_type: 'text' | 'image' | 'video' | 'audio' | 'file'`
  - `status: 'sending' | 'sent' | 'delivered' | 'read' | 'failed'`
  - `media_url: String?`
  - `reply_to_message_id: UUID?`
  - `created_at: ISO8601 Timestamp`

---

## 4. حالات الإرسال ومؤشرات القراءة (Status & Read Receipts)
* **دورة حياة حالة الرسالة:**
  1. `SENDING`: مؤقتة محلياً أثناء إرسال الحزمة للشبكة.
  2. `SENT` (علامة صح واحدة): تأكيد وصول الرسالة إلى خوادم Supabase (`messages` table).
  3. `DELIVERED` (علامتا صح باللون الرمادي): استلام الرسالة على جهاز الطرف الآخر عبر قنوات Realtime.
  4. `READ` (علامتا صح باللون الأزرق / الفيروزي `#67E8F9` أو `#0D9488`): فتح الطرف الآخر للشاشة واستدعاء تحديث `last_read_at` في `conversation_members`.
* **قاعدة الخصوصية لمؤشرات القراءة:**
  - إذا قام المستخدم بتعطيل `read_receipts_enabled = false` في إعدادات خصوصيته، لا يتم إرسال إشعارات القراءة للطرف الآخر، ولا يستلم بدوره إشعارات قراءة من الآخرين.

---

## 5. قواعد الخصوصية وحماية البيانات (Privacy & RLS Policies)
* **حقول الاتصال الخاصة (`user_contacts`):**
  - رقم الهاتف (`phone_number`) والبريد الإلكتروني (`email`) لا يظهران أبداً في نتائج البحث (`UserSearch`) أو الملف الشخصي للعامة (`UserProfile`).
  - فقط صاحب الحساب الحالي (`auth.uid() = user_id`) يستطيع قراءة وتعديل بيانات الاتصال الخاصة به.
* **خيارات الرؤية (`VisibilityOption`):**
  - القيم المعتمدة: `everyone` (الجميع) | `contacts` (جهات الاتصال) | `nobody` (لا أحد).
  - تُطبق على: `last_seen_visibility`، `avatar_visibility`، `status_visibility`، `bio_visibility`.

---

## 6. رفع وتخزين الوسائط والملفات (Storage Buckets)
* **حاوية الصور الشخصية (`avatars` bucket):**
  - الصلاحية: قراءة عامة للجميع (`public = true`)، ورفع وتعديل لصاحب الحساب فقط (`owner`).
  - مسار الملف المعتمد: `avatars/{user_id}/avatar_{timestamp}.(jpg|png|webp)`.
  - الحد الأقصى للحجم: 5 ميغابايت.
* **حاوية وسائط المحادثات (`chat-media` bucket):**
  - الصلاحية: مقيدة حصراً لأعضاء المحادثة الموثقين (`conversation_members`).
  - مسار الملف المعتمد: `chat-media/{conversation_id}/{message_id}.{ext}`.
  - الحد الأقصى للحجم: 50 ميغابايت للصور والمقاطع الصوتية والمستندات.

---

## 7. معالجة الأخطاء والتزامن الفوري (Realtime & Synchronization)
* **استراتيجية التحديث الفوري (Realtime Channels):**
  - الاشتراك في قناة Postgres Changes لجدول `messages`:
    - الفلتر: `conversation_id=eq.{id}` للأحداث `INSERT` و `UPDATE`.
  - الاشتراك في قناة `conversations` لتحديث قائمة المحادثات عند وصول رسائل جديدة.
* **معالجة انقطاع الاتصال (Offline & Reconnect):**
  - **Android:** تخزين الرسائل الصادرة في جدول `outbox_messages` بـ Room وإعادة إرسالها فور عودة الاتصال (`NetworkMonitor`).
  - **Web:** إظهار حالة الشبكة، والاحتفاظ بـ Optimistic Updates مع خيار إعادة المحاولة (Retry) اليدوي عند الفشل.
