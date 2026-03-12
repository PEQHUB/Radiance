package com.radiance.client.util;

/**
 * CIE 1931-based spectral color computations for physically-based flame colorants.
 * Provides wavelength to BT.2020, blackbody to BT.2020, and mixed flame color computation.
 */
public class SpectralColor {

    // CIE 1931 2-degree standard observer color matching functions
    // 380-780nm, 5nm intervals, 81 entries
    private static final float[] CIE_X = {
        0.001368f, 0.002236f, 0.004243f, 0.007650f, 0.014310f,
        0.023190f, 0.043510f, 0.077630f, 0.134380f, 0.214770f,
        0.283900f, 0.328500f, 0.348280f, 0.348060f, 0.336200f,
        0.318700f, 0.290800f, 0.251100f, 0.195360f, 0.142100f,
        0.095640f, 0.058010f, 0.032010f, 0.014700f, 0.004900f,
        0.002400f, 0.009300f, 0.029100f, 0.063270f, 0.109600f,
        0.165500f, 0.225750f, 0.290400f, 0.359700f, 0.433450f,
        0.512050f, 0.594500f, 0.678400f, 0.762100f, 0.842500f,
        0.916300f, 0.978600f, 1.026300f, 1.056700f, 1.062200f,
        1.045600f, 1.002600f, 0.938400f, 0.854450f, 0.751400f,
        0.642400f, 0.541900f, 0.447900f, 0.360800f, 0.283500f,
        0.218700f, 0.164900f, 0.121200f, 0.087400f, 0.063600f,
        0.046770f, 0.032900f, 0.022700f, 0.015840f, 0.011359f,
        0.008111f, 0.005790f, 0.004109f, 0.002899f, 0.002049f,
        0.001440f, 0.001000f, 0.000690f, 0.000476f, 0.000332f,
        0.000235f, 0.000166f, 0.000117f, 0.000083f, 0.000059f,
        0.000042f
    };

    private static final float[] CIE_Y = {
        0.000039f, 0.000064f, 0.000120f, 0.000217f, 0.000396f,
        0.000640f, 0.001210f, 0.002180f, 0.004000f, 0.007300f,
        0.011600f, 0.016840f, 0.023000f, 0.029800f, 0.038000f,
        0.048000f, 0.060000f, 0.073900f, 0.090980f, 0.112600f,
        0.139020f, 0.169300f, 0.208020f, 0.258600f, 0.323000f,
        0.407300f, 0.503000f, 0.608200f, 0.710000f, 0.793200f,
        0.862000f, 0.914850f, 0.954000f, 0.980300f, 0.994950f,
        1.000000f, 0.995000f, 0.978600f, 0.952000f, 0.915400f,
        0.870000f, 0.816300f, 0.757000f, 0.694900f, 0.631000f,
        0.566800f, 0.503000f, 0.441200f, 0.381000f, 0.321000f,
        0.265000f, 0.217000f, 0.175000f, 0.138200f, 0.107000f,
        0.081600f, 0.061000f, 0.044580f, 0.032000f, 0.023200f,
        0.017000f, 0.011920f, 0.008210f, 0.005723f, 0.004102f,
        0.002929f, 0.002091f, 0.001484f, 0.001047f, 0.000740f,
        0.000520f, 0.000361f, 0.000249f, 0.000172f, 0.000120f,
        0.000085f, 0.000060f, 0.000042f, 0.000030f, 0.000021f,
        0.000015f
    };

    private static final float[] CIE_Z = {
        0.006450f, 0.010550f, 0.020050f, 0.036210f, 0.067850f,
        0.110200f, 0.207400f, 0.371300f, 0.645600f, 1.039050f,
        1.385600f, 1.622960f, 1.747060f, 1.782600f, 1.772110f,
        1.744100f, 1.669200f, 1.528100f, 1.287640f, 1.041900f,
        0.812950f, 0.616200f, 0.465180f, 0.353300f, 0.272000f,
        0.212300f, 0.158200f, 0.111700f, 0.078250f, 0.057250f,
        0.042160f, 0.029840f, 0.020300f, 0.013400f, 0.008750f,
        0.005750f, 0.003900f, 0.002750f, 0.002100f, 0.001800f,
        0.001650f, 0.001400f, 0.001100f, 0.001000f, 0.000800f,
        0.000600f, 0.000340f, 0.000240f, 0.000190f, 0.000100f,
        0.000050f, 0.000030f, 0.000020f, 0.000010f, 0.000000f,
        0.000000f, 0.000000f, 0.000000f, 0.000000f, 0.000000f,
        0.000000f, 0.000000f, 0.000000f, 0.000000f, 0.000000f,
        0.000000f, 0.000000f, 0.000000f, 0.000000f, 0.000000f,
        0.000000f, 0.000000f, 0.000000f, 0.000000f, 0.000000f,
        0.000000f, 0.000000f, 0.000000f, 0.000000f, 0.000000f,
        0.000000f
    };

    // XYZ to linear BT.2020 (ITU-R BT.2020)
    private static final float[][] XYZ_TO_BT2020 = {
        {  1.7166511880f, -0.3556707838f, -0.2533662814f },
        { -0.6666843518f,  1.6164812366f,  0.0157685458f },
        {  0.0176398574f, -0.0427706133f,  0.9421031212f }
    };

    // BT.2020 to linear BT.709
    private static final float[][] BT2020_TO_BT709 = {
        {  1.6604910021f, -0.5876411388f, -0.0728498633f },
        { -0.1245504745f,  1.1328998971f, -0.0083494226f },
        { -0.0181507634f, -0.1005789980f,  1.1187297614f }
    };

