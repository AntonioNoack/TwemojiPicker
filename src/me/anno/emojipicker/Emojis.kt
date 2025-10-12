package me.anno.emojipicker

import me.anno.emojipicker.gfx.PixelData
import me.anno.emojipicker.gfx.Texture
import javax.imageio.ImageIO

object Emojis {

    val images: PixelData
    private val names: List<String> // name, desc, name, desc, ...

    init {
        val self = Emojis::class.java
        images = self.getResourceAsStream("/Emojis.png")!!.use { stream ->
            ImageIO.read(stream)
        }.run { PixelData(this) }
        names = self.getResourceAsStream("/Emojis.txt")!!.use { stream ->
            stream.readBytes().decodeToString().split('\n')
        }
    }

    fun getName(i: Int) = names[i * 2]
    fun getDesc(i: Int) = names[i * 2 + 1]

    val numEmojis get() = names.size shr 1

    val texture = lazy {
        Texture(images)
    }

}