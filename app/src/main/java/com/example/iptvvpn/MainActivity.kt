package com.example.iptvvpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.iptvvpn.ui.mobile.AppNav
import dagger.hilt.android.AndroidEntryPoint

/**
 * Point d'entrée mobile : héberge la navigation (onglets Live / Films / Séries / Réglages,
 * détail série et lecteur). Une version TV aura sa propre activité et sa propre UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { AppNav() }
            }
        }
    }
}
```[cite: 1, 3]

---

### 2. `app/src/main/java/com/example/iptvvpn/ui/mobile/AppNav.kt`
```kotlin
package com.example.iptvvpn.ui.mobile

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.iptvvpn.ui.mobile.catalog.CatalogScreen
import com.example.iptvvpn.ui.mobile.catalog.SeriesDetailScreen
import com.example.iptvvpn.ui.mobile.player.PlayerScreen
import com.example.iptvvpn.ui.mobile.settings.SettingsScreen
import com.example.iptvvpn.ui.mobile.settings.SourceSetupScreen
import com.example.iptvvpn.ui.mobile.vpn.VpnScreen

private object Routes {
    const val SETTINGS = "settings"
    const val SOURCE = "settings/source"
    const val VPN = "settings/vpn"
    const val SERIES = "series/{seriesId}"
    const val PLAYER = "player"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentType: String? = null,
)

private val tabs = listOf(
    Tab("live", "Live", Icons.Default.PlayArrow, "LIVE"),
    Tab("movies", "Films", Icons.Default.Star, "MOVIE"),
    Tab("series", "Séries", Icons.AutoMirrored.Filled.List, "SERIES"),
    Tab(Routes.SETTINGS, "Réglages", Icons.Default.Settings),
)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "live",
            modifier = Modifier.padding(padding),
        ) {
            tabs.filter { it.contentType != null }.forEach { tab ->
                composable(
                    route = tab.route,
                    arguments = listOf(
                        navArgument("type") {
                            type = NavType.StringType
                            defaultValue = tab.contentType
                        }
                    ),
                ) {
                    CatalogScreen(
                        onOpenSeries = { id -> navController.navigate("series/${Uri.encode(id)}") },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenSource = { navController.navigate(Routes.SOURCE) },
                    onOpenVpn = { navController.navigate(Routes.VPN) },
                )
            }
            composable(Routes.SOURCE) { SourceSetupScreen() }
            composable(Routes.VPN) { VpnScreen() }

            composable(Routes.SERIES) {
                SeriesDetailScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
            }
            composable(Routes.PLAYER) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
```[cite: 6]

---

### 3. `app/src/main/java/com/example/iptvvpn/ui/mobile/UiErrors.kt`
```kotlin
package com.example.iptvvpn.ui.mobile

import com.example.iptvvpn.data.repository.NoSourceConfiguredException
import com.example.iptvvpn.vpn.VpnNotConnectedException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Message affichable à l'utilisateur pour une erreur remontée par le repository. */
fun Throwable.toUserMessage(): String = when (this) {
    is VpnNotConnectedException -> "VPN non connecté : connecte-le dans Réglages > VPN."
    is NoSourceConfiguredException -> "Aucune source : configure-la dans Réglages > Source."
    is UnknownHostException -> "Serveur introuvable. Vérifie l'adresse."
    is SocketTimeoutException -> "Délai dépassé : le serveur ne répond pas."
    else -> message ?: "Erreur inconnue"
}
```[cite: 4]

---

### 4. `app/src/main/java/com/example/iptvvpn/ui/mobile/settings/SettingsScreen.kt`
```kotlin
package com.example.iptvvpn.ui.mobile.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.iptvvpn.data.local.SourceConfig
import com.example.iptvvpn.data.local.SourceConfig.Kind
import com.example.iptvvpn.data.local.SourceConfigStore
import com.example.iptvvpn.data.repository.PlaylistRepository
import com.example.iptvvpn.ui.mobile.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsScreen(onOpenSource: () -> Unit, onOpenVpn: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Réglages",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
        )
        ListItem(
            headlineContent = { Text("Source de contenu") },
            supportingContent = { Text("Playlist M3U ou serveur Xtream") },
            modifier = Modifier.clickable(onClick = onOpenSource),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("VPN") },
            supportingContent = { Text("Configuration WireGuard et connexion") },
            modifier = Modifier.clickable(onClick = onOpenVpn),
        )
        HorizontalDivider()
    }
}

