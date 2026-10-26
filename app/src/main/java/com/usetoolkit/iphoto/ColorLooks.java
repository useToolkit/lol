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
        ORIGINAL("Original", "효과 없는 원본");
        public final String label;
        public final String hint;
        Look(String label, String hint) { this.label = label; this.hint = hint; }
    }

    private ColorLooks() {}

    public static SingleColorLut generate(Look look, float strength) {
        final int n = 17;
        final float t = clip(strength, 0f, 1f);
        int[][][] cube = new int[n][n][n];
        for (int ri = 0; ri < n; ri++) {
            for (int gi = 0; gi < n; gi++) {
                for (int bi = 0; bi < n; bi++) {
                    float r = (float) ri / (n - 1);
                    float g = (float) gi / (n - 1);
                    float b = (float) bi / (n - 1);
                    float[] graded = grade(look, r, g, b);
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
