package com.usetoolkit.fuelpick;

/**
 * WGS84 <-> KOTI-KATEC converter.
 * KATEC: Bessel 1841, TM, lat_0=38, lon_0=128, k=0.9999,
 * false easting=400000, false northing=600000.
 */
public final class KatecConverter {
    private KatecConverter() {}

    private static final double WGS_A = 6378137.0;
    private static final double WGS_RF = 298.257223563;
    private static final double BES_A = 6377397.155;
    private static final double BES_RF = 299.1528128;

    private static final double DX = -115.80;
    private static final double DY = 474.99;
    private static final double DZ = 674.11;
    private static final double RX = Math.toRadians(1.16 / 3600.0);
    private static final double RY = Math.toRadians(-2.31 / 3600.0);
    private static final double RZ = Math.toRadians(-1.63 / 3600.0);
    private static final double SCALE = 6.43e-6;

    private static final double LAT0 = Math.toRadians(38.0);
    private static final double LON0 = Math.toRadians(128.0);
    private static final double K0 = 0.9999;
    private static final double X0 = 400000.0;
    private static final double Y0 = 600000.0;

    public static double[] wgs84ToKatec(double lat, double lon) {
        double[] xyz = geodeticToXyz(lat, lon, 0, WGS_A, WGS_RF);
        double[] bxyz = inverseHelmert(xyz[0], xyz[1], xyz[2]);
        double[] bllh = xyzToGeodetic(bxyz[0], bxyz[1], bxyz[2], BES_A, BES_RF);
        return tmForward(bllh[0], bllh[1]);
    }

    public static double[] katecToWgs84(double x, double y) {
        double[] bll = tmInverse(x, y);
        double[] bxyz = geodeticToXyz(bll[0], bll[1], 0, BES_A, BES_RF);
        double[] xyz = forwardHelmert(bxyz[0], bxyz[1], bxyz[2]);
        double[] llh = xyzToGeodetic(xyz[0], xyz[1], xyz[2], WGS_A, WGS_RF);
        return new double[]{llh[0], llh[1]};
    }

    private static double[] geodeticToXyz(double latDeg, double lonDeg, double h, double a, double rf) {
        double lat = Math.toRadians(latDeg), lon = Math.toRadians(lonDeg);
        double f = 1.0 / rf, e2 = f * (2.0 - f);
        double sin = Math.sin(lat), cos = Math.cos(lat);
        double n = a / Math.sqrt(1.0 - e2 * sin * sin);
        return new double[]{
                (n + h) * cos * Math.cos(lon),
                (n + h) * cos * Math.sin(lon),
                (n * (1.0 - e2) + h) * sin
        };
    }

    private static double[] xyzToGeodetic(double x, double y, double z, double a, double rf) {
        double f = 1.0 / rf, e2 = f * (2.0 - f);
        double lon = Math.atan2(y, x), p = Math.hypot(x, y);
        double lat = Math.atan2(z, p * (1.0 - e2));
        double h = 0;
        for (int i = 0; i < 15; i++) {
            double sin = Math.sin(lat);
            double n = a / Math.sqrt(1.0 - e2 * sin * sin);
            h = p / Math.cos(lat) - n;
            double next = Math.atan2(z, p * (1.0 - e2 * n / (n + h)));
            if (Math.abs(next - lat) < 1e-14) { lat = next; break; }
            lat = next;
        }
        return new double[]{Math.toDegrees(lat), Math.toDegrees(lon), h};
    }

    private static double[] forwardHelmert(double x, double y, double z) {
        double m = 1.0 + SCALE;
        return new double[]{
                DX + m*x - RZ*y + RY*z,
                DY + RZ*x + m*y - RX*z,
                DZ - RY*x + RX*y + m*z
        };
    }

