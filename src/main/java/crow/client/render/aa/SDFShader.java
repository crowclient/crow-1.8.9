package crow.client.render.aa;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.IntBuffer;

/**
 * Compiled GLSL program for signed-distance-field shape rendering.
 *
 * <p>Single shader, parameterised via uniforms:
 * <ul>
 *   <li>{@code uHalfSize}    — half-extent of the shape in shape-local units (0 for circles → use uRadius)</li>
 *   <li>{@code uRadius}      — corner radius (or full radius when uHalfSize.x == uHalfSize.y == uRadius for a circle)</li>
 *   <li>{@code uOutline}     — outline thickness in shape-local units, 0 = filled</li>
 *   <li>{@code uColor}       — non-premultiplied RGBA; the shader emits premultiplied output</li>
 *   <li>{@code uAaPx}        — anti-aliasing edge softness in shape-local units (≈ pixel-to-shape-unit ratio)</li>
 * </ul>
 *
 * <p>Vertex coords come through {@code gl_Vertex.xy} (positions in screen
 * space) and {@code gl_MultiTexCoord0.xy} (shape-local coords centred on
 * (0,0)).
 */
final class SDFShader {

    private static final String VERT =
            "#version 120\n" +
            "varying vec2 vLocal;\n" +
            "void main() {\n" +
            "    vLocal = gl_MultiTexCoord0.xy;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String FRAG =
            "#version 120\n" +
            "varying vec2 vLocal;\n" +
            "uniform vec2  uHalfSize;\n" +
            "uniform float uRadius;\n" +
            "uniform float uOutline;\n" +
            "uniform vec4  uColor;\n" +
            "uniform float uAaPx;\n" +
            "void main() {\n" +
            // Rounded-rect SDF (Inigo Quilez); a circle is the same SDF with
            // halfSize == radius in both axes.
            "    vec2  d   = abs(vLocal) - uHalfSize + vec2(uRadius);\n" +
            "    float sdf = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - uRadius;\n" +
            "    float coverage;\n" +
            "    if (uOutline > 0.0) {\n" +
            "        coverage = 1.0 - smoothstep(-uAaPx, uAaPx, abs(sdf) - uOutline * 0.5);\n" +
            "    } else {\n" +
            "        coverage = 1.0 - smoothstep(-uAaPx, uAaPx, sdf);\n" +
            "    }\n" +
            "    float a = coverage * uColor.a;\n" +
            // Non-premultiplied output — composes with MC's standard
            // GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA. Premultiplied here would
            // require switching every caller's blend func, which leaks state.
            "    gl_FragColor = vec4(uColor.rgb, a);\n" +
            "}\n";

    private static int program = 0;
    private static int uHalfSize, uRadius, uOutline, uColor, uAaPx;
    private static boolean compileFailed = false;

    static int program() {
        if (program != 0) return program;
        if (compileFailed) return 0;
        try {
            int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
            int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG);
            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                compileFailed = true;
                GL20.glDeleteProgram(p);
                return 0;
            }
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            program  = p;
            uHalfSize = GL20.glGetUniformLocation(p, "uHalfSize");
            uRadius   = GL20.glGetUniformLocation(p, "uRadius");
            uOutline  = GL20.glGetUniformLocation(p, "uOutline");
            uColor    = GL20.glGetUniformLocation(p, "uColor");
            uAaPx     = GL20.glGetUniformLocation(p, "uAaPx");
            return program;
        } catch (Throwable t) {
            compileFailed = true;
            return 0;
        }
    }

    static void setUniforms(float halfW, float halfH, float radius,
                            float outline, float r, float g, float b, float a,
                            float aaPx) {
        GL20.glUniform2f(uHalfSize, halfW, halfH);
        GL20.glUniform1f(uRadius, radius);
        GL20.glUniform1f(uOutline, outline);
        GL20.glUniform4f(uColor, r, g, b, a);
        GL20.glUniform1f(uAaPx, aaPx);
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(s, 1024);
            GL20.glDeleteShader(s);
            throw new RuntimeException("SDF shader compile failed: " + log);
        }
        return s;
    }

    private SDFShader() {}
}
