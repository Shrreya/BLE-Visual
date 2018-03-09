package com.shrreya.blevisual;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class SensorMap extends View {

    private int[] values = new int[16];
    private Paint paint;
    private int height, width, xmin, xmax, xplus, ymin, ymax, yplus;
    private float radiusDivider;
    private final float sensorMaxValue = 2000.0f;

    public SensorMap(Context context) {
        super(context);

        // initialise with zero values
        for(int k = 0; k < 16; k++)
            values[k] = 0;

        // initialise paint parameters
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.FILL);

        // to create responsive sensor map
        // get screen height and width
        height = Resources.getSystem().getDisplayMetrics().heightPixels;
        width = Resources.getSystem().getDisplayMetrics().widthPixels;

        // calculate x and y parameters as percentages of screen space
        xmin = (int) (0.1388 * width);
        xplus = (int) (0.243 * width);
        xmax = (int) (0.868 * width);
        ymin = (int) (0.0836 * height);
        yplus = (int) (0.1463 * height);
        ymax = (int) (0.5226 * height);

        // according to minimum radius
        radiusDivider = sensorMaxValue / xmin;
    }

    // receiving sensor values from main activity
    public void setValues(int[][] values) {
        int k = 0;
        for(int j = 0; j < 4; j++) {
            for(int i = 0; i < 4; i++) {
                this.values[k] = values[i][j];
                ++k;
            }
        }
    }

    // normalize sensor value between 0 and 1 assuming 2000 as max value
    private float normalize(int value) {
        return value/sensorMaxValue;
    }

    // get color gradient for normalized value between blue and red
    private int getColor(float value)
    {
        int aR = 0;   int aG = 0; int aB=255;  // RGB for lower color (blue)
        int bR = 255; int bG = 0; int bB=0;    // RGB for higher color (red)

        int red   = Math.round((bR - aR) * value + aR);
        int green = Math.round((bG - aG) * value + aG);
        int blue  = Math.round((bB - aB) * value + aB);
        return Color.rgb(red, green, blue);
    }

    // get radius for bubble starting from 20
    private float getRadius(int value) {
        return value/radiusDivider + 20;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // make the entire canvas white
        paint.setColor(Color.WHITE);
        canvas.drawPaint(paint);

        // draw bubbles according to sensor values and previously calculated x and y parameters
        int k =0;
        for(int y = ymin; y <= ymax; y += yplus) {
            for(int x = xmin; x <= xmax; x += xplus) {
                paint.setColor(getColor(normalize(values[k])));
                canvas.drawCircle(x, y, getRadius(values[k]), paint);
                k++;
            }
        }
    }
}

