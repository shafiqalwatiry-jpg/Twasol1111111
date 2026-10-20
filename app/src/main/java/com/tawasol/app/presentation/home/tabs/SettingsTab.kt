package com.tawasol.app.presentation.home.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tawasol.app.core.theme.ErrorRed
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.User
import com.tawasol.app.presentation.components.UserAvatar

@Composable
fun SettingsTab(
    currentUser: User?,
    onProfileClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // User Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = currentUser?.displayName ?: "مستخدم تواصل",
                    avatarUrl = currentUser?.avatarUrl,
                    size = 64,
                    isOnline = true
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentUser?.displayName ?: "مستخدم تواصل",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "@${currentUser?.username ?: "tawasol_user"}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TealPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = if (!currentUser?.bio.isNullOrBlank()) currentUser!!.bio!! else "انقر لعرض وتعديل الملف الشخصي",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "تعديل",
                    tint = TealPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Settings items section
        SettingsGroup(title = "الحساب والخصوصية") {
            SettingsRow(
                icon = Icons.Rounded.Lock,
                title = "الخصوصية",
                subtitle = "آخر ظهور، الصورة الشخصية، حالة الاتصال، النبذة",
                onClick = onPrivacyClick
            )
            SettingsRow(
                icon = Icons.Rounded.Security,
                title = "الأمان وحماية الحساب",
                subtitle = "قواعد حماية البيانات المشفرة و RLS",
                onClick = { showSecurityDialog = true }
            )
            SettingsRow(
                icon = Icons.Rounded.Devices,
                title = "الأجهزة المتصلة",
                subtitle = "إدارة الجلسة الحالية وحمايتها محلياً"
            )
            SettingsRow(
                icon = Icons.Rounded.Block,
                title = "المستخدمون المحظورون",
                subtitle = "قائمة الحسابات المحظورة"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup(title = "التطبيق والبيانات") {
            SettingsRow(
                icon = Icons.Rounded.Notifications,
                title = "الإشعارات والأصوات",
                subtitle = "نغمات الرسائل والتنبيهات (Phase 3+)"
            )
            SettingsRow(
                icon = Icons.Rounded.DataUsage,
                title = "التخزين والبيانات",
                subtitle = "قاعدة بيانات Room المحلية والذاكرة المؤقتة"
            )
            SettingsRow(
                icon = Icons.Rounded.Info,
                title = "حول تواصل",
                subtitle = "الإصدار 1.0.0 (Native Kotlin & Jetpack Compose)",
                onClick = { showAboutDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Logout Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogoutClick),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = ErrorRed.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = ErrorRed
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "تسجيل الخروج من الحساب",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Text(
                    text = "الأمان والخصوصية في تواصل",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "• هوية المستخدم مبنية على Supabase Auth دون حفظ كلمات المرور بنص صريح.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "• رقم الهاتف والبريد الإلكتروني مشفران في جدول user_contacts ومحميان بقواعد RLS صارمة تمنع وصول أي مستخدم آخر إليهما.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "• رموز الجلسة والمفاتيح المحلية محفوظة في Android KeyStore بتشفير AES-256 GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("حسناً", color = TealPrimary)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "تطبيق تواصل (Tawasol)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "تطبيق مراسلة واتصال فوري أندرويد حقيقي مبني بنظام Kotlin و Jetpack Compose مع Room و Supabase.\nالإصدار: 1.0.0 (Phase 2).",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("إغلاق", color = TealPrimary)
                }
            }
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = TealPrimary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
