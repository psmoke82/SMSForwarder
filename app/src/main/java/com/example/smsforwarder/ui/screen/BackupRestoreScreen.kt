package com.example.smsforwarder.ui.screen

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smsforwarder.ui.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isDriveActionUpload by remember { mutableStateOf(true) }

    // SAF File Create Launcher (Export)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportLocalBackup(it) { success, message ->
                if (success) {
                    Toast.makeText(context, "백업 파일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "백업 실패: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // SAF File Open Launcher (Import)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importLocalBackup(it) { success, message ->
                if (success) {
                    Toast.makeText(context, message ?: "복원 성공", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "복원 실패: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    var lastSelectedEmail by remember { mutableStateOf<String?>(null) }
    var lastSelectedAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    lateinit var runDriveTask: (GoogleSignInAccount) -> Unit
    lateinit var runDriveTaskByEmail: (String) -> Unit

    val userRecoverableConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val email = lastSelectedEmail
            val account = lastSelectedAccount
            if (account != null) {
                runDriveTask(account)
            } else if (!email.isNullOrBlank()) {
                runDriveTaskByEmail(email)
            }
        } else {
            showToast("⚠️ Google Drive 접근 권한 승인이 취소되었습니다.")
        }
    }

    fun extractConsentIntent(throwable: Throwable?): Intent? {
        var current = throwable
        val visited = mutableSetOf<Throwable>()
        while (current != null && !visited.contains(current)) {
            visited.add(current)
            if (current is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                return current.intent
            }
            if (current is com.google.android.gms.auth.UserRecoverableAuthException) {
                return current.intent
            }
            current = current.cause
        }
        return null
    }

    var driveErrorMessage by remember { mutableStateOf<String?>(null) }

    fun handleDriveError(message: String?, rawException: Throwable?) {
        val consentIntent = extractConsentIntent(rawException)
        if (consentIntent != null) {
            showToast("Google Drive 접근 권한 동의 창을 엽니다...")
            try {
                userRecoverableConsentLauncher.launch(consentIntent)
            } catch (e: Exception) {
                showToast("❌ 권한 동의 창 실행 오류: ${e.localizedMessage ?: e.toString()}")
            }
        } else {
            val exName = rawException?.javaClass?.simpleName ?: ""
            val exMsg = rawException?.localizedMessage ?: rawException?.message ?: rawException?.toString() ?: message ?: "알 수 없는 오류"
            val causeMsg = rawException?.cause?.let { "\n[원인]: ${it.localizedMessage ?: it.toString()}" } ?: ""
            val combined = "[$exName] $exMsg$causeMsg"

            if (combined.contains("UnregisteredOnApiConsole")) {
                driveErrorMessage = "❌ 구글 드라이브 연동을 위해 Google Cloud 콘솔 OAuth 등록(SHA-1)이 필요합니다.\n\n" +
                        "1. Google Cloud Console(console.cloud.google.com) 접속\n" +
                        "2. Google Drive API 활성화\n" +
                        "3. 사용자 인증 정보 -> OAuth 2.0 클라이언트 ID (Android) 생성\n\n" +
                        "• 패키지 이름:\ncom.example.smsforwarder\n\n" +
                        "• SHA-1 지문:\n2F:28:94:FD:C2:88:60:EC:84:63:AF:20:79:9C:84:5B:64:D4:31:A9"
            } else {
                driveErrorMessage = combined
            }
        }
    }

    runDriveTask = { account: GoogleSignInAccount ->
        lastSelectedAccount = account
        lastSelectedEmail = account.email
        val emailStr = account.email ?: "구글 계정"
        if (isDriveActionUpload) {
            showToast("[$emailStr] Google Drive 백업 요청 중...")
            viewModel.uploadDriveBackup(account) { success, message, rawException ->
                if (success) {
                    showToast("✅ 백업 완료: $message")
                } else {
                    handleDriveError(message, rawException)
                }
            }
        } else {
            showToast("[$emailStr] Google Drive 복구 요청 중...")
            viewModel.downloadDriveBackup(account) { success, message, rawException ->
                if (success) {
                    showToast("✅ 복구 완료: $message")
                } else {
                    handleDriveError(message, rawException)
                }
            }
        }
    }

    runDriveTaskByEmail = { emailStr: String ->
        lastSelectedEmail = emailStr
        lastSelectedAccount = null
        if (isDriveActionUpload) {
            showToast("[$emailStr] Google Drive 백업 요청 중...")
            viewModel.uploadDriveBackupByEmail(emailStr) { success, message, rawException ->
                if (success) {
                    showToast("✅ 백업 완료: $message")
                } else {
                    handleDriveError(message, rawException)
                }
            }
        } else {
            showToast("[$emailStr] Google Drive 복구 요청 중...")
            viewModel.downloadDriveBackupByEmail(emailStr) { success, message, rawException ->
                if (success) {
                    showToast("✅ 복구 완료: $message")
                } else {
                    handleDriveError(message, rawException)
                }
            }
        }
    }

    // AccountManager System Launcher (Fallback)
    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                runDriveTaskByEmail(accountName)
            } else {
                showToast("⚠️ 선택된 구글 계정을 확인할 수 없습니다.")
            }
        } else {
            showToast("⚠️ Google 계정 선택이 취소되었습니다.")
        }
    }

    val launchAccountManagerChooser = {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            accountChooserLauncher.launch(intent)
        } catch (e: Exception) {
            showToast("❌ 시스템 계정 선택 실패: ${e.localizedMessage ?: e.toString()}")
        }
    }

    // Google Sign-In Launcher for Drive API
    val googleDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data != null) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    if (account != null) {
                        runDriveTask(account)
                        return@rememberLauncherForActivityResult
                    }
                } catch (apiException: com.google.android.gms.common.api.ApiException) {
                    val statusCode = apiException.statusCode
                    if (statusCode == 10 || statusCode == 12500) {
                        showToast("시스템 계정 선택창으로 자동 전환합니다...")
                        launchAccountManagerChooser()
                        return@rememberLauncherForActivityResult
                    } else {
                        showToast("❌ 구글 로그인 실패 (상태 코드: $statusCode)")
                        return@rememberLauncherForActivityResult
                    }
                }
            } catch (e: Exception) {
                showToast("시스템 계정 선택창으로 전환 중...")
                launchAccountManagerChooser()
                return@rememberLauncherForActivityResult
            }
        }
        showToast("⚠️ Google 계정 선택이 취소되었습니다.")
    }

    val triggerGoogleDrive = { isUpload: Boolean ->
        isDriveActionUpload = isUpload
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            googleDriveLauncher.launch(client.signInIntent)
        } catch (e: Exception) {
            launchAccountManagerChooser()
        }
    }

    if (driveErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { driveErrorMessage = null },
            title = { Text("구글 드라이브 오류 상세", fontWeight = FontWeight.Bold) },
            text = { Text(driveErrorMessage!!) },
            confirmButton = {
                TextButton(onClick = { driveErrorMessage = null }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("백업 및 복구") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // SECTION 1: Local SAF Backup & Restore
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "로컬 파일 백업 및 복구 (.json)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "스마트폰 내 원하는 폴더(다운로드 등)에 필터 데이터를 안전하게 저장하고 불러옵니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("smsforwarder_backup.json") },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "내보내기 (백업)",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "불러오기 (복구)",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: Google Drive Cloud Backup & Restore
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Google Drive 클라우드 백업 및 복구",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "구글 계정과 연동하여 클라우드 AppData 전용 공간에 필터를 저장하고 다른 기기에서 복원합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { triggerGoogleDrive(true) },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "드라이브 백업",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = { triggerGoogleDrive(false) },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "드라이브 복구",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
