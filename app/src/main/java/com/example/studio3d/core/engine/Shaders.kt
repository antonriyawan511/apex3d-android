package com.example.studio3d.core.engine

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import android.opengl.GLES30
import android.util.Log

enum class ShadingMode(val label: String, val shortLabel: String) {
    WIREFRAME("Wireframe", "Wire"),
    CLAY("Solid Clay", "Clay"),
    MATERIAL("Material Preview", "Material"),
    STUDIO_RENDER("Studio Render", "Render")
}

class ShaderProgram(
    vertexSource: String,
    fragmentSource: String
) {
    val programId: Int

    init {
        val vShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vShader)
        GLES30.glAttachShader(programId, fShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Shader link failed: $log")
        }

        GLES30.glDeleteShader(vShader)
        GLES30.glDeleteShader(fShader)
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    fun getUniformLocation(name: String): Int = GLES30.glGetUniformLocation(programId, name)

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed ($type): $log")
        }
        return shader
    }
}

object Shaders {

    // Common Vertex Shader for 3D Geometry
    const val VERTEX_SHADER_3D = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUV;

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform mat4 uNormalMatrix;

out vec3 vWorldPos;
out vec3 vViewPos;
out vec3 vNormal;
out vec3 vViewNormal;
out vec2 vUV;

void main() {
    vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
    vWorldPos = worldPos.xyz;
    vec4 viewPos = uViewMatrix * worldPos;
    vViewPos = viewPos.xyz;
    mat3 normMat = transpose(inverse(mat3(uModelMatrix)));
    vec3 worldNormal = normalize(normMat * aNormal);
    vNormal = worldNormal;
    vViewNormal = normalize(mat3(uViewMatrix) * worldNormal);
    vUV = aUV;
    gl_Position = uProjectionMatrix * viewPos;
}
"""

    // Wireframe / Unlit Color Fragment Shader
    const val FRAGMENT_SHADER_UNLIT = """#version 300 es
precision mediump float;

uniform vec4 uColor;

out vec4 fragColor;

void main() {
    fragColor = uColor;
}
"""

    // Point Vertex Shader (Edit Mode Vertex rendering)
    const val VERTEX_SHADER_POINT = """#version 300 es
layout(location = 0) in vec3 aPosition;

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform float uPointSize;

void main() {
    vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
    gl_Position = uProjectionMatrix * uViewMatrix * worldPos;
    gl_PointSize = uPointSize;
}
"""

    // Point Fragment Shader (Smooth circular points with radiant halo and crisp outline)
    const val FRAGMENT_SHADER_POINT = """#version 300 es
precision mediump float;

uniform vec4 uColor;
uniform vec4 uHaloColor;
uniform float uHaloWidth; // 0.0 = solid circle, > 0.0 = multi-ring halo effect

out vec4 fragColor;

void main() {
    vec2 coord = gl_PointCoord - vec2(0.5);
    float dist = length(coord);
    if (dist > 0.5) {
        discard;
    }

    if (uHaloWidth > 0.0) {
        // High-contrast outer stroke (dark rim) to ensure visibility on bright meshes & backgrounds
        if (dist > 0.44) {
            fragColor = vec4(0.05, 0.05, 0.07, 1.0);
        } else if (dist > (0.5 - uHaloWidth)) {
            // Radiant Golden / Amber Halo Ring
            fragColor = uHaloColor;
        } else {
            // Vibrant Selection Core
            fragColor = uColor;
        }
    } else {
        // Subtle anti-aliased edge for unselected points
        if (dist > 0.44) {
            fragColor = vec4(uColor.rgb * 0.35, uColor.a);
        } else {
            fragColor = uColor;
        }
    }
}
"""

    // Solid / Clay Shading Shader (Form evaluation with subtle ambient occlusion rim)
    const val FRAGMENT_SHADER_CLAY = """#version 300 es
precision mediump float;

in vec3 vWorldPos;
in vec3 vViewPos;
in vec3 vNormal;
in vec3 vViewNormal;
in vec2 vUV;

uniform vec3 uCameraPos;
uniform vec4 uBaseColor;

out vec4 fragColor;

void main() {
    // Exact geometric surface normal in view space (eliminates gouraud diagonal crease & noise on flat quads)
    vec3 dX = dFdx(vViewPos);
    vec3 dY = dFdy(vViewPos);
    vec3 geoViewNormal = normalize(cross(dX, dY));
    if (geoViewNormal.z < 0.0) geoViewNormal = -geoViewNormal;

    vec3 smoothN = normalize(vViewNormal);
    if (smoothN.z < 0.0) smoothN = -smoothN;

    // Auto-smooth crease threshold: if normal deviates by > 25° from polygon face normal, use true flat normal
    float alignment = dot(geoViewNormal, smoothN);
    vec3 N = (alignment < 0.90) ? geoViewNormal : mix(geoViewNormal, smoothN, smoothstep(0.90, 0.98, alignment));

    // Key light coming from upper-right-front of screen
    vec3 lightKeyDir = normalize(vec3(0.38, 0.62, 0.68));
    // Soft fill light coming from lower-left-front of screen
    vec3 lightFillDir = normalize(vec3(-0.48, -0.25, 0.45));

    float diffKey = max(dot(N, lightKeyDir), 0.0);
    float diffFill = max(dot(N, lightFillDir), 0.0) * 0.45;
    float ambient = 0.32;

    // Specular highlight relative to view direction (0, 0, 1) in view space
    vec3 H = normalize(lightKeyDir + vec3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(N, H), 0.0), 32.0) * 0.22;

