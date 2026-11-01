package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class StabilizationPlanTest {
 @Test public void standardEnablesOnlyRecordingEis(){
  StabilizationPlan s=StabilizationPlan.select(1,true,true,true,"FHD",30);
  assertTrue(s.recordEis);assertFalse(s.previewEis);assertFalse(s.opticalBackup);
 }
 @Test public void enhancedEnablesMatchingPreviewWhenSupported(){
  StabilizationPlan s=StabilizationPlan.select(2,true,true,true,"FHD",30);
  assertTrue(s.recordEis);assertTrue(s.previewEis);assertFalse(s.opticalBackup);
 }
 @Test public void noPreviewSupportFallsBackToRecordingEis(){
  StabilizationPlan s=StabilizationPlan.select(2,true,false,true,"FHD",30);
  assertTrue(s.recordEis);assertFalse(s.previewEis);
 }
 @Test public void fourKAndSixtyUseOpticalWhenAvailable(){
  StabilizationPlan s=StabilizationPlan.select(2,true,true,true,"UHD",60);
  assertFalse(s.recordEis);assertFalse(s.previewEis);assertTrue(s.opticalBackup);
 }
 @Test public void unsteadyCameraNeverClaimsStabilized(){
  StabilizationPlan s=StabilizationPlan.select(2,false,false,false,"FHD",30);
  assertFalse(s.recordEis);assertFalse(s.previewEis);assertFalse(s.opticalBackup);
 }
 @Test public void offMeansAllOff(){
  StabilizationPlan s=StabilizationPlan.select(0,true,true,true,"FHD",30);
  assertFalse(s.recordEis);assertFalse(s.previewEis);assertFalse(s.opticalBackup);
 }
}
