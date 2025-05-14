package ai.grazie.code.agents.core.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.Default
