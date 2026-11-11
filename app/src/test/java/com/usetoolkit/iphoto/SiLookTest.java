package com.usetoolkit.iphoto;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression checks for the new si preset, without Android runtime APIs. */
public final class SiLookTest {
    @Test public void outputAlwaysFiniteAndInRange(){
        for(int r=0;r<=10;r++) for(int g=0;g<=10;g++) for(int b=0;b<=10;b++){
            for(float c:SiLook.grade(r/10f,g/10f,b/10f)){
                assertTrue("finite",Float.isFinite(c));
                assertTrue("0..1",c>=0f && c<=1f);
            }
        }
    }
    @Test public void neutralWhitesAndGraysHaveNoYellowCast(){
        for(int i=0;i<=100;i++){
            float v=i/100f;
            float[] c=SiLook.grade(v,v,v);
            assertEquals("red green",c[0],c[1],.00001f);
            assertEquals("green blue",c[1],c[2],.00001f);
        }
    }
    @Test public void toneRemainsMonotonicForNeutralValues(){
        float before=-1f;
        for(int i=0;i<=100;i++){
            float value=SiLook.grade(i/100f,i/100f,i/100f)[0];
            assertTrue("no tonal inversion at step "+i,value>=before-.00001f);
            before=value;
        }
    }
    @Test public void greenFoliageIsMuted(){
        float[] c=SiLook.grade(.18f,.58f,.22f);
        assertTrue("foliage reduced green dominance",c[1]-c[0]<.58f-.18f);
    }
    @Test public void warmLightRemainsAmber(){
        float[] c=SiLook.grade(.76f,.51f,.22f);
        assertTrue("amber preserved",c[0]>c[1] && c[1]>c[2]);
    }
}
