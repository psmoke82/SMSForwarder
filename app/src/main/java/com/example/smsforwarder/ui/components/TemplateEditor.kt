package com.example.smsforwarder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsforwarder.domain.parser.NotificationMeta
import com.example.smsforwarder.domain.parser.TemplateParser
import com.example.smsforwarder.domain.parser.TemplateTags

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditor(
    filterName: String,
    appName: String,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val templateParser = remember { TemplateParser() }

    val sampleMeta = remember(filterName, appName) {
        val singleAppName = if (appName.isBlank()) {
            "신한SOL"
        } else {
            appName.split(",")
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() } ?: "신한SOL"
        }

        NotificationMeta(
            filterName = if (filterName.isBlank()) "카드 알림 필터" else filterName,
            appName = singleAppName,
            title = "[신한카드] 승인",
            body = "김*우 15,400원 일시불 08/04 20:45",
            subText = "체크카드 잔액 125,000원",
            timestamp = System.currentTimeMillis(),
            monthlyTotal = 1250000L,
            yearlyTotal = 15000000L,
            monthlyPeriodLabel = java.text.SimpleDateFormat("yy.MM", java.util.Locale.KOREA).format(java.util.Date()),
            yearlyPeriodLabel = java.text.SimpleDateFormat("yy", java.util.Locale.KOREA).format(java.util.Date())
        )
    }

    val previewText = remember(textFieldValue.text, sampleMeta) {
        if (textFieldValue.text.isBlank()) {
            "(템플릿 텍스트를 입력하면 미리보기가 표시됩니다)"
        } else {
            templateParser.parse(textFieldValue.text, sampleMeta)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Real-time Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Preview,
                        contentDescription = "Preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "실시간 발송 텍스트 미리보기",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Variable Tag Chip Selector (Tightly spaced FlowRow)
        Text(
            text = "치환 변수 태그 (터치시 커서 위치에 삽입):",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TemplateTags.ALL.forEach { item ->
                AssistChip(
                    onClick = {
                        val currentText = textFieldValue.text
                        val selection = textFieldValue.selection
                        val insertPos = selection.start.coerceIn(0, currentText.length)

                        val newText = StringBuilder(currentText)
                            .insert(insertPos, item.tag)
                            .toString()

                        val newCursorPos = insertPos + item.tag.length
                        onValueChange(
                            TextFieldValue(
                                text = newText,
                                selection = TextRange(newCursorPos)
                            )
                        )
                    },
                    modifier = Modifier.height(30.dp),
                    label = {
                        Text(
                            text = "${item.label} (${item.tag})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Template Input TextField
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            label = { Text("메시지 템플릿 작성", fontSize = 12.sp) },
            placeholder = { Text("예: [%na%] %pni%\n내용: %mb%\n시간: %rt%") },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            maxLines = 6
        )
    }
}
