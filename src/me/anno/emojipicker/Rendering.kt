package me.anno.emojipicker

import me.anno.emojipicker.Window.isDarkTheme
import me.anno.emojipicker.Window.mouseX
import me.anno.emojipicker.Window.mouseY
import me.anno.emojipicker.Window.scroll
import me.anno.emojipicker.gfx.Quad
import me.anno.emojipicker.gfx.TextureColorShader
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowShouldClose
import org.lwjgl.opengl.GL11C.GL_BLEND
import org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11C.GL_ONE
import org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11C.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP
import org.lwjgl.opengl.GL11C.glBindTexture
import org.lwjgl.opengl.GL11C.glClear
import org.lwjgl.opengl.GL11C.glClearColor
import org.lwjgl.opengl.GL11C.glDrawArrays
import org.lwjgl.opengl.GL11C.glEnable
import org.lwjgl.opengl.GL11C.glFinish
import org.lwjgl.opengl.GL11C.glGetError
import org.lwjgl.opengl.GL11C.glViewport
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL14C.GL_FUNC_ADD
import org.lwjgl.opengl.GL14C.glBlendFuncSeparate
import org.lwjgl.opengl.GL20C.glBlendEquationSeparate
import org.lwjgl.opengl.GL20C.glUniform4f
import org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30C.glBindFramebuffer
import org.lwjgl.opengl.GL30C.glBindVertexArray
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object Rendering {

    val padding = 2
    var hasLoadedTexture = false
    var lastFilter = "x"

    fun renderWindow(window: Long) {

        val texShader = TextureColorShader()
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

            if (isDarkTheme) {
                glClearColor(0.1f, 0.1f, 0.15f, 1f)
            } else {
                glClearColor(0.9f, 0.9f, 0.9f, 1f)
            }
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
                Window.selectedEmojiX = clamp(Window.selectedEmojiX, 0, numX - 1)
                Window.selectedEmojiY = clamp(Window.selectedEmojiY, 0, numY - 1)

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
                glUniform4f(texShader.color, 1f, 1f, 1f, 1f)

                gfxCheck()

                val ppix = 2f / width
                val ppiy = 2f / height

                val centerX = (width - (numX * (emojiSize + padding) + padding)) / 2

                var wasSelected = false

                val hoveredX = floor((mouseX - centerX).toFloat() / (emojiSize + padding)).toInt()
                val hoveredY = ((mouseY - padding + scroll) / (emojiSize + padding)).toInt()

                for (y in y0 until y1) {
                    for (x in 0 until numX) {

                        gfxCheck()

                        val i = x + y * numX
                        val emojiId = Window.shownEmojiIds.getOrNull(i) ?: continue

                        val emojiX = emojiId % srcNumX
                        val emojiY = emojiId / srcNumX

                        // show selected emoji
                        val isHovered = x == hoveredX && y == hoveredY
                        val isSelected = (x == Window.selectedEmojiX && y == Window.selectedEmojiY) || isHovered
                        if (wasSelected != isSelected) {
                            val color = if (isSelected) 0.7f else 1f
                            glUniform4f(texShader.color, color, color, color, 1f)
                            wasSelected = isSelected
                        }
                        if (isHovered) {
                            Window.hoveredEmoji = emojiId
                        }

                        glUniform4f(texShader.select, dx, dy, dx * emojiX, dy * emojiY)

                        val dxi = emojiSize * ppix
                        val dyi = emojiSize * ppiy
                        val posX = (x * (emojiSize + padding) + centerX)
                        val posY = (y * (emojiSize + padding) + padding - scroll)
                        val x0f = -1f + ppix * posX
                        val y0f = +1f - dyi - ppiy * posY
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
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        Thread.sleep(10)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        robot.keyPress(KeyEvent.VK_CONTROL)
        robot.keyPress(KeyEvent.VK_V)
        Thread.sleep(10)
        robot.keyRelease(KeyEvent.VK_V)
        robot.keyRelease(KeyEvent.VK_CONTROL)
    }
}