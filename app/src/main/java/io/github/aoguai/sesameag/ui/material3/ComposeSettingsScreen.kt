package io.github.aoguai.sesameag.ui.material3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun ComposeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // 使用 Miuix 的 ThemeController 管理主题模式，设置为 System 模式以自动跟随系统
    val themeController = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(200)
            focusRequester.requestFocus()
        }
    }

    val handleBackAction = {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            viewModel.onSearchQueryChanged("")
            focusManager.clearFocus()
        } else if (uiState.selectedModule != null) {
            viewModel.selectModule(null)
            focusManager.clearFocus()
        } else {
            viewModel.handleBack()
        }
    }

    BackHandler(enabled = true) {
        handleBackAction()
    }

    MiuixTheme(controller = themeController) {
        Scaffold(
            topBar = {
                val titleText = if (isSearchActive) "" else (uiState.selectedModule?.name ?: "配置列表")
                TopAppBar(
                    title = titleText,
                    navigationIcon = {
                        IconButton(onClick = { handleBackAction() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.onSearchQueryChanged(it)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                                    .focusRequester(focusRequester),
                                label = "搜索设置项...",
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        viewModel.onSearchQueryChanged("")
                                        isSearchActive = false
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                                    }
                                },
                                singleLine = true
                            )
                        } else {
                            if (uiState.selectedModule == null) {
                                IconButton(onClick = { 
                                    searchQuery = ""
                                    viewModel.onSearchQueryChanged("")
                                    isSearchActive = true 
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索")
                                }
                                IconButton(onClick = { viewModel.save(context, false) }) { 
                                    Icon(Icons.Default.Save, contentDescription = "保存")
                                }
                            } else {
                                IconButton(onClick = { viewModel.save(context, true) }) {
                                    Icon(Icons.Default.Save, contentDescription = "保存")
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    AnimatedContent(
                        targetState = uiState.selectedModule?.code,
                        transitionSpec = {
                            val duration = 400
                            val easing = FastOutSlowInEasing
                            if (targetState != null) {
                                // 进入二级页面：新页面在上层滑入
                                (slideInHorizontally(animationSpec = tween(duration, easing = easing)) { it } + fadeIn(tween(duration))) togetherWith
                                fadeOut(tween(duration))
                            } else {
                                // 返回一级页面：新页面（列表）在下层淡入，旧页面在顶层滑出
                                fadeIn(tween(duration)) togetherWith
                                (slideOutHorizontally(animationSpec = tween(duration, easing = easing)) { it } + fadeOut(tween(duration)))
                            }.apply {
                                // 🚀 关键修复：确保返回时列表层级在下方
                                targetContentZIndex = if (targetState == null) -1f else 0f
                            }.using(SizeTransform(clip = false))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "SettingsScreenTransition"
                    ) { selectedModuleCode ->
                        // 🚀 核心修复：基于选中的 Code 在全量列表中查找数据，确保退出动画时数据依然有效
                        val moduleToRender = uiState.modules.find { it.code == selectedModuleCode }
                        
                        if (moduleToRender != null) {
                            ModuleDetailView(
                                module = moduleToRender,
                                scrollToFieldCode = if (selectedModuleCode == uiState.selectedModule?.code) uiState.scrollToFieldCode else null,
                                onFieldChange = { field, newValue ->
                                    viewModel.updateField(field, newValue)
                                }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.modules, key = { it.code }) { module ->
                                    ModuleCard(
                                        module = module,
                                        onClick = { viewModel.selectModule(module) }
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isSearchActive && searchQuery.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val searchResults = viewModel.getSearchResults(searchQuery)
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { }
                                ),
                            color = MiuixTheme.colorScheme.background
                        ) {
                            LazyColumn {
                                items(searchResults, key = { "${it.module.code}-${it.field.code}" }) { result ->
                                    SearchResultItem(result) {
                                        isSearchActive = false
                                        searchQuery = ""
                                        viewModel.onSearchQueryChanged("")
                                        focusManager.clearFocus()
                                        viewModel.navigateToSearchResult(result.module.code, result.field.code)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: SettingsViewModel.SearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = result.field.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = "所属模块: ${result.module.name}",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (!result.field.desc.isNullOrBlank()) {
            Text(
                text = result.field.desc,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ModuleCard(
    module: ModuleState,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = io.github.aoguai.sesameag.util.IconManager.getIconVector(module.icon),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${module.fields.size} 项设置",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun SettingTreeRenderer(
    field: FieldState,
    childrenMap: Map<String, List<FieldState>>,
    isChild: Boolean,    depth: Int = 0,
    scrollToFieldCode: String?,
    expandedStates: MutableMap<String, Boolean>,
    onFieldChange: (FieldState, Any) -> Unit
) {
    val children = childrenMap[field.code] ?: emptyList()

    if (children.isEmpty()) {
        // 没有子项的普通开关
        SettingFieldDispatcher(
            field = field,
            isChild = isChild,
            depth = depth,
            isHighlighted = (field.code == scrollToFieldCode),
            onFieldChange = onFieldChange
        )
    } else {
        // 拥有下属项目的“父开关”或“分类”
        val isExpanded = expandedStates[field.code] == true

        GroupedSettingContainer(
            isNested = depth > 0,
            depth = depth
        ) {
            SettingFieldDispatcher(
                field = field,
                isChild = isChild,
                depth = depth,
                isHighlighted = (field.code == scrollToFieldCode),
                isExpandable = true,
                isExpanded = isExpanded,
                onRowClick = { expandedStates[field.code] = !isExpanded },
                onFieldChange = { f, v ->
                    onFieldChange(f, v)
                    // 处理自动展开逻辑...
                    val childrenList = childrenMap[f.code] ?: emptyList()
                    val valStr = v.toString()
                    val valInt = valStr.toIntOrNull() ?: 0
                    val isAnyChildSatisfied = childrenList.any { child ->
                        val dep = child.dependencyCode ?: ""
                        when {
                            dep.contains(">=") -> valInt >= (dep.substringAfter(">=").toIntOrNull() ?: 0)
                            dep.contains("<=") -> valInt <= (dep.substringAfter("<=").toIntOrNull() ?: 0)
                            dep.contains(">") -> valInt > (dep.substringAfter(">").toIntOrNull() ?: 0)
                            dep.contains("<") -> valInt < (dep.substringAfter("<").toIntOrNull() ?: 0)
                            dep.contains("!=") -> valInt != (dep.substringAfter("!=").toIntOrNull() ?: 0)
                            dep.contains("=") -> valStr == dep.substringAfter("=")
                            else -> false
                        }
                    }
                    val isActive = when(v) {
                        is Boolean -> v
                        is Number -> v.toInt() != 0
                        is String -> (v.toIntOrNull() ?: 0) != 0
                        else -> false
                    }
                    expandedStates[f.code] = isActive || isAnyChildSatisfied
                }
            )

            // 展开渲染子项
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    children.forEach { child ->
                        val shouldShow = remember(field.value, child.dependencyCode) {
                            val dep = child.dependencyCode ?: ""
                            val currentValue = field.value?.toString()?.toIntOrNull() ?: 0
                            when {
                                dep.contains(">=") -> currentValue >= (dep.substringAfter(">=").toIntOrNull() ?: 0)
                                dep.contains("<=") -> currentValue <= (dep.substringAfter("<=").toIntOrNull() ?: 0)
                                dep.contains(">") -> currentValue > (dep.substringAfter(">").toIntOrNull() ?: 0)
                                dep.contains("<") -> currentValue < (dep.substringAfter("<").toIntOrNull() ?: 0)
                                dep.contains("!=") -> currentValue != (dep.substringAfter("!=").toIntOrNull() ?: 0)
                                dep.contains("=") -> field.value.toString() == dep.substringAfter("=")
                                else -> true
                            }
                        }

                        if (shouldShow) {
                            // 递归调用：深度 + 1，产生嵌套卡片
                            SettingTreeRenderer(
                                field = child,
                                childrenMap = childrenMap,
                                isChild = true,
                                depth = depth + 1,
                                scrollToFieldCode = scrollToFieldCode,
                                expandedStates = expandedStates,
                                onFieldChange = onFieldChange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleDetailView(
    module: ModuleState,
    scrollToFieldCode: String? = null,
    onFieldChange: (FieldState, Any) -> Unit
) {
    val detailListState = rememberLazyListState()

    val childrenMap = remember(module.fields) {
        module.fields.filter { !it.dependencyCode.isNullOrEmpty() }.groupBy {
            val depCode = it.dependencyCode!!
            when {
                depCode.contains(">=") -> depCode.substringBefore(">=")
                depCode.contains("<=") -> depCode.substringBefore("<=")
                depCode.contains(">") -> depCode.substringBefore(">")
                depCode.contains("<") -> depCode.substringBefore("<")
                depCode.contains("!=") -> depCode.substringBefore("!=")
                depCode.contains("=") -> depCode.substringBefore("=")
                else -> depCode
            }
        }
    }

    val topLevelFieldCodes = remember(module.fields) {
        module.fields.filter { it.dependencyCode.isNullOrEmpty() }.map { it.code }
    }

    val expandedStates = remember(module.code, module.fields) {
        mutableStateMapOf<String, Boolean>().apply {
            module.fields.forEach { field ->
                val children = childrenMap[field.code] ?: emptyList()
                if (children.isNotEmpty()) {
                    val valStr = field.value?.toString() ?: ""
                    val valInt = valStr.toIntOrNull() ?: 0
                    val isAnyChildSatisfied = children.any { child ->
                        val dep = child.dependencyCode ?: ""
                        when {
                            dep.contains(">=") -> valInt >= (dep.substringAfter(">=").toIntOrNull() ?: 0)
                            dep.contains("<=") -> valInt <= (dep.substringAfter("<=").toIntOrNull() ?: 0)
                            dep.contains(">") -> valInt > (dep.substringAfter(">").toIntOrNull() ?: 0)
                            dep.contains("<") -> valInt < (dep.substringAfter("<").toIntOrNull() ?: 0)
                            dep.contains("!=") -> valInt != (dep.substringAfter("!=").toIntOrNull() ?: 0)
                            dep.contains("=") -> valStr == dep.substringAfter("=")
                            else -> false
                        }
                    }
                    this[field.code] = if (field.type == "CATEGORY") true else (field.isTrue || isAnyChildSatisfied)
                }
            }
        }
    }

    LaunchedEffect(scrollToFieldCode) {
        if (scrollToFieldCode != null) {
            delay(400)
            val index = topLevelFieldCodes.indexOfFirst { key ->
                scrollToFieldCode == key || module.fields.find { it.code == scrollToFieldCode }?.dependencyCode?.startsWith(key) == true
            }
            if (index >= 0) {
                detailListState.animateScrollToItem(index, scrollOffset = -200)
            }
        }
    }

    LazyColumn(
        state = detailListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        val topLevelFields = module.fields.filter { it.dependencyCode.isNullOrEmpty() }
        items(topLevelFields, key = { it.code }) { field ->
            SettingTreeRenderer(
                field = field,
                childrenMap = childrenMap,
                isChild = false,
                depth = 0,
                scrollToFieldCode = scrollToFieldCode,
                expandedStates = expandedStates,
                onFieldChange = onFieldChange
            )
        }
    }
}
