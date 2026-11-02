package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ZoomMathTest {
 @Test public void roundTripsContinuousRatios(){
    for(float z:new float[]{.5f,1f,1.5f,2f,5f,8f}){
        float p=ZoomMath.position(z,.5f,8f);
        assertEquals(z,ZoomMath.ratio(p,.5f,8f),.001f);
    }
 }
 @Test public void minMaxRespectHardware(){
    assertEquals(1f,ZoomMath.limit(.5f,1f,6f),0f);
    assertEquals(6f,ZoomMath.limit(10f,1f,6f),0f);
    assertEquals(.5f,ZoomMath.limit(.2f,.5f,8f),0f);
 }
 @Test public void pinchingAndDraggingRemainContinuous(){
    assertEquals(2f,ZoomMath.drag(1f,120f,120f,1f,8f),.001f);
    assertEquals(4f,ZoomMath.drag(2f,120f,120f,1f,8f),.001f);
    assertEquals(1f,ZoomMath.drag(1f,-240f,120f,1f,8f),0f);
 }
 @Test public void onlyExposesSupportedPresetStops(){
    assertArrayEquals(new float[]{1f,2f,3f,5f},ZoomMath.presets(1f,6f),0f);
    assertArrayEquals(new float[]{.5f,1f,2f,3f,5f,8f},ZoomMath.presets(.5f,9f),0f);
 }
 @Test public void horizontalSwipeActivatesWithoutWaitingForLongPress(){
    assertTrue(ZoomMath.isHorizontalDrag(14f,2f,7f));
    assertTrue(ZoomMath.isHorizontalDrag(-17f,1f,7f));
    assertFalse(ZoomMath.isHorizontalDrag(4f,0f,7f));
    assertFalse(ZoomMath.isHorizontalDrag(12f,40f,7f));
    assertFalse(ZoomMath.isHorizontalDrag(Float.NaN,0f,7f));
 }
 @Test public void dragIsReversibleAndClampsAtHardwareEnds(){
    float start=2f;
    float zoomed=ZoomMath.drag(start,140,140,1f,8f);
    assertEquals(4f,zoomed,.001f);
    assertEquals(start,ZoomMath.drag(zoomed,-140,140,1f,8f),.001f);
    assertEquals(1f,ZoomMath.drag(start,-800,140,1f,8f),0f);
    assertEquals(8f,ZoomMath.drag(start,800,140,1f,8f),0f);
 }
 @Test public void midpointIsGeometricNotLinear(){
    assertEquals(2f,ZoomMath.ratio(.5f,1f,4f),.001f);
 }

    @org.junit.Test public void thumbNeverMovesWhenWheelExpands(){
        float collapsed=ZoomMath.badgeAbsoluteY(1000f-54f,54f,27f);
        float expanded=ZoomMath.badgeAbsoluteY(1000f-110f,110f,27f);
        org.junit.Assert.assertEquals(973f,collapsed,.001f);
        org.junit.Assert.assertEquals(collapsed,expanded,.001f);
    }
    @org.junit.Test public void dialAndThumbMaintainBottomOffset(){
        org.junit.Assert.assertEquals(27f,ZoomMath.anchorFromBottom(54f,27f),.001f);
        org.junit.Assert.assertEquals(83f,ZoomMath.anchorFromBottom(110f,27f),.001f);
    }
    @Test public void fastSwipeReachesBothHardwareBoundsInSingleMotion(){
        assertEquals(12f,ZoomMath.dragToEdge(1f,135f,135f,1f,12f),0f);
        assertEquals(1f,ZoomMath.dragToEdge(12f,-135f,135f,1f,12f),0f);
        assertEquals(.5f,ZoomMath.dragToEdge(3f,-135f,135f,.5f,30f),0f);
        assertEquals(30f,ZoomMath.dragToEdge(3f,135f,135f,.5f,30f),0f);
    }
    @Test public void fastSwipeClampsOvershootAndStartsSmoothly(){
        assertEquals(1.7f,ZoomMath.dragToEdge(1.7f,0f,135f,1f,30f),0f);
        assertEquals(30f,ZoomMath.dragToEdge(2f,500f,135f,1f,30f),0f);
        assertEquals(1f,ZoomMath.dragToEdge(2f,-500f,135f,1f,30f),0f);
        assertTrue(ZoomMath.dragToEdge(1f,35f,135f,1f,20f)>1f);
        assertTrue(ZoomMath.dragToEdge(1f,35f,135f,1f,20f)<20f);
    }
    @Test public void fastSwipeReversesFromIntermediateZoom(){
        float start=3.5f;
        float up=ZoomMath.dragToEdge(start,80f,135f,1f,10f);
        float down=ZoomMath.dragToEdge(start,-80f,135f,1f,10f);
        assertTrue(up>start);assertTrue(up<10f);
        assertTrue(down<start);assertTrue(down>1f);
    }
    @Test public void doubleTapTimeAndDistance(){
        assertTrue(ZoomMath.isDoubleTap(1000L,1200L,3f,2f,300L,45f));
        assertTrue(ZoomMath.isDoubleTap(1000L,1300L,0f,0f,300L,45f));
        assertFalse(ZoomMath.isDoubleTap(1000L,1400L,0f,0f,300L,45f));
        assertFalse(ZoomMath.isDoubleTap(1000L,1200L,50f,0f,300L,45f));
        assertFalse(ZoomMath.isDoubleTap(0L,1200L,0f,0f,300L,45f));
        assertFalse(ZoomMath.isDoubleTap(1000L,1200L,Float.NaN,0f,300L,45f));
    }
}
