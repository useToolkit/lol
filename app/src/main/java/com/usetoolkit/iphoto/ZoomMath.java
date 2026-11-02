package com.usetoolkit.iphoto;

import java.util.ArrayList;

/** Continuous logarithmic lens zoom geometry, independent from Android UI. */
public final class ZoomMath {
    private ZoomMath(){}
    private static final float[] APPLE_LIKE_STOPS={.5f,1f,2f,3f,5f,8f};
    public static float limit(float value,float min,float max){
        min=Float.isFinite(min)&&min>.01f?min:1f;
        max=Float.isFinite(max)&&max>=min?max:min;
        if(!Float.isFinite(value))return min;
        return Math.max(min,Math.min(max,value));
    }
    public static float position(float value,float min,float max){
        if(max<=min)return 0f;
        float z=limit(value,min,max);
        return (float)(Math.log(z/min)/Math.log(max/min));
    }
    public static float ratio(float position,float min,float max){
        if(max<=min)return min;
        float p=Float.isFinite(position)?Math.max(0f,Math.min(1f,position)):0f;
        return limit((float)(min*Math.exp(Math.log(max/min)*p)),min,max);
    }
    public static boolean isHorizontalDrag(float dx,float dy,float touchSlop){
        if(!Float.isFinite(dx)||!Float.isFinite(dy))return false;
        return Math.abs(dx)>touchSlop && Math.abs(dx)>Math.abs(dy)*1.15f;
    }
    public static float drag(float initial,float deltaPixels,float pxPerOctave,float min,float max){
        if(pxPerOctave<=0)return limit(initial,min,max);
        double octaves=deltaPixels/pxPerOctave;
        return limit((float)(initial*Math.pow(2d,octaves)),min,max);
    }
    /**
     * From any zoom, a swipe of fullSwipePx reaches the maximum on rightward
     * movement or minimum on leftward movement. Log interpolation avoids the
     * previous 155px-per-octave problem on high-zoom devices.
     */
    public static float dragToEdge(float start,float dx,float fullSwipePx,
            float min,float max){
        float from=limit(start,min,max);
        if(max<=min || !Float.isFinite(dx) || !Float.isFinite(fullSwipePx)
            || fullSwipePx<=0f)return from;
        float progress=Math.min(1f,Math.abs(dx)/fullSwipePx);
        if(progress<=0f)return from;
        float destination=dx>=0f?max:min;
        if(progress>=1f)return limit(destination,min,max);
        return limit((float)(from*Math.exp(Math.log(destination/from)*progress)),min,max);
    }

    /** Identify two short nearby taps, never a swipe, long press or far tap. */
    public static boolean isDoubleTap(long firstUp,long secondUp,
            float deltaX,float deltaY,long timeoutMs,float slopPx){
        if(firstUp<=0L || secondUp<=firstUp || secondUp-firstUp>timeoutMs ||
           timeoutMs<=0L || !Float.isFinite(deltaX) ||
           !Float.isFinite(deltaY) || !Float.isFinite(slopPx) ||
           slopPx<=0f)return false;
        return Math.hypot(deltaX,deltaY)<=slopPx;
    }

    /** Fixed visual distance above the shutter independent of strip height. */
    public static float anchorFromBottom(float height,float bottomInset){
        return Math.max(0f,height-Math.max(0f,bottomInset));
    }
    public static float badgeAbsoluteY(float stripTop,float stripHeight,float inset){
        return stripTop+anchorFromBottom(stripHeight,inset);
    }
    public static float[] presets(float min,float max){
        ArrayList<Float> out=new ArrayList<>();
        if(min>0f&&min<.98f && min>.55f)out.add(min);
        for(float stop:APPLE_LIKE_STOPS)
            if(stop>=min-.025f && stop<=max+.025f)out.add(stop);
        if(out.isEmpty())out.add(min);
        float[] a=new float[out.size()];
        for(int i=0;i<a.length;i++)a[i]=out.get(i);
        return a;
    }
}
