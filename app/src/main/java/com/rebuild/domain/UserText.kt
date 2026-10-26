package com.rebuild.domain

fun limitUserText(value: String, maxCodeUnits: Int): String {
    require(maxCodeUnits >= 0)
    if (value.length <= maxCodeUnits) return value
    val end = if (
        maxCodeUnits > 0 &&
        value[maxCodeUnits - 1].isHighSurrogate() &&
        value[maxCodeUnits].isLowSurrogate()
    ) maxCodeUnits - 1 else maxCodeUnits
    return value.substring(0, end)
}

fun isWellFormedUtf16(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char.isHighSurrogate()) {
            if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
            index++
        } else if (char.isLowSurrogate()) {
            return false
        }
        index++
    }
    return true
}