    // Rim lighting along silhouettes for crisp form readability
    float rim = pow(1.0 - clamp(N.z, 0.0, 1.0), 2.5) * 0.22;

    // Studio Clay neutral matte tone with excellent edge/crease contrast
    vec3 clayColor = vec3(0.78, 0.76, 0.73);
    vec3 finalColor = clayColor * (diffKey * 0.72 + diffFill + ambient) + vec3(spec + rim);
    fragColor = vec4(finalColor, uBaseColor.a);
}
"""

    // Material Preview Shader (Automotive paint with metallic flakes, clearcoat specular, roughness)
    const val FRAGMENT_SHADER_MATERIAL = """#version 300 es
precision mediump float;

in vec3 vWorldPos;
in vec3 vViewPos;
in vec3 vNormal;
in vec3 vViewNormal;
in vec2 vUV;

uniform vec3 uCameraPos;
uniform vec4 uBaseColor;
uniform float uMetallic;
uniform float uRoughness;
uniform float uClearcoat;
uniform int uIsSelected;
uniform sampler2D uDiffuseTexture;
uniform int uHasTexture;

out vec4 fragColor;

void main() {
    // Geometric flat normal in world space
    vec3 dX = dFdx(vWorldPos);
    vec3 dY = dFdy(vWorldPos);
    vec3 geoNormal = normalize(cross(dX, dY));
    vec3 V = normalize(uCameraPos - vWorldPos);
    if (dot(geoNormal, V) < 0.0) geoNormal = -geoNormal;

    vec3 smoothN = normalize(vNormal);
    if (dot(smoothN, V) < 0.0) smoothN = -smoothN;

    // Auto-smooth crease threshold: prevent gouraud crease pinching on hard-surface modeling
    float alignment = dot(geoNormal, smoothN);
    vec3 N = (alignment < 0.90) ? geoNormal : mix(geoNormal, smoothN, smoothstep(0.90, 0.98, alignment));

    // Balanced Neutral Studio 3-Point Lighting
    vec3 light1Dir = normalize(vec3(0.5, 0.8, 0.5));
    vec3 light1Color = vec3(1.0, 0.98, 0.95);

    vec3 light2Dir = normalize(vec3(-0.6, 0.4, -0.5));
    vec3 light2Color = vec3(0.65, 0.68, 0.72); // Neutral soft fill

    vec3 light3Dir = normalize(vec3(0.0, -0.8, 0.4)); // Soft ground bounce
    vec3 light3Color = vec3(0.25, 0.25, 0.28);

    // Diffuse
    float diff1 = max(dot(N, light1Dir), 0.0);
    float diff2 = max(dot(N, light2Dir), 0.0);
    float diff3 = max(dot(N, light3Dir), 0.0);

    // Specular (Blinn-Phong approximation for metallic clearcoat)
    vec3 H1 = normalize(light1Dir + V);
    vec3 H2 = normalize(light2Dir + V);
    float specPower = mix(128.0, 4.0, uRoughness);
    float spec1 = pow(max(dot(N, H1), 0.0), specPower);
    float spec2 = pow(max(dot(N, H2), 0.0), specPower);

    // Fresnel effect for automotive clearcoat
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float fresnel = pow(1.0 - NdotV, 4.0);

    // Base color mix with optional diffuse texture mapping
    vec3 baseColor = uBaseColor.rgb;
    float baseAlpha = uBaseColor.a;
    if (uHasTexture == 1) {
        vec4 texCol = texture(uDiffuseTexture, vUV);
        baseColor = texCol.rgb;
        baseAlpha = texCol.a;
    }

    vec3 diffuseColor = (uHasTexture == 1) ? baseColor : mix(baseColor, vec3(0.04), uMetallic);
    vec3 specColor = mix(vec3(0.04), baseColor, uMetallic);

    // Ambient term ensuring no faces or cavities are pitch black when orbiting camera
    vec3 ambientColor = mix(vec3(0.32), baseColor * 0.30, 0.5);

    vec3 color = diffuseColor * (diff1 * light1Color + diff2 * light2Color * 0.55 + diff3 * light3Color + ambientColor);
    color += specColor * (spec1 * light1Color + spec2 * light2Color * 0.5);

    // Clearcoat layer reflection
    color += vec3(fresnel * uClearcoat * 0.65);

    // Subtle Selection Outline/Gaze Tint
    if (uIsSelected == 1) {
        color = mix(color, vec3(1.0, 0.84, 0.04), fresnel * 0.25);
    }

    fragColor = vec4(color, baseAlpha);
}
"""

    // Studio Render Preview Shader (Realistic automotive showroom lighting with reflections and soft ambient occlusion)
    const val FRAGMENT_SHADER_STUDIO = """#version 300 es
