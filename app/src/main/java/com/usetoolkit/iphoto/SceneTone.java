package com.usetoolkit.iphoto;
/** A bounded, deterministic sRGB-like scene tone transform.
 * This is an aesthetic tone style, not a semantic object detector or camera profile. */
public final class SceneTone {
  public static final int DAY=0,NIGHT=1,PORTRAIT=2;
  private SceneTone(){}
  public static float[] apply(int scene,float r,float g,float b){
    float y=clip(.2126f*r+.7152f*g+.0722f*b);
    float skin=smooth(.015f,.13f,r-g)*smooth(.008f,.10f,g-b)*
        smooth(.09f,.31f,y)*(1f-smooth(.75f,.96f,y));
    float hl=smooth(.48f,.92f,y);
    if(scene==NIGHT){
      r=curve(r,.975f,1.05f);g=curve(g,.975f,1.05f);b=curve(b,.975f,1.05f);
      r+=.005f*hl;b-=.004f*hl;
    }else if(scene==PORTRAIT){
      r=curve(r,1.01f,1.015f);g=curve(g,1.01f,1.015f);b=curve(b,1.01f,1.015f);
      r+=.010f*skin;g+=.004f*skin;b-=.010f*skin;
    }else{
      r=curve(r,1.015f,1.035f);g=curve(g,1.015f,1.035f);b=curve(b,1.015f,1.035f);
    }
    return new float[]{clip(r),clip(g),clip(b)};
  }
  private static float curve(float x,float exp,float gamma){
    float v=(float)Math.pow(clip(x),gamma)*exp;
    return clip(v-.027f*smooth(.68f,1f,v));
  }
  private static float clip(float x){return Math.max(0f,Math.min(1f,x));}
  private static float smooth(float lo,float hi,float v){
    float t=clip((v-lo)/(hi-lo));return t*t*(3f-2f*t);
  }
}
