package data

enum class AspectRatio(val textValue: String, val width: Int, val height: Int) {
    RATIO_16_9("16:9", 640, 360),
    RATIO_9_16("9:16", 360, 640),
    RATIO_1_1("1:1", 512, 512);

    companion object {
        fun fromValue(text: String): AspectRatio? {
            return values().find { it.textValue == text }
        }
    }
}