    // Planck radiation constants
    private static final float C2 = 14388.0f; // hc/k in um*K
    private static final double TWO_HC2 = 1.191042952e-16; // 2hc^2 W*m^2

    /**
     * Compute Y-normalized XYZ for a monochromatic spectral line.
     * Interpolates CIE 1931 CMF tables for sub-5nm precision.
     * @param nm Wavelength in nanometers (380-780)
     * @return float[3] XYZ, Y-normalized (Y=1)
     */
    public static float[] wavelengthToXYZ(float nm) {
        if (nm < 380 || nm > 780) return new float[]{0, 0, 0};
        float idx = (nm - 380) / 5.0f;
        int i0 = (int) idx;
        int i1 = Math.min(i0 + 1, 80);
        float t = idx - i0;
        float x = CIE_X[i0] * (1 - t) + CIE_X[i1] * t;
        float y = CIE_Y[i0] * (1 - t) + CIE_Y[i1] * t;
        float z = CIE_Z[i0] * (1 - t) + CIE_Z[i1] * t;
        if (y > 1e-10f) {
            float invY = 1.0f / y;
            x *= invY;
            z *= invY;
            y = 1.0f;
        }
        return new float[]{x, y, z};
    }

    /**
     * Compute Y-normalized XYZ for a blackbody at temperature T.
     * Integrates Planck spectral radiance x CMF over visible range (380-780nm, 5nm steps).
     * @param tempK Temperature in Kelvin
     * @return float[3] XYZ, Y-normalized (Y=1)
     */
    public static float[] blackbodyToXYZ(float tempK) {
        double sumX = 0, sumY = 0, sumZ = 0;
        for (int i = 0; i < 81; i++) {
            double lambda_m = (380.0 + i * 5.0) * 1e-9;
            double lambda_um = lambda_m * 1e6;
            double ex = C2 / (lambda_um * tempK);
            if (ex > 40.0) continue;
            double planck = TWO_HC2 / (Math.pow(lambda_m, 5) * Math.expm1(ex));
            sumX += planck * CIE_X[i];
            sumY += planck * CIE_Y[i];
            sumZ += planck * CIE_Z[i];
        }
        if (sumY > 1e-30) {
            double invY = 1.0 / sumY;
            return new float[]{(float)(sumX * invY), 1.0f, (float)(sumZ * invY)};
        }
        return new float[]{0, 0, 0};
    }

    /**
     * Convert CIE XYZ to linear BT.2020. Clamps negative components to 0.
     */
    public static float[] xyzToBT2020(float[] xyz) {
        float r = XYZ_TO_BT2020[0][0]*xyz[0] + XYZ_TO_BT2020[0][1]*xyz[1] + XYZ_TO_BT2020[0][2]*xyz[2];
        float g = XYZ_TO_BT2020[1][0]*xyz[0] + XYZ_TO_BT2020[1][1]*xyz[1] + XYZ_TO_BT2020[1][2]*xyz[2];
        float b = XYZ_TO_BT2020[2][0]*xyz[0] + XYZ_TO_BT2020[2][1]*xyz[1] + XYZ_TO_BT2020[2][2]*xyz[2];
        return new float[]{Math.max(r, 0), Math.max(g, 0), Math.max(b, 0)};
    }

    /**
     * Convert linear BT.2020 to linear BT.709. Clamps negative components to 0.
     */
    public static float[] bt2020ToBT709(float[] bt2020) {
        float r = BT2020_TO_BT709[0][0]*bt2020[0] + BT2020_TO_BT709[0][1]*bt2020[1] + BT2020_TO_BT709[0][2]*bt2020[2];
        float g = BT2020_TO_BT709[1][0]*bt2020[0] + BT2020_TO_BT709[1][1]*bt2020[1] + BT2020_TO_BT709[1][2]*bt2020[2];
        float b = BT2020_TO_BT709[2][0]*bt2020[0] + BT2020_TO_BT709[2][1]*bt2020[1] + BT2020_TO_BT709[2][2]*bt2020[2];
        return new float[]{Math.max(r, 0), Math.max(g, 0), Math.max(b, 0)};
    }

    /**
     * Compute flame color in BT.2020, normalized to max component = 1.
     * Blends blackbody chromaticity with spectral line chromaticity by purity.
     * @param tempK    Temperature in Kelvin
     * @param wavelengthNm Dominant wavelength in nm (0 = pure blackbody)
     * @param purity   Blend factor 0-1 (0 = pure blackbody, 1 = pure spectral)
     * @return float[3] BT.2020 RGB, max component = 1
     */
    public static float[] computeFlameColor(float tempK, int wavelengthNm, float purity) {
        float[] bbXyz = blackbodyToXYZ(tempK);
        float[] bb = xyzToBT2020(bbXyz);
        normalize(bb);

        if (wavelengthNm <= 0 || purity <= 0.0f) {
            return bb;
        }

        float[] specXyz = wavelengthToXYZ((float) wavelengthNm);
        float[] spec = xyzToBT2020(specXyz);
        normalize(spec);

        float p = Math.max(0, Math.min(1, purity));
        float[] result = {
            bb[0] * (1 - p) + spec[0] * p,
            bb[1] * (1 - p) + spec[1] * p,
            bb[2] * (1 - p) + spec[2] * p
        };
        normalize(result);
        return result;
    }

    private static void normalize(float[] rgb) {
        float maxC = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (maxC > 0) {
            rgb[0] /= maxC;
            rgb[1] /= maxC;
            rgb[2] /= maxC;
        }
    }
}
