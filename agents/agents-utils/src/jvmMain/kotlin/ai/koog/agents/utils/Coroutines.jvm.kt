package ai.koog.agents.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.IO
