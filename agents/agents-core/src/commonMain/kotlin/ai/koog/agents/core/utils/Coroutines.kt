package ai.koog.core.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal expect val Dispatchers.SuitableForIO: CoroutineDispatcher
