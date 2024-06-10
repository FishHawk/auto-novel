import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode

fun koinExtensions() = listOf(
    KoinExtension(
        module = appModule,
        mode = KoinLifecycleMode.Root,
    )
)
