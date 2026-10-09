@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalAnimationApi::class,
)

package com.musicbox.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicbox.app.R
import com.musicbox.app.ui.components.GlobalDialogs
import com.musicbox.app.ui.screens.AiOrganizerScreen
import com.musicbox.app.ui.screens.AiSettingsScreen
import com.musicbox.app.ui.screens.ArtistsManagerScreen
import com.musicbox.app.ui.screens.FolderScreen
import com.musicbox.app.ui.screens.HomeLayoutScreen
import com.musicbox.app.ui.screens.HomeScreen
import com.musicbox.app.ui.screens.LibraryScreen
import com.musicbox.app.ui.screens.MiniPlayer
import com.musicbox.app.ui.screens.MoodsManagerScreen
import com.musicbox.app.ui.screens.NowPlayingScreen
import com.musicbox.app.ui.screens.OthersScreen
import com.musicbox.app.ui.screens.SearchScreen
import com.musicbox.app.ui.screens.SettingsScreen
import com.musicbox.app.ui.screens.SongsPage
import com.musicbox.app.ui.screens.SongsPageScreen
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.ui.theme.Brand

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, audioPermission()) == PackageManager.PERMISSION_GRANTED

private fun permissionList(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(ctx)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPermission(ctx)
        denied = !granted
    }
    LaunchedEffect(granted) { if (granted) vm.repo.rescan() }

    // Tells you when a freshly added song is already in your library.
    LaunchedEffect(Unit) {
        vm.repo.notices.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    }

    // The person may switch the permission on in the system settings and come back to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasAudioPermission(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back closes the player, then goes up one screen, then returns to Home, and only then leaves the app.
    BackHandler(enabled = granted && (vm.nowPlayingOpen || vm.stack.isNotEmpty() || vm.tab != AppTab.HOME)) {
        when {
            vm.nowPlayingOpen -> vm.nowPlayingOpen = false
            vm.stack.isNotEmpty() -> vm.pop()
            else -> vm.selectTab(AppTab.HOME)
        }
    }

    Box(Modifier.fillMaxSize().background(Brand.screen)) {
        if (!granted) {
            PermissionScreen(
                denied = denied,
                onAllow = { launcher.launch(permissionList()) },
                onSettings = {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    try {
                        ctx.startActivity(i)
                    } catch (e: Exception) {
                        // No settings screen available; the button simply does nothing.
                    }
                },
            )
        } else {
            // Kept here (above the animated screens) so Home remembers where you scrolled to.
            val homeList = rememberLazyListState()
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { Screens(vm, homeList) }
                MiniPlayer(vm)
                BottomBar(vm)
            }
            AnimatedVisibility(
                visible = vm.nowPlayingOpen,
                enter = slideInVertically(tween(340)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200)),
            ) {
                NowPlayingScreen(vm)
            }
            GlobalDialogs(vm)
        }
    }
}

/** Where the person is: a tab, optionally with screens opened on top of it. */
private data class Dest(val tab: AppTab, val route: Route?, val depth: Int)

@Composable
private fun Screens(vm: MainViewModel, homeList: LazyListState) {
    val dest = Dest(vm.tab, vm.stack.lastOrNull(), vm.stack.size)
    AnimatedContent(
        targetState = dest,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            when {
                // Opening a screen: the new one slides in from the right.
                targetState.depth > initialState.depth ->
                    (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 8 } + fadeOut(tween(160)))
                // Going back: the old one slides away to the right.
                targetState.depth < initialState.depth ->
                    (slideInHorizontally(tween(280)) { -it / 8 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(160)))
                // Another tab: a quick cross-fade.
                else -> fadeIn(tween(220)) togetherWith fadeOut(tween(140))
            }
        },
        label = "screens",
    ) { d ->
        val top = d.route
        if (top == null) {
            when (d.tab) {
                AppTab.HOME -> HomeScreen(vm, homeList)
                AppTab.SEARCH -> SearchScreen(vm)
                AppTab.LIBRARY -> LibraryScreen(vm)
                AppTab.FAVORITES -> FolderScreen(vm, FolderKind.FAVORITES, "", "Favorites", embedded = true)
                AppTab.SETTINGS -> SettingsScreen(vm)
            }
        } else {
            when (top) {
                is Route.Folder -> when (top.kind) {
                    FolderKind.ALL -> SongsPageScreen(vm, SongsPage.ALL)
                    FolderKind.NEW_SONGS -> SongsPageScreen(vm, SongsPage.NEW)
                    else -> FolderScreen(vm, top.kind, top.key, top.title)
                }
                Route.Others -> OthersScreen(vm)
                Route.AiOrganizer -> AiOrganizerScreen(vm)
                Route.AiSettings -> AiSettingsScreen(vm)
                Route.ArtistsManager -> ArtistsManagerScreen(vm)
                Route.MoodsManager -> MoodsManagerScreen(vm)
                Route.HomeLayout -> HomeLayoutScreen(vm)
            }
        }
    }
}

@Composable
private fun BottomBar(vm: MainViewModel) {
    NavigationBar(containerColor = Brand.Surface, contentColor = Color.White, tonalElevation = 0.dp) {
        for (t in AppTab.values()) {
            NavigationBarItem(
                selected = vm.stack.isEmpty() && vm.tab == t,
                onClick = { vm.selectTab(t) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Brand.Blue,
                    selectedTextColor = Brand.Blue,
                    indicatorColor = Brand.Card,
                    unselectedIconColor = Brand.Dim,
                    unselectedTextColor = Brand.Dim,
                ),
            )
        }
    }
}

@Composable
private fun PermissionScreen(denied: Boolean, onAllow: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(R.drawable.logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(24.dp))
        Text("MusicBox", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Feel The Music", color = Brand.Dim, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "MusicBox plays the songs stored on your phone. Allow access to your music files so it can find and organise them. Nothing is uploaded.",
            color = Brand.Dim,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue)) {
            Text(if (denied) "Try again" else "Allow access to music")
        }
        if (denied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Without this permission MusicBox cannot see your songs. If Android no longer shows the question, switch it on in the app settings.",
                color = Brand.Dim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onSettings) { Text("Open app settings", color = Brand.Cyan) }
        }
    }
}