precision mediump float;

in vec3 vWorldPos;
in vec3 vViewPos;
in vec3 vNormal;
in vec2 vUV;

uniform vec3 uCameraPos;
uniform vec4 uBaseColor;
uniform float uMetallic;
uniform float uRoughness;
uniform float uClearcoat;
uniform int uIsSelected;
uniform sampler2D uDiffuseTexture;
uniform int uHasTexture;

out vec4 fragColor;

void main() {
    // Geometric flat normal in world space
    vec3 dX = dFdx(vWorldPos);
    vec3 dY = dFdy(vWorldPos);
    vec3 geoNormal = normalize(cross(dX, dY));
    vec3 V = normalize(uCameraPos - vWorldPos);
    if (dot(geoNormal, V) < 0.0) geoNormal = -geoNormal;

    vec3 smoothN = normalize(vNormal);
    if (dot(smoothN, V) < 0.0) smoothN = -smoothN;

    // Auto-smooth crease threshold
    float alignment = dot(geoNormal, smoothN);
    vec3 N = (alignment < 0.90) ? geoNormal : mix(geoNormal, smoothN, smoothstep(0.90, 0.98, alignment));

    // High-end Studio Overhead Lightbox + Strip Lights
    vec3 mainLightDir = normalize(vec3(0.2, 1.0, 0.3));
    vec3 mainLightCol = vec3(1.15, 1.12, 1.08);

    vec3 leftStripDir = normalize(vec3(-1.0, 0.3, 0.0));
    vec3 leftStripCol = vec3(0.55, 0.75, 0.95); // Studio cool strip

    vec3 rightStripDir = normalize(vec3(1.0, 0.3, 0.0));
    vec3 rightStripCol = vec3(0.95, 0.70, 0.50); // Studio warm amber strip

    // Headlight fill from camera direction to illuminate interior cavities and back sides
    float dHead = max(dot(N, V), 0.0) * 0.22;

    float dMain = max(dot(N, mainLightDir), 0.0);
    float dLeft = max(dot(N, leftStripDir), 0.0);
    float dRight = max(dot(N, rightStripDir), 0.0);

    vec3 HMain = normalize(mainLightDir + V);
    vec3 HLeft = normalize(leftStripDir + V);
    vec3 HRight = normalize(rightStripDir + V);

    float specPower = mix(180.0, 8.0, uRoughness);
    float sMain = pow(max(dot(N, HMain), 0.0), specPower);
    float sLeft = pow(max(dot(N, HLeft), 0.0), specPower);
    float sRight = pow(max(dot(N, HRight), 0.0), specPower);

    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float fresnel = pow(1.0 - NdotV, 3.5);

    // Base color mix with optional diffuse texture mapping
    vec3 baseColor = uBaseColor.rgb;
    float baseAlpha = uBaseColor.a;
    if (uHasTexture == 1) {
        vec4 texCol = texture(uDiffuseTexture, vUV);
        baseColor = texCol.rgb;
        baseAlpha = texCol.a;
    }

    vec3 diffuseColor = (uHasTexture == 1) ? baseColor : mix(baseColor, vec3(0.02), uMetallic);
    vec3 specColor = mix(vec3(0.04), baseColor, uMetallic);

    vec3 diff = diffuseColor * (dMain * mainLightCol + dLeft * leftStripCol * 0.4 + dRight * rightStripCol * 0.4 + vec3(dHead + 0.32));
    vec3 spec = specColor * (sMain * mainLightCol + sLeft * leftStripCol * 0.6 + sRight * rightStripCol * 0.6);
    vec3 coat = vec3(fresnel * uClearcoat * 0.85);

    vec3 finalCol = diff + spec + coat;

    if (uIsSelected == 1) {
        finalCol = mix(finalCol, vec3(1.0, 0.84, 0.04), fresnel * 0.25);
    }

    fragColor = vec4(finalCol, baseAlpha);
}
"""

    // Procedural Screen-Space Anti-Aliased Blender Grid Shaders
    const val VERTEX_SHADER_GRID = """#version 300 es