data class SourceUiState(
    val kind: Kind = Kind.M3U,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SourceSetupViewModel @Inject constructor(
    private val repo: PlaylistRepository,
    private val store: SourceConfigStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(SourceUiState())
    val ui: StateFlow<SourceUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.config.first()?.let { c ->
                _ui.update {
                    it.copy(
                        kind = c.kind, url = c.url,
                        username = c.username.orEmpty(), password = c.password.orEmpty(),
                    )
                }
            }
        }
    }

    fun onKind(kind: Kind) = _ui.update { it.copy(kind = kind) }
    fun onUrl(v: String) = _ui.update { it.copy(url = v) }
    fun onUsername(v: String) = _ui.update { it.copy(username = v) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v) }
    fun messageShown() = _ui.update { it.copy(message = null) }

    fun save() {
        val s = _ui.value
        if (s.url.isBlank()) return _ui.update { it.copy(message = "Saisis l'adresse de la source") }
        if (s.kind == Kind.XTREAM && (s.username.isBlank() || s.password.isBlank())) {
            return _ui.update { it.copy(message = "Identifiant et mot de passe requis") }
        }

        val config = SourceConfig(
            id = SOURCE_ID,
            kind = s.kind,
            url = s.url.trim(),
            username = s.username.trim().ifEmpty { null },
            password = s.password.ifEmpty { null },
        )
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.validate(config)
                .onSuccess {
                    repo.saveSource(config)
                    _ui.update { it.copy(busy = false, message = "Source enregistrée") }
                }
                .onFailure { e ->
                    _ui.update { it.copy(busy = false, message = e.toUserMessage()) }
                }
        }
    }

    private companion object {
        const val SOURCE_ID = "main"
    }
}

@Composable
fun SourceSetupScreen(viewModel: SourceSetupViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Source de contenu", style = MaterialTheme.typography.headlineMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ui.kind == Kind.M3U,
                    onClick = { viewModel.onKind(Kind.M3U) },
                    label = { Text("Playlist M3U") },
                )
                FilterChip(
                    selected = ui.kind == Kind.XTREAM,
                    onClick = { viewModel.onKind(Kind.XTREAM) },
                    label = { Text("Xtream Codes") },
                )
            }

            OutlinedTextField(
                value = ui.url,
                onValueChange = viewModel::onUrl,
                label = { Text(if (ui.kind == Kind.M3U) "URL de la playlist" else "Adresse du serveur (http://hote:port)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (ui.kind == Kind.XTREAM) {
                OutlinedTextField(
                    value = ui.username,
                    onValueChange = viewModel::onUsername,
                    label = { Text("Identifiant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.password,
                    onValueChange = viewModel::onPassword,
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.busy) "Vérification…" else "Vérifier et enregistrer") }

            Text(
                "Si « VPN obligatoire » est activé, connecte d'abord le VPN : la vérification passe par le réseau.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
```[cite: 5, 7, 11]

---

### 5. `app/src/main/java/com/example/iptvvpn/ui/mobile/player/PlayerScreen.kt`
```kotlin
package com.example.iptvvpn.ui.mobile.player

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.example.iptvvpn.player.PlayerManager
import com.example.iptvvpn.player.PlayerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val manager: PlayerManager,
) : ViewModel()

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val status by viewModel.manager.status.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) viewModel.manager.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.manager.player
                    keepScreenOn = true
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        val overlay = when (val s = status) {
            PlayerStatus.VpnRequired -> "VPN déconnecté : la lecture a été coupée."
            is PlayerStatus.Error -> "Erreur de lecture (${s.message})."
            else -> null
        }
        overlay?.let {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(it, color = Color.White)
                Button(onClick = onBack) { Text("Retour") }
            }
        }
    }
}
```[cite: 8, 12]

---

### 6. `app/src/main/java/com/example/iptvvpn/ui/mobile/catalog/SeriesDetailScreen.kt`
```kotlin
package com.example.iptvvpn.ui.mobile.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.iptvvpn.data.repository.PlaylistRepository
import com.example.iptvvpn.domain.Episode
import com.example.iptvvpn.domain.SeriesDetail
import com.example.iptvvpn.player.PlayerManager
import com.example.iptvvpn.player.toPlayRequest
import com.example.iptvvpn.ui.mobile.toUserMessage
import com.example.iptvvpn.vpn.VpnNotConnectedException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesUiState(
    val detail: SeriesDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: PlaylistRepository,
    private val player: PlayerManager,
) : ViewModel() {

    val seriesId: String = savedState.get<String>("seriesId").orEmpty()

    private val _ui = MutableStateFlow(SeriesUiState())
    val ui: StateFlow<SeriesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.seriesDetail(seriesId)
                .onSuccess { d -> _ui.update { it.copy(loading = false, detail = d) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.toUserMessage()) } }
        }
    }

    fun play(episode: Episode): Boolean {
        val started = player.play(episode.toPlayRequest())
        if (!started) _ui.update { it.copy(message = VpnNotConnectedException().toUserMessage()) }
        return started
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}

@Composable
fun SeriesDetailScreen(
    onOpenPlayer: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            ui.error != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(ui.error!!)
                Button(onClick = viewModel::load) { Text("Réessayer") }
            }

            else -> {
                val detail = ui.detail
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(viewModel.seriesId, style = MaterialTheme.typography.headlineSmall)
                            detail?.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            if (detail == null || detail.seasons.isEmpty()) Text("Aucun épisode trouvé")
                        }
                    }
                    detail?.seasons?.forEach { season ->
                        item(key = "season-${season.number}") {
                            Text(
                                "Saison ${season.number}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(season.episodes, key = { "ep-${it.id}" }) { episode ->
                            ListItem(
                                headlineContent = { Text(episode.title, maxLines = 2) },
                                supportingContent = {
                                    episode.durationSec?.let { Text("${it / 60} min") }
                                },
                                modifier = Modifier.clickable {
                                    if (viewModel.play(episode)) onOpenPlayer()
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
```[cite: 9]

Vous n'avez plus qu'à déposer ces fichiers dans votre dépôt GitHub aux chemins indiqués pour lancer votre première compilation !
