package com.example.smsforwarder.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.smsforwarder.domain.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppPickerDialog(
    initialSelectedPackages: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (selectedApps: List<AppInfo>) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val allInstalledApps = remember { mutableStateListOf<AppInfo>() }
    val selectedPackages = remember { mutableStateListOf<String>().apply { addAll(initialSelectedPackages) } }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = loadInstalledUserApps(context)
            withContext(Dispatchers.Main) {
                allInstalledApps.clear()
                allInstalledApps.addAll(apps)
            }
        }
    }

    val filteredApps = remember(searchQuery, allInstalledApps) {
        if (searchQuery.isBlank()) {
            allInstalledApps
        } else {
            allInstalledApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "포착할 앱 선택 (사용자 앱 전용)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("앱 이름 또는 패키지 검색") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "총 ${allInstalledApps.size}개 앱 (선택됨: ${selectedPackages.size}개)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = selectedPackages.contains(app.packageName)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedPackages.remove(app.packageName)
                                    } else {
                                        selectedPackages.add(app.packageName)
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedPackages.add(app.packageName)
                                    } else {
                                        selectedPackages.remove(app.packageName)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val resultList = allInstalledApps.filter { selectedPackages.contains(it.packageName) }
                    onConfirm(resultList)
                }
            ) {
                Text("선택 완료")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun loadInstalledUserApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    val resultList = mutableListOf<AppInfo>()

    for (appInfo in packages) {
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        // Filter out system apps except updated system apps or default SMS messaging apps
        if (isSystem && !isUpdatedSystem) {
            val isMessagingApp = appInfo.packageName.contains("messaging", ignoreCase = true) ||
                    appInfo.packageName.contains("mms", ignoreCase = true) ||
                    appInfo.packageName.contains("telephony", ignoreCase = true)
            if (!isMessagingApp) {
                continue
            }
        }

        val name = pm.getApplicationLabel(appInfo).toString()
        if (name.isBlank() || appInfo.packageName == "android" || appInfo.packageName.startsWith("com.android.internal")) {
            continue
        }

        resultList.add(
            AppInfo(
                appName = name,
                packageName = appInfo.packageName,
                icon = null
            )
        )
    }

    return resultList.distinctBy { it.packageName }.sortedBy { it.appName }
}
