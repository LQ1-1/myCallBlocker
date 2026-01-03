package com.example.mycallblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


class MainActivity : ComponentActivity() {

    // 1. 注册权限请求回调 (设为默认应用)
    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        checkRoleAndToast()
    }

    // 2. 注册权限请求回调 (读取通讯录)
    private val requestContactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "通讯录权限已获取，白名单生效！", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "必须授权通讯录才能区分熟人！", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenContent()
                }
            }
        }
    }

    // 3. 界面布局函数 (UI代码必须写在这里面)
    @Composable
    fun ScreenContent() {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🛡️ 强力防骚扰 (Android 14)", style = MaterialTheme.typography.headlineMedium)//myCallBlocker intercept all incoming calls
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "仅允许通讯录好友，其他自动挂断。")
            Spacer(modifier = Modifier.height(40.dp))

            // 按钮 1：设为默认拦截应用
            Button(onClick = { requestRole() }) {
                Text(text = "第一步：开启拦截权限")
            }

            Spacer(modifier = Modifier.height(20.dp)) // 加个间距

            // 按钮 2：授权读取通讯录 (✅ 移动到了这里)
            Button(onClick = {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }) {
                Text(text = "第二步：授权读取通讯录")
            }
        }
    }

    // 4. 辅助逻辑函数
    private fun requestRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val roleName = RoleManager.ROLE_CALL_SCREENING

            if (roleManager.isRoleAvailable(roleName)) {
                if (roleManager.isRoleHeld(roleName)) {
                    Toast.makeText(this, "权限已获取，拦截服务正在运行！", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = roleManager.createRequestRoleIntent(roleName)
                    requestRoleLauncher.launch(intent)
                }
            }
        } else {
            Toast.makeText(this, "系统版本过低，不支持此功能", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRoleAndToast() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "设置成功！陌生电话将被拦截", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "设置失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }
}