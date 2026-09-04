package com.networktoolbox.core.common.diagnostic

internal fun requireBoundedText(value: String, fieldName: String, maxLength: Int) {
    require(value.isNotBlank()) { "$fieldName must not be blank." }
    require(value.length <= maxLength) { "$fieldName is too long." }
}
internal fun requireBoundedList(size: Int, fieldName: String, maxSize: Int) {
    require(size <= maxSize) { "$fieldName contains too many entries." }
}
