package app.streamshield.blocking

/** Kept by R8; names and static signatures must match the JNI exports. */
object NativeBridge {
    init { System.loadLibrary("streamshield_adblock") }
    @JvmStatic external fun replaceRules(rules: String): Boolean
    @JvmStatic external fun shouldBlock(url: String, source: String, kind: String, method: String): Boolean
    @JvmStatic external fun cosmeticSelectors(url: String): String
}
