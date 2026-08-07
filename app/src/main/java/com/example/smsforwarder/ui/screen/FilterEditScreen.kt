package com.example.smsforwarder.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic
import com.example.smsforwarder.ui.MainViewModel
import com.example.smsforwarder.ui.components.AppPickerDialog
import com.example.smsforwarder.ui.components.TemplateEditor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterEditScreen(
    filterId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    val selectedAppNames = remember { mutableStateListOf<String>() }
    val selectedPackages = remember { mutableStateListOf<String>() }
    val keywords = remember { mutableStateListOf<String>() }
    var keywordInput by remember { mutableStateOf("") }
    val excludeKeywords = remember { mutableStateListOf<String>() }
    var excludeKeywordInput by remember { mutableStateOf("") }
    var keywordLogic by remember { mutableStateOf(KeywordLogic.OR) }
    var recipientPhone by remember { mutableStateOf("") }
    var templateValue by remember { mutableStateOf(TextFieldValue("[%na%] %pni%\n%mb%\n시간: %rt%")) }
    var isActive by remember { mutableStateOf(true) }

    var showAppPickerDialog by remember { mutableStateOf(false) }

    // Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { contactUri ->
                try {
                    var phoneNum: String? = null
                    val cursor1 = context.contentResolver.query(
                        contactUri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null, null, null
                    )
                    cursor1?.use { c ->
                        if (c.moveToFirst()) {
                            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numIdx != -1) {
                                phoneNum = c.getString(numIdx)
                            }
                        }
                    }

                    if (phoneNum.isNullOrBlank()) {
                        val contactId = contactUri.lastPathSegment
                        if (!contactId.isNullOrBlank()) {
                            val cursor2 = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            cursor2?.use { c ->
                                if (c.moveToFirst()) {
                                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIdx != -1) {
                                        phoneNum = c.getString(numIdx)
                                    }
                                }
                            }
                        }
                    }

                    if (!phoneNum.isNullOrBlank()) {
                        recipientPhone = phoneNum!!.replace(Regex("[^0-9]"), "")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(filterId) {
        if (filterId > 0) {
            val existingFilter = viewModel.getFilterById(filterId)
            if (existingFilter != null) {
                name = existingFilter.name ?: ""
                selectedPackages.clear()
                selectedPackages.addAll(existingFilter.targetPackageNames ?: emptyList())
                selectedAppNames.clear()
                selectedAppNames.addAll(existingFilter.appNames ?: emptyList())

                keywords.clear()
                keywords.addAll(existingFilter.keywords ?: emptyList())

                excludeKeywords.clear()
                excludeKeywords.addAll(existingFilter.excludeKeywords ?: emptyList())

                keywordLogic = existingFilter.keywordLogic ?: KeywordLogic.OR
                recipientPhone = existingFilter.recipientPhoneNumber ?: ""
                templateValue = TextFieldValue(existingFilter.messageTemplate ?: "[%na%] %pni%\n%mb%\n시간: %rt%")
                isActive = existingFilter.isActive
            }
        }
    }

    if (showAppPickerDialog) {
        AppPickerDialog(
            initialSelectedPackages = selectedPackages,
            onDismiss = { showAppPickerDialog = false },
            onConfirm = { selectedApps ->
                selectedPackages.clear()
                selectedPackages.addAll(selectedApps.map { it.packageName })
                selectedAppNames.clear()
                selectedAppNames.addAll(selectedApps.map { it.appName })
                showAppPickerDialog = false
            }
        )
    }

    val onSaveAction = {
        if (name.trim().isBlank()) {
            android.widget.Toast.makeText(context, "❌ 필터 이름을 입력해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
        } else if (recipientPhone.trim().isBlank()) {
            android.widget.Toast.makeText(context, "❌ 수신자 전화번호를 입력해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val trimmedExclude = excludeKeywordInput.trim()
            if (trimmedExclude.isNotBlank() && !excludeKeywords.contains(trimmedExclude)) {
                excludeKeywords.add(trimmedExclude)
            }
            val trimmedKeyword = keywordInput.trim()
            if (trimmedKeyword.isNotBlank() && !keywords.contains(trimmedKeyword)) {
                keywords.add(trimmedKeyword)
            }

            val newFilter = Filter(
                id = if (filterId > 0) filterId else 0L,
                name = name.trim(),
                targetPackageNames = selectedPackages.toList(),
                appNames = selectedAppNames.toList(),
                keywords = keywords.toList(),
                excludeKeywords = excludeKeywords.toList(),
                keywordLogic = keywordLogic,
                recipientPhoneNumber = recipientPhone.trim(),
                messageTemplate = templateValue.text,
                isActive = isActive
            )
            viewModel.saveFilter(newFilter)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (filterId > 0) "필터 수정" else "새 필터 추가") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSaveAction) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // SECTION 0: Filter Activation Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isActive = !isActive }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "필터 활성화 상태",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isActive) "알림 감지 및 SMS 포워딩 동작 중" else "필터가 비활성화 상태입니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECTION 1: Filter Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("필터 이름") },
                placeholder = { Text("예: 카드 결제 알림 포워딩") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Section Divider 1
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECTION 2: Unified Target App Picker
            Text(
                text = "포착할 앱 선택 (다중 선택 가능):",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = { showAppPickerDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedPackages.isEmpty())
                        "앱 선택하기 (비워두면 모든 앱 감지)"
                    else
                        "선택된 앱: ${selectedAppNames.ifEmpty { selectedPackages }.joinToString(", ")}",
                    fontSize = 13.sp
                )
            }

            if (selectedPackages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    selectedPackages.forEachIndexed { index, pkg ->
                        val label = selectedAppNames.getOrNull(index) ?: pkg
                        InputChip(
                            selected = true,
                            onClick = { },
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(32.dp),
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove App",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            selectedPackages.removeAt(index)
                                            if (index < selectedAppNames.size) {
                                                selectedAppNames.removeAt(index)
                                            }
                                        }
                                )
                            }
                        )
                    }
                }
            }

            // Section Divider 2
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECTION 3: Keywords Multi-Input & Reorder Logic
            Text(
                text = "포착할 키워드 등록 (순서 변경 가능):",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    label = { Text("키워드 입력", fontSize = 12.sp) },
                    placeholder = { Text("예: 승인, 결제, 입금", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = keywordInput.trim()
                            if (trimmed.isNotBlank() && !keywords.contains(trimmed)) {
                                keywords.add(trimmed)
                                keywordInput = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val trimmed = keywordInput.trim()
                        if (trimmed.isNotBlank() && !keywords.contains(trimmed)) {
                            keywords.add(trimmed)
                            keywordInput = ""
                        }
                    },
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Keyword",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("추가", fontSize = 13.sp)
                }
            }

            if (keywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keywords.forEachIndexed { idx, kw ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(32.dp),
                            leadingIcon = {
                                if (idx > 0) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Move Left",
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable {
                                                val item = keywords.removeAt(idx)
                                                keywords.add(idx - 1, item)
                                            }
                                    )
                                }
                            },
                            label = { Text(kw, fontSize = 12.sp) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (idx < keywords.size - 1) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Move Right",
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    val item = keywords.removeAt(idx)
                                                    keywords.add(idx + 1, item)
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Keyword",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { keywords.removeAt(idx) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logic selector: OR FIRST & DEFAULT
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "키워드 논리 조건:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
                FilterChip(
                    selected = keywordLogic == KeywordLogic.OR,
                    onClick = { keywordLogic = KeywordLogic.OR },
                    label = { Text("OR") }
                )
                FilterChip(
                    selected = keywordLogic == KeywordLogic.AND,
                    onClick = { keywordLogic = KeywordLogic.AND },
                    label = { Text("AND") }
                )
            }

            // Section Divider 3.5
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECTION 3.5: Exclusion Keywords Registration
            Text(
                text = "제외 키워드 등록 (포착되더라도 해당 키워드 포함 시 전송 제외):",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = excludeKeywordInput,
                    onValueChange = { excludeKeywordInput = it },
                    label = { Text("제외 키워드 입력", fontSize = 12.sp) },
                    placeholder = { Text("예: 취소, 테스트, 스팸", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = excludeKeywordInput.trim()
                            if (trimmed.isNotBlank() && !excludeKeywords.contains(trimmed)) {
                                excludeKeywords.add(trimmed)
                                excludeKeywordInput = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val trimmed = excludeKeywordInput.trim()
                        if (trimmed.isNotBlank() && !excludeKeywords.contains(trimmed)) {
                            excludeKeywords.add(trimmed)
                            excludeKeywordInput = ""
                        }
                    },
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Exclude Keyword",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("추가", fontSize = 13.sp)
                }
            }

            if (excludeKeywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    excludeKeywords.forEachIndexed { idx, kw ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(32.dp),
                            leadingIcon = {
                                if (idx > 0) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Move Left",
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable {
                                                val item = excludeKeywords.removeAt(idx)
                                                excludeKeywords.add(idx - 1, item)
                                            }
                                    )
                                }
                            },
                            label = { Text(kw, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (idx < excludeKeywords.size - 1) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Move Right",
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    val item = excludeKeywords.removeAt(idx)
                                                    excludeKeywords.add(idx + 1, item)
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Exclude Keyword",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { excludeKeywords.removeAt(idx) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Section Divider 3
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECTION 4: Recipient Phone Number & Contact Picker Integration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { recipientPhone = it },
                    label = { Text("수신자 전화번호") },
                    placeholder = { Text("01012345678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            contactPickerLauncher.launch(pickIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = "Contact Picker",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("연락처", fontSize = 12.sp)
                }
            }

            // Section Divider 4
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // SECTION 5: Custom Message Template Editor (Tight FlowRow tags & Live Preview)
            TemplateEditor(
                filterName = name,
                appName = selectedAppNames.joinToString(", "),
                textFieldValue = templateValue,
                onValueChange = { templateValue = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSaveAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (filterId > 0) "필터 수정 완료" else "필터 저장")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
