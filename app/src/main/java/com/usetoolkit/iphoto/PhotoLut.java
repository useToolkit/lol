package com.usetoolkit.iphoto;

/**
 * Full resolution sRGB JPEG color grading. Uses the same creative
 * ColorLooks + SceneTone transforms as the live CameraX LUT, but converts
 * encoded JPEG sRGB to linear light before applying the transform.
 * 33x33x33 points, tetrahedral interpolation (4 vertices per pixel).
 * No interpolation step changes pixel dimensions.
 */
public final class PhotoLut {
    private static final int STEPS=32;
    private final int[] cube=new int[33*33*33];
    public PhotoLut(ColorLooks.Look look,ColorLooks.Scene scene,float strength) {
        for(int r=0;r<=STEPS;r++)
            for(int g=0;g<=STEPS;g++)
                for(int b=0;b<=STEPS;b++){
                    float lr=srgbToLinear(r/32f);
                    float lg=srgbToLinear(g/32f);
                    float lb=srgbToLinear(b/32f);
                    float[] graded=ColorLooks.gradeLinearPixel(
                        look,scene,strength,lr,lg,lb);
                    int cr=linearToByte(graded[0]);
                    int cg=linearToByte(graded[1]);
                    int cb=linearToByte(graded[2]);
                    cube[index(r,g,b)]=0xFF000000|(cr<<16)|(cg<<8)|cb;
                }
    }
    private static float srgbToLinear(float x) {
        return x<=.04045f?x/12.92f:
            (float)Math.pow((x+.055f)/1.055f,2.4d);
    }
    private static int linearToByte(float x){
        x=Math.max(0f,Math.min(1f,x));
        float s=x<=.0031308f?x*12.92f:
            (float)(1.055d*Math.pow(x,1d/2.4d)-.055d);
        return Math.max(0,Math.min(255,Math.round(s*255f)));
    }
    private static int index(int r,int g,int b){
        return (r*33+g)*33+b;
    }
    /** 4-corner tetrahedral interpolation, no allocations per pixel. */
    public int transform(int argb){
        int r=(argb>>>16)&255,g=(argb>>>8)&255,b=argb&255;
        int rr=r*32,gg=g*32,bb=b*32;
        int ri=Math.min(31,rr/255),gi=Math.min(31,gg/255),
            bi=Math.min(31,bb/255);
        float fr=(rr-ri*255)/255f,fg=(gg-gi*255)/255f,
            fb=(bb-bi*255)/255f;
        int i000=index(ri,gi,bi), i111=index(ri+1,gi+1,bi+1);
        int c0=cube[i000],c1,c2,c3=cube[i111];
        float w0,w1,w2,w3;
        final int R=33*33,G=33,B=1;
        if(fr>=fg){
            if(fg>=fb){ // r >= g >= b
                c1=cube[i000+R];c2=cube[i000+R+G];
                w0=1-fr;w1=fr-fg;w2=fg-fb;w3=fb;
            }else if(fr>=fb){ // r >= b > g
                c1=cube[i000+R];c2=cube[i000+R+B];
                w0=1-fr;w1=fr-fb;w2=fb-fg;w3=fg;
            }else{ // b > r >= g
                c1=cube[i000+B];c2=cube[i000+R+B];
                w0=1-fb;w1=fb-fr;w2=fr-fg;w3=fg;
            }
        }else{
            if(fb>=fg){ // b >= g > r
                c1=cube[i000+B];c2=cube[i000+G+B];
                w0=1-fb;w1=fb-fg;w2=fg-fr;w3=fr;
            }else if(fb>=fr){ // g > b >= r
                c1=cube[i000+G];c2=cube[i000+G+B];
                w0=1-fg;w1=fg-fb;w2=fb-fr;w3=fr;
            }else{ // g > r > b
                c1=cube[i000+G];c2=cube[i000+R+G];
                w0=1-fg;w1=fg-fr;w2=fr-fb;w3=fb;
            }
        }
        int red=Math.min(255,Math.max(0,Math.round(
            ((c0>>>16)&255)*w0+((c1>>>16)&255)*w1+
            ((c2>>>16)&255)*w2+((c3>>>16)&255)*w3)));
        int green=Math.min(255,Math.max(0,Math.round(
            ((c0>>>8)&255)*w0+((c1>>>8)&255)*w1+
            ((c2>>>8)&255)*w2+((c3>>>8)&255)*w3)));
        int blue=Math.min(255,Math.max(0,Math.round(
            (c0&255)*w0+(c1&255)*w1+(c2&255)*w2+(c3&255)*w3)));
        return (argb&0xFF000000)|(red<<16)|(green<<8)|blue;
    }
}