layout(location = 0) in vec3 aPosition;

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;

out vec3 vWorldPos;

void main() {
    vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
    vWorldPos = worldPos.xyz;
    gl_Position = uProjectionMatrix * uViewMatrix * worldPos;
}
"""

    const val FRAGMENT_SHADER_GRID = """#version 300 es
precision highp float;

in vec3 vWorldPos;

uniform vec3 uCameraPos;
uniform float uMaxDistance;
uniform vec4 uSubdivColor;
uniform vec4 uMajorColor;
uniform vec4 uAxisXColor;
uniform vec4 uAxisZColor;
uniform vec4 uAxisYColor;
uniform int uGridPlane; // 0 = Floor (XZ), 1 = Front/Back (XY), 2 = Side/Left/Right (ZY)

out vec4 fragColor;

float gridLine(float coord, float stepSize, float lineWidthInPixels) {
    float wrapped = abs(fract(coord / stepSize - 0.5) - 0.5) * stepSize;
    float dCoord = fwidth(coord);
    float w = max(dCoord * lineWidthInPixels, 0.008);
    return 1.0 - smoothstep(0.0, w, wrapped);
}

float axisLine(float coord, float lineWidthInPixels) {
    float dCoord = fwidth(coord);
    float w = max(dCoord * lineWidthInPixels, 0.012);
    return 1.0 - smoothstep(0.0, w, abs(coord));
}

