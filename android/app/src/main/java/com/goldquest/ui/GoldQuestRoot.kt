package com.goldquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goldquest.domain.MsgKind

private data class Tab(val route: String, val label: String, val icon: String)

private val TABS = listOf(
    Tab("tasks", "Tarefas", "✅"),
    Tab("week", "Semana", "📅"),
    Tab("shop", "Loja", "🛒"),
    Tab("inventory", "Inventário", "🎒"),
    Tab("history", "Histórico", "📜"),
)

@Composable
fun GoldQuestRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var lastKind by remember { mutableStateOf(MsgKind.OK) }
    var showReminder by remember { mutableStateOf(false) }

    val ready by vm.ready.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val gold by vm.gold.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    // O dia pode ter virado enquanto o app estava em segundo plano
    LifecycleResumeEffect(Unit) {
        vm.refreshDay()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        vm.messages.collect {
            lastKind = it.kind
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it.text)
        }
    }

    Scaffold(
        topBar = { TopBar(gold) { showReminder = true } },
        bottomBar = {
            if (ready && review.isEmpty() && TABS.any { it.route == route }) {
                NavigationBar {
                    for (tab in TABS) {
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("tasks") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.icon, fontSize = 20.sp) },
                            label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                val (container, content) = when (lastKind) {
                    MsgKind.GOLD -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                    MsgKind.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                    MsgKind.OK -> MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
                }
                Snackbar(data, containerColor = container, contentColor = content)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        if (showReminder) ReminderDialog { showReminder = false }
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                !ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                review.isNotEmpty() -> ReviewScreen(review, vm.todayDate().minusDays(1), vm::submitReview)
                else -> AppNavHost(nav, vm)
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: androidx.navigation.NavHostController, vm: MainViewModel) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    val gold by vm.gold.collectAsStateWithLifecycle()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(nav, startDestination = "tasks", modifier = Modifier.fillMaxSize()) {
        composable("tasks") {
            val ui by vm.tasksUi.collectAsStateWithLifecycle()
            ui?.let {
                TasksScreen(
                    ui = it, vm = vm,
                    onEditTask = { id -> nav.navigate("edit/task/$id") },
                    onEditRecurring = { id -> nav.navigate("edit/rec/$id") },
                )
            }
        }
        composable("week") {
            val ui by vm.weekUi.collectAsStateWithLifecycle()
            ui?.let { WeekScreen(it, vm) }
        }
        composable("shop") {
            val offers by vm.offers.collectAsStateWithLifecycle()
            ShopScreen(offers, gold, vm)
        }
        composable("inventory") {
            val ui by vm.inventoryUi.collectAsStateWithLifecycle()
            InventoryScreen(ui, vm)
        }
        composable("history") {
            val ui by vm.historyUi.collectAsStateWithLifecycle()
            HistoryScreen(ui)
        }
        composable("edit/task/{id}", arguments = idArg) {
            EditTaskScreen(it.arguments!!.getLong("id"), tags, vm) { nav.popBackStack() }
        }
        composable("edit/rec/{id}", arguments = idArg) {
            EditRecurringScreen(it.arguments!!.getLong("id"), tags, vm) { nav.popBackStack() }
        }
    }
}

@Composable
private fun TopBar(gold: Int, onReminder: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Gold Quest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onReminder) { Text("🔔", fontSize = 20.sp) }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Coin(20.dp)
                        Text(
                            gold.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (gold < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}
