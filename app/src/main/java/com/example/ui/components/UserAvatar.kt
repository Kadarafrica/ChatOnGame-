package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R

@Composable
fun UserAvatar(
    avatarUrl: String?,
    username: String = "",
    size: Dp = 44.dp,
    showOnlineStatus: Boolean = false,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF334155))
                .border(1.dp, Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                avatarUrl == "res:chess_king" -> {
                    Image(
                        painter = painterResource(id = R.drawable.chess_king_avatar_1784903973722),
                        contentDescription = "Chess King Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                avatarUrl == "res:football_star" -> {
                    Image(
                        painter = painterResource(id = R.drawable.football_star_avatar_1784903988451),
                        contentDescription = "Football Star Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                !avatarUrl.isNullOrEmpty() -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "User Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                username.isNotEmpty() -> {
                    Text(
                        text = username.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.4).sp
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = Color.LightGray,
                        modifier = Modifier.size(size * 0.6f)
                    )
                }
            }
        }

        if (showOnlineStatus) {
            val statusSize = size * 0.28f
            Box(
                modifier = Modifier
                    .size(statusSize)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF10B981) else Color.Gray)
                    .border(1.5.dp, Color(0xFF0F172A), CircleShape)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
