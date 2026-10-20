/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  Smartphone,
  Layers,
  Database,
  Shield,
  FileCode,
  Terminal,
  CheckCircle2,
  Copy,
  Lock,
  UserCheck,
  Search,
  Image as ImageIcon,
  Mic,
  Play,
  Pause,
  Phone,
  Video,
  PhoneOff,
  ArrowRight,
  Send,
  Check,
  CheckCheck,
  Paperclip,
  Smile,
  MoreVertical,
  X,
  Key,
  QrCode,
  ShieldCheck,
  User,
  Settings as SettingsIcon,
  Info,
  ChevronLeft,
  Volume2,
  VolumeX,
  RefreshCw,
  Eye,
  Sliders,
  Share2,
  Wifi,
  Battery,
  Signal
} from 'lucide-react';

interface FileNode {
  name: string;
  path: string;
  category: 'core' | 'ui' | 'data' | 'security' | 'sql';
  description: string;
  codeSnippet: string;
}

interface SimulatedUser {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string;
  bio: string;
  phone: string;
  email: string;
  publicKeyPreview: string;
  safetyNumber: string;
}

interface ChatMessage {
  id: string;
  senderId: string;
  type: 'text' | 'audio' | 'image';
  text?: string;
  audioDuration?: string;
  audioWave?: number[];
  imageUrl?: string;
  caption?: string;
  timestamp: string;
  status: 'sending' | 'sent' | 'delivered' | 'read';
  isEncrypted: boolean;
  ciphertext?: string;
  iv?: string;
}

const MOCK_USERS: SimulatedUser[] = [
  {
    id: 'usr_ahmed_1',
    username: 'ahmed_dev',
    displayName: 'أحمد الشمري',
    avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80',
    bio: 'مهندس برمجيات أندرويد شغوف بالأمان والتشفير 🚀',
    phone: '+966 50 123 4567',
    email: 'ahmed@tawasol.internal',
    publicKeyPreview: 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9f...',
    safetyNumber: '48291 03948 29482 10492 84729 10482 93847 20194 85739 20184 75839 20194'
  },
  {
    id: 'usr_sara_2',
    username: 'sara_design',
    displayName: 'سارة المنصور',
    avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80',
    bio: 'مصممة واجهات ومستخدمة نشطة على تواصل 🎨✨',
    phone: '+966 55 987 6543',
    email: 'sara@tawasol.internal',
    publicKeyPreview: 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7a...',
    safetyNumber: '48291 03948 29482 10492 84729 10482 93847 20194 85739 20184 75839 20194'
  },
  {
    id: 'usr_omar_3',
    username: 'omar_tech',
    displayName: 'عمر الفاروق',
    avatarUrl: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150&auto=format&fit=crop&q=80',
    bio: 'مهتم بالأنظمة الموزعة والخصوصية الرقمية 🔒',
    phone: '+966 54 321 0987',
    email: 'omar@tawasol.internal',
    publicKeyPreview: 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE3c...',
    safetyNumber: '19482 74839 20194 84729 10482 93847 20194 85739 20184 75839 20194 62847'
  }
];

const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: 'msg_1',
    senderId: 'usr_sara_2',
    type: 'text',
    text: 'السلام عليكم يا أحمد! كيف يسير العمل على معمارية التشفير التام (E2EE) في تواصل؟',
    timestamp: '10:14 ص',
    status: 'read',
    isEncrypted: true,
    ciphertext: 'enc:v1:9F3A1D:xK8e2m+vT9qB1...L7P==:SIGNATURE_P256',
    iv: '9F3A1D0B7A4C'
  },
  {
    id: 'msg_2',
    senderId: 'usr_ahmed_1',
    type: 'text',
    text: 'وعليكم السلام يا سارة! اكتملت تماماً بنجاح بفضل الله. نستخدم ECDH على منحنى NIST P-256 وتشفير AES-256-GCM متكامل.',
    timestamp: '10:15 ص',
    status: 'read',
    isEncrypted: true,
    ciphertext: 'enc:v1:C78E0A:kJ2l4N/z9eA3b...Q1W==:SIGNATURE_P256',
    iv: 'C78E0A22FD91'
  },
  {
    id: 'msg_3',
    senderId: 'usr_sara_2',
    type: 'audio',
    audioDuration: '0:18',
    audioWave: [20, 45, 60, 30, 80, 95, 70, 40, 85, 90, 65, 30, 50, 75, 40, 60, 85, 30],
    timestamp: '10:16 ص',
    status: 'read',
    isEncrypted: true,
    ciphertext: 'enc:v1:E12F9B:mK4eP8...AUDIO_PAYLOAD...==:SIGNATURE_P256',
    iv: 'E12F9B77AC34'
  },
  {
    id: 'msg_4',
    senderId: 'usr_ahmed_1',
    type: 'image',
    imageUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=80',
    caption: 'معاينة شاشة التحقق من أرقام الأمان (Safety Number) الجديدة!',
    timestamp: '10:17 ص',
    status: 'read',
    isEncrypted: true,
    ciphertext: 'enc:v1:4C81BA:oW8aR1...IMAGE_METADATA...==:SIGNATURE_P256',
    iv: '4C81BA098E21'
  },
  {
    id: 'msg_5',
    senderId: 'usr_sara_2',
    type: 'text',
    text: 'ممتاز جداً! السيرفر لا يملك المفتاح الخاص وبالتالي لا يمكن لأي طرف ثالث قراءة المحادثات 🛡️',
    timestamp: '10:18 ص',
    status: 'read',
    isEncrypted: true,
    ciphertext: 'enc:v1:5D91C2:tP7xQ4...L0M==:SIGNATURE_P256',
    iv: '5D91C200AA11'
  }
];

