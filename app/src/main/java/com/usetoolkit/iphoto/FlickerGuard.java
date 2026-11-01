package com.usetoolkit.iphoto;
/** Exposure period helper; Camera2 AE antibanding is disabled by manual AE_MODE_OFF. */
public final class FlickerGuard {
    public static final int AUTO=0, HZ50=50, HZ60=60, OFF=-1;
    private FlickerGuard(){}
    public static final class Choice {
        public final long shutterNs;
        public final int iso;
        public final boolean adjusted;
        public final boolean shortExposure;
        Choice(long shutterNs,int iso,boolean adjusted,boolean shortExposure){
            this.shutterNs=shutterNs;this.iso=iso;this.adjusted=adjusted;this.shortExposure=shortExposure;
        }
    }
    public static int effectiveHz(int preference,int observedHz){
        if(preference==OFF)return 0;
        if(preference==HZ50 || preference==HZ60)return preference;
        return preference==AUTO && (observedHz==HZ50||observedHz==HZ60)?observedHz:0;
    }
    public static long periodNs(int hz){
        return hz==HZ50?10_000_000L:hz==HZ60?8_333_333L:0;
    }
    /** Snap only shutter times long enough to cover a mains-flicker cycle.
     *  Preserve approximately ISO*exposure; never force longer blurred exposures in daylight. */
    public static Choice choose(long shutter,int iso,int minIso,int maxIso,
            long minShutter,long maxShutter,int hz){
        long lo=Math.max(1L,minShutter),hi=Math.max(lo,maxShutter);
        long base=Math.max(lo,Math.min(hi,shutter));
        int sens=Math.max(minIso,Math.min(maxIso,iso));
        long period=periodNs(hz);
        if(period==0)return new Choice(base,sens,false,false);
        if(base<period*.8d || hi<period)return new Choice(base,sens,false,true);
        long cycles=Math.max(1L,Math.round((double)base/period));
        cycles=Math.min(cycles,Math.max(1L,hi/period));
        long snapped=Math.max(lo,Math.min(hi,cycles*period));
        if(Math.abs(snapped-base)<20000L)return new Choice(base,sens,false,false);
        int newIso=(int)Math.round((double)sens*base/snapped);
        newIso=Math.max(minIso,Math.min(maxIso,newIso));
        return new Choice(snapped,newIso,true,false);
    }
}
