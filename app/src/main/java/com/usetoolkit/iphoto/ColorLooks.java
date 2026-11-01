package com.usetoolkit.iphoto;

import android.graphics.Color;
import androidx.media3.effect.SingleColorLut;

/**
 * Creative looks inspired by six supplied night/hotel images. Values are aesthetic
 * starting points, not camera-profile calibration (source images lack a matched
 * Galaxy reference / scene-referred color chart).
 *
 * The same three-dimensional LUT is consumed by Media3 for Preview, ImageCapture,
 * and VideoCapture, avoiding the common problem of preview-only camera filters.
 */
public final class ColorLooks {
    public enum Look {
        LUXURY("Luxury Night", "딥 블랙 · 웜 라이트"),
        SOFT("Soft Hotel", "부드러운 조명 · 자연스러운 피부"),
        CINEMA("City Noir", "차가운 야경 · 깊은 대비"),
        CANON50("Canon R5 II · 50mm", "자연스러운 피부톤 · 부드러운 하이라이트"),
        ORIGINAL("Original", "효과 없는 원본");
        public final String label;
        public final String hint;
        Look(String label, String hint) { this.label = label; this.hint = hint; }
    }

    public enum Scene {
        AUTO("자동 · 노출 기반"), DAYLIGHT("밝은 장면"),
        NIGHT("야경"), PORTRAIT("인물");
        public final String label;
        Scene(String label){this.label=label;}
    }
    private ColorLooks() {}

    public static SingleColorLut generate(Look look, float strength) {
        return generate(look,strength,Scene.DAYLIGHT);
    }
    public static SingleColorLut generate(Look look, float strength, Scene scene) {
        final int n = 33;
        final float t = clip(strength, 0f, 1f);
        int[][][] cube = new int[n][n][n];
        for (int ri = 0; ri < n; ri++) {
            for (int gi = 0; gi < n; gi++) {
                for (int bi = 0; bi < n; bi++) {
                    float r = (float) ri / (n - 1);
                    float g = (float) gi / (n - 1);
                    float b = (float) bi / (n - 1);
                    float[] graded = grade(look, r, g, b);
                    if (look != Look.ORIGINAL) {
                        int sceneCode=scene==Scene.NIGHT?SceneTone.NIGHT:
                            scene==Scene.PORTRAIT?SceneTone.PORTRAIT:SceneTone.DAY;
                        graded=SceneTone.apply(sceneCode,graded[0],graded[1],graded[2]);
                    }
                    float ro = mix(r, graded[0], t);
                    float go = mix(g, graded[1], t);
                    float bo = mix(b, graded[2], t);
                    cube[ri][gi][bi] = Color.rgb(toByte(ro), toByte(go), toByte(bo));
                }
            }
        }
        return SingleColorLut.createFromCube(cube);
    }

    private static float[] grade(Look look, float r, float g, float b) {
        if (look == Look.ORIGINAL) return new float[] {r, g, b};
        if (look == Look.CANON50) return gradeCanon50(r, g, b);

        // Linear-domain luminance approximation; Media3's SDR input is linear BT.709.
        float lum = clip(.2126f * r + .7152f * g + .0722f * b, 0, 1);
        float saturation = look == Look.LUXURY ? .79f : look == Look.CINEMA ? .76f : .89f;
        r = lum + (r - lum) * saturation;
        g = lum + (g - lum) * saturation;
        b = lum + (b - lum) * saturation;

        float exposure = look == Look.SOFT ? 1.00f : look == Look.CINEMA ? .82f : .87f;
        float gamma = look == Look.SOFT ? 1.045f : look == Look.CINEMA ? 1.20f : 1.13f;
        float contrast = look == Look.SOFT ? 1.035f : look == Look.CINEMA ? 1.12f : 1.10f;
        r = curve(r, exposure, gamma, contrast);
        g = curve(g, exposure, gamma, contrast);
        b = curve(b, exposure, gamma, contrast);

        // Preserve warm highlights, while biasing deep shadows teal, not orange.
        float shadows = 1f - smoothstep(.05f, .40f, lum);
        float highlights = smoothstep(.32f, .83f, lum);
        if (look == Look.LUXURY) {
            r += .011f * highlights - .011f * shadows;
            g += .002f * highlights + .002f * shadows;
            b += -.025f * highlights + .009f * shadows;
        } else if (look == Look.CINEMA) {
            r += .006f * highlights - .015f * shadows;
            g += .002f * highlights + .003f * shadows;
            b += -.018f * highlights + .017f * shadows;
        } else {
            r += .016f * highlights;
            g += .003f * highlights;
            b += -.015f * highlights;
        }
        // Subtle luminance-dependent highlight compression to reduce glare.
        return new float[] {clip(r, 0, 1), clip(g, 0, 1), clip(b, 0, 1)};
    }

