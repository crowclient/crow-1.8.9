package crow.client.utils.font.msdf;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class MsdfShader {

    private static int program = -1;
    private static int uAtlas      = -1;
    private static int uColor      = -1;
    private static int uPxRange    = -1;

    private MsdfShader() {}

    public static int program()  { return program; }
    public static int uAtlas()   { return uAtlas; }
    public static int uColor()   { return uColor; }
    public static int uPxRange() { return uPxRange; }

    public static void setup() {
        if (program == -2 || program > 0) return;
        try {
            int p = GL20.glCreateProgram();
            if (p == 0) { program = -2; return; }

            String vert =
                "#version 120\n" +

                "void main() {\n" +
                "  gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                "}\n";

            String frag =
                "#version 120\n" +
                "uniform sampler2D uAtlas;\n" +
                "uniform vec4 uColor;\n" +
                "uniform float uPxRange;\n" +
                "float median(float r, float g, float b) {\n" +
                "  return max(min(r, g), min(max(r, g), b));\n" +
                "}\n" +
                "void main() {\n" +
                "  vec3 msd = texture2D(uAtlas, gl_TexCoord[0].st).rgb;\n" +
                "  float sd = median(msd.r, msd.g, msd.b);\n" +
                "  float screenPxDistance = uPxRange * (sd - 0.5);\n" +
                "  float alpha = clamp(screenPxDistance + 0.5, 0.0, 1.0);\n" +
                "  gl_FragColor = vec4(uColor.rgb, uColor.a * alpha);\n" +
                "}\n";

            int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(v, vert);
            GL20.glCompileShader(v);
            if (GL20.glGetShaderi(v, GL20.GL_COMPILE_STATUS) == 0) {
                program = -2; return;
            }
            GL20.glAttachShader(p, v);

            int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(f, frag);
            GL20.glCompileShader(f);
            if (GL20.glGetShaderi(f, GL20.GL_COMPILE_STATUS) == 0) {
                program = -2; return;
            }
            GL20.glAttachShader(p, f);

            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
                program = -2; return;
            }

            program  = p;
            uAtlas   = GL20.glGetUniformLocation(p, "uAtlas");
            uColor   = GL20.glGetUniformLocation(p, "uColor");
            uPxRange = GL20.glGetUniformLocation(p, "uPxRange");
        } catch (Throwable t) {
            program = -2;
        }
    }

    public static boolean isReady() {
        setup();
        return program > 0;
    }
}
