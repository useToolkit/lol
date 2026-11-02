package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PhotoResolutionInfoTest {
    @Test public void screenshotSizedPhoto(){
        assertEquals(1327104L,PhotoResolutionInfo.pixels(864,1536));
        assertEquals("1.3MP",PhotoResolutionInfo.megapixelLabel(864,1536));
        assertTrue(PhotoResolutionInfo.describe(864,1536).contains("132만 화소"));
    }
    @Test public void twelveMegapixelPhoto(){
        assertEquals(12000000L,PhotoResolutionInfo.pixels(4000,3000));
        assertEquals("12.0MP",PhotoResolutionInfo.megapixelLabel(4000,3000));
    }
    @Test public void fiftyMegapixelPhotoWithoutOverflow(){
        assertEquals(49939200L,PhotoResolutionInfo.pixels(8160,6120));
        assertEquals("49.9MP",PhotoResolutionInfo.megapixelLabel(8160,6120));
    }
    @Test public void invalidImageDimensionsDoNotClaimPixels(){
        assertEquals(0L,PhotoResolutionInfo.pixels(-1,4000));
        assertEquals("화소 미확인",PhotoResolutionInfo.megapixelLabel(0,1536));
    }
}
