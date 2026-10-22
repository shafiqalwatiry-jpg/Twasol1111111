package com.tawasol.app.presentation.home.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tawasol.app.core.theme.TealPrimary
import com.tawasol.app.domain.model.CallSession
import com.tawasol.app.domain.model.CallStatus
import com.tawasol.app.domain.model.CallType
import com.tawasol.app.presentation.components.UserAvatar
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CallsTab(
    calls: List<CallSession> = emptyList(),
    currentUserId: String? = null,
    onStartCall: (String, CallType) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // WebRTC P2P Security banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(TealPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "مكالمات WebRTC مشفرة من طرف إلى طرف",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "اتصال Peer-to-Peer مباشر عبر خوادم STUN الموثوقة",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (calls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneCallback,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "سجل المكالمات فارغ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ابدأ مكالمة صوتية أو مرئية من داخل أي محادثة",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    val isCaller = call.callerId == currentUserId
                    val peerName = if (isCaller) call.receiverDisplayName else call.callerDisplayName
                    val peerAvatar = if (isCaller) call.receiverAvatarUrl else call.callerAvatarUrl
                    val peerId = if (isCaller) call.receiverId else call.callerId

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = peerName,
                                avatarUrl = peerAvatar,
                                size = 46
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (dirIcon, dirColor) = when {
                                        call.status == CallStatus.MISSED || call.status == CallStatus.REJECTED ->
                                            Pair(Icons.Rounded.CallMissed, MaterialTheme.colorScheme.error)
                                        isCaller ->
                                            Pair(Icons.AutoMirrored.Rounded.CallMade, TealPrimary)
                                        else ->
                                            Pair(Icons.AutoMirrored.Rounded.CallReceived, TealPrimary)
                                    }

                                    Icon(
                                        imageVector = dirIcon,
                                        contentDescription = null,
                                        tint = dirColor,
                                        modifier = Modifier.size(14.dp)
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    val statusText = when (call.status) {
                                        CallStatus.ACCEPTED -> "مكالمة مكتملة (${call.durationSeconds} ث)"
                                        CallStatus.REJECTED -> "مكالمة مرفوضة"
                                        CallStatus.MISSED -> "مكالمة فائتة"
                                        CallStatus.BUSY -> "الخط مشغول"
                                        CallStatus.OUTGOING -> "مكالمة صادرة"
                                        CallStatus.INCOMING -> "مكالمة واردة"
                                        CallStatus.RINGING -> "رنين..."
                                        CallStatus.ENDED -> if (call.durationSeconds > 0) "المدة: ${call.durationSeconds} ث" else "منتهية"
                                    }

                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            // Call Back Action Button
                            IconButton(
                                onClick = {
                                    if (peerId.isNotBlank()) {
                                        onStartCall(peerId, call.type)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (call.type == CallType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                    contentDescription = "معاودة الاتصال",
                                    tint = TealPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
