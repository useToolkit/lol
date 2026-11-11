package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RadialPaletteMathTest {
    @Test public void fanFits218DpPanelWithoutClipping(){
        final float cx=173f,cy=168f,r=143f,diameter=42f;
        for(int i=0;i<RadialPaletteMath.COUNT;i++){
            RadialPaletteMath.Point p=RadialPaletteMath.point(cx,cy,r,i);
            assertTrue("left",p.x>=diameter/2f);
            assertTrue("right",p.x<=218f-diameter/2f);
            assertTrue("top",p.y>=diameter/2f);
            assertTrue("bottom",p.y<=218f-diameter/2f);
            assertTrue("fans leftwards",p.x<cx);
            assertTrue("fans upwards",p.y<cy);
        }
    }
    @Test public void adjacentFilterButtonsHaveSeparateTargets(){
        for(int i=0;i<RadialPaletteMath.COUNT-1;i++){
            RadialPaletteMath.Point a=RadialPaletteMath.point(173,168,143,i);
            RadialPaletteMath.Point b=RadialPaletteMath.point(173,168,143,i+1);
            assertTrue(Math.hypot(a.x-b.x,a.y-b.y)>=43d);
        }
    }
    @Test(expected=IllegalArgumentException.class)
    public void impossibleIndexRejected(){
        RadialPaletteMath.point(173,168,143,6);
    }
}
