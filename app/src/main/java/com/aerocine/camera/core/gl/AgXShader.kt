package com.aerocine.camera.core.gl

/**
 * Shader GLSL untuk transformasi kurva warna AgX Filmis dan peningkatan mikrokontras.
 * Menggantikan pemrosesan saturasi murahan bawaan pabrik dengan degradasi highlight analog yang lembut.
 */
object AgXShader {

    const val VERTEX_SHADER = """#version 300 es
        layout(location = 0) in vec4 aPosition;
        layout(location = 1) in vec4 aTexCoord;

        uniform mat4 uSTMatrix;
        out vec2 vTexCoord;

        void main() {
            gl_Position = aPosition;
            vTexCoord = (uSTMatrix * aTexCoord).xy;
        }
    """

    const val FRAGMENT_SHADER = """#version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;

        in vec2 vTexCoord;
        uniform samplerExternalOES sTexture;
        uniform vec2 uTexelSize;
        out vec4 fragColor;

        // Matriks kompresi gamut AgX Inset
        const mat3 AGX_INSET = mat3(
            0.842479062223, 0.042328242261, 0.042375654905,
            0.078433599999, 0.878468636494, 0.078433600000,
            0.079223722244, 0.079166166667, 0.879142999999
        );

        // Matriks ekspansi gamut AgX Outset
        const mat3 AGX_OUTSET = mat3(
            1.196879024258, -0.052896854902, -0.052971635544,
            -0.098020881140, 1.151903129900, -0.098043450000,
            -0.099029745564, -0.098961176471, 1.151010500000
        );

        // Kurva aproksimasi S-curve AgX (Domain Logaritmik)
        vec3 agxDefaultContrastApprox(vec3 x) {
            vec3 x2 = x * x;
            vec3 x4 = x2 * x2;
            return 15.5 * x4 * x2 - 40.14 * x4 * x + 31.96 * x4 - 6.868 * x2 * x + 0.4298 * x2 + 0.1191 * x - 0.00232;
        }

        void main() {
            // Pengambilan sampel piksel utama
            vec4 baseColor = texture(sTexture, vTexCoord);
            vec3 col = baseColor.rgb;

            // Peningkatan mikrokontras (Unsharp mask halus) tanpa artefak halo buatan
            if (uTexelSize.x > 0.0 && uTexelSize.y > 0.0) {
                vec3 blur = (
                    texture(sTexture, vTexCoord + vec2(uTexelSize.x, 0.0)).rgb +
                    texture(sTexture, vTexCoord - vec2(uTexelSize.x, 0.0)).rgb +
                    texture(sTexture, vTexCoord + vec2(0.0, uTexelSize.y)).rgb +
                    texture(sTexture, vTexCoord - vec2(0.0, uTexelSize.y)).rgb
                ) * 0.25;
                col = col + (col - blur) * 0.20; // 20% penajaman mikro optik alami
                col = max(col, vec3(0.0));
            }

            // Langkah 1: Kompresi Gamut Inset
            col = AGX_INSET * col;

            // Langkah 2: Konversi ke Domain Logaritmik (Rentang -10.0 EV hingga +6.5 EV)
            const float minEV = -10.0;
            const float maxEV = 6.5;
            col = clamp(log2(max(col, vec3(1e-5))), minEV, maxEV);
            col = (col - minEV) / (maxEV - minEV);

            // Langkah 3: Transfer Kurva Nada Filmis AgX
            col = clamp(agxDefaultContrastApprox(col), 0.0, 1.0);

            // Langkah 4: Pemulihan Gamut Outset
            col = AGX_OUTSET * col;
            col = clamp(col, 0.0, 1.0);

            fragColor = vec4(col, baseColor.a);
        }
    """
}
