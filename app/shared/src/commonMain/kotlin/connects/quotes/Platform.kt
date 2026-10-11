package connects.quotes

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform