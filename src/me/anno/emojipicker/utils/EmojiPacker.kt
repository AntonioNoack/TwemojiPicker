package me.anno.emojipicker.utils

import me.anno.emojipicker.emojiSize
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 *  Download folder expects a folder with the unpacked PNG icons from twemoji, named 72x72
 *  and emoji-test.txt from https://unicode.org/Public/emoji/latest/,
 *  and then this script creates Emoji.txt and Emoji.png from them, which can then be put in the assets folder.
 *
 *  If you want to change the size of each emoji, change emojiSize in EmojiLauncher.kt.
 * */
fun main() {

    val home = File(System.getProperty("user.home"))
    val downloads = File(home, "Downloads")

    val src = File(downloads, "72x72").listFiles()!!
        .filter { it.extension == "png" }
        .sortedBy { it.name }

    val numImages = src.size
    val numX = 64 // ~ sqrt(numImages)
    val numY = (numImages + numX - 1) / numX
    val dstSize = emojiSize

    val srcNames = File(downloads, "emoji-test.txt")
        .readText().lowercase().split('\n')
        .filter { !it.startsWith("#") && it.isNotBlank() }
        .associate {
            // 1F600       ; fully-qualified     # 😀 E1.0
            val i0 = it.indexOf(';')
            val i1 = it.indexOf('.', i0) + 2
            val codes = it.substring(0, i0).trim().replace(' ', '-')
            val name = it.substring(i1).trim()
            codes to name
        }

    val names = StringBuilder(src.sumOf { it.name.length })
    val image = BufferedImage(numX * dstSize, numY * dstSize, BufferedImage.TYPE_INT_ARGB)

    val dstFile1 = File(downloads, "Emojis.png")
    val dstFile2 = File(downloads, "Emojis.txt")

    val gfx = image.graphics
    (gfx as? Graphics2D)
        ?.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

    var i = 0
    for (file in src) {
        val name = file.nameWithoutExtension
        val description = srcNames[name] ?: continue

        val srcImage = ImageIO.read(file)
        check(srcImage.width == srcImage.height)

        val xi = (i % numX) * dstSize
        val yi = (i / numX) * dstSize
        gfx.drawImage(
            srcImage, xi, yi,
            dstSize, dstSize, null
        )

        val emojiStr = name.split('-').joinToString("") {
            String(Character.toChars(it.toInt(16)))
        }
        names.append(emojiStr).append('\n')
        names.append(description).append('\n')
        i++
    }

    gfx.dispose()

    ImageIO.write(image, "png", dstFile1)
    dstFile2.writeText(names.toString())
}