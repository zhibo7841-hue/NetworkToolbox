package com.networktoolbox.core.common.favorites

/** Pure Kotlin validation and precedence rules for a saved device name. */
object DeviceDisplayNameResolver {
    const val MAX_CUSTOM_NAME_CODE_POINTS = 40

    fun resolve(
        customName: String?,
        detectedName: String?,
        unknownLabel: String = "未知设备",
    ): String {
        normalizeCustomName(customName)?.let { return it }
        detectedName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return unknownLabel
    }

    fun normalizeCustomName(value: String?): String? =
        validateCustomName(value).getOrNull()

    fun validateCustomName(value: String?): Result<String> {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Custom device name must not be blank."))
        }
        if (normalized.any(Char::isISOControl)) {
            return Result.failure(IllegalArgumentException("Custom device name contains a control character."))
        }
        if (normalized.codePointCount(0, normalized.length) > MAX_CUSTOM_NAME_CODE_POINTS) {
            return Result.failure(IllegalArgumentException("Custom device name is too long."))
        }
        return Result.success(normalized)
    }
}
