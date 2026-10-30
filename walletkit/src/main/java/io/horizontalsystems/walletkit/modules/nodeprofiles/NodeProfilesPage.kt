package io.horizontalsystems.walletkit.modules.nodeprofiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.NodeProfileTestResult
import io.horizontalsystems.walletkit.entities.NodeAuthType
import io.horizontalsystems.walletkit.entities.NodeCredentials
import io.horizontalsystems.walletkit.entities.NodeProfile
import io.horizontalsystems.walletkit.entities.NodeProtocol
import io.horizontalsystems.walletkit.entities.NodeTlsMode
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.ui.compose.components.VSpacer
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.marketkit.models.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class NodeProfilesPage(val initialChainUid: String? = null) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NodeProfilesScreen(initialChainUid, navigation)
    }
}

private data class NodeChainOption(
    val uid: String,
    val title: String,
    val defaultEndpoint: () -> String,
)

private val nodeChainOptions = listOf(
    NodeChainOption(
        uid = BlockchainType.PirateChain.uid,
        title = "Pirate Chain (ARRR)",
        defaultEndpoint = { "lightd1.pirate.black:443" },
    ),
    NodeChainOption(
        uid = BlockchainType.Zcash.uid,
        title = "Zcash (ZEC)",
        defaultEndpoint = { App.zcashEndpointManager.defaultEndpoints.first().url },
    ),
)

@Composable
private fun NodeProfilesScreen(initialChainUid: String?, navigation: HSNavigation) {
    val manager = App.nodeProfileManager
    val profiles by manager.profilesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val screenContext = LocalContext.current
    var chainUid by remember(initialChainUid) {
        mutableStateOf(nodeChainOptions.firstOrNull { it.uid == initialChainUid }?.uid ?: nodeChainOptions.first().uid)
    }
    var editingProfile by remember { mutableStateOf<NodeProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val chain = nodeChainOptions.first { it.uid == chainUid }
    val chainProfiles = profiles.filter { it.chainUid == chain.uid && it.network == MAINNET }
    val activeProfile = chainProfiles.firstOrNull { it.isSelected }

    HSScaffold(
        title = stringResource(R.string.NodeProfiles_Title),
        onBack = navigation::removeLastOrNull,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item {
                VSpacer(12.dp)
                Text(stringResource(R.string.NodeProfiles_Description))
                VSpacer(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    nodeChainOptions.forEach { option ->
                        if (option.uid == chainUid) {
                            Button(onClick = { chainUid = option.uid }) { Text(option.title) }
                        } else {
                            OutlinedButton(onClick = { chainUid = option.uid }) { Text(option.title) }
                        }
                    }
                }
                VSpacer(20.dp)
                Text(stringResource(R.string.NodeProfiles_BuiltIn))
                Text(chain.defaultEndpoint())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = activeProfile == null,
                        enabled = activeProfile != null,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                if (chain.uid == BlockchainType.Zcash.uid) {
                                    App.zcashEndpointManager.save(App.zcashEndpointManager.defaultEndpoints.first())
                                } else {
                                    manager.clearActive(chain.uid, MAINNET)
                                }
                            }
                        },
                    )
                    Text(
                        text = if (activeProfile == null) stringResource(R.string.NodeProfiles_Active)
                        else stringResource(R.string.NodeProfiles_UseDefault),
                    )
                }
                VSpacer(12.dp)
            }

            if (chainProfiles.isEmpty()) {
                item {
                    Text(stringResource(R.string.NodeProfiles_NoProfiles))
                    VSpacer(12.dp)
                }
            }

            items(chainProfiles, key = { it.profileId }) { profile ->
                NodeProfileRow(
                    profile = profile,
                    profileSupported = manager.isProfileSupported(profile),
                    authSupported = manager.isAuthenticationSupported(profile),
                    onSelect = {
                        if (!profile.isSelected) {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        manager.setActive(profile.chainUid, profile.network, profile.profileId)
                                    }
                                }
                                    .onFailure { _ -> message = screenContext.getString(R.string.NodeProfiles_SaveFailed) }
                            }
                        }
                    },
                    onEdit = {
                        editingProfile = profile
                        showEditor = true
                    },
                    onDelete = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val wasActive = profile.isSelected
                                    val clearZcashSelection = wasActive && profile.chainUid == BlockchainType.Zcash.uid
                                    manager.delete(
                                        profile.chainUid,
                                        profile.network,
                                        profile.profileId,
                                        notify = !clearZcashSelection,
                                    )
                                    if (clearZcashSelection) {
                                        App.zcashEndpointManager.save(App.zcashEndpointManager.defaultEndpoints.first())
                                    }
                                }
                            }
                                .onFailure { _ -> message = screenContext.getString(R.string.NodeProfiles_SaveFailed) }
                        }
                    },
                )
            }

            item {
                VSpacer(16.dp)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        editingProfile = null
                        showEditor = true
                    },
                ) {
                    Text(stringResource(R.string.AddMoneroNode_AddNode))
                }
                VSpacer(40.dp)
                message?.takeIf { it.isNotBlank() }?.let { Text(it) }
            }
        }
    }

    if (showEditor) {
        NodeProfileEditorDialog(
            chain = chain,
            profile = editingProfile,
            onDismiss = { showEditor = false },
            onSave = { profile, credentials ->
                try {
                    withContext(Dispatchers.IO) { manager.save(profile, credentials) }
                    showEditor = false
                    message = null
                    null
                } catch (_: Exception) {
                    screenContext.getString(R.string.NodeProfiles_SaveFailed)
                }
            },
            onTest = { profile, credentials -> manager.testConnection(profile, credentials) },
        )
    }
}

