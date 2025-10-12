package me.anno.emojipicker.gfx

import org.lwjgl.opengl.GL46C.glGetUniformLocation

class TextureColorShader : Shader(
    """
        #version 330 core
        layout(location = 0) in vec2 aPos;
        uniform vec4 bounds;
        uniform vec4 select;
        out vec2 uv;
        void main() {
            gl_Position = vec4(aPos * bounds.xy + bounds.zw, 0.0, 1.0);
            uv = vec2(aPos.x, 1.0-aPos.y) * select.xy + select.zw;
        }
    """, """
        #version 330 core
        in vec2 uv;
        out vec4 FragColor;
        uniform sampler2D tex1;
        uniform vec4 color;
        void main() {
            FragColor = texture2D(tex1, uv) * color;
        }
    """
) {
    val texture = bindTexture("tex1", 0)
    val color = glGetUniformLocation(pointer, "color")
    val bounds = glGetUniformLocation(pointer, "bounds")
    val select = glGetUniformLocation(pointer, "select")
}