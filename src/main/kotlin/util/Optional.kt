package util

fun <T : Any> T?.toOptional(): Optional<T> = if (this == null) None else Some(this)

data class Some<out T>(val value: T) : Optional<T>()

object None : Optional<Nothing>() {
    override fun toString(): String {
        return "None"
    }
}

sealed class Optional<out T> {
    inline fun ifSome(action: (T) -> Unit): Optional<T> {
        return when (this) {
            is Some -> apply { action(value) }
            is None -> this
        }
    }

    inline fun ifNone(action: () -> Unit): Optional<T> {
        return when (this) {
            is Some -> this
            is None -> apply { action() }
        }
    }
}