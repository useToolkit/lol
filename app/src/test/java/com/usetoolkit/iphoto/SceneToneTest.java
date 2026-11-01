package com.usetoolkit.iphoto;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SceneToneTest {
 @Test public void bounded(){
  for(int scene=0;scene<=2;scene++)
   for(int i=0;i<=10;i++)for(int j=0;j<=10;j++)for(int k=0;k<=10;k++)
    for(float c:SceneTone.apply(scene,i/10f,j/10f,k/10f))
      assertTrue("range",c>=0f && c<=1f);
 }
 @Test public void neutralGray(){
  for(int scene=0;scene<=2;scene++){
    float[] v=SceneTone.apply(scene,.5f,.5f,.5f);
    assertEquals(v[0],v[1],.008f);assertEquals(v[1],v[2],.008f);
  }
 }
 @Test public void monotonic(){
  for(int scene=0;scene<=2;scene++){
   float prior=0f;
   for(int i=0;i<=100;i++){
    float x=i/100f, v=SceneTone.apply(scene,x,x,x)[0];
    assertTrue("tone monotone",v>=prior-.002f);prior=v;
   }
  }
 }
}
