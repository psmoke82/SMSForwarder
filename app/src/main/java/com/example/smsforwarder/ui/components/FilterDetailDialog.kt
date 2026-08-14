package com.example.smsforwarder.ui.components

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.utils.ContactUtils

@Composable
fun FilterDetailDialog(
    filter: Filter,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "필터 상세 정보 (조회 모드)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val summationStatusText = if (filter.isSummationEnabled) {
                    "활성화 (당월: ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(filter.monthlyTotal)}원 / 연간: ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(filter.yearlyTotal)}원)"
                } else {
                    "비활성화 (미사용)"
                }
                DetailItem(label = "금액합산 기능", value = summationStatusText)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val appsText = when {
                    filter.appNames.isNotEmpty() -> filter.appNames.joinToString(", ")
                    filter.targetPackageNames.isNotEmpty() -> filter.targetPackageNames.joinToString(", ")
                    else -> "전체 앱 (모든 알림 감지)"
                }
                DetailItem(label = "감지 앱 목록", value = appsText)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val keywordsText = if (filter.keywords.isEmpty()) "조건 없음 (모든 알림)"
                else "${filter.keywords.joinToString(", ")} (논리 조건: ${filter.keywordLogic.name})"
                DetailItem(label = "포착 키워드 조건", value = keywordsText)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val excludeText = if (filter.excludeKeywords.isEmpty()) "제외 키워드 없음"
                else filter.excludeKeywords.joinToString(", ")
                DetailItem(label = "제외 키워드 조건", value = excludeText)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val context = LocalContext.current
                val formattedRecipient = remember(filter.recipientPhoneNumber) {
                    ContactUtils.getFormattedNumberWithContact(context, filter.recipientPhoneNumber)
                }
                DetailItem(label = "수신 전화번호", value = formattedRecipient)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "메시지 템플릿:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = filter.messageTemplate,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onEdit()
            }) {
                Text("수정하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
