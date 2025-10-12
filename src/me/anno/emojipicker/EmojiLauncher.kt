package me.anno.emojipicker

import me.anno.emojipicker.Rendering.hasLoadedTexture
import me.anno.emojipicker.Rendering.onFinish
import me.anno.emojipicker.Rendering.renderWindow
import me.anno.emojipicker.Window.addListeners
import me.anno.emojipicker.Window.createWindow
import kotlin.concurrent.thread

val emojiSize = 36

fun main() {
    // done create a new window
    // UI:
    //  search bar = typing in everything / top
    // clicking = selecting emoji, putting into paste-history, and java.awt.Robot pasting it into the program below
    // todo show recently used emojis at the top
    // hotkey to toggle dark/light theme -> F1
    thread(name = "Loading Images") {
        Emojis.images
        hasLoadedTexture = true
    }
    val window = createWindow()
    addListeners(window)
    renderWindow(window)
    onFinish()
}