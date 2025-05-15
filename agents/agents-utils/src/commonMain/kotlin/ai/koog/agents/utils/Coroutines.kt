package ai.koog.code.agents.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

expect val Dispatchers.SuitableForIO: CoroutineDispatcher
