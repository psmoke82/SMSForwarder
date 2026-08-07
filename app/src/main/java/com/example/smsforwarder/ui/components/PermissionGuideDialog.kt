package com.example.smsforwarder.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsforwarder.MainActivity
import com.example.smsforwarder.findActivity

@Composable
fun PermissionGuideDialog(
    hasSmsPermission: Boolean,
    isListenerEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SMSForwarder 권한 설정 안내",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "앱이 정상적으로 문자를 감지하고 포워딩하려면 아래 필수 권한 설정이 필요합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. SMS Permission Item
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasSmsPermission)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasSmsPermission) Icons.Default.CheckCircle else Icons.Default.Message,
                                    contentDescription = null,
                                    tint = if (hasSmsPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1. SMS 수신 및 발송 권한",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = if (hasSmsPermission) "허용됨" else "미허용",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasSmsPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "문자 수신 감지 및 다른 번호로 SMS 자동 전송에 필요합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                        if (!hasSmsPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val activity = context.findActivity() as? MainActivity
                                    if (activity != null) {
                                        activity.requestRequiredPermissions(forceOpenSettingsIfDenied = true)
                                        activity.openAppSettings()
                                    } else {
                                        openAppSettingsFallback(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("SMS 권한 설정 (설정 화면으로 이동)", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 2. Notification Listener Item
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isListenerEnabled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isListenerEnabled) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (isListenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "2. 알림 접근 허용 (Listener)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = if (isListenerEnabled) "허용됨" else "미허용",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isListenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "카드사, 은행 앱 및 메신저 알림을 실시간 감지하기 위해 필요합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                        if (!isListenerEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("알림 접근 설정 (설정 화면으로 이동)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val activity = context.findActivity() as? MainActivity
                    activity?.openAppSettings() ?: openAppSettingsFallback(context)
                }
            ) {
                Text("전체 앱 설정 화면 열기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

private fun openAppSettingsFallback(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
