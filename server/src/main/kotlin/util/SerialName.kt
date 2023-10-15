package util

import kotlinx.serialization.SerialName

fun Enum<*>.serialName(): String =
    javaClass
        .getDeclaredField(name)
        .getAnnotation(SerialName::class.java)!!
        .value
