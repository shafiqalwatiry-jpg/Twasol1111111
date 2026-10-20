# مشروع تطبيق «تواصل» (Tawasol) - Android Native

تطبيق مراسلة واتصال فوري حقيقي لنظام أندرويد (Android Native)، مبني بالكامل بلغة **Kotlin** وأحدث تقنيات **Jetpack Compose** مع بنية Clean Architecture وOffline-First ومخدم خلفي مدعوم بـ **Supabase**.

---

## 📱 الهوية والخصائص الأساسية

- **اسم التطبيق العربي:** تواصل
- **اسم التطبيق الإنجليزي:** Tawasol
- **Package Name:** `com.tawasol.app`
- **نظام التشغيل المستهدف:** Android فقط (Native)
- **الحد الأدنى للنظام (minSdk):** 26 (Android 8.0 Oreo)
- **النظام المستهدف (targetSdk / compileSdk):** 35 (Android 15)
- **لغة البرمجة:** Kotlin 2.1.0
- **واجهة المستخدم:** Jetpack Compose + Material 3 (دعم RTL كامل للغة العربية)

---

## 🏛 بنية المشروع (Clean Architecture)

تم تقسيم المشروع وفق أفضل معايير هندسة البرمجيات في أندرويد:

```
app/src/main/java/com/tawasol/app/
├── TawasolApp.kt                # فئة التطبيق الرئيسية وتهيئة الـ AppContainer
├── MainActivity.kt              # النشاط الرئيسي، يدعم RTL و TawasolTheme
│
├── core/                        # النواة المشتركة
│   ├── di/                      # Dependency Injection (AppContainer)
│   ├── theme/                   # ألوان وهوية تواصل، الخطوط والمظهر (Material 3)
│   ├── database/                # قاعدة البيانات المحلية Room (Offline-First)
│   │   ├── entity/              # جداول المستخدمين، المحادثات، والرسائل، والـ Outbox
│   │   ├── dao/                 # دوال الاستعلام UserDao, ConversationDao, MessageDao
│   │   └── converter/           # محولات التواريخ والأوقات
│   ├── supabase/                # تهيئة عميل Supabase الآمن (Anon Key فقط)
│   ├── network/                 # مراقبة الاتصال والإنترنت (NetworkMonitor)
│   └── security/                # Android Keystore لتشفير الجلسات محلياً
│
├── domain/                      # طبقة الأعمال والنماذج
│   ├── model/                   # Domain Models (User, Conversation, Message)
│   ├── repository/              # واجهات المستودعات (Auth, Chat, User)
│   └── usecase/                 # حالات الاستخدام (Login, Register, SendMessage, ...)
│
├── data/                        # طبقة مصادر البيانات
│   └── repository/              # تنفيذ المستودعات والربط بين Room و Supabase
│
└── presentation/                # طبقة واجهة المستخدم (Jetpack Compose)
    ├── navigation/              # التنقل وإدارة المسارات (Screen, AppNavGraph)
    ├── components/              # مكونات تواصل القابلة لإعادة الاستخدام
    ├── splash/                  # شاشة البداية والتحقق من الجلسة
    ├── auth/                    # شاشات الدخول وإنشاء الحساب
    │   ├── login/               # LoginScreen & LoginViewModel
    │   └── register/            # RegisterScreen & RegisterViewModel
    └── home/                    # الشاشة الرئيسية مع الألسن الأربعة
        ├── HomeScreen.kt        # الشريط العلوي والسفلي وزر إنشاء محادثة
        └── tabs/                # المحادثات، المكالمات، الحالات، الإعدادات
```

---

## 🗄️ إعداد قاعدة بيانات Supabase (Backend)

1. أنشئ مشروعاً جديداً في [Supabase](https://supabase.com).
2. افتح **SQL Editor** في لوحة تحكم Supabase.
3. انسخ محتويات الملف `supabase/schema.sql` والصقه في المحرر ثم اضغط **Run**.
4. سينشئ السكربت تلقائياً:
   - جدول `profiles` مع قيد فرادة اسم المستخدم `@username`.
   - إخفاء رقم الهاتف والبريد عن المستخدمين العاديين وتأمينهم بواسطة **Row Level Security (RLS)**.
   - جداول `conversations`, `conversation_members`, `messages`, `calls`, `statuses`, `blocks`, `reports`.
   - تفعيل الـ **Realtime Replication** على الرسائل والمحادثات والمكالمات.
   - Trigger تلقائي لإنشاء الملف الشخصي عند تسجيل المستخدم.

5. انسخ رابط المشروع **Project URL** ومفتاح **anon (public) key** من صفحة `Project Settings -> API`.
6. ضع المفاتيح في ملف `local.properties` أو في `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "SUPABASE_URL", "\"https://your-project.supabase.co\"")
   buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOi...\"")
   ```

---

## 🚀 كيفية فتح وتشغيل المشروع

### 1. في برنامج Android Studio
1. افتح **Android Studio** (نسخة Iguana أو Koala أو Ladybug أو أحدث).
2. اختر **Open** وحدد المجلد الجذر لهذا المشروع.
3. انتظر حتى يكتمل فحص **Gradle Sync**.
4. تأكد من ضبط إصدار JDK على **JDK 17** أو أحدث في إعدادات Android Studio:
   `Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JDK`.

### 2. التشغيل على محاكي أندرويد (Android Emulator)
1. افتح **Device Manager** في Android Studio.
2. أنشئ محاكياً بنظام Android 12 أو 13 أو 14 أو 15 (API 31 - 35).
3. اختر المحاكي واضغط على زر **Run** (الأيقونة الخضراء ▶) أو اضغط `Shift + F10`.

### 3. التشغيل على هاتف أندرويد حقيقي
1. على الهاتف: ادخل إلى **الإعدادات** -> **حول الهاتف** -> اضغط على **رقم الإصدار (Build Number)** 7 مرات لتفعيل خيارات المطور.
2. ادخل إلى **خيارات المطور** وفعّل **تصحيح أخطاء USB (USB Debugging)**.
3. صِل الهاتف بجهاز الكمبيوتر بواسطة كابل USB واختر "السماح بتصحيح الأخطاء".
4. سيظهر هاتفك في قائمة الأجهزة في Android Studio. اختره واضغط **Run**.

### 4. بناء ملف APK للتثبيت والتجربة
يمكنك بناء ملف تثبيت Debug APK مباشرة من سطر الأوامر:
```bash
./gradlew assembleDebug
```
سيكون ملف الـ APK جاهزاً في المسار:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 الأمان والخصوصية في المرحلة الأولى

1. **التحقق من الهوية:** استخدام Supabase Auth المشفر دون حفظ كلمات المرور بنص صريح.
2. **الخصوصية الصارمة:** رقم الهاتف والبريد اختياريان ومخفيان تماماً عن العامة ولا يظهران للمستخدمين الآخرين.
3. **التخزين الآمن:** استخدام `AndroidKeyStore` مع `EncryptedSharedPreferences` (AES-256 GCM) لحفظ رموز الجلسات.
4. **Offline-First:** دعم التخزين المحلي عبر Room Database ونظام Outbox Queue للرسائل غير المتصلة.