    /**
     * Creative Canon R5 Mark II / RF50 F1.8-inspired SDR look.
     *
     * Designed from Canon's published Standard and Portrait picture-style goals:
     * natural warm skin, moderate local-looking contrast and gentle highlights.
     * NOT an extracted Canon camera profile, official Picture Style, or optical
     * f/1.8 depth-of-field simulation. A 3D LUT cannot synthesize lens bokeh.
     * The LUT is applied to photo, video and live preview identically.
     */
    private static float[] gradeCanon50(float r, float g, float b) {
        // Use input luminance for scene-dependent coloration, avoiding a harsh
        // global warm tint that would yellow neutral walls and white clothing.
        final float originalLum = clip(.2126f * r + .7152f * g + .0722f * b, 0f, 1f);
        final float skinWarm = smoothstep(.012f, .105f, r - g)
                * smoothstep(.006f, .095f, g - b)
                * smoothstep(.08f, .34f, originalLum)
                * (1f - smoothstep(.74f, .97f, originalLum));
        final float highlight = smoothstep(.44f, .91f, originalLum);
        final float shadow = 1f - smoothstep(.05f, .35f, originalLum);
        // Keep natural chroma: more saturated than the dark Luxury Night look,
        // still avoiding aggressive smartphone HDR saturation.
        final float saturation = .965f;
        r = originalLum + (r - originalLum) * saturation;
        g = originalLum + (g - originalLum) * saturation;
        b = originalLum + (b - originalLum) * saturation;

        r = canonTone(r);
        g = canonTone(g);
        b = canonTone(b);

        // Subtle skin warmth and natural pastel-orange highlights.
        r += .014f * skinWarm + .004f * highlight;
        g += .005f * skinWarm + .001f * highlight;
        b -= .011f * skinWarm + .003f * highlight;

        // Prevent blue-green tinting in neutral dark areas. A mild lift helps
        // retain detail without flattening the deepest blacks.
        r += .002f * shadow;
        g += .002f * shadow;
        b += .003f * shadow;

        // Restrained foliage greens and fluorescent greens.
        float greenDominance = clip((g - .5f * (r + b)) * 4f, 0f, 1f);
        g -= .010f * greenDominance;
        return new float[] {clip(r, 0f, 1f), clip(g, 0f, 1f), clip(b, 0f, 1f)};
    }

    private static float canonTone(float v) {
        float x = clip((v - .5f) * 1.045f + .5f, 0f, 1f);
        // Modest midtone lift, then rounded specular roll-off to keep skin and
        // light sources smooth rather than abruptly clipped.
        x += .015f * x * (1f - x);
        x -= .027f * smoothstep(.70f, 1f, x);
        return clip(x, 0f, 1f);
    }

    private static float curve(float v, float exposure, float gamma, float contrast) {
        float x = (float) Math.pow(clip(v, 0, 1), gamma) * exposure;
        return clip((x - .5f) * contrast + .5f, 0, 1);
    }
    private static float smoothstep(float lo, float hi, float v) {
        float x = clip((v - lo) / (hi - lo), 0, 1);
        return x * x * (3f - 2f * x);
    }
    private static int toByte(float v) { return Math.round(255f * clip(v, 0, 1)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float clip(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
