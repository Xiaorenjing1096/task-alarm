package com.appalarm.ui.ringtone

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val SYSTEM_DEFAULT_URI = ""

/**
 * 铃声选择对话框。
 *
 * 三条来源并列，是因为它们互有短板：系统铃声不用授权但未必好听；媒体库方便但要
 * 读媒体权限；SAF 万能但要记住 URI 授权。与其替用户选，不如都摆出来。
 */
@Composable
fun RingtonePickerDialog(
    currentUri: String?,
    onDismiss: () -> Unit,
    onPicked: (RingtoneOption) -> Unit,
) {
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf(currentUri ?: SYSTEM_DEFAULT_URI) }
    var showDeviceAudio by remember { mutableStateOf(false) }

    val systemOptions = remember { RingtoneSupport.systemAlarmOptions(context) }
    val deviceOptions = remember(showDeviceAudio) {
        if (showDeviceAudio) RingtoneSupport.deviceAudioOptions(context) else emptyList()
    }

    // SAF 选文件：不需要任何权限，但要持久化 URI 授权，否则重启后铃声就失效了。
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPicked(RingtoneOption(uri.toString(), uri.lastPathSegment ?: "所选音频"))
        }
    }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showDeviceAudio = true }

    val options = buildList {
        if (showDeviceAudio) {
            addAll(deviceOptions)
        } else {
            addAll(systemOptions)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择铃声") },
        text = {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    LazyColumn {
                        item {
                            RingtoneRow(
                                title = "跟随系统默认闹钟铃声",
                                selected = selectedUri == SYSTEM_DEFAULT_URI,
                                onClick = { selectedUri = SYSTEM_DEFAULT_URI },
                            )
                        }
                        items(options, key = { it.uri }) { option ->
                            RingtoneRow(
                                title = option.title,
                                selected = selectedUri == option.uri,
                                onClick = { selectedUri = option.uri },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = when {
                        selectedUri == SYSTEM_DEFAULT_URI ->
                            RingtoneOption(SYSTEM_DEFAULT_URI, "系统默认")

                        else -> options.firstOrNull { it.uri == selectedUri }
                            ?: RingtoneOption(selectedUri, selectedUri)
                    }
                    onPicked(picked)
                },
            ) { Text("确定") }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(
                    onClick = {
                        if (showDeviceAudio) {
                            showDeviceAudio = false
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            audioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            audioPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    },
                ) { Text(if (showDeviceAudio) "看系统铃声" else "看本机音乐") }

                TextButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                    Text("从文件选择")
                }

                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun RingtoneRow(title: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
