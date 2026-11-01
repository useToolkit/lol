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
 @Test public void midpointIsGeometricNotLinear(){
    assertEquals(2f,ZoomMath.ratio(.5f,1f,4f),.001f);
 }
}
