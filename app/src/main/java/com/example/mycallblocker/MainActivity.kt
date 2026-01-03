package com.example.mycallblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.text.SimpleDateFormat
import java.util.*

// 全局常量
const val PREFS_NAME = "mycallblocker_prefs"
const val PREF_INTERCEPTION_ACTIVE = "interception_active"
const val PREF_CONTACT_WHITELIST_ENABLED = "contact_whitelist_enabled"

class MainActivity : ComponentActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private var isRoleHeldState = mutableStateOf(false)
    private var isContactPermittedState = mutableStateOf(false)

    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { checkSystemPermissions() }
    private val requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { checkSystemPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 监听 App 回到前台刷新权限
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) checkSystemPermissions()
        }
        lifecycle.addObserver(lifecycleObserver)
        checkSystemPermissions()

        setContent { MaterialTheme { MainAppStructure() } }
    }

    private fun checkSystemPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            isRoleHeldState.value = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
        isContactPermittedState.value = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                requestRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        }
    }
    fun requestContactPermission() { requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }

    @Composable
    fun MainAppStructure() {
        val navController = rememberNavController()
        var currentRoute by remember { mutableStateOf("home") }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("主页") },
                        selected = currentRoute == "home",
                        onClick = { currentRoute = "home"; navController.navigate("home") { popUpTo("home") { inclusive = true } } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text("记录 & 设置") },
                        selected = currentRoute == "settings",
                        onClick = { currentRoute = "settings"; navController.navigate("settings") { popUpTo("home") } }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(navController, startDestination = "home", modifier = Modifier.padding(innerPadding)) {
                composable("home") {
                    HomeScreen(sharedPrefs, isRoleHeldState, isContactPermittedState, { requestCallScreeningRole() }, { requestContactPermission() })
                }
                composable("settings") {
                    SettingsAndLogsScreen(isRoleHeldState.value, isContactPermittedState.value)
                }
            }
        }
    }
}

// ======================= 页面 1: 主页 =======================
@Composable
fun HomeScreen(
    prefs: SharedPreferences,
    isRoleHeldState: State<Boolean>,
    isContactPermittedState: State<Boolean>,
    onRequestRole: () -> Unit,
    onRequestContact: () -> Unit
) {
    var prefInterception by remember { mutableStateOf(prefs.getBoolean(PREF_INTERCEPTION_ACTIVE, false)) }
    var prefWhitelist by remember { mutableStateOf(prefs.getBoolean(PREF_CONTACT_WHITELIST_ENABLED, false)) }

    val isA = prefInterception && isRoleHeldState.value
    val isB = prefWhitelist && isContactPermittedState.value

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("🛡️ Call Blocker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(30.dp))

        StatusDashboard(isA, isB)
        Spacer(modifier = Modifier.height(40.dp))

        StatusDropdown("电话拦截服务", Icons.Default.Phone, prefInterception, isRoleHeldState.value) { enable ->
            prefInterception = enable
            prefs.edit().putBoolean(PREF_INTERCEPTION_ACTIVE, enable).apply()
            if (enable && !isRoleHeldState.value) onRequestRole()
        }
        Spacer(modifier = Modifier.height(24.dp))
        StatusDropdown("通讯录白名单", Icons.Default.Person, prefWhitelist, isContactPermittedState.value) { enable ->
            prefWhitelist = enable
            prefs.edit().putBoolean(PREF_CONTACT_WHITELIST_ENABLED, enable).apply()
            if (enable && !isContactPermittedState.value) onRequestContact()
        }
    }
}

