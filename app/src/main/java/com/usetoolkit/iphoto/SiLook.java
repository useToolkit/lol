package com.usetoolkit.iphoto;

/**
 * si v2 — Parisian hotel/restaurant editorial grade.
 * More pronounced than the original si: champagne limestone, amber practical
 * lighting, olive foliage, subdued cyan/blue and dimensional soft blacks.
 * Input and output use SDR linear-light RGB, each value normalized to [0,1].
 * No claim of exact pixel matching to references without paired camera images.
 */
public final class SiLook {
    private SiLook(){}

    public static float[] grade(float inputR,float inputG,float inputB){
        float r0=clip(inputR),g0=clip(inputG),b0=clip(inputB);
        float y=clip(.2126f*r0+.7152f*g0+.0722f*b0);

        // Pixel-aware hue masks prevent a constant orange cast across the scene.
        float warm=smooth(.006f,.16f,r0-g0)*smooth(.004f,.19f,g0-b0);
        float foliage=smooth(.01f,.16f,g0-r0)*smooth(.008f,.18f,g0-b0);
        float blue=smooth(.012f,.18f,b0-r0)*smooth(.01f,.16f,b0-g0);
        float neutral=1f-smooth(.012f,.12f,max(r0,g0,b0)-min(r0,g0,b0));

        // A deliberate difference at ordinary default strength; less synthetic HDR.
        // Keep warm lamps relatively vibrant, tame harsh foliage and cyan skies.
        float sat=clip(.76f+.13f*warm-.13f*foliage-.035f*blue);
        float r=tone(y+(r0-y)*sat);
        float g=tone(y+(g0-y)*sat);
        float b=tone(y+(b0-y)*sat);

        float amber=warm*smooth(.15f,.68f,y);
        float champagne=neutral*smooth(.20f,.68f,y)*
                (1f-smooth(.80f,.99f,y));
        float shadow=1f-smooth(.08f,.40f,y);

        // Amber chandeliers, champagne stone walls and warm muted skin.
        r+=.042f*amber+.015f*champagne;
        g+=.009f*amber+.003f*champagne;
        b-=.046f*amber+.019f*champagne;

        // Foliage -> olive, skies -> less electric blue.
        r+=.019f*foliage-.009f*blue;
        g-=.024f*foliage;
        b-=.009f*foliage;

        // Cool/neutral shadow detail, without a gray haze over the entire frame.
        r-=.003f*shadow;
        g+=.002f*shadow;
        b+=.009f*shadow+.004f*blue*shadow;

        return new float[]{clip(r),clip(g),clip(b)};
    }

    private static float max(float x,float y,float z){
        return Math.max(x,Math.max(y,z));
    }
    private static float min(float x,float y,float z){
        return Math.min(x,Math.min(y,z));
    }
    private static float tone(float value){
        float x=clip((value-.5f)*1.075f+.5f);
        float shadow=1f-smooth(.075f,.32f,x);
        float highlight=smooth(.57f,.96f,x);
        float mid=smooth(.10f,.36f,x)*(1f-smooth(.60f,.85f,x));
        return clip(.935f*x+.012f*shadow+.012f*mid-.036f*highlight);
    }
    private static float smooth(float low,float high,float value){
        float t=clip((value-low)/(high-low));
        return t*t*(3f-2f*t);
    }
    private static float clip(float value){
        return Math.max(0f,Math.min(1f,value));
    }
}
