package com.gammac3.ddlapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 编辑器面板和截屏浮卡共用的输入框和选项样式。

/** 输入框的纸角：近乎直角的小圆，像铅笔框出来的格子。 */
val FieldShape = RoundedCornerShape(4.dp)

/** 透明底 + 1dp 细边；聚焦时细线转陶土，像笔尖落纸。 */
@Composable
fun paperFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)

/** 选中时填充墨色，未选中时显示细描边。 */
@Composable
fun StampChip(
    text: String,
    selected: Boolean,
    shape: Shape = CircleShape,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
                shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontFamily = UiFont,
            fontSize = 12.sp,
            letterSpacing = 0.3.sp,
            color = if (selected) MaterialTheme.colorScheme.inverseOnSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 小标题：字距拉开的小号标签，配一条收尾细线（章节感）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = UiFont,
        fontSize = 10.5.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