// ======================= 页面 2: 记录与设置 (分页 + 多选) =======================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsAndLogsScreen(isRoleHeld: Boolean, isContactPermitted: Boolean) {
    val context = LocalContext.current
    val dbHelper = remember { CallLogDbHelper(context) }

    // 分页状态
    val pageSize = 20
    var logs by remember { mutableStateOf(listOf<CallRecord>()) }
    var currentOffset by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    // 多选和弹窗状态
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showAdvancedDeleteDialog by remember { mutableStateOf(false) }

    fun loadNextPage() {
        if (isLoading || endReached) return
        isLoading = true
        val newBatch = dbHelper.getRecordsPage(pageSize, currentOffset)
        if (newBatch.isEmpty()) endReached = true
        else {
            logs = logs + newBatch
            currentOffset += newBatch.size
            if (newBatch.size < pageSize) endReached = true
        }
        isLoading = false
    }

    fun refreshLogs() {
        logs = emptyList(); currentOffset = 0; endReached = false; selectedIds = emptySet()
        loadNextPage()
    }

    LaunchedEffect(Unit) { refreshLogs() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 权限概览
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App 权限状态", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                PermissionRow("拦截服务权限", isRoleHeld)
                PermissionRow("通讯录读取权限", isContactPermitted)
            }
        }
        Divider()

        // 顶部操作栏
        Row(
            modifier = Modifier.fillMaxWidth().background(if (isSelectionMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "取消") }
                    Text("已选 ${selectedIds.size} 项", fontWeight = FontWeight.Bold)
                }
                Row {
                    TextButton(onClick = { selectedIds = if (selectedIds.size == logs.size) emptySet() else logs.map { it.id }.toSet() }) { Text(if(selectedIds.size == logs.size) "全不选" else "全选") }
                    IconButton(onClick = {
                        dbHelper.deleteBatch(selectedIds.toList()); Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show(); refreshLogs()
                    }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                }
            } else {
                Text("拦截记录", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { showAdvancedDeleteDialog = true }) { Icon(Icons.Default.DateRange, "按日期") }
                    IconButton(onClick = { refreshLogs() }) { Icon(Icons.Default.Refresh, "刷新") }
                    IconButton(onClick = { dbHelper.deleteAllRecords(); refreshLogs(); Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.DeleteForever, "清空") }
                }
            }
        }

        // 无限滚动列表
        if (logs.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无记录", color = Color.Gray) }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(logs, key = { _, item -> item.id }) { index, log ->
                    if (index >= logs.size - 3 && !endReached && !isLoading) LaunchedEffect(Unit) { loadNextPage() }

                    LogItemSelectableRow(
                        log, selectedIds.contains(log.id), isSelectionMode,
                        { if (isSelectionMode) selectedIds = if (selectedIds.contains(log.id)) selectedIds - log.id else selectedIds + log.id },
                        { if (!isSelectionMode) selectedIds = selectedIds + log.id }
                    )
                }
                if (isLoading) item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } }
                if (endReached && logs.isNotEmpty()) item { Text("— 没有更多了 —", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = Color.Gray) }
            }
        }
    }

    if (showAdvancedDeleteDialog) {
        AdvancedDeleteDialog({ showAdvancedDeleteDialog = false },
            { y, m -> dbHelper.deleteByMonth(y, m); refreshLogs(); showAdvancedDeleteDialog = false },
            { y -> dbHelper.deleteByYear(y); refreshLogs(); showAdvancedDeleteDialog = false }
        )
    }
}

// ======================= 组件库 =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItemSelectableRow(log: CallRecord, isSelected: Boolean, isSelectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(log.time))
    val isBlocked = log.action == "已拦截"
    val color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = bg), elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) { Checkbox(checked = isSelected, onCheckedChange = { onClick() }); Spacer(modifier = Modifier.width(8.dp)) }
            else { Icon(if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(12.dp)) }

            Column(modifier = Modifier.weight(1f)) {
                Text(log.number, fontWeight = FontWeight.Bold)
                Text("$dateStr · ${log.reason}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (!isSelectionMode) Text(log.action, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AdvancedDeleteDialog(onDismiss: () -> Unit, onDeleteByMonth: (Int, Int) -> Unit, onDeleteByYear: (Int) -> Unit) {
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((Calendar.getInstance().get(Calendar.MONTH) + 1).toString()) }
    var mode by remember { mutableStateOf("month") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按日期清理") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    FilterChip(selected = mode == "month", onClick = { mode = "month" }, label = { Text("按月份") })
                    FilterChip(selected = mode == "year", onClick = { mode = "year" }, label = { Text("按年份") })
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = year, onValueChange = { if(it.length<=4) year=it }, label = { Text("年份") })
                if (mode == "month") OutlinedTextField(value = month, onValueChange = { if(it.length<=2) month=it }, label = { Text("月份 (1-12)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val y = year.toIntOrNull(); val m = month.toIntOrNull()
                if (y != null) { if (mode == "year") onDeleteByYear(y) else if (m != null) onDeleteByMonth(y, m) }
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun StatusDashboard(isA: Boolean, isB: Boolean) {
    val (text, color, contentColor) = when {
        isA && isB -> Triple("拦截所有来电除了通讯录好友", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        isA && !isB -> Triple("拦截所有来电 (慎用!)", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        !isA && isB -> Triple("停止拦截来电", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple("MyCallBlocker 暂停使用", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val animatedColor by animateColorAsState(color, label = "color")
    Card(colors = CardDefaults.cardColors(containerColor = animatedColor, contentColor = contentColor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (isA) Icons.Default.Shield else Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDropdown(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isUserEnabled: Boolean, isSystemAuthorized: Boolean, onOptionSelected: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val (text, color) = when {
        !isUserEnabled -> "已关闭" to MaterialTheme.colorScheme.outline
        isUserEnabled && isSystemAuthorized -> "已开启 (运行中)" to MaterialTheme.colorScheme.primary
        else -> "⚠️ 需授权" to MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = text, onValueChange = {}, readOnly = true, leadingIcon = { Icon(icon, null, tint = color) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = color, unfocusedTextColor = color, focusedBorderColor = color, unfocusedBorderColor = color), modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem({ Text("开启") }, { onOptionSelected(true); expanded = false }, leadingIcon = { Icon(Icons.Default.Check, null) })
                DropdownMenuItem({ Text("关闭") }, { onOptionSelected(false); expanded = false }, leadingIcon = { Icon(Icons.Default.Close, null) })
            }
        }
    }
}

@Composable
fun PermissionRow(text: String, isGranted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}