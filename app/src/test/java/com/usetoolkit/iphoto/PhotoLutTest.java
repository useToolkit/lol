package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PhotoLutTest {
    @Test public void originalLookChangesNoPixelChannels(){
        PhotoLut lut=new PhotoLut(ColorLooks.Look.ORIGINAL,
            ColorLooks.Scene.DAYLIGHT,1f);
        for(int v:new int[]{0xFF000000,0xFFFFFFFF,0xFF634523,
                0xFF7B7B7B,0xFF1B39DC,0xFF010203}){
            int output=lut.transform(v);
            // LUT sample/interpolation and gamma roundtrip can vary by 1.
            assertTrue(Math.abs(((v>>>16)&255)-((output>>>16)&255))<=2);
            assertTrue(Math.abs(((v>>>8)&255)-((output>>>8)&255))<=2);
            assertTrue(Math.abs((v&255)-(output&255))<=2);
        }
    }
    @Test public void strengthZeroIsNearlyIdentity(){
        PhotoLut lut=new PhotoLut(ColorLooks.Look.CANON50,
            ColorLooks.Scene.PORTRAIT,0f);
        int before=0xFFC98142,after=lut.transform(before);
        assertTrue(Math.abs((before&255)-(after&255))<=2);
    }
    @Test public void everyLookComputesClampedRgb(){
        for(ColorLooks.Look look:ColorLooks.Look.values()){
            PhotoLut lut=new PhotoLut(look,ColorLooks.Scene.NIGHT,.85f);
            for(int i=0;i<128;i++){
                int c=0xFF000000|((i*53&255)<<16)|((i*97&255)<<8)|(i*149&255);
                int next=lut.transform(c);
                assertEquals(0xFF000000,next&0xFF000000);
            }
        }
    }
}