void main() {
    vec2 coord;
    float axH = 0.0;
    float axV = 0.0;
    vec4 axHColor;
    vec4 axVColor = (uAxisYColor.a > 0.0) ? uAxisYColor : vec4(0.24, 0.54, 0.96, 1.0); // Vibrant Blue (Y-axis)

    if (uGridPlane == 1) {
        // Front / Back Orthographic View (XY Plane)
        // coord.x = X (horizontal), coord.y = Y (vertical)
        coord = vec2(vWorldPos.x, vWorldPos.y);
        axH = axisLine(coord.y, 2.5); // Floor ground line at Y = 0 (Red)
        axV = axisLine(coord.x, 2.5); // Upright vertical axis at X = 0 (Blue)
        axHColor = uAxisXColor;
    } else if (uGridPlane == 2) {
        // Left / Right Orthographic View (ZY Plane)
        // coord.x = Z (horizontal), coord.y = Y (vertical)
        coord = vec2(vWorldPos.z, vWorldPos.y);
        axH = axisLine(coord.y, 2.5); // Floor ground line at Y = 0 (Green)
        axV = axisLine(coord.x, 2.5); // Upright vertical axis at Z = 0 (Blue)
        axHColor = uAxisZColor;
    } else {
        // Standard Floor Plane (XZ Plane)
        coord = vWorldPos.xz;
        axH = axisLine(coord.y, 2.5); // X-Axis line along Z = 0 (Red)
        axV = axisLine(coord.x, 2.5); // Z-Axis line along X = 0 (Green)
        axHColor = uAxisXColor;
        axVColor = uAxisZColor;
    }

    // Subdivided 1m grid lines (screen-space anti-aliased, 1.5px thickness)
    float subX = gridLine(coord.x, 1.0, 1.5);
    float subZ = gridLine(coord.y, 1.0, 1.5);
    float subAlpha = max(subX, subZ);

    // Major 5m grid lines (2.0px thickness)
    float majX = gridLine(coord.x, 5.0, 2.0);
    float majZ = gridLine(coord.y, 5.0, 2.0);
    float majAlpha = max(majX, majZ);

    // Distance horizon fade (only for 3D perspective floor; in orthographic views keep full crispness)
    float fade = 1.0;
    if (uGridPlane == 0) {
        float dist = length(vWorldPos.xz - uCameraPos.xz);
        float maxD = max(uMaxDistance, 30.0);
        fade = 1.0 - smoothstep(maxD * 0.55, maxD, dist);
    }

    vec4 color = vec4(0.0);

    if (subAlpha > 0.001) {
        color = vec4(uSubdivColor.rgb, subAlpha * uSubdivColor.a);
    }
    if (majAlpha > 0.001) {
        color = mix(color, vec4(uMajorColor.rgb, 1.0), majAlpha * uMajorColor.a);
    }
    if (axH > 0.001) {
        color = mix(color, vec4(axHColor.rgb, 1.0), axH);
    }
    if (axV > 0.001) {
        color = mix(color, vec4(axVColor.rgb, 1.0), axV);
    }

    color.a *= fade;

    if (color.a < 0.005) {
        discard;
    }

    fragColor = color;
}
"""

    // Viewport Dark Gray Canvas Gradient Shaders
    const val VERTEX_SHADER_BG = """#version 300 es
layout(location = 0) in vec3 aPosition;
out vec2 vPos;

void main() {
    vPos = aPosition.xy;
    gl_Position = vec4(aPosition.x, aPosition.y, 0.9999, 1.0);
}
"""

    const val FRAGMENT_SHADER_BG = """#version 300 es
precision mediump float;
in vec2 vPos;
out vec4 fragColor;

void main() {
    // Canvas Background Hex #3C3C3C (RGB 0.235, 0.235, 0.235)
    vec3 topColor = vec3(0.20, 0.20, 0.20);
    vec3 centerColor = vec3(0.235, 0.235, 0.235);
    vec3 bottomColor = vec3(0.27, 0.27, 0.27);

    float t = vPos.y * 0.5 + 0.5;
    vec3 col = (t > 0.5) ? mix(centerColor, topColor, (t - 0.5) * 1.8) : mix(bottomColor, centerColor, t * 1.8);

    // Subtle radial falloff for natural studio depth
    float r = length(vPos * vec2(0.5, 0.7));
    col = mix(col, topColor, clamp(r * 0.12, 0.0, 0.15));

    fragColor = vec4(col, 1.0);
}
"""
}
