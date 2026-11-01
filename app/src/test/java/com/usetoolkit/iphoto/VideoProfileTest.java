package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class VideoProfileTest {
 @Test public void downgrade4KOn1080Only(){
  assertEquals("FHD",VideoProfile.chooseQuality("UHD",false,true,true));
 }
 @Test public void keep4KOnSupportedCamera(){
  assertEquals("UHD",VideoProfile.chooseQuality("UHD",true,true,true));
 }
 @Test public void when60MissingUse30(){
  assertEquals(30,VideoProfile.chooseFps(60,false));
  assertEquals(60,VideoProfile.chooseFps(60,true));
 }
 @Test public void stabilizedOnlyIfGuaranteed(){
  assertTrue(VideoProfile.allowStabilization(true,true,"FHD",30));
  assertFalse(VideoProfile.allowStabilization(true,true,"UHD",30));
  assertFalse(VideoProfile.allowStabilization(true,true,"FHD",60));
  assertFalse(VideoProfile.allowStabilization(true,false,"FHD",30));
 }
}
