@file:Suppress("PropertyName")

package io.github.cryolitia.stickerbot

@kotlinx.serialization.Serializable
data class TelegramResult<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    val error_code: Int? = null,
    val parameters: ResponseParameters? = null
)

@kotlinx.serialization.Serializable
data class ResponseParameters(
    val migrate_to_chat_id: Int? = null,
    val retry_after: Int? = null
)

@kotlinx.serialization.Serializable
data class StickerSet(
    val name: String,
    val title: String,
    val sticker_type: String,
    val stickers: List<Sticker>,
    val thumb: PhotoSize? = null
) {
    override fun toString(): String {
        return StringBuilder().run {
            append("name=\t")
            append(name)
            append('\n')
            append("title=\t")
            append(title)
            append('\n')
            append("sticker_type=\t")
            append(sticker_type)
            toString()
        }
    }
}

@kotlinx.serialization.Serializable
data class Sticker(
    val file_id: String,
    val file_unique_id: String,
    val type: String,
    val width: Int,
    val height: Int,
    val is_animated: Boolean,
    val is_video: Boolean,
    val thumb: PhotoSize? = null,
    val emoji: String? = null,
    val set_name: String? = null,
    val custom_emoji_id: String? = null,
    val file_size: Int? = null
) {
    override fun toString(): String {
        return StringBuilder().run {
            append("file_id=\t")
            append(file_id)
            append('\n')
            append("file_unique_id=\t")
            append(file_unique_id)
            append('\n')
            append("type=\t")
            append(type)
            append('\n')
            append("width=\t")
            append(width)
            append('\n')
            append("height=\t")
            append(height)
            append('\n')
            append("is_animated=\t")
            append(is_animated)
            append('\n')
            append("is_video=\t")
            append(is_video)
            if (emoji != null) {
                append('\n')
                append("emoji=\t")
                append(emoji)
            }
            if (set_name != null) {
                append('\n')
                append("set_name=\t")
                append(set_name)
            }
            if (custom_emoji_id != null) {
                append('\n')
                append("custom_emoji_id=\t")
                append(custom_emoji_id)
            }
            if (file_size != null) {
                append('\n')
                append("file_size=\t")
                append(file_size)
            }
            toString()
        }
    }
}

@kotlinx.serialization.Serializable
data class PhotoSize(
    val file_id: String,
    val file_unique_id: String,
    val width: Int,
    val height: Int,
    val file_size: Int? = null
)

@kotlinx.serialization.Serializable
data class TelegramFile(
    val file_id: String,
    val file_unique_id: String,
    val file_size: Int? = null,
    val file_path: String? = null
)