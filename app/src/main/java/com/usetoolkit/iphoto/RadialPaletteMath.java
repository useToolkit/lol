package com.usetoolkit.iphoto;

/** Pure geometry for a small bottom-right, upper-left fan of five filters. */
public final class RadialPaletteMath {
    public static final int COUNT=5;
    private static final double FIRST_ANGLE_DEG=184d;
    private static final double STEP_DEG=21d;
    private RadialPaletteMath(){}
    public static final class Point {
        public final float x,y;
        public Point(float x,float y){this.x=x;this.y=y;}
    }
    /** Android screen: positive X right, positive Y down. */
    public static Point point(float cx,float cy,float radius,int index){
        if(index<0||index>=COUNT)
            throw new IllegalArgumentException("filter index "+index);
        double angle=Math.toRadians(FIRST_ANGLE_DEG+index*STEP_DEG);
        return new Point((float)(cx+radius*Math.cos(angle)),
            (float)(cy+radius*Math.sin(angle)));
    }
}
