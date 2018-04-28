package com.samebits.beacon.locator.util;

public class NeuralNet {
    private static int inputSize = 4;
    private static int outputSize = 16;

    private double[] inputOffset = {2.78, 3.478, 2.23, 2.78};
    private double[] inputGain = {0.089, 0.099, 0.087, 0.089};

    private double[] b1 = {0.26, -1.09, -0.13, 0.68};

    private double[][] iw = {
            {0.57, 1.70, 0.33, 0.93},
            {-2.19, 0.61, -2.71, 0.75},
            {1.91, 0.57, -1.78, -0.94},
            {0.81, -1.29, -0.69, 1.56}
    };

    private double[] b2 = {-0.21, 4.95, -0.80, -3.75, 0.05, 1.66, 2.58, 0.70, 3.11,
            -2.49, -1.86, 1.45, -2.51, -3.54, 6.29, -3.13};
    private double[][]lw = {
            {1.37, 1.18, -9.71, -7.26},
            {-1.97, 1.26, -4.32, -4.16},
            {6.09, -6.62, -8.66, 0.25},
            {-8.39, -5.72, -3.65, 4.90},
            {4.52, 4.62, -4.09, 0.10},
            {-6.59, 4.47, 4.91, 1.36},
            {-4.13, -1.41, 0.05, 5.74},
            {-0.97, -1.24, -1.61, 7.07},
            {1.95, 1.51, 1.19, 3.89},
            {-0.75, 10.06, 3.58, 1.89},
            {8.30, -4.37, 2.34, 4.09},
            {2.25, -8.11, -1.00, -3.90},
            {3.97, 5.05, 6.08, 0.86},
            {-3.01, 8.68, 8.92, -3.48},
            {-1.42, -2.22, 1.47, -1.85},
            {-3.90, -8.55, 4.11, -6.40}
    };

    public int evaluatePosition(double []xin)
    {
        double[] xp1 = new double[inputSize];
        double[] n1 = new double[inputSize];
        double[] a2 = new double[outputSize];
        double[] y = new double[outputSize];

        for(int i = 0; i < inputSize; i++)
        {
            xp1[i] = (xin[i] - inputOffset[i]) * inputGain[i] - 1;
        }

        double[] in1 = {0, 0, 0, 0};
        for(int i = 0; i < inputSize; i++)
        {
            for(int j = 0; j < inputSize; j++)
            {
                in1[i] += iw[i][j]*xp1[j];
            }
        }

        for(int i = 0; i < inputSize; i++)
        {
            double n = b1[i] + in1[i];
            n1[i] = 2 / (1 + Math.exp(-2 * n)) - 1;
        }

        double[] in2 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for(int i = 0; i < outputSize; i++)
        {
            for(int j = 0; j < inputSize; j++)
            {
                in2[i] += lw[i][j]*n1[j];
            }
        }

        double a2max = Double.NEGATIVE_INFINITY;
        for(int i = 0; i < outputSize; i++)
        {
            a2[i] = b2[i] + in2[i];
            if(a2[i] > a2max)
                a2max = a2[i];
        }

        double ysum = 0;
        for(int i = 0; i < outputSize; i++)
        {
            a2[i] -= a2max;
            y[i] = Math.exp(a2[i]);
            ysum += y[i];
        }

        double ymax = Double.NEGATIVE_INFINITY;
        for(int i = 0; i < outputSize; i++)
        {
            y[i] /= ysum;
            if(y[i] > ymax)
                ymax = y[i];
        }

        for(int i = 0; i < outputSize; i++)
        {
            y[i] /= ymax;
            if(Math.floor(y[i]) == 1)
                return i;
        }

        return -1;
    }
}
