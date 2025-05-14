package ai.grazie.code.agents.core.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * It would use IO on JVM and Default on JS
 */
internal actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.IO
