package me.anno.emojipicker

import me.anno.emojipicker.Window.isDarkTheme
import me.anno.emojipicker.Window.keyboardSelectedX
import me.anno.emojipicker.Window.keyboardSelectedY
import me.anno.emojipicker.Window.mouseX
import me.anno.emojipicker.Window.mouseY
import me.anno.emojipicker.Window.numX
import me.anno.emojipicker.Window.numY
import me.anno.emojipicker.Window.scroll
import me.anno.emojipicker.Window.shownEmojiIds
import me.anno.emojipicker.gfx.FlatColorShader
import me.anno.emojipicker.gfx.Quad
import me.anno.emojipicker.gfx.TextureColorShader
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL46C.*
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object Rendering {

    class Color(val r: Float, val g: Float, val b: Float)

    val bright = Color(0.9f, 0.9f, 0.9f)
    val dark = Color(0.1f, 0.1f, 0.15f)

    val padding = 2
    var hasLoadedTexture = false
    var lastFilter = "x"

    fun renderWindow(window: Long) {

        val texShader = TextureColorShader()
        val flatShader = FlatColorShader()
        val quad = Quad()

        val widthI = IntArray(1)
        val heightI = IntArray(1)

        // Main loop
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            glfwGetWindowSize(window, widthI, heightI)

            val width = widthI[0]
            val height = heightI[0]
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, width, height)

            val bgColor = if (isDarkTheme) dark else bright
            glClearColor(bgColor.r, bgColor.g, bgColor.b, 1f)
            glClear(GL_COLOR_BUFFER_BIT)

            glEnable(GL_BLEND)
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD)
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            glBindVertexArray(quad.vao)

            if (hasLoadedTexture) {

                val tex = Emojis.texture.value
                val srcNumX = tex.width / emojiSize
                val srcNumY = tex.height / emojiSize

                val filter = Window.input
                if (filter != lastFilter) {
                    val parts = filter
                        .replace(',', ' ')
                        .split(' ')
                        .filter { it.isNotEmpty() }
                    Window.shownEmojiIds = (0 until Emojis.numEmojis).filter { emojiId ->
                        // todo better filter/similarity
                        val desc = Emojis.getDesc(emojiId)
                        parts.all { part -> desc.contains(part) }
                    }
                    lastFilter = filter
                }

                val numX = (width - padding) / (emojiSize + padding)
                val numY = (Window.shownEmojiIds.size + numX - 1) / numX
                Window.numX = numX
                Window.numY = numY
                clampSelection()

                // val usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                // println("%.1f MiB".format((usedMemory / (1024f * 1024f))))

                val totalHeight = emojiSize * numY + (numY + 1) * padding
                scroll = clamp(scroll, 0f, max(totalHeight - height, 0).toFloat())

                val y0 = max((0f + scroll) / (emojiSize + padding), 0f).toInt()
                val y1 = min((height - padding + scroll) / (emojiSize + padding) + 1, numY.toFloat()).toInt()
                Window.y0 = y0
                Window.y1 = y1

                val dx = 1f / srcNumX
                val dy = 1f / srcNumY

                texShader.use()
                glActiveTexture(texShader.texture)
                glBindTexture(GL_TEXTURE_2D, tex.pointer)

                gfxCheck()

                val ppix = 2f / width
                val ppiy = 2f / height

                val centerX = (width - (numX * (emojiSize + padding) + padding)) / 2

                val mouseHoveredX = floor((mouseX - centerX).toFloat() / (emojiSize + padding)).toInt()
                val mouseHoveredY = ((mouseY - padding + scroll) / (emojiSize + padding)).toInt()
                val isHovering = Window.movedSinceKeyboardKeys > 10f

                for (y in y0 until y1) {
                    for (x in 0 until numX) {

                        gfxCheck()

                        val i = x + y * numX
                        val emojiId = shownEmojiIds.getOrNull(i) ?: continue

                        val emojiX = emojiId % srcNumX
                        val emojiY = emojiId / srcNumX

                        val dxi = emojiSize * ppix
                        val dyi = emojiSize * ppiy
                        val posX = (x * (emojiSize + padding) + centerX)
                        val posY = (y * (emojiSize + padding) + padding - scroll)
                        val x0f = -1f + ppix * posX
                        val y0f = +1f - dyi - ppiy * posY

                        // show selected emoji
                        val isMouseHovered = x == mouseHoveredX && y == mouseHoveredY
                        val isKeyboardSelected = x == keyboardSelectedX && y == keyboardSelectedY
                        if (if (isHovering) isMouseHovered else isKeyboardSelected) {
                            // show border around hovered/selected element
                            flatShader.use()

                            val invColor = if (isDarkTheme) bright else dark
                            glUniform4f(flatShader.color, invColor.r, invColor.g, invColor.b, 1f)
                            glUniform4f(
                                flatShader.bounds,
                                dxi + 2f * padding * ppix, dyi + 2f * padding * ppiy,
                                x0f - padding * ppix, y0f - padding * ppiy
                            )
                            drawQuad()

                            glUniform4f(flatShader.color, bgColor.r, bgColor.g, bgColor.b, 1f)
                            glUniform4f(flatShader.bounds, dxi, dyi, x0f, y0f)
                            drawQuad()

                            texShader.use()
                        }
                        if (isMouseHovered && isHovering) {
                            keyboardSelectedX = x
                            keyboardSelectedY = y
                            if (Window.hoveredEmoji != emojiId) {
                                Window.hoveredEmoji = emojiId
                                glfwSetWindowTitle(window, Emojis.getDesc(emojiId))
                            }
                        }

                        glUniform4f(texShader.select, dx, dy, dx * emojiX, dy * emojiY)
                        glUniform4f(texShader.bounds, dxi, dyi, x0f, y0f)
                        drawQuad()

                        gfxCheck()

                    }
                }
            }

            glFinish()
            glfwSwapBuffers(window)

        }

        if (hasLoadedTexture && Emojis.texture.isInitialized()) {
            Emojis.texture.value.destroy()
        }

        // Cleanup
        texShader.destroy()
        quad.destroy()
        glfwDestroyWindow(window)
        glfwTerminate()
    }

    fun gfxCheck() {
        // enable this if you suspect OpenGL abuse
        if (false) {
            val error = glGetError()
            if (error != 0) throw IllegalStateException("Error! $error")
        }
    }

    fun drawQuad() {
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    fun clamp(x: Float, min: Float, max: Float): Float {
        return if (x < min) min else if (x < max) x else max
    }

    fun clamp(x: Int, min: Int, max: Int): Int {
        return if (x < min) min else if (x < max) x else max
    }

    fun pasteEmoji(emoji: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(emoji), null)
        shouldPaste = true
    }

    var shouldPaste = false
    fun onFinish() {
        if (!shouldPaste) return
        val robot = Robot()
        robot.keyPress(KeyEvent.VK_CONTROL)
        robot.keyPress(KeyEvent.VK_V)
        Thread.sleep(10)
        robot.keyRelease(KeyEvent.VK_V)
        robot.keyRelease(KeyEvent.VK_CONTROL)
    }

    fun clampSelection() {
        keyboardSelectedX = clamp(keyboardSelectedX, 0, numX - 1)
        keyboardSelectedY = clamp(keyboardSelectedY, 0, numY - 1)
    }

    fun getSelectedEmojiId(): Int {
        val index = keyboardSelectedX + keyboardSelectedY * numX
        return shownEmojiIds.getOrNull(index) ?: -1
    }

}