package ai.koog.koogelis

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform