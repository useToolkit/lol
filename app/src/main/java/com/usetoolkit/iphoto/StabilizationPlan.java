package com.usetoolkit.iphoto;

/** One stabilization policy for camera-preview, recording and optical backup.
 * A camera can report OIS without being capable of electronic preview EIS.
 */
public final class StabilizationPlan {
    public final boolean recordEis;
    public final boolean previewEis;
    public final boolean opticalBackup;
    private StabilizationPlan(boolean video,boolean preview,boolean ois){
        recordEis=video;previewEis=preview;opticalBackup=ois;
    }
    public static StabilizationPlan select(int mode,boolean videoCap,
            boolean previewCap,boolean oisCap,String quality,int fps){
        if(mode<=0)return new StabilizationPlan(false,false,false);
        boolean record=videoCap && VideoProfile.allowStabilization(true,videoCap,quality,fps);
        boolean preview=mode==2 && record && previewCap;
        boolean optical=oisCap && !record;
        return new StabilizationPlan(record,preview,optical);
    }
}
