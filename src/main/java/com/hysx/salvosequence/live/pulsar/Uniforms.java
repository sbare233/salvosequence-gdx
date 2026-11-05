package com.hysx.salvosequence.live.pulsar;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** GLSL 编译器会把"没用到"的 uniform 直接删掉，setUniformf 抛异常；这里统一做存在性检查。 */
final public class Uniforms {
    private Uniforms() {}

    public static void f(ShaderProgram s, String n, float v) {
        if (s.hasUniform(n)) s.setUniformf(n, v);
    }

    public static void f3(ShaderProgram s, String n, float a, float b, float c) {
        if (s.hasUniform(n)) s.setUniformf(n, a, b, c);
    }

    public static void v3(ShaderProgram s, String n, Vector3 v) {
        if (s.hasUniform(n)) s.setUniformf(n, v);
    }

    public static void m(ShaderProgram s, String n, Matrix4 v) {
        if (s.hasUniform(n)) s.setUniformMatrix(n, v);
    }

    public static void i(ShaderProgram s, String n, int v) {
        if (s.hasUniform(n)) s.setUniformi(n, v);
    }
}
