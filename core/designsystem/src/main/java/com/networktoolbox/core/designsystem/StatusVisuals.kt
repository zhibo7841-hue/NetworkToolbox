package com.networktoolbox.core.designsystem

import androidx.compose.ui.graphics.Color

enum class StatusVisualState {
    NORMAL,
    NOTICE,
    WARNING,
    ERROR,
    UNKNOWN,
    RUNNING,
    CANCELLED,
    NOT_EXECUTED,
}

data class StatusVisual(
    val label: String,
    val foregroundColor: Color,
    val containerColor: Color,
    val contentColor: Color,
)

object NetworkToolboxStatusVisuals {
    fun resolve(state: StatusVisualState, darkTheme: Boolean): StatusVisual {
        val colors = NetworkToolboxColors
        return when (state) {
            StatusVisualState.NORMAL -> StatusVisual(
                label = "正常",
                foregroundColor = if (darkTheme) colors.DarkSuccess else colors.LightSuccess,
                containerColor = if (darkTheme) Color(0xFF143D31) else Color(0xFFD7F5E8),
                contentColor = if (darkTheme) Color(0xFFD4F9E7) else Color(0xFF0B3A29),
            )

            StatusVisualState.NOTICE,
            StatusVisualState.WARNING,
            -> StatusVisual(
                label = if (state == StatusVisualState.NOTICE) "提示" else "异常",
                foregroundColor = if (darkTheme) colors.DarkNotice else colors.LightNotice,
                containerColor = if (darkTheme) Color(0xFF403617) else Color(0xFFFFF1C2),
                contentColor = if (darkTheme) Color(0xFFFFE8A6) else Color(0xFF4D3800),
            )

            StatusVisualState.ERROR -> StatusVisual(
                label = "严重异常",
                foregroundColor = if (darkTheme) colors.DarkError else colors.LightError,
                containerColor = if (darkTheme) Color(0xFF491D28) else Color(0xFFFFDAD6),
                contentColor = if (darkTheme) Color(0xFFFFDADF) else Color(0xFF601410),
            )

            StatusVisualState.UNKNOWN -> StatusVisual(
                label = "未确定",
                foregroundColor = if (darkTheme) colors.DarkUnknown else colors.LightUnknown,
                containerColor = if (darkTheme) Color(0xFF283444) else Color(0xFFE7ECF3),
                contentColor = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF273445),
            )

            StatusVisualState.RUNNING -> StatusVisual(
                label = "进行中",
                foregroundColor = if (darkTheme) colors.DarkPrimary else colors.LightPrimary,
                containerColor = if (darkTheme) Color(0xFF1A3867) else Color(0xFFDCE8FF),
                contentColor = if (darkTheme) Color(0xFFD9E7FF) else Color(0xFF0D2A63),
            )

            StatusVisualState.CANCELLED -> StatusVisual(
                label = "已停止",
                foregroundColor = if (darkTheme) colors.DarkUnknown else colors.LightUnknown,
                containerColor = if (darkTheme) Color(0xFF283444) else Color(0xFFE7ECF3),
                contentColor = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF273445),
            )

            StatusVisualState.NOT_EXECUTED -> StatusVisual(
                label = "未执行",
                foregroundColor = if (darkTheme) colors.DarkUnknown else colors.LightUnknown,
                containerColor = if (darkTheme) Color(0xFF283444) else Color(0xFFE7ECF3),
                contentColor = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF273445),
            )
        }
    }
}
