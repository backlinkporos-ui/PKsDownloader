package com.pks.downloader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage

private val Black = Color(0xFF000000)
private val Panel = Color(0xFF141414)
private val Line = Color(0xFF3A3A3A)
private val White = Color(0xFFFFFFFF)
private val Grey = Color(0xFFB3B3B3)

class MainActivity : ComponentActivity() {
    private val vm: DownloadViewModel by viewModels()
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleShare(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = White, onPrimary = Black,
                    background = Black, onBackground = White,
                    surface = Panel, onSurface = White
                )
            ) { Screen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val link = Regex("https?://\\S+").find(text)?.value ?: text
                vm.url = link
                vm.fetchInfo()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun Screen(vm: DownloadViewModel) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf<Set<Long>?>(null) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { selected = emptySet() }

    Surface(color = Black, modifier = Modifier.fillMaxSize()) {
        confirm?.let { ids ->
            var alsoFiles by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(if (ids.size == 1) "Delete this item?" else "Delete ${ids.size} items?") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { alsoFiles = !alsoFiles }
                    ) {
                        Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                        Text("Also delete file from phone")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        Downloader.remove(ids, alsoFiles)
                        selected = emptySet()
                        confirm = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
                containerColor = Panel, titleContentColor = White, textContentColor = White
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.pk_logo),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("PK'S Downloader", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedTextField(
                    value = vm.url,
                    onValueChange = { vm.url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste video link here", color = Grey) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { vm.url = it.trim() }
                        }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = White) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = White, unfocusedBorderColor = Line,
                        focusedTextColor = White, unfocusedTextColor = White, cursorColor = White
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item {
                Button(
                    onClick = { vm.fetchInfo() },
                    enabled = vm.url.isNotBlank() && !vm.fetching && !vm.downloading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
                ) {
                    if (vm.fetching) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Black, strokeWidth = 2.dp)
                    } else {
                        Text("Search", fontWeight = FontWeight.Bold)
                    }
                }
            }

            vm.meta?.let { m ->
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Panel)
                            .border(1.dp, Line, RoundedCornerShape(16.dp))
                    ) {
                        AsyncImage(
                            model = m.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF222222))
                        )
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(m.title, color = White, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(m.uploader, durationText(m.durationSec)).joinToString("  •  ")
                            if (sub.isNotEmpty()) Text(sub, color = Grey, fontSize = 13.sp)

                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Quality.values().forEach { q ->
                                    FilterChip(
                                        selected = vm.quality == q,
                                        onClick = { if (!vm.downloading) vm.quality = q },
                                        label = { Text(q.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = White,
                                            selectedLabelColor = Black,
                                            containerColor = Panel,
                                            labelColor = White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true, selected = vm.quality == q,
                                            borderColor = Line, selectedBorderColor = White
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            if (vm.downloading) {
                                LinearProgressIndicator(
                                    progress = { vm.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = White, trackColor = Line
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(vm.progress * 100).toInt()}%", color = Grey, fontSize = 13.sp)
                                    TextButton(onClick = { vm.cancel() }) { Text("Cancel", color = White) }
                                }
                            } else {
                                Button(
                                    onClick = { vm.startDownload() },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            vm.message?.let { msg ->
                item { Text(msg, color = Grey, fontSize = 14.sp) }
            }

            if (vm.history.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) {
                            IconButton(onClick = { selected = emptySet() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = White)
                            }
                            Text("${selected.size} selected", color = White, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { selected = vm.history.map { it.id }.toSet() }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = White)
                            }
                            IconButton(onClick = { confirm = selected }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = White)
                            }
                        } else {
                            Text("Downloads", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { confirm = vm.history.map { it.id }.toSet() }) {
                                Text("Clear all", color = White)
                            }
                        }
                    }
                }
                items(vm.history, key = { it.id }) { h ->
                    val isSel = h.id in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel)
                            .then(if (isSel) Modifier.border(1.dp, White, RoundedCornerShape(12.dp)) else Modifier)
                            .combinedClickable(
                                onClick = {
                                    if (selecting) {
                                        selected = if (isSel) selected - h.id else selected + h.id
                                    } else if (h.ok) {
                                        openItem(ctx, h)
                                    }
                                },
                                onLongClick = { selected = selected + h.id }
                            )
                            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                selecting && isSel -> Icons.Filled.CheckBox
                                selecting -> Icons.Filled.CheckBoxOutlineBlank
                                h.ok -> Icons.Filled.CheckCircle
                                else -> Icons.Filled.Error
                            },
                            contentDescription = null, tint = White
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                h.quality + if (h.ok) "  •  Tap to open" else "  •  Failed",
                                color = Grey, fontSize = 12.sp
                            )
                        }
                        if (!selecting) {
                            IconButton(onClick = { confirm = setOf(h.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Grey)
                            }
                        } else {
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Opens the file in the phone's default app for that file type. */
private fun openItem(ctx: Context, h: HistoryItem) {
    val u = h.uri?.let { Uri.parse(it) } ?: return
    if (!Storage.exists(ctx, u)) {
        Toast.makeText(ctx, "File not found. It may have been deleted.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(u, h.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app found to open this file.", Toast.LENGTH_SHORT).show()
    }
}

private fun durationText(sec: Int): String? {
    if (sec <= 0) return null
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
