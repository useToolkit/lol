package com.usetoolkit.iphoto;

/**
 * si — a restrained Paris editorial look derived from five user-supplied
 * restaurant and courtyard references (not an extracted device ICC profile).
 *
 * Scene-adaptive tonal / hue-selective artistic transform in linear-light RGB.
 * Keep gray neutrals essentially neutral; protect warm interior lamps, gently
 * compress specular highlights, subdue bright greens, retain shadow texture.
 *
 * Used only by ColorLooks.Look.SI; other existing filters stay unchanged.
 */
public final class SiLook {
    private SiLook(){}

    public static float[] grade(float inputR,float inputG,float inputB){
        final float r0=clip(inputR),g0=clip(inputG),b0=clip(inputB);
        final float y=clip(.2126f*r0+.7152f*g0+.0722f*b0);
        final float warm=smooth(.008f,.13f,r0-g0)
            *smooth(.006f,.14f,g0-b0);
        final float foliage=smooth(.006f,.15f,g0-r0)
            *smooth(.005f,.16f,g0-b0);
        final float blue=smooth(.010f,.18f,b0-r0)
            *smooth(.006f,.14f,b0-g0);

        // Muted limestone / taupe atmosphere, with selective amber retention.
        float saturation=clip(.91f-.105f*foliage-.025f*blue+.035f*warm);
        float r=tone(y+(r0-y)*saturation);
        float g=tone(y+(g0-y)*saturation);
        float b=tone(y+(b0-y)*saturation);

        float warmHighlight=warm*smooth(.15f,.70f,y);
        float shadow=(1f-smooth(.045f,.40f,y));
        // Small hue-dependent offsets: no universal yellow tint on white walls.
        r+=.009f*warmHighlight;
        g+=.003f*warmHighlight;
        b-=.010f*warmHighlight;

        // Olive/grey foliage rather than neon green.
        r+=.007f*foliage;
        g-=.008f*foliage;
        b-=.002f*foliage;

        // Slightly cooler blue shadow hues, preserving neutral blacks.
        b+=.003f*blue*shadow;

        return new float[]{clip(r),clip(g),clip(b)};
    }

    private static float tone(float v){
        float x=clip((v-.5f)*1.035f+.5f);
        // Subtle interior midtone lift + highlights with a gentle shoulder.
        float mid=smooth(.05f,.35f,x)*(1f-smooth(.55f,.90f,x));
        x+=.008f*mid;
        x-=.030f*smooth(.68f,.99f,x);
        x-=.004f*(1f-smooth(.025f,.19f,x));
        return clip(x);
    }

    private static float smooth(float low,float high,float x){
        float t=clip((x-low)/(high-low));
        return t*t*(3f-2f*t);
    }
    private static float clip(float value){
        return Math.max(0f,Math.min(1f,value));
    }
}
