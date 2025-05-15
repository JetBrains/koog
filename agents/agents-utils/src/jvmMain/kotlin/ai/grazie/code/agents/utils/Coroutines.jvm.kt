package ai.grazie.code.agents.utils.ai.grazie.code.agents.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.IO
