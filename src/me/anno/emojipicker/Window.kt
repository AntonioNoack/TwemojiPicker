package me.anno.emojipicker

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
    var selectedEmojiX = 0
    var selectedEmojiY = 0
    var numX = 10

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
                    val id = selectedEmojiX + selectedEmojiY * numX
                    if (id in shownEmojiIds.indices) {
                        pasteEmoji(Emojis.getName(shownEmojiIds[id]))
                        glfwSetWindowShouldClose(window, true)
                    }
                }
                GLFW_KEY_LEFT -> selectedEmojiX--
                GLFW_KEY_RIGHT -> selectedEmojiX++
                GLFW_KEY_UP -> {
                    selectedEmojiY--
                    if (selectedEmojiY < y0 + 1) {
                        scroll -= emojiSize + padding
                    }
                }
                GLFW_KEY_DOWN -> {
                    selectedEmojiY++
                    if (selectedEmojiY > y1 - 2) {
                        scroll += emojiSize + padding
                    }
                }
            }
        }
        glfwSetCharCallback(window) { _, char ->
            input += String(Character.toChars(char)).lowercase()
            glfwSetWindowTitle(window, input.trim().ifEmpty { WINDOW_TITLE })
        }
        glfwSetCursorPosCallback(window) { _, x, y ->
            val xi = x.toInt()
            val yi = y.toInt()
            val dx = xi - mouseX
            val dy = yi - mouseY
            moved += sqrt((dx * dx + dy * dy).toFloat())
            mouseX = xi
            mouseY = yi
        }
        glfwSetMouseButtonCallback(window) { _, button, action, _ ->
            if (button == GLFW_MOUSE_BUTTON_1) {
                isLeftDown = action == GLFW_PRESS
                val now = System.nanoTime()
                if (!isLeftDown && moved < 10f && now - downTime < 500_000_000L) {
                    // click an emoji
                    val id = hoveredEmoji
                    if (id in 0 until Emojis.numEmojis) {
                        pasteEmoji(Emojis.getName(shownEmojiIds[id]))
                        glfwSetWindowShouldClose(window, true)
                    }
                }
                downTime = now
                moved = 0f
            }
        }
    }

    fun getGlobalMousePosition(): Pair<Int, Int> {
        val p = MouseInfo.getPointerInfo().location
        return p.x to p.y
    }

    var isLeftDown = false
    var moved = 0f
    var downTime = 0L

}