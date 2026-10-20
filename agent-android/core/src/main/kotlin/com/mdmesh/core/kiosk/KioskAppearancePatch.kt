package com.mdmesh.core.kiosk

import com.mdmesh.proto.KioskThemeDto

/** Appearance only. Null explicitly restores a default; an absent field retains its current value. */
class KioskAppearancePatch(fields: Set<String>, private val value: KioskThemeDto) {
    private val fields = fields.toSet()
    fun valid(): Boolean = fields.isNotEmpty() && fields.all { it in setOf("backgroundColor", "textColor", "iconSize", "headerText") } &&
        listOf(value.backgroundColor, value.textColor).all { it == null || Regex("^#[a-fA-F0-9]{6}$").matches(it) } &&
        (value.iconSize == null || value.iconSize in setOf("SMALL", "MEDIUM", "LARGE")) && value.validHeader()
    fun applyTo(old: KioskThemeDto) = old.copy(
        backgroundColor = if ("backgroundColor" in fields) value.backgroundColor else old.backgroundColor,
        textColor = if ("textColor" in fields) value.textColor else old.textColor,
        iconSize = if ("iconSize" in fields) value.iconSize else old.iconSize,
        headerText = if ("headerText" in fields) value.headerText else old.headerText,
    )
}
