package com.example.mycallblocker

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.res.stringResource
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
                        label = { Text(stringResource(R.string.nav_home)) },
                        selected = currentRoute == "home",
                        onClick = { currentRoute = "home"; navController.navigate("home") { popUpTo("home") { inclusive = true } } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
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
        Text(stringResource(R.string.title_app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(30.dp))

        StatusDashboard(isA, isB)
        Spacer(modifier = Modifier.height(40.dp))

        StatusDropdown(stringResource(R.string.setting_intercept_service), Icons.Default.Phone, prefInterception, isRoleHeldState.value) { enable ->
            prefInterception = enable
            prefs.edit().putBoolean(PREF_INTERCEPTION_ACTIVE, enable).apply()
            if (enable && !isRoleHeldState.value) onRequestRole()
        }
        Spacer(modifier = Modifier.height(24.dp))
        StatusDropdown(stringResource(R.string.setting_whitelist), Icons.Default.Person, prefWhitelist, isContactPermittedState.value) { enable ->
            prefWhitelist = enable
            prefs.edit().putBoolean(PREF_CONTACT_WHITELIST_ENABLED, enable).apply()
            if (enable && !isContactPermittedState.value) onRequestContact()
        }
    }
}

// ======================= 页面 2: 记录与设置 (全屏滑动 + 吸顶操作栏) =======================
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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

    // 下拉菜单状态
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }

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

    fun restartApp() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        if (context is Activity) context.finish()
    }

    LaunchedEffect(Unit) { refreshLogs() }

    // ============================================
    //  修改点：整个页面是一个 LazyColumn
    // ============================================
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {

        // --- 1. 语言设置区域 (作为列表的第一项) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.language_setting), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    val currentLangCode = LanguageUtil.getSavedLanguage(context)
                    val currentLangLabel = when (currentLangCode) {
                        "zh" -> stringResource(R.string.lang_zh)
                        "en" -> stringResource(R.string.lang_en)
                        else -> stringResource(R.string.lang_follow_system)
                    }

                    ExposedDropdownMenuBox(
                        expanded = isLanguageMenuExpanded,
                        onExpandedChange = { isLanguageMenuExpanded = !isLanguageMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentLangLabel,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLanguageMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = isLanguageMenuExpanded,
                            onDismissRequest = { isLanguageMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_follow_system)) },
                                onClick = { LanguageUtil.setLanguage(context, ""); restartApp() },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_zh)) },
                                onClick = { LanguageUtil.setLanguage(context, "zh"); restartApp() },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lang_en)) },
                                onClick = { LanguageUtil.setLanguage(context, "en"); restartApp() },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
            Divider(color = Color.LightGray.copy(alpha = 0.5f))
        }

        // --- 2. 权限概览 (作为列表的第二项) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.perm_status), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionRow(stringResource(R.string.perm_intercept), isRoleHeld)
                    PermissionRow(stringResource(R.string.perm_contact), isContactPermitted)
                }
            }
            Divider()
        }

        // --- 3. 顶部操作栏 (吸顶 Header!) ---
        stickyHeader {
            // 需要给 Row 一个背景色，否则滚动时文字会重叠
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface) // 重要：背景色
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, stringResource(R.string.btn_cancel)) }
                        Text(stringResource(R.string.log_selected_count, selectedIds.size), fontWeight = FontWeight.Bold)
                    }
                    Row {
                        TextButton(onClick = { selectedIds = if (selectedIds.size == logs.size) emptySet() else logs.map { it.id }.toSet() }) {
                            Text(if (selectedIds.size == logs.size) stringResource(R.string.action_unselect_all) else stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = {
                            dbHelper.deleteBatch(selectedIds.toList());
                            Toast.makeText(context, context.getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
                            refreshLogs()
                        }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    Text(stringResource(R.string.log_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { showAdvancedDeleteDialog = true }) { Icon(Icons.Default.DateRange, stringResource(R.string.dialog_date_clean)) }
                        IconButton(onClick = { refreshLogs() }) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) }
                        IconButton(onClick = {
                            dbHelper.deleteAllRecords(); refreshLogs();
                            Toast.makeText(context, context.getString(R.string.msg_cleared), Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.DeleteForever, stringResource(R.string.action_clear)) }
                    }
                }
            }
            // 操作栏下方的分割线，也跟着吸顶
            Divider()
        }

        // --- 4. 拦截记录列表 ---
        if (logs.isEmpty() && !isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_no_records), color = Color.Gray)
                }
            }
        } else {
            itemsIndexed(logs, key = { _, item -> item.id }) { index, log ->
                if (index >= logs.size - 3 && !endReached && !isLoading) LaunchedEffect(Unit) { loadNextPage() }

                LogItemSelectableRow(
                    log, selectedIds.contains(log.id), isSelectionMode,
                    { if (isSelectionMode) selectedIds = if (selectedIds.contains(log.id)) selectedIds - log.id else selectedIds + log.id },
                    { if (!isSelectionMode) selectedIds = selectedIds + log.id }
                )
            }
            if (isLoading) item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } }
            if (endReached && logs.isNotEmpty()) item {
                Text(stringResource(R.string.msg_no_more), modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = Color.Gray)
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
    val isBlocked = log.action.contains("拦截") || log.action.contains("Blocked") || log.action.contains("Block")

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
        title = { Text(stringResource(R.string.dialog_date_clean)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    FilterChip(selected = mode == "month", onClick = { mode = "month" }, label = { Text(stringResource(R.string.dialog_by_month)) })
                    FilterChip(selected = mode == "year", onClick = { mode = "year" }, label = { Text(stringResource(R.string.dialog_by_year)) })
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = year, onValueChange = { if(it.length<=4) year=it }, label = { Text(stringResource(R.string.label_year)) })
                if (mode == "month") OutlinedTextField(value = month, onValueChange = { if(it.length<=2) month=it }, label = { Text(stringResource(R.string.label_month)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                val y = year.toIntOrNull(); val m = month.toIntOrNull()
                if (y != null) { if (mode == "year") onDeleteByYear(y) else if (m != null) onDeleteByMonth(y, m) }
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.btn_confirm_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun StatusDashboard(isA: Boolean, isB: Boolean) {
    val (text, color, contentColor) = when {
        isA && isB -> Triple(stringResource(R.string.status_intercept_all_except_contacts), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        isA && !isB -> Triple(stringResource(R.string.status_intercept_all_warning), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        !isA && isB -> Triple(stringResource(R.string.status_stop_intercept), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(stringResource(R.string.status_paused), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
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
        !isUserEnabled -> stringResource(R.string.status_off) to MaterialTheme.colorScheme.outline
        isUserEnabled && isSystemAuthorized -> stringResource(R.string.status_on) to MaterialTheme.colorScheme.primary
        else -> stringResource(R.string.status_need_auth) to MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = text,
                onValueChange = {},
                readOnly = true,
                leadingIcon = { Icon(icon, null, tint = color) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = color, unfocusedTextColor = color, focusedBorderColor = color, unfocusedBorderColor = color),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.action_enable)) }, { onOptionSelected(true); expanded = false }, leadingIcon = { Icon(Icons.Default.Check, null) })
                DropdownMenuItem({ Text(stringResource(R.string.action_disable)) }, { onOptionSelected(false); expanded = false }, leadingIcon = { Icon(Icons.Default.Close, null) })
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