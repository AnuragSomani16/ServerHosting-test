package com.example.ChatApp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ChatApp.ui.theme.ChatAppTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement

data class ChatMessage(val sender: String, val content: String, val isMine: Boolean, val time: String, val latencyMs: Long = -1L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                    ) {
                        // Inside your setContent block, at the top of the Column:
                        var serverIp by remember { mutableStateOf("192.168.") }
                        var isConfigured by remember { mutableStateOf(false) }

                        if (!isConfigured) {
                            // Config screen
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Enter Server IP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = serverIp,
                                    onValueChange = { serverIp = it },
                                    label = { Text("e.g. 192.168.1.42") },
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { if (serverIp.isNotBlank()) isConfigured = true }) {
                                    Text("Connect")
                                }
                            }
                        } else {
                            // Chat screen (your existing split layout)
                            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                ChatPanel(
                                    title = "Client A",
                                    serverUrl = "ws://$serverIp:3001",
                                    color = Color(0xFF4FC3F7),
                                    modifier = Modifier.weight(1f)
                                )
                                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                                ChatPanel(
                                    title = "Client B",
                                    serverUrl = "ws://$serverIp:3002",
                                    color = Color(0xFFCE93D8),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPanel(title: String, serverUrl: String, color: Color, modifier: Modifier = Modifier) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val wsClient = remember {
        ChatWebSocketClient(
            url = serverUrl,
            onMessageReceived = { sender, content, latencyMs ->
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                messages.add(ChatMessage(sender, content, isMine = false, time = time, latencyMs = latencyMs))
            },
            onConnectionChanged = { connected ->
                isConnected = connected
            }
        )
    }

    LaunchedEffect(Unit) {
        wsClient.connect()
    }

    DisposableEffect(Unit) {
        onDispose { wsClient.disconnect() }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth().background(color.copy(alpha = 0.08f))) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title ${if (isConnected) "✅" else "❌"}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            items(messages) { msg ->
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = if (msg.isMine) "You: ${msg.content}" else "${msg.sender}: ${msg.content}",
                        color = if (msg.isMine) color else onSurfaceColor,
                        fontWeight = if (msg.isMine) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = if (msg.latencyMs >= 0) "${msg.time} · ${msg.latencyMs}ms" else msg.time,
                        fontSize = 10.sp,
                        color = onSurfaceColor.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        wsClient.sendMessage(inputText)
                        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        messages.add(ChatMessage("You", inputText, isMine = true, time = time))
                        inputText = ""
                        coroutineScope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Text("Send", color = Color.White)
            }
        }
    }
}

