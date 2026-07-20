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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
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
    private var closeLayout: FrameLayout? = null
    private var isOverCloseZoneState = mutableStateOf(false)

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
                activeChatId = ChatRepository.startChatWith("bot_speed")
            }
        }

        if (!this::bubbleLayout.isInitialized) {
            setupFloatingBubble()
        }

        return START_NOT_STICKY
    }

    private fun setupFloatingBubble() {
        bubbleLayout = FrameLayout(this).apply {
            // Bind lifecycle owners to the root container layout as well
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
        }
        
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
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        var expanded by remember { mutableStateOf(false) }
        var currentX by remember { mutableStateOf(bubbleParams.x) }
        var currentY by remember { mutableStateOf(bubbleParams.y) }
        var selectedCategory by remember { mutableStateOf("Football") }

        val activeChat = activeChatId ?: ""
        val friends by ChatRepository.friends.collectAsState()
        val currentUser by ChatRepository.currentUser.collectAsState()

        val recipient = remember(activeChat, friends) {
            val otherUid = activeChat.replace("chat_", "").split("_").firstOrNull { it != currentUser?.uid }
            friends.find { it.uid == otherUid } ?: User("bot_speed", "SpeedRunner", "speed@chatongame.com", "FC-X4M81Q", true)
        }

        val footballMessages = listOf(
            "Play fast! ⚡",
            "Gooooool! ⚽",
            "Come down! 😂",
            "Nice play! 👍",
            "Defend! 🛡️",
            "Pass the ball! 🎯",
            "Lucky win! 😉",
            "Unlucky! 💔"
        )

        val chessMessages = listOf(
            "CHECKMATE! 👑",
            "Mate! ♟️",
            "Nice move! 💡",
            "Good game! 🤝",
            "Hurry up! ⏱️",
            "Draw? 🤝"
        )

        val generalMessages = listOf(
            "Hello! 👋",
            "Playing now! 🎮",
            "Hold on... ⏳",
            "GG! 🏆"
        )

        val messagesList = when (selectedCategory) {
            "Football" -> footballMessages
            "Chess" -> chessMessages
            else -> generalMessages
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
                                onDragStart = {
                                    showCloseZone()
                                },
                                onDragEnd = {
                                    if (isOverCloseZoneState.value) {
                                        stopSelf()
                                    } else {
                                        // Snap to nearest screen edge horizontally
                                        val targetX = if (bubbleParams.x < screenWidth / 2) 0 else screenWidth - 180
                                        bubbleParams.x = targetX
                                        windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                        currentX = targetX
                                    }
                                    hideCloseZone()
                                },
                                onDragCancel = {
                                    hideCloseZone()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    bubbleParams.x = (bubbleParams.x + dragAmount.x.toInt()).coerceIn(0, screenWidth - 180)
                                    bubbleParams.y = (bubbleParams.y + dragAmount.y.toInt()).coerceIn(100, screenHeight - 300)
                                    windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                    currentX = bubbleParams.x
                                    currentY = bubbleParams.y
                                    
                                    // Check if over close zone (bottom center)
                                    val bubbleCenterX = bubbleParams.x + 90
                                    val bubbleCenterY = bubbleParams.y + 90
                                    val closeCenterX = screenWidth / 2
                                    val closeCenterY = screenHeight - 250
                                    val dist = Math.hypot((bubbleCenterX - closeCenterX).toDouble(), (bubbleCenterY - closeCenterY).toDouble())
                                    isOverCloseZoneState.value = dist < 220
                                }
                            )
                        }
                        .clickable {
                            expanded = true
                            isExpanded = true
                            // Reconfigure window params to allow focus/keyboard typing inside the overlay!
                            bubbleParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            bubbleParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            val density = resources.displayMetrics.density
                            bubbleParams.width = (330 * density).toInt()
                            bubbleParams.height = (250 * density).toInt()
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
                        .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(10.dp)
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
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (recipient.isOnline) Color(0xFF10B981) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = recipient.username,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Back to small bubble
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                expanded = false
                                isExpanded = false
                                bubbleParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                bubbleParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                bubbleParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                bubbleParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Minimize", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Category Toggle Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val categories = listOf("Football", "Chess", "General")
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B))
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when(cat) {
                                        "Football" -> "Football ⚽"
                                        "Chess" -> "Chess ♟️"
                                        else -> "General 💬"
                                    },
                                    color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 2.dp))

                    // Grid of Ready Quick Messages
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(messagesList.chunked(2)) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pair.forEach { text ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E293B))
                                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                            .clickable {
                                                ChatRepository.sendMessage(activeChat, text)
                                                // Instant minimize for seamless gaming!
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                expanded = false
                                                isExpanded = false
                                                bubbleParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                                bubbleParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                                bubbleParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                                bubbleParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                                windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                                Toast.makeText(this@FloatingBubbleService, "Sent: $text", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = text,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Message Reply Input Row (for custom typed messages)
                    var replyText by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (replyText.isEmpty()) {
                                Text("Type response...", color = Color.Gray, fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                             )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Send Button
                        FloatingActionButton(
                            onClick = {
                                if (replyText.trim().isNotEmpty()) {
                                    ChatRepository.sendMessage(activeChat, replyText.trim())
                                    replyText = ""
                                    // Minimize immediately upon sending custom typed messages
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    expanded = false
                                    isExpanded = false
                                    bubbleParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    bubbleParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                    bubbleParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                    bubbleParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                    windowManager.updateViewLayout(bubbleLayout, bubbleParams)
                                    Toast.makeText(this@FloatingBubbleService, "Message sent!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White,
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(14.dp))
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

    private fun showCloseZone() {
        if (closeLayout != null) return
        
        closeLayout = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
        }
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val closeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            300, // height of close zone container
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100 // gap from the bottom of the screen
        }
        
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setContent {
                CloseZoneUI()
            }
        }
        
        closeLayout?.addView(composeView)
        try {
            windowManager.addView(closeLayout, closeParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideCloseZone() {
        closeLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        closeLayout = null
        isOverCloseZoneState.value = false
    }

    @Composable
    fun CloseZoneUI() {
        val isOver by isOverCloseZoneState
        val scale by animateFloatAsState(targetValue = if (isOver) 1.3f else 1.0f, label = "close_scale")
        val containerColor = if (isOver) Color(0xFFEF4444) else Color(0x990F172A)
        val iconColor = Color.White
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(CircleShape)
                        .background(containerColor)
                        .border(1.5.dp, if (isOver) Color.White else Color(0xFFEF4444), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Bubble",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isOver) "Release to Close" else "Drag here to Close",
                    color = if (isOver) Color(0xFFEF4444) else Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    override fun onDestroy() {
        hideCloseZone()
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
