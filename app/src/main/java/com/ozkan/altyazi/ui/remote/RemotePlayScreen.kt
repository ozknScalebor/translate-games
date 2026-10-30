package com.ozkan.altyazi.ui.remote

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozkan.altyazi.R
import com.ozkan.altyazi.remote.ConsoleItem
import com.ozkan.altyazi.remote.ConsoleStatus
import com.ozkan.altyazi.remote.Connection
import com.ozkan.altyazi.remote.FoundConsole
import com.ozkan.altyazi.remote.RemoteError
import com.ozkan.altyazi.remote.RemoteKeys
import com.ozkan.altyazi.remote.RemoteUiState
import com.ozkan.altyazi.remote.SavedConsole
import com.ozkan.altyazi.ui.AppLogo
import com.ozkan.altyazi.ui.theme.AppColors

/** Everything the Remote Play screen can ask for. */
interface RemotePlayActions {
    fun back()
    fun useCamera()
    fun connect(console: SavedConsole)
    fun cancelConnect()
    fun dismissError()
    fun submitLoginPin(pin: String)
    fun pair(host: String, accountId: String, pin: String, name: String)
    fun cancelPairing()
    fun resetPairing()
    fun rename(id: String, name: String)
    fun forget(id: String)
}

/** Draft for the pairing dialog: prefilled from a console found on the network, or empty. */
data class PairDraft(val host: String, val name: String)

/**
 * PS5 picker: paired consoles connect with one tap (waking them from rest mode if needed); consoles
 * found on the Wi-Fi can be paired; any PS5 can be added by its IP address.
 */
@Composable
fun RemotePlayScreen(state: RemoteUiState, actions: RemotePlayActions) {
    BackHandler(onBack = actions::back)
    var draft by remember { mutableStateOf<PairDraft?>(null) }
    var renaming by remember { mutableStateOf<SavedConsole?>(null) }
    var forgetting by remember { mutableStateOf<SavedConsole?>(null) }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            Header(onBack = actions::back, onUseCamera = actions::useCamera, onAdd = { draft = PairDraft("", "") })

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!state.discoveryAvailable) {
                    item(span = { GridItemSpan(maxLineSpan) }) { NetworkBanner() }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.remote_my_consoles)) }
                if (state.consoles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyConsoles(onAdd = { draft = PairDraft("", "") }) }
                } else {
                    items(state.consoles, key = { it.console.id }) { item ->
                        ConsoleCard(
                            item = item,
                            busy = state.connection.console?.id == item.console.id && state.connection !is Connection.Failed,
                            onClick = { actions.connect(item.console) },
                            onRename = { renaming = item.console },
                            onForget = { forgetting = item.console },
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        SectionTitle(stringResource(R.string.remote_on_network))
                        Spacer(Modifier.width(12.dp))
                        if (state.discoveryAvailable) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.TextSecondary)
                    }
                }
                if (state.found.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.remote_searching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                        )
                    }
                } else {
                    items(state.found, key = { "found-" + it.id }) { found ->
                        FoundCard(found, onPair = { draft = PairDraft(found.host, found.name) })
                    }
                }
            }
        }

        ConnectionOverlay(state.connection, actions)
    }

    draft?.let { d ->
        PairDialog(
            draft = d,
            savedAccountId = state.savedAccountId,
            pairing = state.pairing,
            onPair = actions::pair,
            onCancel = {
                actions.cancelPairing()
                actions.resetPairing()
                draft = null
            },
            onDone = {
                actions.resetPairing()
                draft = null
            },
        )
    }

    renaming?.let { console ->
        RenameDialog(
            current = console.name,
            onSave = { name ->
                actions.rename(console.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    forgetting?.let { console ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            containerColor = AppColors.SurfaceHigh,
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.remote_forget_title, console.name)) },
            text = { Text(stringResource(R.string.remote_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.forget(console.id)
                    forgetting = null
                }) { Text(stringResource(R.string.remote_forget), color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit, onUseCamera: () -> Unit, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
        Spacer(Modifier.width(4.dp))
        AppLogo(Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.remote_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.remote_subtitle), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onUseCamera) {
            Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.remote_use_camera))
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.remote_add))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = AppColors.TextSecondary)
}

@Composable
private fun NetworkBanner() {
    Surface(shape = RoundedCornerShape(16.dp), color = AppColors.Danger.copy(alpha = 0.12f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WifiOff, null, tint = AppColors.Danger)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.remote_network_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyConsoles(onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            ConsoleGlyph(Icons.Rounded.WifiFind, Modifier.size(52.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.remote_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.remote_empty_body), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.width(16.dp))
            FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.remote_add)) }
        }
    }
}