const ANDROID_FILES: FileNode[] = [
  {
    name: 'E2EEManager.kt',
    path: 'app/src/main/java/com/tawasol/app/core/security/E2EEManager.kt',
    category: 'security',
    description: 'نواة التشفير التام (E2EE): توليد مفاتيح ECDH P-256، اشتقاق المفاتيح بـ SHA-256 وتشفير الحزم المتماثل بـ AES-256-GCM',
    codeSnippet: `package com.tawasol.app.core.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E2EEManager(private val keystoreManager: KeystoreManager) {

    private val secureRandom = SecureRandom()
    private val keyFactory = KeyFactory.getInstance("EC")

    @Synchronized
    fun getOrCreateUserKeyPair(userId: String): KeyPair {
        val privBase64 = keystoreManager.getUserPrivateKey(userId)
        val pubBase64 = keystoreManager.getUserPublicKey(userId)

        if (privBase64 != null && pubBase64 != null) {
            try {
                val privBytes = Base64.decode(privBase64, Base64.NO_WRAP)
                val pubBytes = Base64.decode(pubBase64, Base64.NO_WRAP)
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(publicKey, privateKey)
            } catch (_: Exception) {}
        }

        // Generate standard NIST P-256 (secp256r1)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()

        keystoreManager.saveUserPrivateKey(userId, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
        keystoreManager.saveUserPublicKey(userId, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
        return keyPair
    }

    fun encryptMessage(myUserId: String, peerPublicKeyBase64: String, plainText: String): Result<String> {
        return try {
            val myKeyPair = getOrCreateUserKeyPair(myUserId)
            val peerPubKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
            val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubKeyBytes))

            val aesKey = deriveSharedKey(myKeyPair.private, peerPublicKey)
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val cipherTextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(cipherTextBytes, Base64.NO_WRAP)
            val myPubBase64 = Base64.encodeToString(myKeyPair.public.encoded, Base64.NO_WRAP)

            Result.success("enc:v1:$ivBase64:$cipherBase64:$myPubBase64")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}`
  },
  {
    name: 'KeystoreManager.kt',
    path: 'app/src/main/java/com/tawasol/app/core/security/KeystoreManager.kt',
    category: 'security',
    description: 'تخزين المفاتيح الحساسة في Android KeyStore المادي المشفر بنظام MasterKey و AES-256-GCM',
    codeSnippet: `package com.tawasol.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeystoreManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "tawasol_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserPrivateKey(userId: String, privateKeyBase64: String) {
        sharedPreferences.edit().putString(KEY_E2EE_PRIVATE_PREFIX + userId, privateKeyBase64).apply()
    }

    fun getUserPrivateKey(userId: String): String? {
        return sharedPreferences.getString(KEY_E2EE_PRIVATE_PREFIX + userId, null)
    }
}`
  },
  {
    name: 'ChatScreen.kt',
    path: 'app/src/main/java/com/tawasol/app/presentation/chat/ChatScreen.kt',
    category: 'ui',
    description: 'شاشة المحادثة بـ Jetpack Compose مع دعم RTL، الرسائل النصية، الصوتية (Waveform)، التمرير، وشارة الأمان',
    codeSnippet: `@Composable
fun ChatScreen(
    conversationId: String,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSafetyNumber: (peerUserId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            ChatTopAppBar(
                conversation = uiState.conversation,
                onBack = onNavigateBack,
                onLockClick = { uiState.conversation?.peerUserId?.let(onNavigateToSafetyNumber) }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                onTextChanged = viewModel::onInputTextChanged,
                onSend = viewModel::sendMessage,
                onSendAudio = viewModel::sendAudioMessage
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), reverseLayout = true) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message, isOutgoing = message.senderId == uiState.currentUserId)
            }
        }
    }
}`
  },
  {
    name: 'ChatRepositoryImpl.kt',
    path: 'app/src/main/java/com/tawasol/app/data/repository/ChatRepositoryImpl.kt',
    category: 'data',
    description: 'مستودع المحادثات الحقيقي: دمج Supabase Realtime مع Room، طابور Outbox للعمل بدون إنترنت، ورفع الوسائط',
    codeSnippet: `class ChatRepositoryImpl(
    private val database: TawasolDatabase,
    private val supabaseProvider: SupabaseClientProvider,
    private val networkMonitor: NetworkMonitor,
    private val currentUserIdProvider: () -> String?
) : ChatRepository {

    override suspend fun sendMessage(conversationId: String, text: String?, replyToId: String?): Result<Message> {
        // Optimistic UI: Insert into Room with SENDING status
        // Broadcast via Supabase Postgrest & Realtime channel
        // Fallback to Outbox queue if network is disconnected
    }

    override suspend fun sendMediaMessage(
        conversationId: String, fileBytes: ByteArray, fileName: String,
        mimeType: String, messageType: MessageType, caption: String?
    ): Result<Message> {
        // Uploads encrypted stream to Supabase Storage ('chat-media' bucket)
        // Records metadata in Room and Postgres messages table
    }
}`
  },
  {
    name: 'phase7_e2ee.sql',
    path: 'supabase/migrations/phase7_e2ee.sql',
    category: 'sql',
    description: 'مخطط المفاتيح العامة للتشفير التام (ECDH-P256) مع سياسات RLS الصارمة',
    codeSnippet: `CREATE TABLE IF NOT EXISTS public.user_e2ee_keys (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,
    key_algorithm TEXT NOT NULL DEFAULT 'ECDH-P256',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.user_e2ee_keys ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read public keys"
    ON public.user_e2ee_keys FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Users can manage only their own public key"
    ON public.user_e2ee_keys FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);`
  },
  {
    name: 'SafetyNumberScreen.kt',
    path: 'app/src/main/java/com/tawasol/app/presentation/security/SafetyNumberScreen.kt',
    category: 'ui',
    description: 'شاشة Compose للتحقق من أرقام الأمان المشفرة (60 رقماً في 12 مجموعة) مع مفتاح التحقق الموثّق لمنع هجمات الوسيط (MITM)',
    codeSnippet: `@Composable
fun SafetyNumberScreen(
    viewModel: SafetyNumberViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    // Displays 60-digit cryptographic safety number, verified toggle, and QR representation
}`
  },
  {
    name: 'E2EEManagerTest.kt',
    path: 'app/src/test/java/com/tawasol/app/core/security/E2EEManagerTest.kt',
    category: 'security',
    description: 'اختبارات وحدة متكاملة لـ E2EEManager: توليد المفاتيح، التشفير وفك التشفير بين طرفين، تطابق أرقام الأمان، والحماية من التنصت',
    codeSnippet: `@Test
fun \`encrypt and decrypt message between Alice and Bob\`() {
    val bobPubKey = bobE2EE.getUserPublicKeyBase64(bobId)
    val cipherPayload = aliceE2EE.encryptMessage(aliceId, bobPubKey, "رسالة مشفرة").getOrNull()!!
    val decrypted = bobE2EE.decryptMessage(bobId, cipherPayload)
    assertEquals("رسالة مشفرة", decrypted)
}`
  },
  {
    name: 'schema.sql',
    path: 'supabase/schema.sql',
    category: 'sql',
    description: 'مخطط Supabase الكامل: البروفايلات، عزل الهاتف والبريد، المحادثات، والرسائل وسياسات RLS',
    codeSnippet: `-- Segregate private phone & email to protect user privacy
CREATE TABLE IF NOT EXISTS public.user_contacts (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    phone_number TEXT,
    email TEXT
);
ALTER TABLE public.user_contacts ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can only read own private contacts"
    ON public.user_contacts FOR SELECT USING (auth.uid() = user_id);`
  }
];

