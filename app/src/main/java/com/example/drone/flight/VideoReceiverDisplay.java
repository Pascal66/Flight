package com.example.drone.flight;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.RelativeLayout;


import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener;
import com.parrot.arsdk.arcontroller.ARFrame;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Created by drone on 2/28/16.
 */
public class VideoReceiverDisplay implements ARDeviceControllerStreamListener, SurfaceHolder.Callback{

    private static String TAG = VideoReceiverDisplay.class.getSimpleName();

    // video Decoder vars
    private  static final String VIDEO_MIME_TYPE = "video/avc";
    private static final int VIDEO_DEQUEUE_TIMEOUT = 33000;
    private static final int VIDEO_WIDTH =640;
    private static final int VIDEO_HEIGHT =368;
    private static MediaCodec decoder;
    private Lock readyLock;
    private DisplaySurface surfView;
    private boolean isCodecConfigured = false;
    private  ByteBuffer spsBuffer;
    private  ByteBuffer ppsBuffer;
    private  ByteBuffer [] buffers;


    //Thread Vars
    Boolean startImageproc;



    public VideoReceiverDisplay(){
        ControlActivity.deviceController.addStreamListener(this);
        initVideoVars();
        startImageproc =true;


    }

    @Override
    public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, ARControllerCodec codec)
    {
        readyLock.lock();

        if ((decoder != null))
        {
            if (!isCodecConfigured)
            {
                if (codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264)
                {
                    ARControllerCodec.H264 codecH264 = codec.getAsH264();

                    spsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
                    ppsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());



                    if ((spsBuffer != null) ) {
                        configureMediaCodec();
                    }
                }
            }
        }

        readyLock.unlock();

        return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
    }

    @Override
    public ARCONTROLLER_ERROR_ENUM onFrameReceived(ARDeviceController deviceController, ARFrame frame)
    {
        readyLock.lock();

        if ((decoder != null))
        {
            if (isCodecConfigured)
            {
                // Here we have either a good PFrame, or an IFrame
                int index = -1;


                try
                {
                    index = decoder.dequeueInputBuffer(VIDEO_DEQUEUE_TIMEOUT);
                }
                catch (IllegalStateException e)
                {
                    Log.e(TAG, "Error while dequeue input buffer");
                }
                if ((index >= 0))
                {
                    ByteBuffer buff = buffers[index];
                    buff.clear();
                    buff.put(frame.getByteData(), 0, frame.getDataSize());

                    try
                    {
                        decoder.queueInputBuffer(index, 0, frame.getDataSize(), 0, 0);

                    }
                    catch (IllegalStateException e)
                    {
                        Log.e(TAG, "Error while queue input buffer");
                    }
                }
            }

            //display previous frame on the surfaceview
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outIndex = -1;
            Image rawImage;
            try
            {
                outIndex = decoder.dequeueOutputBuffer(info, 0);

                while (outIndex >= 0)
                {
                    rawImage = decoder.getOutputImage(outIndex);
                    setRawMatFrame(rawImage);
                    decoder.releaseOutputBuffer(outIndex, true);
                    outIndex = decoder.dequeueOutputBuffer(info, 0);

                }
            }
            catch (IllegalStateException e)
            {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }


        readyLock.unlock();

        return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
    }

    @Override
    public void onFrameTimeout(ARDeviceController deviceController)
    {
       Log.i(TAG, "onFrameTimeout ..... ");
    }


    /**
     * Configure and start media codec
     * media codec uses input buffer and output buffer
     * linked to surface view
     * @param type
     */
    @SuppressLint("NewApi")
    public  void initMediaCodec(String type)
    {
        try
        {
            decoder = MediaCodec.createDecoderByType(type);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if ((spsBuffer != null))
        {
            configureMediaCodec();
        }
    }

    @SuppressLint("NewApi")
    private  void configureMediaCodec()
    {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setByteBuffer("csd-0", spsBuffer);
        format.setByteBuffer("csd-1", ppsBuffer);
        decoder.configure(format, null, null, 0);

        decoder.start();

        buffers = decoder.getInputBuffers();

        isCodecConfigured = true;
    }

    @SuppressLint("NewApi")
    public  void releaseMediaCodec()
    {
        if ((decoder != null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN))
        {
            if (isCodecConfigured)
            {
                decoder.stop();
                decoder.release();
            }
            isCodecConfigured = false;
            decoder = null;

        }
    }

    public void setRawMatFrame(Image image)
    {
        Mat yuvMat;
        Mat bgrMat = new Mat(ControlActivity.view.getHeight(), ControlActivity.view.getHeight(), CvType.CV_8UC4);

        //process ARframe to convert to Mat type
        yuvMat = imageToMat(image);
        image.close();
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2RGB_I420);

        //Display video
        displayVideo(bgrMat);

        //Start thread
        ThreadComm.setFrame(bgrMat);
        if(startImageproc){
            startImproc();
            startImageproc = false;
        }

        //release memory
        yuvMat.release();
        System.gc(); // force garbage collection
    }

    //Converts android image type to openCV Mat
    private Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        // Finally, create the Mat.
        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);
        //System.gc(); // force garbage collection

        return mat;
    }


    //video display

    private void videoDisplaySetup()
    {
        String deviceModel = Build.DEVICE;
        Log.d(TAG, "configuring HW video codec for device: [" + deviceModel + "]");
        surfView = new DisplaySurface(ControlActivity.mContext);
        surfView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        surfView.getHolder().addCallback(this);
        ControlActivity.view.addView(surfView, 0);

    }

    public void initVideoVars()
    {
        readyLock = new ReentrantLock();
        videoDisplaySetup();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        readyLock.lock();
        initMediaCodec(VIDEO_MIME_TYPE);
        readyLock.unlock();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
    }

    @SuppressLint("NewApi")
    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        readyLock.lock();
        releaseMediaCodec();
        readyLock.unlock();
    }

    @SuppressLint("NewApi")
    public void reset()
    {
        /* This will be run either before or after decoding a frame. */
        readyLock.lock();

        ControlActivity.view.removeView(surfView);
        surfView = null;

        releaseMediaCodec();

        readyLock.unlock();
    }

    //Display the Video stream
    private void displayVideo(Mat frame){

        surfView.setDisplayFrame(frame);
        Canvas c;
        c = null;
        try {
            c = surfView.getHolder().lockCanvas(null);
            synchronized (surfView.getHolder()) {
                surfView.draw(c);

            }
        } finally {
            if (c != null) {
                surfView.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    private void startImproc()
    {
        //threads
        Runnable obstacleMap;
        Thread thread1;
        obstacleMap = new ObstacleMap();
        thread1 = new Thread(obstacleMap);
        thread1.start();
    }

}
