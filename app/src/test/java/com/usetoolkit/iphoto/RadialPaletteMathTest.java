package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RadialPaletteMathTest {
    @Test public void fanFits170DpPanelWithoutClipping(){
        final float cx=143f,cy=145f,r=115f,diameter=42f;
        for(int i=0;i<5;i++){
            RadialPaletteMath.Point p=RadialPaletteMath.point(cx,cy,r,i);
            assertTrue("left",p.x>=diameter/2f);
            assertTrue("right",p.x<=170f-diameter/2f);
            assertTrue("top",p.y>=diameter/2f);
            assertTrue("bottom",p.y<=170f-diameter/2f);
            assertTrue("fans leftwards",p.x<cx);
            assertTrue("fans upwards",p.y<cy);
        }
    }
    @Test public void adjacentFilterButtonsHaveSeparateTargets(){
        for(int i=0;i<4;i++){
            RadialPaletteMath.Point a=RadialPaletteMath.point(143,145,115,i);
            RadialPaletteMath.Point b=RadialPaletteMath.point(143,145,115,i+1);
            assertTrue(Math.hypot(a.x-b.x,a.y-b.y)>=41d);
        }
    }
    @Test(expected=IllegalArgumentException.class)
    public void impossibleIndexRejected(){
        RadialPaletteMath.point(143,145,115,5);
    }
}