    private static double[] inverseHelmert(double x2, double y2, double z2) {
        double m = 1.0 + SCALE;
        double[][] a = {
                {m, -RZ, RY, x2 - DX},
                {RZ, m, -RX, y2 - DY},
                {-RY, RX, m, z2 - DZ}
        };
        for (int i = 0; i < 3; i++) {
            int pivot = i;
            for (int r = i + 1; r < 3; r++) if (Math.abs(a[r][i]) > Math.abs(a[pivot][i])) pivot = r;
            double[] tmp = a[i]; a[i] = a[pivot]; a[pivot] = tmp;
            double div = a[i][i];
            for (int c = i; c < 4; c++) a[i][c] /= div;
            for (int rr = 0; rr < 3; rr++) if (rr != i) {
                double factor = a[rr][i];
                for (int c = i; c < 4; c++) a[rr][c] -= factor * a[i][c];
            }
        }
        return new double[]{a[0][3], a[1][3], a[2][3]};
    }

    private static double meridionalArc(double phi, double a, double e2) {
        return a * ((1-e2/4-3*e2*e2/64-5*e2*e2*e2/256)*phi
                -(3*e2/8+3*e2*e2/32+45*e2*e2*e2/1024)*Math.sin(2*phi)
                +(15*e2*e2/256+45*e2*e2*e2/1024)*Math.sin(4*phi)
                -(35*e2*e2*e2/3072)*Math.sin(6*phi));
    }

    private static double[] tmForward(double latDeg, double lonDeg) {
        double f = 1.0 / BES_RF, e2 = f * (2-f), ep2 = e2 / (1-e2);
        double lat = Math.toRadians(latDeg), lon = Math.toRadians(lonDeg);
        double sin = Math.sin(lat), cos = Math.cos(lat), tan = Math.tan(lat);
        double n = BES_A / Math.sqrt(1-e2*sin*sin);
        double t = tan*tan, c = ep2*cos*cos, aa = (lon-LON0)*cos;
        double m = meridionalArc(lat, BES_A, e2), m0 = meridionalArc(LAT0, BES_A, e2);
        double x = X0 + K0*n*(aa + (1-t+c)*Math.pow(aa,3)/6
                + (5-18*t+t*t+72*c-58*ep2)*Math.pow(aa,5)/120);
        double y = Y0 + K0*(m-m0+n*tan*(aa*aa/2
                +(5-t+9*c+4*c*c)*Math.pow(aa,4)/24
                +(61-58*t+t*t+600*c-330*ep2)*Math.pow(aa,6)/720));
        return new double[]{x,y};
    }

    private static double[] tmInverse(double x, double y) {
        double f = 1.0 / BES_RF, e2 = f * (2-f), ep2 = e2/(1-e2);
        double m0 = meridionalArc(LAT0, BES_A, e2);
        double m1 = m0 + (y-Y0)/K0;
        double mu = m1/(BES_A*(1-e2/4-3*e2*e2/64-5*e2*e2*e2/256));
        double e1 = (1-Math.sqrt(1-e2))/(1+Math.sqrt(1-e2));
        double fp = mu
                +(3*e1/2-27*Math.pow(e1,3)/32)*Math.sin(2*mu)
                +(21*e1*e1/16-55*Math.pow(e1,4)/32)*Math.sin(4*mu)
                +(151*Math.pow(e1,3)/96)*Math.sin(6*mu)
                +(1097*Math.pow(e1,4)/512)*Math.sin(8*mu);
        double sin = Math.sin(fp), cos = Math.cos(fp), tan = Math.tan(fp);
        double c1 = ep2*cos*cos, t1 = tan*tan;
        double n1 = BES_A/Math.sqrt(1-e2*sin*sin);
        double r1 = BES_A*(1-e2)/Math.pow(1-e2*sin*sin,1.5);
        double d = (x-X0)/(n1*K0);
        double lat = fp-(n1*tan/r1)*(d*d/2
                -(5+3*t1+10*c1-4*c1*c1-9*ep2)*Math.pow(d,4)/24
                +(61+90*t1+298*c1+45*t1*t1-252*ep2-3*c1*c1)*Math.pow(d,6)/720);
        double lon = LON0+(d-(1+2*t1+c1)*Math.pow(d,3)/6
                +(5-2*c1+28*t1-3*c1*c1+8*ep2+24*t1*t1)*Math.pow(d,5)/120)/cos;
        return new double[]{Math.toDegrees(lat), Math.toDegrees(lon)};
    }
}
