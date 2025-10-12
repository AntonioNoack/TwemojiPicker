package me.anno.emojipicker

import me.anno.emojipicker.Rendering.clampSelection
import me.anno.emojipicker.Rendering.getSelectedEmojiId
import me.anno.emojipicker.Rendering.gfxCheck
import me.anno.emojipicker.Rendering.padding
import me.anno.emojipicker.Rendering.pasteEmoji
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL46C
import java.awt.MouseInfo
import java.io.File
import kotlin.math.sqrt

object Window {

    const val WINDOW_TITLE = "Twemoji Picker"
    const val NULL = 0L

    var scroll = 0f
    var mouseX = 0
    var mouseY = 0

    var input = ""

    val lightThemeFile = File("./UseLightTheme.txt")

    var isDarkTheme = !lightThemeFile.exists()

    var shownEmojiIds = listOf(0)
    var keyboardSelectedX = 0
    var keyboardSelectedY = 0
    var numX = 10
    var numY = 10

    var hoveredEmoji = 0

    var y0 = 0
    var y1 = 10

    fun createWindow(): Long {
        // Initialize GLFW
        if (!glfwInit()) error("Failed to init GLFW")
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

        val width = 400
        val height = 300
        val window = glfwCreateWindow(width, height, "LWJGL Quad Example", NULL, NULL)
        if (window == NULL) error("Failed to create GLFW window")

        // Center the window at cursor
        val (mouseX, mouseY) = getGlobalMousePosition()
        val windowX = mouseX - width.shr(1)
        val windowY = mouseY - height.shr(1)
        glfwSetWindowPos(window, windowX, windowY)

        glfwSetWindowTitle(window, WINDOW_TITLE)

        glfwMakeContextCurrent(window)
        glfwSwapInterval(1) // vsync
        glfwShowWindow(window)
        GL.createCapabilities()

        gfxCheck()

        println("OpenGL Version: ${GL46C.glGetString(GL46C.GL_VERSION)}")
        println("GLSL Version: ${GL46C.glGetString(GL46C.GL_SHADING_LANGUAGE_VERSION)}")
        println("GPU: ${GL46C.glGetString(GL46C.GL_RENDERER)}, Vendor: ${GL46C.glGetString(GL46C.GL_VENDOR)}")

        gfxCheck()

        return window
    }

    fun addListeners(window: Long) {
        glfwSetScrollCallback(window) { _, _, dy ->
            scroll -= dy.toFloat() * 20f
        }
        glfwSetKeyCallback(window) { _, key, _, action, _ ->
            // action: GLFW_PRESS, GLFW_RELEASE or GLFW_REPEAT
            // key: The keyboard key that was pressed or released.
            val pressed = action == GLFW_PRESS
            val typed = pressed || action == GLFW_REPEAT
            if (typed) when (key) {
                GLFW_KEY_ESCAPE -> {
                    glfwSetWindowShouldClose(window, true)
                }
                GLFW_KEY_BACKSPACE -> if (input.isNotEmpty()) {
                    input = input.substring(0, input.lastIndex)
                    glfwSetWindowTitle(window, input.trim().ifEmpty { WINDOW_TITLE })
                }
                GLFW_KEY_F1 -> if (pressed) {
                    isDarkTheme = !isDarkTheme
                    if (isDarkTheme) lightThemeFile.delete()
                    else lightThemeFile.createNewFile()
                }
                GLFW_KEY_ENTER -> {
                    val id = getSelectedEmojiId()
                    if (id >= 0) {
                        pasteEmoji(Emojis.getName(id))
                        glfwSetWindowShouldClose(window, true)
                    }
                }
                GLFW_KEY_LEFT,
                GLFW_KEY_RIGHT,
                GLFW_KEY_UP,
                GLFW_KEY_DOWN -> {
                    val prev = keyboardSelectedY
                    when (key) {
                        GLFW_KEY_LEFT -> {
                            keyboardSelectedX--
                            if (keyboardSelectedX < 0 && keyboardSelectedY > 0) {
                                keyboardSelectedY--
                                keyboardSelectedX += numX
                            }
                        }
                        GLFW_KEY_RIGHT -> {
                            keyboardSelectedX++
                            if (keyboardSelectedX >= numX && keyboardSelectedY + 1 < numY) {
                                keyboardSelectedX -= numX
                                keyboardSelectedY++
                            }
                        }
                        GLFW_KEY_UP -> keyboardSelectedY = posMod(prev - 1, numY)
                        GLFW_KEY_DOWN -> keyboardSelectedY = posMod(prev + 1, numY)
                    }
                    scroll = ((emojiSize + padding) * (keyboardSelectedY - (y1 - y0) * 0.5f) + padding)
                    movedSinceKeyboardKeys = 0f
                    updateSelectedTitle(window)
                }
            }
        }
        glfwSetCharCallback(window) { _, char ->
            input += String(Character.toChars(char)).lowercase()
            movedSinceKeyboardKeys = 0f
            glfwSetWindowTitle(window, input.trim().ifEmpty { WINDOW_TITLE })
        }
        glfwSetCursorPosCallback(window) { _, x, y ->
            val xi = x.toInt()
            val yi = y.toInt()
            val dx = xi - mouseX
            val dy = yi - mouseY
            val distance = sqrt((dx * dx + dy * dy).toFloat())
            movedSinceLeftPress += distance
            movedSinceKeyboardKeys += distance
            mouseX = xi
            mouseY = yi
            if (isLeftDown) {
                scroll -= dy
            }
        }
        glfwSetMouseButtonCallback(window) { _, button, action, _ ->
            if (button == GLFW_MOUSE_BUTTON_1) {
                isLeftDown = action == GLFW_PRESS
                val now = System.nanoTime()
                if (!isLeftDown && movedSinceLeftPress < 10f && now - downTime < 500_000_000L) {
                    // click an emoji
                    val id = hoveredEmoji
                    if (id >= 0) {
                        pasteEmoji(Emojis.getName(id))
                        glfwSetWindowShouldClose(window, true)
                    }
                }
                downTime = now
                movedSinceLeftPress = 0f
            }
        }
    }

    fun updateSelectedTitle(window: Long) {
        clampSelection()
        val id = getSelectedEmojiId()
        if (id >= 0) {
            glfwSetWindowTitle(window, Emojis.getDesc(id))
        }
    }

    fun posMod(i: Int, m: Int): Int {
        return if (m <= 0) i else {
            val mod = i % m
            if (mod < 0) i + m else i
        }
    }

    fun getGlobalMousePosition(): Pair<Int, Int> {
        val p = MouseInfo.getPointerInfo().location
        return p.x to p.y
    }

    var isLeftDown = false
    var movedSinceLeftPress = 0f
    var downTime = 0L
    var movedSinceKeyboardKeys = 0f

}