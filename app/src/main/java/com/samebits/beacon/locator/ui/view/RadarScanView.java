/*
 *
 *  Copyright (c) 2015 SameBits UG. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.samebits.beacon.locator.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.samebits.beacon.locator.R;
import com.samebits.beacon.locator.model.DetectedBeacon;
import com.samebits.beacon.locator.util.NeuralNet;

import org.altbeacon.beacon.Beacon;
import java.io.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class RadarScanView extends View {

    private float mDistanceRatio = 1.0f;
    private Context mContext;
    private WindowManager mWindowManager;
    private Map<String, DetectedBeacon> mBeacons = new LinkedHashMap();
    private boolean mHaveDetected = false;
    private TextView mInfoView;
    private Rect mTextBounds = new Rect();
    private Paint mGridPaint;
    private Paint mErasePaint;
    private Bitmap mBlip;
    private boolean mUseMetric;
    private NeuralNet neuralNet = new NeuralNet();
    private File csvFile;
    private String dataFolder = "/beaconData";
    private String filePrefix = "data";
    /**
     * Used to draw the animated ring that sweeps out from the center
     */
    private Paint mSweepPaint0;
    /**
     * Used to draw the animated ring that sweeps out from the center
     */
    private Paint mSweepPaint1;
    /**
     * Used to draw the animated ring that sweeps out from the center
     */
    private Paint mSweepPaint2;
    /**
     * Used to draw a beacon
     */
    private Paint mBeaconPaint;
    /**
     * Time in millis when the most recent sweep began
     */
    private long mSweepTime;
    /**
     * True if the sweep has not yet intersected the blip
     */
    private boolean mSweepBefore;
    /**
     * Time in millis when the sweep last crossed the blip
     */
    private long mBlipTime;

    private long prevTime;
    private Random r1;
    private int[] blipXPos, blipYPos;
    private int pos;
    private int currentPosition;
    private int fileNumber;
    private int numValues;

    public RadarScanView(Context context) {
        this(context, null);
    }

    public RadarScanView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadarScanView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        // Paint used for the rings and ring text
        mGridPaint = new Paint();
        mGridPaint.setColor(getResources().getColor(R.color.hn_orange_lighter));

        mGridPaint.setAntiAlias(true);
        mGridPaint.setStyle(Style.STROKE);
        mGridPaint.setStrokeWidth(4.0f);
        mGridPaint.setTextSize(16.0f);
        mGridPaint.setTextAlign(Align.CENTER);

        // Paint used to erase the rectangle behind the ring text
        mErasePaint = new Paint();
        mErasePaint.setColor(getResources().getColor(R.color.white));
        //mErasePaint.setColor(getResources().getColor(R.color.hn_orange_lighter));

        mErasePaint.setStyle(Style.FILL);

        mBeaconPaint = new Paint();
        mBeaconPaint.setColor(getResources().getColor(R.color.white));
        mBeaconPaint.setAntiAlias(true);
        mBeaconPaint.setStyle(Style.FILL_AND_STROKE);

        // Outer ring of the sweep
        mSweepPaint0 = new Paint();
        mSweepPaint0.setColor(ContextCompat.getColor(context, R.color.hn_orange));
        mSweepPaint0.setAntiAlias(true);
        mSweepPaint0.setStyle(Style.STROKE);
        mSweepPaint0.setStrokeWidth(3f);

        // Middle ring of the sweep
        mSweepPaint1 = new Paint();
        mSweepPaint1.setColor(ContextCompat.getColor(context, R.color.hn_orange));

        mSweepPaint1.setAntiAlias(true);
        mSweepPaint1.setStyle(Style.STROKE);
        mSweepPaint1.setStrokeWidth(3f);

        // Inner ring of the sweep
        mSweepPaint2 = new Paint();
        mSweepPaint2.setColor(ContextCompat.getColor(context, R.color.hn_orange));

        mSweepPaint2.setAntiAlias(true);
        mSweepPaint2.setStyle(Style.STROKE);
        mSweepPaint2.setStrokeWidth(3f);

        mBlip = ((BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.ic_location_on_black_24dp)).getBitmap();

        blipXPos = new int[16];
        blipYPos = new int[16];
        prevTime = 0;
        pos = 0;
        r1 = new Random();

        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "beaconData");
        if (!f.exists()) {
            f.mkdirs();
        }

        File[] files = f.listFiles();
        fileNumber = 0;
        for(int i = 0; i < files.length; i++){
            String name = files[i].getName();
            int num = Integer.parseInt(name.substring(filePrefix.length(), name.length()-4));
            if(fileNumber < num)
                fileNumber = num;
        }
        System.out.println(fileNumber);

    }

    /**
     * Sets the view that we will use to report distance
     *     * @param t The text view used to report distance
     */
    public void setDistanceView(TextView t) {
        mInfoView = t;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int center = getWidth() / 2;
        int radius = center - 8;
        int x0 = 150;
        int y0 = 20;
        int gridSize = (getWidth() - 2*x0)/4;

        int idx = 0;
        for(int i = 3; i >= 0; i--)
        {
            int ypos = y0 + (gridSize/2) + (i*gridSize);
            for(int j = 0; j < 4; j++)
            {
                int xpos = x0 + (gridSize/2) + (j*gridSize);
                blipXPos[idx] = xpos;
                blipYPos[idx] = ypos;
                idx++;
            }
        }
        final Paint gridPaint = mGridPaint;
        int blipRadius = (int) (mDistanceRatio * radius);

        final long now = SystemClock.uptimeMillis();
        if (mSweepTime > 0) {
            // Draw the sweep. Radius is determined by how long ago it started
            long sweepDifference = now - mSweepTime;
            if (sweepDifference < 512L) {
                int sweepRadius = (int) (((radius + 6) * sweepDifference) >> 9);
                canvas.drawCircle(center, center, sweepRadius, mSweepPaint0);
                canvas.drawCircle(center, center, sweepRadius - 2, mSweepPaint1);
                canvas.drawCircle(center, center, sweepRadius - 4, mSweepPaint2);

                // Note when the sweep has passed the blip
                boolean before = sweepRadius < blipRadius;
                if (!before && mSweepBefore) {
                    mSweepBefore = false;
                    mBlipTime = now;
                }
            } else {
                mSweepTime = now + 1000;
                mSweepBefore = true;
            }
            postInvalidate();
        }

        //draw horizontal lines
        canvas.drawLine(x0, y0 + 0*gridSize, x0 + 4*gridSize, y0 + 0*gridSize, gridPaint);
        canvas.drawLine(x0, y0 + 1*gridSize, x0 + 4*gridSize, y0 + 1*gridSize, gridPaint);
        canvas.drawLine(x0, y0 + 2*gridSize, x0 + 4*gridSize, y0 + 2*gridSize, gridPaint);
        canvas.drawLine(x0, y0 + 3*gridSize, x0 + 4*gridSize, y0 + 3*gridSize, gridPaint);
        canvas.drawLine(x0, y0 + 4*gridSize, x0 + 4*gridSize, y0 + 4*gridSize, gridPaint);

        //draw vertical lines
        canvas.drawLine(x0 + 0*gridSize, y0 , x0 + 0*gridSize, y0 + 4*gridSize, gridPaint);
        canvas.drawLine(x0 + 1*gridSize, y0 , x0 + 1*gridSize, y0 + 4*gridSize, gridPaint);
        canvas.drawLine(x0 + 2*gridSize, y0 , x0 + 2*gridSize, y0 + 4*gridSize, gridPaint);
        canvas.drawLine(x0 + 3*gridSize, y0 , x0 + 3*gridSize, y0 + 4*gridSize, gridPaint);
        canvas.drawLine(x0 + 4*gridSize, y0 , x0 + 4*gridSize, y0 + 4*gridSize, gridPaint);


        if (mHaveDetected) {

            // Draw the blip. Alpha is based on how long ago the sweep crossed the blip
            long blipDifference = now - mBlipTime;
            gridPaint.setAlpha(255 - (int) ((128 * blipDifference) >> 10));

            for (Map.Entry<String, DetectedBeacon> entry : mBeacons.entrySet()) {
                //String key = entry.getKey();
                DetectedBeacon dBeacon = entry.getValue();
                //System.out.println("value: " + dBeacon);
            }

            canvas.drawBitmap(mBlip, blipXPos[currentPosition]-mBlip.getWidth()/2, blipYPos[currentPosition]-mBlip.getWidth()/2, gridPaint);

            gridPaint.setAlpha(255);
        }
    }

    private void insertBeacons(Collection<Beacon> beacons) {
        Iterator<Beacon> iterator = beacons.iterator();
        boolean updatePos = false;
        double[] dist = new double[4];
        int distIndex = 0;
        if(beacons.size() == 4)
            updatePos = true;

        while (iterator.hasNext()) {
            DetectedBeacon dBeacon = new DetectedBeacon(iterator.next());
            dBeacon.setTimeLastSeen(System.currentTimeMillis());
            this.mBeacons.put(dBeacon.getId(), dBeacon);
            if(updatePos) {
                int dBeaconMinorId = dBeacon.getId3().toInt();
                if(dBeaconMinorId > 64000 && dBeaconMinorId < 64006) {
                    distIndex = dBeaconMinorId - 64001;
                    if(distIndex == 4)
                        distIndex = 3;
                    dist[distIndex] = dBeacon.getDistance()*3;
                    System.out.println(distIndex + ":" + dist[distIndex]);
                }
            }
        }

        if(updatePos){
            String fileContents = "";
            for(int i = 0; i < 4; i++)
                fileContents += dist[i] + ",";

            currentPosition = neuralNet.evaluatePosition(dist);

            fileContents += currentPosition + "\n";

            try {
                FileOutputStream outputStream = new FileOutputStream(csvFile, true);
                OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                writer.append(fileContents);
                writer.close();
                outputStream.close();
                numValues = numValues + 1;
            } catch (Exception e) {
                e.printStackTrace();
            }


            updateDistances();
        }
    }

    public void onDetectedBeacons(final Collection<Beacon> beacons) {

        insertBeacons(beacons);

        //updateDistances();

        updateBeaconsInfo(beacons);

        invalidate();
    }

    private void updateBeaconsInfo(final Collection<Beacon> beacons) {
        mInfoView.setText(beacons.size() + " beacons. Valid Samples = " + numValues);
    }

    /**
     * update on radar
     */
    private void updateDistances() {
        if (!mHaveDetected) {
            mHaveDetected = true;
        }
    }

    /**
     * Turn on the sweep animation starting with the next draw
     */
    public void startSweep() {
        mInfoView.setText("Writing to data" + (fileNumber+1) + ".csv");
        csvFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)+dataFolder, filePrefix+(fileNumber+1)+".csv");
        numValues = 0;
        mSweepTime = SystemClock.uptimeMillis();
        mSweepBefore = true;
    }

    /**
     * Turn off the sweep animation
     */
    public void stopSweep() {
        mSweepTime = 0L;
        fileNumber = fileNumber + 1;
        mInfoView.setText("Stopped");
    }

}
