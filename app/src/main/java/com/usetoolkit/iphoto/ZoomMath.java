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
    public static float drag(float initial,float deltaPixels,float pxPerOctave,float min,float max){
        if(pxPerOctave<=0)return limit(initial,min,max);
        double octaves=deltaPixels/pxPerOctave;
        return limit((float)(initial*Math.pow(2d,octaves)),min,max);
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
