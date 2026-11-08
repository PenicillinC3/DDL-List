package com.gammac3.ddlapp.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gammac3.ddlapp.R

/**
 * 顶栏入口胶囊：26dp 高、15dp 星芒图标 +「自动识别」。
 * 实心 = 悬浮球已开启；描边 = 未开启。点击始终打开设置卡，不直接切换。
 */
@Composable
fun CaptureEntryCapsule(onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { CapturePrefs.get(context) }
    val enabled by prefs.floatingBallEnabled.collectAsStateWithLifecycle()
    val connected by CaptureServiceState.connected.collectAsStateWithLifecycle()
    val running = enabled && connected

    val background = if (running) MaterialTheme.colorScheme.inverseSurface else Color.Transparent
    val content = if (running) MaterialTheme.colorScheme.inverseOnSurface
    else MaterialTheme.colorScheme.onSurface
    val border = if (running) MaterialTheme.colorScheme.inverseSurface
    else MaterialTheme.colorScheme.outline
    val stateDesc = stringResource(
        if (running) R.string.capture_state_running else R.string.capture_state_off,
    )

    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(background)
                .border(1.dp, border, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .semantics { stateDescription = stateDesc }
                .padding(start = 7.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_capture_sparkle),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(15.dp),
            )
            Text(
                stringResource(R.string.capture_entry),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