export default function App() {
  const [activeMainTab, setActiveMainTab] = useState<'simulator' | 'phases' | 'code' | 'security' | 'gradle'>('simulator');
  const [selectedFile, setSelectedFile] = useState<FileNode>(ANDROID_FILES[0]);
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // Phone Simulator States
  const [currentUserIndex, setCurrentUserIndex] = useState(0);
  const currentUser = MOCK_USERS[currentUserIndex];
  const peerUser = MOCK_USERS[(currentUserIndex + 1) % MOCK_USERS.length];

  const [simulatorScreen, setSimulatorScreen] = useState<'chats' | 'chat_room' | 'search' | 'profile' | 'safety_number' | 'call'>('chat_room');
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>(INITIAL_MESSAGES);
  const [inputText, setInputText] = useState('');
  const [isRecordingVoice, setIsRecordingVoice] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [activeAudioPlaying, setActiveAudioPlaying] = useState<string | null>(null);
  const [showEncryptedPayload, setShowEncryptedPayload] = useState(false);
  const [activeCallType, setActiveCallType] = useState<'voice' | 'video'>('voice');
  const [callDuration, setCallDuration] = useState(14);
  const [isMuted, setIsMuted] = useState(false);
  const [isSpeaker, setIsSpeaker] = useState(false);
  const [safetyVerified, setSafetyVerified] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    setTimeout(() => setCopiedText(null), 2000);
  };

  // Scroll to bottom when messages change
  useEffect(() => {
    if (simulatorScreen === 'chat_room') {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [chatMessages, simulatorScreen]);

  // Handle voice recording timer
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (isRecordingVoice) {
      timer = setInterval(() => {
        setRecordingSeconds((prev) => prev + 1);
      }, 1000);
    } else {
      setRecordingSeconds(0);
    }
    return () => clearInterval(timer);
  }, [isRecordingVoice]);

  // Handle call timer
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (simulatorScreen === 'call') {
      timer = setInterval(() => {
        setCallDuration((prev) => prev + 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [simulatorScreen]);

  const handleSendMessage = () => {
    if (!inputText.trim()) return;
    const now = new Date();
    const timeStr = now.toLocaleTimeString('ar-SA', { hour: '2-digit', minute: '2-digit' });
    const randomHex = Math.random().toString(16).substring(2, 8).toUpperCase();

    const newMsg: ChatMessage = {
      id: `msg_${Date.now()}`,
      senderId: currentUser.id,
      type: 'text',
      text: inputText.trim(),
      timestamp: timeStr,
      status: 'sending',
      isEncrypted: true,
      ciphertext: `enc:v1:${randomHex}:${btoa(encodeURIComponent(inputText.trim()))}:SIGNATURE_ECDH`,
      iv: randomHex
    };

    setChatMessages((prev) => [...prev, newMsg]);
    setInputText('');

    // Simulate sending -> sent -> read transition
    setTimeout(() => {
      setChatMessages((prev) =>
        prev.map((m) => (m.id === newMsg.id ? { ...m, status: 'sent' } : m))
      );
    }, 600);

    setTimeout(() => {
      setChatMessages((prev) =>
        prev.map((m) => (m.id === newMsg.id ? { ...m, status: 'delivered' } : m))
      );
    }, 1200);

    setTimeout(() => {
      setChatMessages((prev) =>
        prev.map((m) => (m.id === newMsg.id ? { ...m, status: 'read' } : m))
      );
    }, 2200);
  };

  const handleSendVoiceNote = () => {
    setIsRecordingVoice(false);
    const now = new Date();
    const timeStr = now.toLocaleTimeString('ar-SA', { hour: '2-digit', minute: '2-digit' });
    const secStr = recordingSeconds < 10 ? `0:0${recordingSeconds}` : `0:${recordingSeconds}`;
    const randomHex = Math.random().toString(16).substring(2, 8).toUpperCase();

    const newVoice: ChatMessage = {
      id: `voice_${Date.now()}`,
      senderId: currentUser.id,
      type: 'audio',
      audioDuration: recordingSeconds > 0 ? secStr : '0:05',
      audioWave: [25, 60, 40, 85, 95, 70, 50, 80, 90, 45, 60, 30, 75, 90, 40],
      timestamp: timeStr,
      status: 'sent',
      isEncrypted: true,
      ciphertext: `enc:v1:${randomHex}:AUDIO_STREAM_AES256_GCM:SIGNATURE_ECDH`,
      iv: randomHex
    };

    setChatMessages((prev) => [...prev, newVoice]);
  };

  const filteredUsers = MOCK_USERS.filter(
    (u) =>
      u.id !== currentUser.id &&
      (u.displayName.includes(searchQuery) || u.username.includes(searchQuery))
  );

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-['Cairo',sans-serif] selection:bg-teal-500 selection:text-white flex flex-col">
      {/* Top Header */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-teal-700 to-teal-500 flex items-center justify-center shadow-lg shadow-teal-500/25 text-white font-extrabold text-2xl tracking-tight">
              ت
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold text-white tracking-wide">تواصل | Tawasol</h1>
                <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                  Android Native + E2EE
                </span>
                <span className="hidden md:inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-teal-500/10 text-teal-300 border border-teal-500/20">
                  <ShieldCheck className="w-3 h-3 text-teal-400" />
                  NIST P-256 ECDH
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                تطبيق مراسلة واتصال أصيل مبني بـ Kotlin و Jetpack Compose مع Supabase و Room والتشفير التام
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* User Switcher */}
            <div className="flex items-center gap-2 bg-slate-800/80 p-1 rounded-xl border border-slate-700/80 text-xs">
              <span className="text-slate-400 text-[11px] px-2 font-medium">المستخدم الحالي:</span>
              <button
                onClick={() => setCurrentUserIndex((prev) => (prev === 0 ? 1 : 0))}
                className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-teal-600/30 hover:bg-teal-600/50 border border-teal-500/40 text-teal-200 transition-all font-medium"
                title="تبديل حساب المستخدم لمحاكاة المحادثة والتشفير بين طرفين"
              >
                <img
                  src={currentUser.avatarUrl}
                  alt={currentUser.displayName}
                  className="w-5 h-5 rounded-full object-cover"
                />
                <span>{currentUser.displayName.split(' ')[0]}</span>
                <RefreshCw className="w-3 h-3 text-teal-400" />
              </button>
            </div>

            <button
              onClick={() => copyToClipboard('./gradlew assembleDebug', 'gradle')}
              className="hidden sm:flex px-3.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 transition-colors text-xs font-mono text-slate-300 items-center gap-1.5 border border-slate-700"
            >
              <Terminal className="w-3.5 h-3.5 text-teal-400" />
              <span>{copiedText === 'gradle' ? 'تم نسخ الأمر!' : './gradlew assembleDebug'}</span>
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 flex gap-1 overflow-x-auto border-t border-slate-800/70 pt-1.5">
          <button
            onClick={() => setActiveMainTab('simulator')}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 whitespace-nowrap ${
              activeMainTab === 'simulator'
                ? 'border-teal-400 text-teal-300 bg-teal-500/10'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Smartphone className="w-4 h-4 text-teal-400" />
            <span>محاكي التطبيق التفاعلي (Live Android App)</span>
          </button>

          <button
            onClick={() => setActiveMainTab('phases')}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 whitespace-nowrap ${
              activeMainTab === 'phases'
                ? 'border-teal-400 text-teal-300 bg-teal-500/10'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="w-4 h-4 text-emerald-400" />
            <span>مراحل المشروع السبع (Roadmap 1-7)</span>
          </button>

          <button
            onClick={() => setActiveMainTab('code')}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 whitespace-nowrap ${
              activeMainTab === 'code'
                ? 'border-teal-400 text-teal-300 bg-teal-500/10'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <FileCode className="w-4 h-4 text-blue-400" />
            <span>مستكشف كود أندرويد (Kotlin & Compose)</span>
          </button>

          <button
            onClick={() => setActiveMainTab('security')}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 whitespace-nowrap ${
              activeMainTab === 'security'
                ? 'border-teal-400 text-teal-300 bg-teal-500/10'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Shield className="w-4 h-4 text-purple-400" />
            <span>معمارية التشفير التام (E2EE) و RLS</span>
          </button>

          <button
            onClick={() => setActiveMainTab('gradle')}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 whitespace-nowrap ${
              activeMainTab === 'gradle'
                ? 'border-teal-400 text-teal-300 bg-teal-500/10'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Terminal className="w-4 h-4 text-amber-400" />
            <span>أوامر البناء وتشغيل أندرويد (Gradle)</span>
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-8 flex-1 w-full">
        {/* TAB 1: INTERACTIVE SMARTPHONE EMULATOR */}
        {activeMainTab === 'simulator' && (
          <div className="grid lg:grid-cols-12 gap-8 items-start">
            {/* Left/Main Column: Phone Mockup */}
            <div className="lg:col-span-7 flex flex-col items-center">
              {/* Device Frame */}
              <div className="w-full max-w-[410px] bg-slate-900 border-[7px] border-slate-800 rounded-[48px] shadow-2xl shadow-teal-950/40 overflow-hidden relative flex flex-col h-[740px]">
                {/* Android Top Notch / Speaker & Status Bar */}
                <div className="bg-slate-950 px-6 pt-3 pb-2 flex items-center justify-between text-[11px] text-slate-400 font-mono select-none border-b border-slate-900">
                  <div className="flex items-center gap-1.5">
                    <span>10:20</span>
                  </div>
                  {/* Camera hole */}
                  <div className="w-3.5 h-3.5 rounded-full bg-slate-900 border border-slate-800 flex items-center justify-center">
                    <div className="w-1.5 h-1.5 rounded-full bg-slate-950"></div>
                  </div>
                  <div className="flex items-center gap-1.5 text-slate-300">
                    <Signal className="w-3 h-3" />
                    <Wifi className="w-3 h-3" />
                    <Battery className="w-3.5 h-3.5" />
                  </div>
                </div>

                {/* SCREEN CONTENT BASED ON STATE */}
                <div className="flex-1 flex flex-col bg-slate-950 overflow-hidden relative">
                  {/* --- SCREEN: CHATS LIST --- */}
                  {simulatorScreen === 'chats' && (
                    <div className="flex-1 flex flex-col">
                      {/* App Bar */}
                      <div className="p-4 bg-slate-900/90 border-b border-slate-800 flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                          <div className="w-9 h-9 rounded-xl bg-teal-600 flex items-center justify-center text-white font-bold text-lg">
                            ت
                          </div>
                          <div>
                            <h2 className="text-base font-bold text-white leading-tight">تواصل</h2>
                            <p className="text-[11px] text-teal-400 font-medium">مشفر بالكامل (E2EE)</p>
                          </div>
                        </div>

                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => setSimulatorScreen('search')}
                            className="p-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition-colors"
                            title="بحث عن المستخدمين"
                          >
                            <Search className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setSimulatorScreen('profile')}
                            className="p-1 rounded-full border border-teal-500/40 hover:border-teal-400"
                            title="الملف الشخصي"
                          >
                            <img
                              src={currentUser.avatarUrl}
                              alt={currentUser.displayName}
                              className="w-7 h-7 rounded-full object-cover"
                            />
                          </button>
                        </div>
                      </div>

                      {/* Conversation list */}
                      <div className="flex-1 overflow-y-auto divide-y divide-slate-850">
                        {/* Conversation 1 (Active peer) */}
                        <div
                          onClick={() => setSimulatorScreen('chat_room')}
                          className="p-3.5 hover:bg-slate-900/60 transition-colors cursor-pointer flex items-center gap-3 bg-slate-900/30"
                        >
                          <div className="relative">
                            <img
                              src={peerUser.avatarUrl}
                              alt={peerUser.displayName}
                              className="w-12 h-12 rounded-full object-cover border border-slate-700"
                            />
                            <span className="absolute bottom-0 left-0 w-3.5 h-3.5 rounded-full bg-emerald-500 border-2 border-slate-950"></span>
                          </div>

                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <h3 className="text-sm font-bold text-white truncate flex items-center gap-1.5">
                                <span>{peerUser.displayName}</span>
                                <Lock className="w-3 h-3 text-teal-400" />
                              </h3>
                              <span className="text-[10px] text-teal-400 font-mono">10:18 ص</span>
                            </div>
                            <p className="text-xs text-slate-400 truncate mt-0.5 flex items-center gap-1">
                              <CheckCheck className="w-3.5 h-3.5 text-teal-400 inline shrink-0" />
                              <span>ممتاز جداً! السيرفر لا يملك المفتاح الخاص...</span>
                            </p>
                          </div>
                        </div>

                        {/* Conversation 2 (Omar) */}
                        <div
                          onClick={() => setSimulatorScreen('chat_room')}
                          className="p-3.5 hover:bg-slate-900/60 transition-colors cursor-pointer flex items-center gap-3"
                        >
                          <div className="relative">
                            <img
                              src={MOCK_USERS[2].avatarUrl}
                              alt={MOCK_USERS[2].displayName}
                              className="w-12 h-12 rounded-full object-cover border border-slate-700"
                            />
                          </div>

                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <h3 className="text-sm font-bold text-white truncate flex items-center gap-1.5">
                                <span>{MOCK_USERS[2].displayName}</span>
                                <Lock className="w-3 h-3 text-teal-400" />
                              </h3>
                              <span className="text-[10px] text-slate-400 font-mono">أمس</span>
                            </div>
                            <p className="text-xs text-slate-400 truncate mt-0.5">
                              تم التحقق من رقم الأمان (Safety Number) بنجاح 🔒
                            </p>
                          </div>
                        </div>
                      </div>

                      {/* Floating New Chat Button */}
                      <button
                        onClick={() => setSimulatorScreen('search')}
                        className="absolute bottom-5 left-5 w-13 h-13 rounded-2xl bg-teal-600 text-white flex items-center justify-center shadow-lg shadow-teal-500/30 hover:bg-teal-500 transition-all hover:scale-105"
                      >
                        <Search className="w-5 h-5" />
                      </button>
                    </div>
                  )}

                  {/* --- SCREEN: LIVE CHAT ROOM --- */}
                  {simulatorScreen === 'chat_room' && (
                    <div className="flex-1 flex flex-col bg-slate-950">
                      {/* Chat Room Top Bar */}
                      <div className="px-3 py-2.5 bg-slate-900/90 border-b border-slate-800 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setSimulatorScreen('chats')}
                            className="p-1 rounded-lg text-slate-300 hover:text-white"
                          >
                            <ArrowRight className="w-5 h-5" />
                          </button>

                          <div
                            onClick={() => setSimulatorScreen('profile')}
                            className="flex items-center gap-2 cursor-pointer"
                          >
                            <div className="relative">
                              <img
                                src={peerUser.avatarUrl}
                                alt={peerUser.displayName}
                                className="w-9 h-9 rounded-full object-cover border border-slate-700"
                              />
                              <span className="absolute bottom-0 left-0 w-2.5 h-2.5 rounded-full bg-emerald-500 border border-slate-900"></span>
                            </div>
                            <div>
                              <div className="text-xs font-bold text-white flex items-center gap-1">
                                <span>{peerUser.displayName}</span>
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setSimulatorScreen('safety_number');
                                  }}
                                  className="text-teal-400 hover:text-teal-300 p-0.5"
                                  title="التحقق من التشفير ورقم الأمان"
                                >
                                  <ShieldCheck className="w-3.5 h-3.5" />
                                </button>
                              </div>
                              <span className="text-[10px] text-emerald-400">متصل الآن</span>
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-1 text-slate-300">
                          <button
                            onClick={() => {
                              setActiveCallType('video');
                              setSimulatorScreen('call');
                            }}
                            className="p-2 rounded-xl hover:bg-slate-800 hover:text-teal-400 transition-colors"
                            title="مكالمة مرئية WebRTC"
                          >
                            <Video className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => {
                              setActiveCallType('voice');
                              setSimulatorScreen('call');
                            }}
                            className="p-2 rounded-xl hover:bg-slate-800 hover:text-teal-400 transition-colors"
                            title="مكالمة صوتية مشفرة"
                          >
                            <Phone className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setShowEncryptedPayload(!showEncryptedPayload)}
                            className={`p-2 rounded-xl transition-colors ${
                              showEncryptedPayload
                                ? 'bg-teal-500/20 text-teal-300'
                                : 'hover:bg-slate-800 hover:text-white'
                            }`}
                            title="معاينة الحزم المشفرة بالسيرفر"
                          >
                            <Key className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      {/* E2EE Info Pill */}
                      <div className="py-1 px-3 bg-teal-950/40 border-b border-teal-900/30 flex items-center justify-between text-[11px] text-teal-300">
                        <div className="flex items-center gap-1.5 truncate">
                          <Lock className="w-3 h-3 text-teal-400 shrink-0" />
                          <span className="truncate">محادثة مشفرة تماماً (E2EE) بين الطرفين</span>
                        </div>
                        <button
                          onClick={() => setSimulatorScreen('safety_number')}
                          className="underline hover:text-white text-[10px] shrink-0 font-medium"
                        >
                          رقم الأمان
                        </button>
                      </div>

                      {/* Messages Scroll Area */}
                      <div className="flex-1 overflow-y-auto p-3.5 space-y-3 bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:16px_16px]">
                        {chatMessages.map((msg) => {
                          const isMe = msg.senderId === currentUser.id;
                          return (
                            <div
                              key={msg.id}
                              className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}
                            >
                              <div
                                className={`max-w-[85%] rounded-2xl p-3 shadow-sm ${
                                  isMe
                                    ? 'bg-teal-700 text-white rounded-tr-none'
                                    : 'bg-slate-800 text-slate-100 rounded-tl-none border border-slate-700/60'
                                }`}
                              >
                                {/* TEXT TYPE */}
                                {msg.type === 'text' && (
                                  <p className="text-xs leading-relaxed break-words">{msg.text}</p>
                                )}

                                {/* AUDIO TYPE */}
                                {msg.type === 'audio' && (
                                  <div className="flex items-center gap-2.5 min-w-[200px]">
                                    <button
                                      onClick={() =>
                                        setActiveAudioPlaying(
                                          activeAudioPlaying === msg.id ? null : msg.id
                                        )
                                      }
                                      className={`w-8 h-8 rounded-full flex items-center justify-center transition-colors ${
                                        isMe
                                          ? 'bg-white text-teal-800'
                                          : 'bg-teal-500 text-slate-950'
                                      }`}
                                    >
                                      {activeAudioPlaying === msg.id ? (
                                        <Pause className="w-4 h-4 fill-current" />
                                      ) : (
                                        <Play className="w-4 h-4 fill-current ml-0.5" />
                                      )}
                                    </button>

                                    {/* Audio Waveform visualization */}
                                    <div className="flex-1 flex items-center gap-0.5 h-6">
                                      {(msg.audioWave || [30, 60, 40, 80, 50, 70, 90, 40]).map(
                                        (height, i) => (
                                          <span
                                            key={i}
                                            style={{ height: `${height}%` }}
                                            className={`w-1 rounded-full transition-all ${
                                              activeAudioPlaying === msg.id
                                                ? 'bg-emerald-300 animate-pulse'
                                                : isMe
                                                ? 'bg-teal-200/60'
                                                : 'bg-slate-500'
                                            }`}
                                          />
                                        )
                                      )}
                                    </div>

                                    <span className="text-[10px] font-mono opacity-80">
                                      {msg.audioDuration}
                                    </span>
                                  </div>
                                )}

                                {/* IMAGE TYPE */}
                                {msg.type === 'image' && (
                                  <div className="space-y-1.5">
                                    <img
                                      src={msg.imageUrl}
                                      alt="Media"
                                      className="rounded-xl w-full max-h-48 object-cover border border-black/20"
                                    />
                                    {msg.caption && (
                                      <p className="text-xs pt-1">{msg.caption}</p>
                                    )}
                                  </div>
                                )}

                                {/* Message Footer: Time + Status */}
                                <div className="flex items-center justify-end gap-1.5 mt-1 text-[10px] opacity-75 font-mono">
                                  <span>{msg.timestamp}</span>
                                  {isMe && (
                                    <span>
                                      {msg.status === 'sending' && (
                                        <RefreshCw className="w-3 h-3 animate-spin text-teal-200" />
                                      )}
                                      {msg.status === 'sent' && <Check className="w-3 h-3" />}
                                      {msg.status === 'delivered' && (
                                        <CheckCheck className="w-3 h-3 text-slate-300" />
                                      )}
                                      {msg.status === 'read' && (
                                        <CheckCheck className="w-3 h-3 text-cyan-300" />
                                      )}
                                    </span>
                                  )}
                                </div>

                                {/* Raw Encrypted Payload Toggle Inspector */}
                                {showEncryptedPayload && (
                                  <div className="mt-2 pt-2 border-t border-black/20 text-[9px] font-mono bg-black/30 rounded p-1.5 text-teal-300 break-all select-all">
                                    <div className="text-[8px] text-amber-300 mb-0.5">
                                      Ciphertext المخزن في Supabase:
                                    </div>
                                    {msg.ciphertext}
                                  </div>
                                )}
                              </div>
                            </div>
                          );
                        })}
                        <div ref={messagesEndRef} />
                      </div>

                      {/* Input composer */}
                      <div className="p-2.5 bg-slate-900 border-t border-slate-800">
                        {isRecordingVoice ? (
                          <div className="flex items-center justify-between bg-rose-950/40 border border-rose-900/60 rounded-2xl px-4 py-2.5">
                            <div className="flex items-center gap-2 text-rose-400 text-xs font-semibold">
                              <span className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-ping"></span>
                              <span>جارٍ تسجيل رسالة صوتية مشفرة...</span>
                              <span className="font-mono text-white">0:0{recordingSeconds}</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => setIsRecordingVoice(false)}
                                className="text-xs text-slate-400 hover:text-white px-2 py-1"
                              >
                                إلغاء
                              </button>
                              <button
                                onClick={handleSendVoiceNote}
                                className="px-3 py-1 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-bold"
                              >
                                إرسال
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="flex items-center gap-1.5">
                            <div className="flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-3 py-2 flex items-center gap-2 focus-within:border-teal-500">
                              <button
                                className="text-slate-400 hover:text-slate-200"
                                title="إضافة وسائط"
                                onClick={() => {
                                  setInputText('🖼️ [تم إرفاق صورة تجريبية]');
                                }}
                              >
                                <Paperclip className="w-4 h-4" />
                              </button>
                              <input
                                type="text"
                                value={inputText}
                                onChange={(e) => setInputText(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
                                placeholder="اكتب رسالة مشفرة..."
                                className="bg-transparent text-xs text-white placeholder-slate-500 focus:outline-none flex-1 text-right"
                                dir="rtl"
                              />
                              <button
                                onClick={() => setShowEncryptedPayload(!showEncryptedPayload)}
                                className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                                  showEncryptedPayload
                                    ? 'bg-teal-500/20 text-teal-300'
                                    : 'text-slate-500 hover:text-slate-400'
                                }`}
                              >
                                E2EE
                              </button>
                            </div>

                            {inputText.trim() ? (
                              <button
                                onClick={handleSendMessage}
                                className="w-10 h-10 rounded-2xl bg-teal-600 hover:bg-teal-500 text-white flex items-center justify-center transition-all shrink-0 shadow-md shadow-teal-600/30"
                              >
                                <Send className="w-4 h-4 ml-0.5" />
                              </button>
                            ) : (
                              <button
                                onClick={() => setIsRecordingVoice(true)}
                                className="w-10 h-10 rounded-2xl bg-slate-800 hover:bg-teal-600 text-slate-300 hover:text-white flex items-center justify-center transition-all shrink-0"
                                title="تسجيل صوتي PTT"
                              >
                                <Mic className="w-4 h-4" />
                              </button>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {/* --- SCREEN: SAFETY NUMBER (E2EE VERIFICATION) --- */}
                  {simulatorScreen === 'safety_number' && (
                    <div className="flex-1 flex flex-col bg-slate-950 p-4">
                      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                        <button
                          onClick={() => setSimulatorScreen('chat_room')}
                          className="p-1 rounded-lg text-slate-300 hover:text-white"
                        >
                          <ArrowRight className="w-5 h-5" />
                        </button>
                        <h3 className="text-sm font-bold text-white">التحقق من رقم الأمان</h3>
                        <div className="w-6"></div>
                      </div>

                      <div className="flex-1 overflow-y-auto py-4 space-y-4 text-center">
                        <div className="w-16 h-16 mx-auto rounded-3xl bg-teal-500/10 border border-teal-500/30 flex items-center justify-center text-teal-400">
                          <ShieldCheck className="w-8 h-8" />
                        </div>

                        <div>
                          <h4 className="text-sm font-bold text-white">
                            التحقق من أمان المحادثة مع {peerUser.displayName}
                          </h4>
                          <p className="text-[11px] text-slate-400 mt-1 max-w-xs mx-auto leading-relaxed">
                            تتم حماية هذه المحادثة عبر التشفير التام (ECDH P-256 + AES-256-GCM). قارن رقم الأمان مع الطرف الآخر للتأكد من عدم وجود طرف ثالث.
                          </p>
                        </div>

                        {/* Simulated QR Code */}
                        <div className="p-3 bg-white rounded-2xl w-36 h-36 mx-auto flex items-center justify-center shadow-md">
                          <QrCode className="w-28 h-28 text-slate-950" />
                        </div>

                        {/* 60-digit safety numbers in 12 blocks */}
                        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3 font-mono text-xs text-teal-300 tracking-wider leading-relaxed select-all">
                          {peerUser.safetyNumber}
                        </div>

                        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-3 text-right">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-white font-medium">تمييز كرقم أمان موثّق</span>
                            <button
                              onClick={() => setSafetyVerified(!safetyVerified)}
                              className={`w-11 h-6 rounded-full transition-colors relative ${
                                safetyVerified ? 'bg-teal-600' : 'bg-slate-700'
                              }`}
                            >
                              <span
                                className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform ${
                                  safetyVerified ? 'right-6' : 'right-1'
                                }`}
                              />
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* --- SCREEN: SEARCH USERS --- */}
                  {simulatorScreen === 'search' && (
                    <div className="flex-1 flex flex-col bg-slate-950 p-4">
                      <div className="flex items-center gap-2 pb-3 border-b border-slate-800">
                        <button
                          onClick={() => setSimulatorScreen('chats')}
                          className="p-1 rounded-lg text-slate-300 hover:text-white"
                        >
                          <ArrowRight className="w-5 h-5" />
                        </button>
                        <div className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-3 py-1.5 flex items-center gap-2">
                          <Search className="w-4 h-4 text-slate-400" />
                          <input
                            type="text"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="ابحث باسم المعرف @username..."
                            className="bg-transparent text-xs text-white placeholder-slate-500 focus:outline-none flex-1 text-right"
                            dir="rtl"
                          />
                        </div>
                      </div>

                      <div className="py-2 text-[11px] text-slate-400 flex items-center gap-1">
                        <Lock className="w-3 h-3 text-teal-400" />
                        <span>رقم الهاتف والبريد الإلكتروني مخفيان تماماً لحماية الخصوصية</span>
                      </div>

                      <div className="flex-1 overflow-y-auto divide-y divide-slate-850">
                        {filteredUsers.map((user) => (
                          <div
                            key={user.id}
                            className="py-3 flex items-center justify-between gap-3"
                          >
                            <div className="flex items-center gap-3">
                              <img
                                src={user.avatarUrl}
                                alt={user.displayName}
                                className="w-10 h-10 rounded-full object-cover border border-slate-700"
                              />
                              <div>
                                <h4 className="text-xs font-bold text-white">{user.displayName}</h4>
                                <span className="text-[11px] text-teal-400 font-mono">
                                  @{user.username}
                                </span>
                                <p className="text-[10px] text-slate-400 line-clamp-1">{user.bio}</p>
                              </div>
                            </div>

                            <button
                              onClick={() => {
                                setSimulatorScreen('chat_room');
                              }}
                              className="px-3 py-1.5 rounded-xl bg-teal-600 hover:bg-teal-500 text-white text-xs font-bold transition-colors shrink-0"
                            >
                              محادثة
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* --- SCREEN: USER PROFILE & PRIVACY --- */}
                  {simulatorScreen === 'profile' && (
                    <div className="flex-1 flex flex-col bg-slate-950 p-4">
                      <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                        <button
                          onClick={() => setSimulatorScreen('chats')}
                          className="p-1 rounded-lg text-slate-300 hover:text-white"
                        >
                          <ArrowRight className="w-5 h-5" />
                        </button>
                        <h3 className="text-sm font-bold text-white">الملف الشخصي والخصوصية</h3>
                        <div className="w-6"></div>
                      </div>

                      <div className="flex-1 overflow-y-auto py-4 space-y-4 text-center">
                        <div className="relative inline-block mx-auto">
                          <img
                            src={currentUser.avatarUrl}
                            alt={currentUser.displayName}
                            className="w-20 h-20 rounded-full object-cover border-2 border-teal-500 shadow-lg"
                          />
                        </div>

                        <div>
                          <h4 className="text-base font-bold text-white">{currentUser.displayName}</h4>
                          <span className="text-xs text-teal-400 font-mono">@{currentUser.username}</span>
                          <p className="text-xs text-slate-300 mt-2 bg-slate-900/60 p-2 rounded-xl border border-slate-800">
                            {currentUser.bio}
                          </p>
                        </div>

                        {/* Private Data Warning (Visible only to owner) */}
                        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3 text-right space-y-2">
                          <div className="text-[11px] font-bold text-teal-400 flex items-center gap-1.5">
                            <Lock className="w-3.5 h-3.5" />
                            <span>بيانات الاتصال المحمية (لا يراها أحد سواك):</span>
                          </div>
                          <div className="text-xs text-slate-300 font-mono flex justify-between">
                            <span className="text-slate-400">الهاتف:</span>
                            <span>{currentUser.phone}</span>
                          </div>
                          <div className="text-xs text-slate-300 font-mono flex justify-between">
                            <span className="text-slate-400">البريد:</span>
                            <span>{currentUser.email}</span>
                          </div>
                        </div>

                        {/* E2EE Key Pair Fingerprint */}
                        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3 text-right">
                          <div className="text-[11px] font-bold text-purple-400 flex items-center gap-1.5 mb-1">
                            <Key className="w-3.5 h-3.5" />
                            <span>مفتاح التشفير العام (ECDH-P256):</span>
                          </div>
                          <p className="text-[10px] font-mono text-slate-400 break-all select-all">
                            {currentUser.publicKeyPreview}
                          </p>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* --- SCREEN: LIVE CALL SIMULATOR (WEBRTC) --- */}
                  {simulatorScreen === 'call' && (
                    <div className="flex-1 flex flex-col bg-gradient-to-b from-slate-900 via-slate-950 to-slate-950 p-6 items-center justify-between">
                      <div className="text-center pt-8">
                        <div className="relative inline-block mb-4">
                          <img
                            src={peerUser.avatarUrl}
                            alt={peerUser.displayName}
                            className="w-24 h-24 rounded-full object-cover border-4 border-teal-500/50 shadow-2xl"
                          />
                          <span className="absolute inset-0 rounded-full border-2 border-teal-400 animate-ping opacity-30"></span>
                        </div>
                        <h3 className="text-lg font-bold text-white">{peerUser.displayName}</h3>
                        <p className="text-xs text-teal-400 font-medium mt-1">
                          {activeCallType === 'video' ? 'مكالمة مرئية مشفرة' : 'مكالمة صوتية مشفرة'}
                        </p>
                        <span className="text-xs font-mono text-slate-400 mt-2 block">
                          00:{callDuration < 10 ? `0${callDuration}` : callDuration}
                        </span>
                      </div>

                      {/* Wave visualizer */}
                      <div className="flex items-center gap-1.5 h-12">
                        {[40, 70, 90, 50, 80, 100, 60, 45, 85, 65, 30].map((h, idx) => (
                          <div
                            key={idx}
                            style={{ height: `${h}%` }}
                            className="w-1.5 bg-teal-400 rounded-full animate-pulse"
                          />
                        ))}
                      </div>

                      {/* Call Controls */}
                      <div className="w-full pb-8 flex items-center justify-center gap-4">
                        <button
                          onClick={() => setIsMuted(!isMuted)}
                          className={`w-12 h-12 rounded-full flex items-center justify-center transition-colors ${
                            isMuted ? 'bg-rose-600 text-white' : 'bg-slate-800 text-slate-200'
                          }`}
                        >
                          <Mic className="w-5 h-5" />
                        </button>

                        <button
                          onClick={() => setSimulatorScreen('chat_room')}
                          className="w-16 h-16 rounded-full bg-rose-600 hover:bg-rose-500 text-white flex items-center justify-center shadow-lg shadow-rose-600/40 transition-transform active:scale-95"
                          title="إنهاء المكالمة"
                        >
                          <PhoneOff className="w-7 h-7" />
                        </button>

                        <button
                          onClick={() => setIsSpeaker(!isSpeaker)}
                          className={`w-12 h-12 rounded-full flex items-center justify-center transition-colors ${
                            isSpeaker ? 'bg-teal-600 text-white' : 'bg-slate-800 text-slate-200'
                          }`}
                        >
                          <Volume2 className="w-5 h-5" />
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                {/* Android Bottom System Bar */}
                <div className="bg-slate-950 py-2 flex items-center justify-center border-t border-slate-900">
                  <div className="w-32 h-1 bg-slate-700 rounded-full"></div>
                </div>
              </div>
            </div>

            {/* Right Column: Emulator Controls & Quick Actions */}
            <div className="lg:col-span-5 space-y-6">
              {/* Emulator Interactive Guide */}
              <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-xl">
                <div className="flex items-center gap-3 mb-4">
                  <div className="w-10 h-10 rounded-2xl bg-teal-600/20 text-teal-400 flex items-center justify-center border border-teal-500/30">
                    <Sliders className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-white">التحكم في المحاكي المباشر</h3>
                    <p className="text-xs text-slate-400">جرب خصائص أندرويد الحقيقية في تواصل</p>
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="p-3 rounded-2xl bg-slate-950 border border-slate-800">
                    <div className="text-xs font-bold text-teal-300 mb-1 flex items-center gap-1.5">
                      <Key className="w-3.5 h-3.5" />
                      <span>فحص التشفير التام (E2EE) المباشر:</span>
                    </div>
                    <p className="text-xs text-slate-400 leading-relaxed">
                      انقر على أيقونة المفتاح <Key className="w-3 h-3 inline text-teal-400" /> في أعلى المحادثة لمعاينة نصوص التشفير (Ciphertext) الفعلية التي تُخزن في سيرفر Supabase ولا يستطيع فكها إلا الهاتف صاحب المفتاح الخاص!
                    </p>
                  </div>

                  <div className="p-3 rounded-2xl bg-slate-950 border border-slate-800">
                    <div className="text-xs font-bold text-emerald-300 mb-1 flex items-center gap-1.5">
                      <Mic className="w-3.5 h-3.5" />
                      <span>الرسائل الصوتية التفاعلية (Waveform):</span>
                    </div>
                    <p className="text-xs text-slate-400 leading-relaxed">
                      انقر على زر الميكروفون بالأسفل لبدء تسجيل صوتي تجريبي مع مؤقت وعداد حي، ثم أرسله ليظهر فوراً مع موجات ترددية تفاعلية وقابلية للتشغيل.
                    </p>
                  </div>

                  <div className="p-3 rounded-2xl bg-slate-950 border border-slate-800">
                    <div className="text-xs font-bold text-purple-300 mb-1 flex items-center gap-1.5">
                      <ShieldCheck className="w-3.5 h-3.5" />
                      <span>التحقق من رقم الأمان (Safety Number):</span>
                    </div>
                    <p className="text-xs text-slate-400 leading-relaxed">
                      انقر على شارة الدرع بجانب اسم جهة الاتصال لفتح شاشة فحص أرقام الأمان المكونة من 60 رقماً مشتقة من المفاتيح العامة للطرفين لمنع هجمات الوسيط (MITM).
                    </p>
                  </div>
                </div>

                {/* Quick Screen Navigation Buttons */}
                <div className="mt-5 pt-4 border-t border-slate-800/80">
                  <div className="text-xs font-semibold text-slate-400 mb-2">التنقل السريع بين الشاشات:</div>
                  <div className="grid grid-cols-2 gap-2 text-xs font-medium">
                    <button
                      onClick={() => setSimulatorScreen('chats')}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        simulatorScreen === 'chats'
                          ? 'bg-teal-600/20 border-teal-500 text-teal-300'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:bg-slate-850'
                      }`}
                    >
                      قائمة المحادثات
                    </button>
                    <button
                      onClick={() => setSimulatorScreen('chat_room')}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        simulatorScreen === 'chat_room'
                          ? 'bg-teal-600/20 border-teal-500 text-teal-300'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:bg-slate-850'
                      }`}
                    >
                      غرفة المحادثة
                    </button>
                    <button
                      onClick={() => setSimulatorScreen('safety_number')}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        simulatorScreen === 'safety_number'
                          ? 'bg-teal-600/20 border-teal-500 text-teal-300'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:bg-slate-850'
                      }`}
                    >
                      أرقام الأمان (E2EE)
                    </button>
                    <button
                      onClick={() => setSimulatorScreen('search')}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        simulatorScreen === 'search'
                          ? 'bg-teal-600/20 border-teal-500 text-teal-300'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:bg-slate-850'
                      }`}
                    >
                      البحث عن المعرفات
                    </button>
                    <button
                      onClick={() => setSimulatorScreen('profile')}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        simulatorScreen === 'profile'
                          ? 'bg-teal-600/20 border-teal-500 text-teal-300'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:bg-slate-850'
                      }`}
                    >
                      الملف والخصوصية
                    </button>
                    <button
                      onClick={() => {
                        setActiveCallType('voice');
                        setSimulatorScreen('call');
                      }}
                      className={`p-2.5 rounded-xl border text-center transition-all ${
                        simulatorScreen === 'call'
                          ? 'bg-teal-600/20 border-teal-500 text-teal-300'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:bg-slate-850'
                      }`}
                    >
                      مكالمة نشطة (Call)
                    </button>
                  </div>
                </div>
              </div>

              {/* Native Architecture Spec Box */}
              <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-xl">
                <h4 className="text-sm font-bold text-white mb-3 flex items-center gap-2">
                  <Database className="w-4 h-4 text-teal-400" />
                  <span>مواصفات تطبيق أندرويد الأصيل</span>
                </h4>
                <div className="space-y-2 text-xs">
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400">لغة البرمجة:</span>
                    <span className="font-mono text-teal-300 font-semibold">Kotlin 2.0+</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400">واجهة المستخدم:</span>
                    <span className="font-mono text-teal-300 font-semibold">Jetpack Compose + M3</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400">قاعدة البيانات المحلية:</span>
                    <span className="font-mono text-teal-300 font-semibold">Room SQLite (Offline-first)</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400">تخزين المفاتيح:</span>
                    <span className="font-mono text-teal-300 font-semibold">Android KeyStore (Hardware)</span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400">خوارزمية التشفير:</span>
                    <span className="font-mono text-purple-300 font-semibold">ECDH P-256 + AES-GCM</span>
                  </div>
                  <div className="flex justify-between py-1">
                    <span className="text-slate-400">الحزمة (Package):</span>
                    <span className="font-mono text-slate-300">com.tawasol.app</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: ROADMAP 7 PHASES */}
        {activeMainTab === 'phases' && (
          <div className="space-y-6">
            <div className="bg-gradient-to-br from-slate-900 via-slate-900 to-slate-800/80 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-xl">
              <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
                <div>
                  <h2 className="text-2xl font-bold text-white mb-2">
                    المراحل السبع لمشروع تواصل (Complete 7-Phase Architecture)
                  </h2>
                  <p className="text-slate-300 text-sm max-w-3xl leading-relaxed">
                    تم بناء تطبيق تواصل كمنظومة متكاملة للتواصل الفوري الآمن على أندرويد بدءاً من المعمارية الأصيلة وحتى التشفير التام بين الطرفين (E2EE).
                  </p>
                </div>
                <span className="px-3.5 py-1.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-xs font-semibold flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4" />
                  All 7 Phases Implemented
                </span>
              </div>
            </div>

            {/* Grid of 7 Phases */}
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
              {/* Phase 1 */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-teal-500/50 transition-all flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-teal-400 bg-teal-500/10 px-2.5 py-1 rounded-lg border border-teal-500/20">
                      PHASE 01
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <h3 className="text-base font-bold text-white mb-2">التأسيس ومعمارية أندرويد الأصيلة</h3>
                  <p className="text-xs text-slate-400 leading-relaxed mb-4">
                    بناء هيكل Clean Architecture مع Jetpack Compose و Material 3 ودعم كامل لاتجاه RTL العربي، مع إعداد Room Database ونظام Outbox Queue للعمل بدون إنترنت و Android KeyStore.
                  </p>
                </div>
                <div className="pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-400">
                  Room, AppContainer, RTL, libs.versions.toml
                </div>
              </div>

              {/* Phase 2 */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-teal-500/50 transition-all flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-teal-400 bg-teal-500/10 px-2.5 py-1 rounded-lg border border-teal-500/20">
                      PHASE 02
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <h3 className="text-base font-bold text-white mb-2">نظام الحسابات والخصوصية الصارمة</h3>
                  <p className="text-xs text-slate-400 leading-relaxed mb-4">
                    المصادقة عبر Supabase Auth مع التحقق من اسم المستخدم الفريد (@username). عزل الهاتف والبريد في جدول <code className="text-teal-300 font-mono">user_contacts</code> وتطبيق سياسات RLS صارمة لحمايتهما.
                  </p>
                </div>
                <div className="pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-400">
                  Supabase Auth, RLS isolation, UserSearchScreen
                </div>
              </div>

              {/* Phase 3 */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-teal-500/50 transition-all flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-teal-400 bg-teal-500/10 px-2.5 py-1 rounded-lg border border-teal-500/20">
                      PHASE 03
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <h3 className="text-base font-bold text-white mb-2">المحادثات الفردية الفورية (Real 1-to-1)</h3>
                  <p className="text-xs text-slate-400 leading-relaxed mb-4">
                    إنشاء المحادثات بدالة ذرية في Postgres، استماع Realtime للرسائل الجديدة، حالات الرسائل (sending → sent → delivered → read)، والترقيم مع التخزين المحلي في Room.
                  </p>
                </div>
                <div className="pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-400">
                  Supabase Realtime, ChatScreen, Atomic RPC
                </div>
              </div>

              {/* Phase 4 */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-teal-500/50 transition-all flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-teal-400 bg-teal-500/10 px-2.5 py-1 rounded-lg border border-teal-500/20">
                      PHASE 04
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <h3 className="text-base font-bold text-white mb-2">الرسائل الصوتية والوسائط (Media & Audio)</h3>
                  <p className="text-xs text-slate-400 leading-relaxed mb-4">
                    تسجيل وبث الرسائل الصوتية PTT مع موجات ترددية تفاعلية، إرسال الصور والمستندات المشفرة ورفعها إلى Supabase Storage (chat-media bucket) مع التخزين في Room.
                  </p>
                </div>
                <div className="pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-400">
                  Audio Waveforms, Chat-Media Bucket, Coil
                </div>
              </div>

              {/* Phase 5 */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-teal-500/50 transition-all flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-teal-400 bg-teal-500/10 px-2.5 py-1 rounded-lg border border-teal-500/20">
                      PHASE 05
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <h3 className="text-base font-bold text-white mb-2">المكالمات الصوتية والمرئية (Calls Signaling)</h3>
                  <p className="text-xs text-slate-400 leading-relaxed mb-4">
                    بنية تبادل الإشارات (Signaling) للمكالمات الصوتية والمرئية، جداول الحالات (incoming, outgoing, ringing, accepted, ended) ومحاكي الاتصال المباشر.
                  </p>
                </div>
                <div className="pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-400">
                  WebRTC Signaling, CallScreen, Audio Routing
                </div>
              </div>

              {/* Phase 6 */}
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 hover:border-teal-500/50 transition-all flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-teal-400 bg-teal-500/10 px-2.5 py-1 rounded-lg border border-teal-500/20">
                      PHASE 06
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <h3 className="text-base font-bold text-white mb-2">المجموعات والحالات (Groups & Stories)</h3>
                  <p className="text-xs text-slate-400 leading-relaxed mb-4">
                    إنشاء المحادثات الجماعية عبر RPC الذري <code className="text-teal-300 font-mono">create_group_conversation</code> وإدارة الأعضاء وصلاحيات المشرفين مع سياسات RLS للمجموعات.
                  </p>
                </div>
                <div className="pt-3 border-t border-slate-800 text-[11px] font-mono text-slate-400">
                  Group Membership, Group Admin Roles, RPC
                </div>
              </div>

              {/* Phase 7 */}
              <div className="bg-slate-900 border-2 border-teal-500/60 rounded-2xl p-5 hover:border-teal-400 transition-all flex flex-col justify-between md:col-span-2 lg:col-span-3 bg-gradient-to-r from-slate-900 via-teal-950/20 to-slate-900">
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono font-bold text-purple-400 bg-purple-500/10 px-2.5 py-1 rounded-lg border border-purple-500/30">
                      PHASE 07 - MAXIMUM SECURITY
                    </span>
                    <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-teal-500/10 text-teal-300 border border-teal-500/30">
                      E2EE Cryptography
                    </span>
                  </div>
                  <h3 className="text-lg font-bold text-white mb-2">التشفير التام بين الطرفين (End-to-End Encryption)</h3>
                  <p className="text-xs text-slate-300 leading-relaxed max-w-4xl">
                    توليد مفاتيح المنحنيات الإهليلجية ECDH على معيار NIST P-256 محلياً داخل Android KeyStore المشفر بمفتاح العتاد، تبادل المفاتيح العامة عبر جدول <code className="text-teal-300 font-mono">user_e2ee_keys</code> مع سياسات RLS، اشتقاق المفاتيح بـ SHA-256، وتشفير حمولة الرسائل بـ AES-256-GCM متضمنة التحقق من السلامة (IV + Auth Tag)، وتوليد أرقام الأمان (Safety Numbers) لمنع هجمات الوسيط (MITM).
                  </p>
                </div>
                <div className="pt-4 mt-4 border-t border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs">
                  <div className="flex items-center gap-3 font-mono text-slate-400">
                    <span>E2EEManager.kt</span>
                    <span>•</span>
                    <span>phase7_e2ee.sql</span>
                    <span>•</span>
                    <span>AES-256-GCM</span>
                    <span>•</span>
                    <span>ECDH secp256r1</span>
                  </div>
                  <button
                    onClick={() => {
                      setActiveMainTab('security');
                    }}
                    className="text-teal-400 hover:text-teal-300 font-bold flex items-center gap-1"
                  >
                    <span>تفاصيل المعمارية الأمنية</span>
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: CODE INSPECTOR */}
        {activeMainTab === 'code' && (
          <div className="grid lg:grid-cols-12 gap-6">
            <div className="lg:col-span-4 space-y-2">
              <div className="text-xs font-semibold text-slate-400 px-2 mb-2 flex items-center justify-between">
                <span>ملفات المشروع الأصلية (Kotlin & SQL)</span>
                <span className="font-mono text-teal-400">{ANDROID_FILES.length} ملفات</span>
              </div>
              <div className="space-y-1.5 max-h-[620px] overflow-y-auto pr-1">
                {ANDROID_FILES.map((file) => (
                  <button
                    key={file.path}
                    onClick={() => setSelectedFile(file)}
                    className={`w-full text-right p-3 rounded-xl border transition-all flex items-start justify-between gap-2 ${
                      selectedFile.path === file.path
                        ? 'bg-teal-500/15 border-teal-500/60 text-white shadow-md shadow-teal-950/30'
                        : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-850 hover:border-slate-700'
                    }`}
                  >
                    <div>
                      <div className="font-mono text-xs font-bold text-teal-300">{file.name}</div>
                      <div className="text-xs text-slate-400 mt-1 line-clamp-1">{file.description}</div>
                    </div>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 uppercase font-mono">
                      {file.category}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            <div className="lg:col-span-8 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col">
              <div className="p-3.5 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
                <span className="text-xs font-mono text-teal-400 font-medium">{selectedFile.path}</span>
                <button
                  onClick={() => copyToClipboard(selectedFile.codeSnippet, selectedFile.name)}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 flex items-center gap-1.5 transition-colors border border-slate-700"
                >
                  <Copy className="w-3.5 h-3.5 text-teal-400" />
                  <span>{copiedText === selectedFile.name ? 'تم النسخ!' : 'نسخ الكود'}</span>
                </button>
              </div>
              <div className="p-4 bg-slate-950/70 border-b border-slate-800/60 text-xs text-slate-300 leading-relaxed">
                <b className="text-white">الشرح الهندسي: </b> {selectedFile.description}
              </div>
              <pre className="p-4 text-xs font-mono text-slate-200 overflow-x-auto max-h-[520px] leading-relaxed bg-slate-950 text-left dir-ltr">
                <code>{selectedFile.codeSnippet}</code>
              </pre>
            </div>
          </div>
        )}

        {/* TAB 4: E2EE & SECURITY ARCHITECTURE */}
        {activeMainTab === 'security' && (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-xl">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 rounded-2xl bg-purple-600/20 text-purple-400 flex items-center justify-center border border-purple-500/30">
                  <ShieldCheck className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">معمارية التشفير التام (E2EE Pipeline)</h3>
                  <p className="text-xs text-slate-400">
                    كيف يتم حماية الرسائل والوسائط بين مرسل ومستقبل بحيث لا يستطيع السيرفر قراءتها
                  </p>
                </div>
              </div>

              {/* Step-by-Step Cryptographic Flow */}
              <div className="grid md:grid-cols-4 gap-4 mt-6">
                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="text-xs font-mono font-bold text-purple-400 mb-1">الخطوة 1</div>
                  <div className="text-sm font-bold text-white mb-2">توليد زوج المفاتيح محلياً</div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    يولد الجهاز مفتاحين (خاص وعام) على منحنى NIST P-256. يُخزن المفتاح الخاص في Android KeyStore المادي ولا يغادر الهاتف أبداً.
                  </p>
                </div>

                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="text-xs font-mono font-bold text-blue-400 mb-1">الخطوة 2</div>
                  <div className="text-sm font-bold text-white mb-2">نشر المفتاح العام</div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    يتم رفع المفتاح العام فقط إلى جدول <code className="text-teal-300 font-mono">user_e2ee_keys</code> في Supabase ليتمكن المستخدمون الآخرون من قراءته.
                  </p>
                </div>

                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="text-xs font-mono font-bold text-teal-400 mb-1">الخطوة 3</div>
                  <div className="text-sm font-bold text-white mb-2">اشتقاق المفتاح المشترك (ECDH)</div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    عند إرسال رسالة لسارة، يجمع أحمد مفتاحه الخاص مع مفتاح سارة العام لتوليد سر مشترك، ثم يمرره عبر SHA-256 للحصول على مفتاح AES-256 متماثل.
                  </p>
                </div>

                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="text-xs font-mono font-bold text-emerald-400 mb-1">الخطوة 4</div>
                  <div className="text-sm font-bold text-white mb-2">التشفير المتماثل بـ AES-GCM</div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    تُشفر الرسالة أو الوسائط بـ AES-256-GCM مع متجه تهيئة (IV) عشوائي بطول 12 بايت و Auth Tag يمنع أي تعديل أو تلاعب في السيرفر.
                  </p>
                </div>
              </div>
            </div>

            {/* Privacy Matrix: Public vs Private vs E2EE */}
            <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8">
              <h3 className="text-lg font-bold text-white mb-4">مصفوفة توزيع البيانات ومستويات الحماية</h3>
              <div className="overflow-x-auto">
                <table className="w-full text-right text-xs">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400">
                      <th className="py-3 px-4">نوع البيانات</th>
                      <th className="py-3 px-4">موقع التخزين</th>
                      <th className="py-3 px-4">سياسة الأمان (RLS / E2EE)</th>
                      <th className="py-3 px-4">من يستطيع الاطلاع؟</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono text-slate-300">
                    <tr>
                      <td className="py-3 px-4 text-white font-sans font-semibold">محتوى الرسائل والصوتيات</td>
                      <td className="py-3 px-4 text-teal-300">public.messages</td>
                      <td className="py-3 px-4 text-emerald-400">E2EE (AES-256-GCM + ECDH)</td>
                      <td className="py-3 px-4 text-slate-300 font-sans">طرفا المحادثة فقط (الهواتف)</td>
                    </tr>
                    <tr>
                      <td className="py-3 px-4 text-white font-sans font-semibold">رقم الهاتف والبريد</td>
                      <td className="py-3 px-4 text-blue-300">public.user_contacts</td>
                      <td className="py-3 px-4 text-emerald-400">RLS (auth.uid() = user_id)</td>
                      <td className="py-3 px-4 text-slate-300 font-sans">المستخدم نفسه فقط</td>
                    </tr>
                    <tr>
                      <td className="py-3 px-4 text-white font-sans font-semibold">المفتاح العام للمستخدم</td>
                      <td className="py-3 px-4 text-purple-300">public.user_e2ee_keys</td>
                      <td className="py-3 px-4 text-slate-400">RLS: Authenticated SELECT</td>
                      <td className="py-3 px-4 text-slate-300 font-sans">جميع المستخدمين المسجلين لتشفير الرسائل له</td>
                    </tr>
                    <tr>
                      <td className="py-3 px-4 text-white font-sans font-semibold">المفتاح الخاص للمستخدم</td>
                      <td className="py-3 px-4 text-rose-400">Android KeyStore (Local)</td>
                      <td className="py-3 px-4 text-rose-400">Hardware AES-256 MasterKey</td>
                      <td className="py-3 px-4 text-slate-300 font-sans">نظام أندرويد على هاتف المستخدم فقط</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: GRADLE & COMMAND LINE */}
        {activeMainTab === 'gradle' && (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-xl">
              <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2">
                <Terminal className="w-5 h-5 text-amber-400" />
                <span>أوامر بناء وتشغيل تطبيق تواصل على أندرويد</span>
              </h3>
              <p className="text-xs text-slate-400 mb-6 leading-relaxed">
                مشروع تواصل هو تطبيق Android Native أصيل بالكامل. يمكنك تجميعه عبر سطر الأوامر أو استيراده مباشرة في Android Studio.
              </p>

              <div className="space-y-4">
                {/* Command 1: assembleDebug */}
                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-white">تجميع نسخة التصحيح (Debug APK):</span>
                    <button
                      onClick={() => copyToClipboard('./gradlew assembleDebug', 'cmd1')}
                      className="text-xs text-teal-400 hover:text-teal-300 flex items-center gap-1"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      <span>{copiedText === 'cmd1' ? 'تم النسخ!' : 'نسخ'}</span>
                    </button>
                  </div>
                  <pre className="text-xs font-mono text-emerald-400 bg-slate-900 p-2.5 rounded-xl border border-slate-800">
                    ./gradlew assembleDebug
                  </pre>
                  <p className="text-[11px] text-slate-400 mt-2">
                    ينتج ملف <code className="text-teal-300">app/build/outputs/apk/debug/app-debug.apk</code> جاهزاً للتثبيت.
                  </p>
                </div>

                {/* Command 2: run tests */}
                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-white">تشغيل اختبارات الوحدة المنطقية (Unit Tests):</span>
                    <button
                      onClick={() => copyToClipboard('./gradlew test', 'cmd2')}
                      className="text-xs text-teal-400 hover:text-teal-300 flex items-center gap-1"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      <span>{copiedText === 'cmd2' ? 'تم النسخ!' : 'نسخ'}</span>
                    </button>
                  </div>
                  <pre className="text-xs font-mono text-emerald-400 bg-slate-900 p-2.5 rounded-xl border border-slate-800">
                    ./gradlew test
                  </pre>
                  <p className="text-[11px] text-slate-400 mt-2">
                    يختبر حالات تسجيل الدخول والتسجيل والبحث والمحادثات المكتوبة في <code className="text-teal-300">ChatUseCasesTest.kt</code>.
                  </p>
                </div>

                {/* Command 3: adb install */}
                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-white">تثبيت التطبيق على جهاز أندرويد متصل (ADB):</span>
                    <button
                      onClick={() => copyToClipboard('adb install app/build/outputs/apk/debug/app-debug.apk', 'cmd3')}
                      className="text-xs text-teal-400 hover:text-teal-300 flex items-center gap-1"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      <span>{copiedText === 'cmd3' ? 'تم النسخ!' : 'نسخ'}</span>
                    </button>
                  </div>
                  <pre className="text-xs font-mono text-emerald-400 bg-slate-900 p-2.5 rounded-xl border border-slate-800">
                    adb install app/build/outputs/apk/debug/app-debug.apk
                  </pre>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-950 py-4 text-center text-xs text-slate-500">
        تطبيق تواصل (Tawasol) • بنية أندرويد أصلية (Kotlin + Jetpack Compose) • Supabase • Room • End-to-End Encryption
      </footer>
    </div>
  );
}