@Composable
private fun NodeProfileRow(
    profile: NodeProfile,
    profileSupported: Boolean,
    authSupported: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(profile.name)
        Text(profile.endpoint)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = profile.isSelected, onClick = onSelect, enabled = profileSupported)
            Text(
                text = if (profile.isSelected) stringResource(R.string.NodeProfiles_Active)
                else stringResource(R.string.NodeProfiles_SetActive),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.NodeProfiles_Edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.NodeProfiles_Remove)) }
        }
        if (!profileSupported) {
            Text(
                stringResource(
                    if (!authSupported && profile.authType != NodeAuthType.NONE) R.string.NodeProfiles_AuthUnsupported
                    else R.string.NodeProfiles_ProtocolUnsupported,
                ),
            )
        }
    }
}

@Composable
private fun NodeProfileEditorDialog(
    chain: NodeChainOption,
    profile: NodeProfile?,
    onDismiss: () -> Unit,
    onSave: suspend (NodeProfile, NodeCredentials?) -> String?,
    onTest: suspend (NodeProfile, NodeCredentials?) -> NodeProfileTestResult,
) {
    var name by remember(profile?.profileId) { mutableStateOf(profile?.name.orEmpty()) }
    val parsedEndpoint = remember(profile?.endpoint) { profile?.endpoint?.let { runCatching { URI(it) }.getOrNull() } }
    var host by remember(profile?.profileId) { mutableStateOf(parsedEndpoint?.host.orEmpty()) }
    var port by remember(profile?.profileId) {
        mutableStateOf(parsedEndpoint?.port?.takeIf { it > 0 }?.toString() ?: "443")
    }
    var useTls by remember(profile?.profileId) { mutableStateOf(profile?.tlsMode != NodeTlsMode.DISABLED) }
    var authType by remember(profile?.profileId) { mutableStateOf(profile?.authType ?: NodeAuthType.NONE) }
    var username by remember(profile?.profileId) { mutableStateOf("") }
    var password by remember(profile?.profileId) { mutableStateOf("") }
    var bearer by remember(profile?.profileId) { mutableStateOf("") }
    var authMenuExpanded by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun buildProfile(): NodeProfile {
        val protocol = profile?.protocol ?: NodeProtocol.LIGHTWALLETD_GRPC
        val scheme = when (protocol) {
            NodeProtocol.LIGHTWALLETD_GRPC,
            NodeProtocol.HTTP_JSON_RPC -> if (useTls) "https" else "http"
            NodeProtocol.ELECTRUM_JSON_RPC -> if (useTls) "ssl" else "tcp"
        }
        val normalizedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host.trim()
        return NodeProfile(
            chainUid = chain.uid,
            network = profile?.network ?: MAINNET,
            profileId = profile?.profileId.orEmpty(),
            name = name.trim(),
            endpoint = "$scheme://$normalizedHost:${port.trim()}",
            protocol = protocol,
            tlsMode = if (useTls) NodeTlsMode.REQUIRED else NodeTlsMode.DISABLED,
            authType = authType,
            credentialRef = profile?.credentialRef,
            isSelected = profile?.isSelected ?: false,
        )
    }

    fun buildCredentials(): NodeCredentials? = when (authType) {
        NodeAuthType.NONE -> null
        NodeAuthType.BASIC -> NodeCredentials(username = username.trim(), password = password)
        NodeAuthType.BEARER -> NodeCredentials(bearerToken = bearer.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (profile == null) R.string.AddMoneroNode_AddNode else R.string.NodeProfiles_Edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.NodeProfiles_Name)) }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.NodeProfiles_Host)) }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.NodeProfiles_Port)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.NodeProfiles_Tls), modifier = Modifier.weight(1f))
                    Switch(checked = useTls, onCheckedChange = { useTls = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.NodeProfiles_AuthType), modifier = Modifier.weight(1f))
                    TextButton(onClick = { authMenuExpanded = true }) {
                        Text(
                            stringResource(
                                when (authType) {
                                    NodeAuthType.NONE -> R.string.NodeProfiles_AuthNone
                                    NodeAuthType.BASIC -> R.string.NodeProfiles_AuthBasic
                                    NodeAuthType.BEARER -> R.string.NodeProfiles_AuthBearer
                                },
                            ),
                        )
                    }
                    DropdownMenu(expanded = authMenuExpanded, onDismissRequest = { authMenuExpanded = false }) {
                        NodeAuthType.entries.forEach { option ->
                            DropdownMenuItem(onClick = {
                                authType = option
                                authMenuExpanded = false
                            }) {
                                Text(
                                    stringResource(
                                        when (option) {
                                            NodeAuthType.NONE -> R.string.NodeProfiles_AuthNone
                                            NodeAuthType.BASIC -> R.string.NodeProfiles_AuthBasic
                                            NodeAuthType.BEARER -> R.string.NodeProfiles_AuthBearer
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                if (authType == NodeAuthType.BASIC) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.AddMoneroNode_Username)) }, singleLine = true)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.Password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (authType == NodeAuthType.BEARER) {
                    OutlinedTextField(
                        value = bearer,
                        onValueChange = { bearer = it },
                        label = { Text(stringResource(R.string.NodeProfiles_AuthBearer)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                testMessage?.let { Text(it) }
                saveError?.let { Text(it) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isTesting && !isSaving,
                    onClick = {
                        val candidate = buildProfile()
                        val credentials = buildCredentials()
                        scope.launch {
                            isTesting = true
                            try {
                                testMessage = when (val result = onTest(candidate, credentials)) {
                                    is NodeProfileTestResult.Reachable -> context.getString(R.string.NodeProfiles_TestSucceeded, result.latencyMs)
                                    NodeProfileTestResult.AuthenticationUnsupported -> context.getString(R.string.NodeProfiles_AuthUnsupported)
                                    NodeProfileTestResult.UnsupportedProtocol,
                                    NodeProfileTestResult.Unreachable -> context.getString(R.string.NodeProfiles_TestFailed)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                testMessage = context.getString(R.string.NodeProfiles_TestFailed)
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.NodeProfiles_TestConnection)) }
                Button(
                    enabled = !isTesting && !isSaving,
                    onClick = {
                        scope.launch {
                            isSaving = true
                            saveError = onSave(buildProfile(), buildCredentials())
                            isSaving = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.Button_Save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.Button_Cancel)) } },
    )
}

private const val MAINNET = "mainnet"