@Composable
private fun ConsoleCard(item: ConsoleItem, busy: Boolean, onClick: () -> Unit, onRename: () -> Unit, onForget: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(24.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, if (item.status == ConsoleStatus.READY) AppColors.Primary.copy(alpha = 0.55f) else AppColors.Outline),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConsoleGlyph(Icons.Rounded.SportsEsports, Modifier.size(52.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.console.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.remote_console_detail, item.console.consoleName, item.address),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.remote_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppColors.SurfaceHigh) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remote_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remote_forget)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; onForget() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(item.status)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(if (item.status == ConsoleStatus.STANDBY) R.string.remote_tap_wake else R.string.remote_tap_connect),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun FoundCard(found: FoundConsole, onPair: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Outline),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ConsoleGlyph(Icons.Rounded.SportsEsports, Modifier.size(44.dp), muted = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(found.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(found.host, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                StatusChip(found.status)
            }
            FilledTonalButton(onClick = onPair) { Text(stringResource(R.string.remote_pair)) }
        }
    }
}

@Composable
private fun ConsoleGlyph(icon: ImageVector, modifier: Modifier, muted: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (muted) SolidColor(AppColors.SurfaceHigh) else AppColors.BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun StatusChip(status: ConsoleStatus) {
    val (color, label, icon) = when (status) {
        ConsoleStatus.READY -> Triple(AppColors.Success, stringResource(R.string.remote_status_ready), Icons.Rounded.PowerSettingsNew)
        ConsoleStatus.STANDBY -> Triple(AppColors.Accent, stringResource(R.string.remote_status_standby), Icons.Rounded.Bedtime)
        ConsoleStatus.OFFLINE -> Triple(AppColors.TextSecondary, stringResource(R.string.remote_status_offline), Icons.Rounded.WifiOff)
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Waking / connecting progress, login PIN prompt and connection errors on top of the list. */
@Composable
private fun ConnectionOverlay(connection: Connection, actions: RemotePlayActions) {
    val visible = connection is Connection.Waking || connection is Connection.Connecting || connection is Connection.Failed
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = AppColors.SurfaceHigh, modifier = Modifier.widthIn(min = 420.dp, max = 520.dp)) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (connection) {
                        is Connection.Waking -> Progress(
                            stringResource(R.string.remote_waking, connection.console.name),
                            stringResource(R.string.remote_waking_hint),
                            actions::cancelConnect,
                        )
                        is Connection.Connecting -> Progress(
                            stringResource(R.string.remote_connecting, connection.console.name),
                            stringResource(R.string.remote_connecting_hint),
                            actions::cancelConnect,
                        )
                        is Connection.Failed -> Failure(connection, actions)
                        else -> Unit
                    }
                }
            }
        }
    }

    if (connection is Connection.NeedsPin) {
        LoginPinDialog(
            incorrect = connection.incorrect,
            onSubmit = actions::submitLoginPin,
            onCancel = actions::cancelConnect,
        )
    }
}

@Composable
private fun Progress(title: String, hint: String, onCancel: () -> Unit) {
    CircularProgressIndicator(Modifier.size(44.dp), color = AppColors.Primary, strokeWidth = 3.dp)
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(hint, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun Failure(failed: Connection.Failed, actions: RemotePlayActions) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(AppColors.Danger.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = AppColors.Danger, modifier = Modifier.size(30.dp))
    }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.remote_failed, failed.console.name), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        when (val e = failed.error) {
            RemoteError.NotReachable -> stringResource(R.string.remote_error_unreachable)
            RemoteError.InUse -> stringResource(R.string.remote_error_in_use)
            RemoteError.Unsupported -> stringResource(R.string.remote_error_unsupported)
            is RemoteError.Other -> stringResource(R.string.remote_error_other, e.message)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = actions::dismissError) { Text(stringResource(R.string.action_close)) }
        Button(onClick = { actions.connect(failed.console) }) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun LoginPinDialog(incorrect: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        containerColor = AppColors.SurfaceHigh,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(R.string.remote_login_pin_title)) },
        text = {
            Column {
                Text(
                    stringResource(if (incorrect) R.string.remote_login_pin_wrong else R.string.remote_login_pin_body),
                    color = if (incorrect) AppColors.Danger else AppColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 8.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.width(200.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = RemoteKeys.isLoginPin(pin)) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SurfaceHigh,
        title = { Text(stringResource(R.string.remote_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                singleLine = true,
                label = { Text(stringResource(R.string.remote_field_name)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

internal const val MAX_NAME = 32
