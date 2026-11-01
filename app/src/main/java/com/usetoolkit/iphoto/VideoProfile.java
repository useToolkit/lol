package com.usetoolkit.iphoto;
/** Device-supported configuration fallbacks, independent from Android runtime. */
public final class VideoProfile {
  private VideoProfile(){}
  public static String chooseQuality(String q,boolean uhd,boolean fhd,boolean hd){
    if("UHD".equals(q) && uhd)return "UHD";
    if("UHD".equals(q) && fhd)return "FHD";
    if("FHD".equals(q) && fhd)return "FHD";
    if("HD".equals(q) && hd)return "HD";
    if(fhd)return "FHD";
    if(hd)return "HD";
    if(uhd)return "UHD";
    return "FHD"; // fallback lets CameraX reject/repair unsupported combos
  }
  public static int chooseFps(int req,boolean supported60){
    return req==60 && supported60?60:30;
  }
  /** CameraX only guarantees stabilization for <=1080p @ <=30fps. */
  public static boolean allowStabilization(boolean want,boolean capable,String q,int fps){
    return want && capable && !"UHD".equals(q) && fps<=30;
  }
}
