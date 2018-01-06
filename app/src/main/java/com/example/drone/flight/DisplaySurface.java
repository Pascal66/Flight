package com.example.drone.flight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;


/**
 * Created by drone on 4/7/16.
 */


//Surface used to display the video `
public class DisplaySurface extends SurfaceView implements SurfaceHolder.Callback {

    private static String TAG = DisplaySurface.class.getSimpleName();
    private Mat frame;
    private Boolean first;
    private Bitmap bmp = null;


    public DisplaySurface(Context context) {
        super(context);
        getHolder().addCallback(this);
        frame = new Mat();
        first = true;
        Log.i(TAG, "created successfully");
    }

    //displays each video frames as it arrives
    @Override
    public void draw(Canvas canvas) {
            super.draw(canvas);
            int scale = 2;
             Bitmap bmp = matToBitmap(frame);
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(bmp, new Rect(0, 0, bmp.getWidth(), bmp.getHeight()),
                    new Rect((int) ((canvas.getWidth() - scale * bmp.getWidth()) / 2),
                            (int) ((canvas.getHeight() - scale * bmp.getHeight()) / 2),
                            (int) ((canvas.getWidth() - scale * bmp.getWidth()) / 2 + scale * bmp.getWidth()),
                            (int) ((canvas.getHeight() - scale * bmp.getHeight()) / 2 + scale * bmp.getHeight())), null);
        System.gc();
    }

    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {}

    public void surfaceCreated(SurfaceHolder arg0) {}

    public void surfaceDestroyed(SurfaceHolder arg0) {
        if((frame !=null) &&(bmp !=null)) {
            frame.release();
            bmp.recycle();
            System.gc();
        }
    }

    public void setDisplayFrame(Mat outFrame) {

        frame = outFrame;
    }

    private Bitmap matToBitmap(Mat mat) {

        if((mat.width()>0) &&(mat.height()>0)) {
            if (first) {
                bmp = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
                first = false;
            }
            Utils.matToBitmap(mat, bmp);
        }
        return bmp;
    }

}