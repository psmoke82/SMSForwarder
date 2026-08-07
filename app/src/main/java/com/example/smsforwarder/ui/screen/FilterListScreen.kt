package com.example.smsforwarder.ui.screen

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.example.smsforwarder.findActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsforwarder.data.repository.ExecutionMode
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.ui.MainViewModel
import com.example.smsforwarder.ui.components.FilterDetailDialog
import com.example.smsforwarder.utils.ContactUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterListScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onAddFilter: () -> Unit,
    onEditFilter: (Long) -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val filters by viewModel.filters.collectAsState()
    val executionMode by viewModel.executionMode.collectAsState()
    val context = LocalContext.current

    var selectedDetailFilter by remember { mutableStateOf<Filter?>(null) }
    var filterToDelete by remember { mutableStateOf<Filter?>(null) }
    var showPermissionGuideDialog by remember { mutableStateOf(true) }

    val hasSmsPermission = viewModel.hasSmsPermissions()
    val isListenerEnabled = viewModel.isNotificationListenerEnabled()
    val isFullyReady = hasSmsPermission && isListenerEnabled

    if (!isFullyReady && showPermissionGuideDialog) {
        com.example.smsforwarder.ui.components.PermissionGuideDialog(
            hasSmsPermission = hasSmsPermission,
            isListenerEnabled = isListenerEnabled,
            onDismiss = { showPermissionGuideDialog = false }
        )
    }

    if (selectedDetailFilter != null) {
        val targetFilter = selectedDetailFilter!!
        FilterDetailDialog(
            filter = targetFilter,
            onDismiss = { selectedDetailFilter = null },
            onEdit = {
                selectedDetailFilter = null
                onEditFilter(targetFilter.id)
            }
        )
    }

    if (filterToDelete != null) {
        AlertDialog(
            onDismissRequest = { filterToDelete = null },
            title = { Text("필터 삭제 확인", fontWeight = FontWeight.Bold) },
            text = { Text("'${filterToDelete!!.name}' 필터를 정말 삭제하시겠습니까?\n삭제된 필터는 복구할 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = filterToDelete
                        filterToDelete = null
                        if (target != null) {
                            viewModel.deleteFilter(target)
                            Toast.makeText(context, "'${target.name}' 필터가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { filterToDelete = null }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMSForwarder") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Drawer Menu"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFilter) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Filter")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Service Status Card
            item {
                var refreshTrigger by remember { mutableStateOf(0) }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            refreshTrigger++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Trigger recalculation on lifecycle resume
                val isListenerEnabled = remember(refreshTrigger) { viewModel.isNotificationListenerEnabled() }
                val hasSmsPermission = remember(refreshTrigger) { viewModel.hasSmsPermissions() }
                val isForeground = executionMode == ExecutionMode.FOREGROUND
                val isFullyReady = isListenerEnabled && hasSmsPermission

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFullyReady)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (isFullyReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!hasSmsPermission) {
                                        "SMS 필수 런타임 권한 필요!"
                                    } else if (!isListenerEnabled) {
                                        "알림 접근 권한 필요!"
                                    } else if (isForeground) {
                                        "포그라운드 서비스 동작 중"
                                    } else {
                                        "백그라운드 서비스 동작 중"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isFullyReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            }

                            if (isFullyReady) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = if (isForeground) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = if (isForeground) "포그라운드 모드" else "백그라운드 모드",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (isForeground) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (!hasSmsPermission) {
                                "문자 수신 및 자동 발송을 처리하려면 SMS 런타임 권한(SMS 수신/발송)이 필수입니다."
                            } else if (!isListenerEnabled) {
                                "다른 앱 알림을 캐치하려면 '알림 접근 허용' 설정이 필요합니다."
                            } else if (isForeground) {
                                "상단바 고정 알림을 유지하여 OS의 수면/종료 없이 높은 우선순위로 알림을 감지합니다."
                            } else {
                                "상단바 알림 없이 백그라운드 리스너로 알림을 감지하여 SMS를 자동 전달합니다."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (!hasSmsPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    showPermissionGuideDialog = true
                                    val activity = context.findActivity() as? com.example.smsforwarder.MainActivity
                                    if (activity != null) {
                                        activity.requestRequiredPermissions(forceOpenSettingsIfDenied = true)
                                        activity.openAppSettings()
                                    } else {
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
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SMS 권한 허용하기")
                            }
                        } else if (!isListenerEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("알림 접근 권한 설정 바로가기")
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Text(
                    text = "포워딩 필터 목록 (${filters.size}개)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (filters.isEmpty()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "등록된 필터가 없습니다.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "우측 하단 '+' 버튼을 눌러 새 필터를 추가해 보세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filters, key = { it.id }) { filter ->
                    FilterItemCard(
                        filter = filter,
                        onClickDetail = { selectedDetailFilter = filter },
                        onToggle = { newActive -> viewModel.toggleFilterActive(filter, newActive) },
                        onEdit = { onEditFilter(filter.id) },
                        onDelete = { filterToDelete = filter }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterItemCard(
    filter: Filter,
    onClickDetail: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedRecipient = remember(filter.recipientPhoneNumber) {
        ContactUtils.getFormattedNumberWithContact(context, filter.recipientPhoneNumber)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClickDetail() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = filter.isActive,
                    onCheckedChange = { onToggle(it) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClickDetail() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "수신 번호: $formattedRecipient",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
