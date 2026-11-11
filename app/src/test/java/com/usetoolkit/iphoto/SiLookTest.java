package com.usetoolkit.iphoto;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SiLookTest {
    @Test public void finiteAndBounded(){
        for(int r=0;r<=10;r++) for(int g=0;g<=10;g++) for(int b=0;b<=10;b++)
            for(float x:SiLook.grade(r/10f,g/10f,b/10f)){
                assertTrue(Float.isFinite(x));
                assertTrue(x>=0f && x<=1f);
            }
    }

    @Test public void neutralGrayGetsDeliberatelyMutedWarmGrade(){
        float[] gray=SiLook.grade(.5f,.5f,.5f);
        assertTrue("gray should change perceptibly",Math.abs(gray[0]-.5f)>.005f);
        assertTrue("neutral should be subtly warm, not orange",gray[0]>=gray[1]&&gray[1]>=gray[2]);
        assertTrue("gray color cast must stay restrained",gray[0]-gray[2]<.035f);
    }

    @Test public void neutralToneMonotonic(){
        float before=-1f;
        for(int i=0;i<=100;i++){
            float f=i/100f;
            float current=SiLook.grade(f,f,f)[0];
            assertTrue("tone inversion at "+i,current>=before-.00001f);
            before=current;
        }
    }

    @Test public void oliveGreensDifferClearlyFromOriginal(){
        float[] g=SiLook.grade(.18f,.58f,.22f);
        assertTrue("foliage should be muted",g[1]<.54f);
        assertTrue("olive shift from electric green",g[0]>.24f);
    }

    @Test public void warmLightsRetainGoldAndShiftTone(){
        float[] v=SiLook.grade(.76f,.51f,.22f);
        assertTrue(v[0]>v[1] && v[1]>v[2]);
        assertTrue("golden light should have a visible difference",v[2]<.21f);
    }

    @Test public void brightSkyAndHighlightsAreControlled(){
        float[] sky=SiLook.grade(.20f,.45f,.72f);
        assertTrue("blue should be subdued",sky[2]<.66f);
        float[] white=SiLook.grade(.94f,.94f,.94f);
        assertTrue("highlights should gently roll off",white[0]<.92f);
    }
}
