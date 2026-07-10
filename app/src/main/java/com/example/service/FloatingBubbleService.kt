package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.ChatRepository
import com.example.data.Message
import com.example.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingBubbleService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "chatongame_bubble_channel"
        private const val NOTIFICATION_ID = 88291
        const val EXTRA_CHAT_ID = "extra_chat_id"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleLayout: FrameLayout
    private lateinit var bubbleParams: WindowManager.LayoutParams
    
    // Lifecycle setup for Compose inside Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    // Bubble state
    private var isExpanded = false
    private var screenWidth = 1080
    private var screenHeight = 1920

    private var lastX = 0
    private var lastY = 0
    private var activeChatId: String? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Fetch display metrics to snap correctly
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeChatId = intent?.getStringExtra(EXTRA_CHAT_ID) ?: activeChatId

        if (activeChatId == null) {
            // Find last active chat ID as fallback
            val recent = ChatRepository.recentChats.value.firstOrNull()
            if (recent != null) {
                activeChatId = recent.id
            } else {
                // If no chats exist, default to the first bot
                activeChatId = ChatRepository.startChatWith("bot_apex")
            }
        }

        if (!this::bubbleLayout.isInitialized) {
            setupFloatingBubble()
        }

        return START_NOT_STICKY
    }

    private fun setupFloatingBubble() {
        bubbleLayout = FrameLayout(this)
        
        // Window parameters
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 200
            y = screenHeight / 3
        }

        // Create ComposeView inside service container
        val composeView = ComposeView(this).apply {
            // Bind lifecycle owners for proper Jetpack Compose support
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            
            setContent {
                BubbleOverlay()
            }
        }

        bubbleLayout.addView(composeView)
        windowManager.addView(bubbleLayout, bubbleParams)
    }

    @Composable
    fun BubbleOverlay() {
        var expanded by remember { mutableStateOf(false) }
        var currentX by remember { mutableStateOf(bubbleParams.x) }
        var currentY by remember { mutableStateOf(bubbleParams.y) }

        val activeChat = activeChatId ?: ""
        val messages by ChatRepository.activeChatMessages.collectAsState()
        val friends by ChatRepository.friends.collectAsState()
        val currentUser by ChatRepository.currentUser.collectAsState()

        val recipient = remember(activeChat, friends) {
            val otherUid = activeChat.replace("chat_", "").split("_").firstOrNull { it != currentUser?.uid }
            friends.find { it.uid == otherUid } ?: User("bot_apex", "ApexLegend", "apex@chatongame.com", "FC-A7K92P", true)
        }

        // Sync messages for the active chat ID
        LaunchedEffect(activeChat) {
            if (activeChat.isNotEmpty()) {
                ChatRepository.observeChatMessages(activeChat)
            }
        }

        Box(modifier = Modifier.wrapContentSize()) {
            if (!expanded) {
                // Circular Floating Bubble
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)) // Slate 800 dark theme base
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    // Snap to nearest screen edge horizontally
                                    val targetX = if (bubbleParams.x < screenWidth / 2) 0 else screenWidth - 180
                                    bubbleParams.x = targetX
                                    windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                    currentX = targetX
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    bubbleParams.x = (bubbleParams.x + dragAmount.x.toInt()).coerceIn(0, screenWidth - 180)
                                    bubbleParams.y = (bubbleParams.y + dragAmount.y.toInt()).coerceIn(100, screenHeight - 300)
                                    windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                    currentX = bubbleParams.x
                                    currentY = bubbleParams.y
                                }
                            )
                        }
                        .clickable {
                            expanded = true
                            isExpanded = true
                            // Reconfigure window params to allow focus/keyboard typing inside the overlay!
                            bubbleParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            bubbleParams.width = (screenWidth * 0.9).toInt()
                            bubbleParams.height = (screenHeight * 0.5).toInt()
                            windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Gaming theme icon inside the bubble
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "ChatOnGame",
                        tint = Color(0xFF10B981), // Neon gaming green
                        modifier = Modifier.size(32.dp)
                    )
                    
                    // Small unread indicator badge
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
            } else {
                // Expanded Overlay Chat Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F172A)) // Slate 900 custom dark
                        .padding(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (recipient.isOnline) Color(0xFF10B981) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = recipient.username,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row {
                            // Back to small bubble
                            IconButton(
                                onClick = {
                                    expanded = false
                                    isExpanded = false
                                    bubbleParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    bubbleParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                    bubbleParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                    windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Minimize", tint = Color.LightGray)
                            }
                        }
                    }

                    // Divider
                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                    // Chat list area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Show last 3 messages to keep lightweight
                            val lastMessages = messages.takeLast(4)
                            if (lastMessages.isEmpty()) {
                                Text(
                                    "No messages yet. Send a response below!",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            } else {
                                lastMessages.forEach { msg ->
                                    val isMe = msg.senderId == currentUser?.uid
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isMe) Color(0xFF3B82F6) else Color(0xFF475569))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Message Reply Input Row
                    var replyText by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            if (replyText.isEmpty()) {
                                Text("Type response...", color = Color.Gray, fontSize = 14.sp)
                            }
                            BasicTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button
                        FloatingActionButton(
                            onClick = {
                                if (replyText.trim().isNotEmpty()) {
                                    ChatRepository.sendMessage(activeChat, replyText.trim())
                                    replyText = ""
                                }
                            },
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White,
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChatOnGame Bubble Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active overlay chat bubble channel"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatOnGame Bubble Active")
            .setContentText("The interactive chat bubble overlay is active.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        if (this::bubbleLayout.isInitialized) {
            try {
                windowManager.removeView(bubbleLayout)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        store.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
