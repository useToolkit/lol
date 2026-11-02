package com.usetoolkit.iphoto;
import java.util.Locale;
/** Reports EXACT saved JPEG dimensions, never sensor marketing megapixels. */
public final class PhotoResolutionInfo {
    private PhotoResolutionInfo(){}
    public static long pixels(int width,int height){
        if(width<=0 || height<=0)return 0L;
        return (long)width*height;
    }
    public static String megapixelLabel(int width,int height){
        long p=pixels(width,height);
        return p==0L?"화소 미확인":
            String.format(Locale.US,"%.1fMP",p/1_000_000d);
    }
    public static String describe(int width,int height){
        long p=pixels(width,height);
        if(p==0L)return "해상도 미확인";
        return width+"×"+height+" · "+megapixelLabel(width,height)+
            " (약 "+p/10_000L+"만 화소)";
    }
}
