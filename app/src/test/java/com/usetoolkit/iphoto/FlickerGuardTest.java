package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class FlickerGuardTest {
    @Test public void autoNeedsDetection(){
        assertEquals(0,FlickerGuard.effectiveHz(0,0));
        assertEquals(50,FlickerGuard.effectiveHz(0,50));
        assertEquals(60,FlickerGuard.effectiveHz(0,60));
    }
    @Test public void manualModeUsesSelectedFrequency(){
        assertEquals(50,FlickerGuard.effectiveHz(50,60));
        assertEquals(60,FlickerGuard.effectiveHz(60,50));
        assertEquals(0,FlickerGuard.effectiveHz(-1,60));
    }
    @Test public void sixtyHzRoundsAndCompensatesIso(){
        FlickerGuard.Choice c=FlickerGuard.choose(20_000_000L,200,50,3200,100000,33333333,60);
        assertEquals(16666666L,c.shutterNs);
        assertEquals(240,c.iso);
        assertTrue(c.adjusted);
        assertEquals(4e9,(double)c.shutterNs*c.iso,4e7);
    }
    @Test public void fiftyHzRoundsToTwentyMs(){
        FlickerGuard.Choice c=FlickerGuard.choose(18000000,200,50,3200,100000,33333333,50);
        assertEquals(20000000L,c.shutterNs);assertEquals(180,c.iso);
    }
    @Test public void brightScenesAreNotForcedToLongShutter(){
        FlickerGuard.Choice c=FlickerGuard.choose(3000000,100,50,3200,100000,33333333,60);
        assertTrue(c.shortExposure);assertFalse(c.adjusted);assertEquals(3000000L,c.shutterNs);
    }
    @Test public void deviceIsoLimitsAreRespected(){
        FlickerGuard.Choice c=FlickerGuard.choose(18000000,100,100,800,100000,33333333,50);
        assertEquals(100,c.iso);assertEquals(20000000L,c.shutterNs);
    }
}
