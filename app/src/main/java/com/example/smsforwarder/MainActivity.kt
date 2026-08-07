package com.example.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.smsforwarder.ui.MainViewModel
import com.example.smsforwarder.ui.navigation.NavGraph
import com.example.smsforwarder.ui.theme.SMSForwarderTheme

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.forEach { (permission, isGranted) ->
            Log.d("MainActivity", "Permission $permission granted: $isGranted")
            if (!isGranted && (permission == Manifest.permission.SEND_SMS || permission == Manifest.permission.RECEIVE_SMS)) {
                allGranted = false
            }
        }
        if (!allGranted) {
            Toast.makeText(
                this,
                "SMS 권한이 허용되지 않았습니다. 포워딩을 위해 앱 설정에서 SMS 권한을 허용해 주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request required runtime permissions immediately on app launch
        requestRequiredPermissions(forceOpenSettingsIfDenied = false)

        setContent {
            SMSForwarderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(viewModel = mainViewModel)
                }
            }
        }
    }

    fun requestRequiredPermissions(forceOpenSettingsIfDenied: Boolean = true) {
        try {
            val permissionsToRequest = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.SEND_SMS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_SMS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                if (forceOpenSettingsIfDenied) {
                    Toast.makeText(this, "모든 SMS 런타임 권한이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting permissions", e)
            if (forceOpenSettingsIfDenied) {
                openAppSettings()
            }
        }
    }

    fun openAppSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening app settings", e)
        }
    }
}