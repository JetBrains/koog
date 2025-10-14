package ai.koog.utils.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

public actual val Dispatchers.SuitableForIO: CoroutineDispatcher
    get() = Dispatchers.IO
