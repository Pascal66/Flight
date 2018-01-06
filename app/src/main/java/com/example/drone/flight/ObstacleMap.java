package com.example.drone.flight;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by drone on 2/28/16.
 */
public class ObstacleMap implements Runnable {


    private static final String  TAG = "obstacleDetection";
    private final static int scaleFactor = 3;
    public static boolean stop;
    private Size size;


    //obstacle
    public static Mat inputFrame;
    Mat prevFrame;
    List<Mat> hsv;
    List<Mat> xy;
    Mat rawFlow;
    Mat magnitude;
    Mat angle;
    Mat hsvMap;
    Mat magFlow;
    Mat obstMap;

    //Path calculation
    Rect[] windows;
    boolean windowsSet;
    private int path = 5;



    ObstacleMap()
    {
        stop = false;
        Log.i(TAG, "created successfully");

    }


    @Override
    public void run()
    {
        Log.i(TAG, "started running");
        initMotionDetection();

        while(!stop)
        {
            inputFrame = ThreadComm.getFrame().clone();
            if((inputFrame !=null) &&(inputFrame.width() >0) &&(inputFrame.height()>0)){
                detectMotion(inputFrame);
                magFlow.copyTo(obstMap);
                calcPath(obstMap);
                inputFrame.release();
            }

        }
        releaseMemory();
    }


    //Initialize motion detection variables
    private void initMotionDetection()
    {
        inputFrame = new Mat();
        prevFrame = new Mat();

        magnitude = new Mat();
        angle = new Mat();
        hsvMap = new Mat();
        obstMap = new Mat();
        magFlow = new Mat(); // magnitute of motion
        hsv = new ArrayList<>(3);
        xy = new ArrayList<>(2);

        windows = new Rect[5];
        windowsSet = false;
    }


    //detect relative motion using dense optical flow
    private void detectMotion(Mat currentFrame) {

        Imgproc.cvtColor(currentFrame, currentFrame, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.resize(currentFrame, currentFrame, new Size(currentFrame.width()/scaleFactor,currentFrame.height()/scaleFactor));

        if(prevFrame.empty())
        {
            currentFrame.copyTo(prevFrame);
            rawFlow = new Mat(currentFrame.size(), CvType.CV_32FC2);
            //Imgproc.resize(rawFlow, rawFlow, new Size(currentFrame.width()/scaleFactor,currentFrame.height()/scaleFactor));
        }

        Video.calcOpticalFlowFarneback(prevFrame, currentFrame, rawFlow, 0.5, 2, 18, 1,
                5, 1.2, Video.OPTFLOW_USE_INITIAL_FLOW);
        currentFrame.copyTo(prevFrame);

        //extraxt x and y channels
        Core.split(rawFlow, xy);
        //calculate angle and magnitude
        Core.cartToPolar(xy.get(0), xy.get(1), magnitude, angle, true);
        //translate magnitude to range [0;1]
        Core.MinMaxLocResult mmr = Core.minMaxLoc(magnitude);
        magnitude.convertTo(magnitude, -1, 255.0 / mmr.maxVal);

        //build hsv image
        Mat matOfOnes = Mat.ones(angle.size(), CvType.CV_32F);
        Mat matOfZeros = Mat.zeros(angle.size(), CvType.CV_32F);
        hsv.add(0, matOfZeros);
        hsv.add(1, matOfOnes);
        hsv.add(2, magnitude);
        Core.merge(hsv, hsvMap);
        hsv.clear();
        //convert hsv to BGR
        Imgproc.cvtColor(hsvMap, magFlow, Imgproc.COLOR_HSV2BGR);
        magFlow.convertTo(magFlow, CvType.CV_8UC3);

        //apply mask to reduce noise
        Mat mask = new Mat(magFlow.size(),magFlow.type());
        mask.setTo(new Scalar(0,0,255));
        Core.bitwise_and(mask,magFlow,magFlow);

        mask.release();
        matOfOnes.release();
        matOfZeros.release();
        System.gc();
    }


    //calculate path
    //determines the region with least obstacles in the frame
    private  void calcPath(Mat image) {

        if(!windowsSet)
        {
            int width = image.width();
            int height = image.height();
            windows[0] = new Rect(new Point(0,0),new Point(width/2 , height/2));
            windows[1]= new Rect(new Point(width/2,0),new Point(width, height/2));
            windows[2] = new Rect(new Point(0,height/2),new Point(width/2 , height));
            windows[3]= new Rect(new Point(width/2,height/2),new Point(width,height));
            windows[4] = new Rect(new Point(width/4, height/4),new Point(3*width/4,3*height/4));
            windowsSet = true;
        }

        int count= 1000000000;
        int rt =0;

        Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2GRAY);
        for(int i=0;i<windows.length;i++)
        {
            if(Core.countNonZero(image.submat(windows[i]))<count)
            {
                count =Core.countNonZero(image.submat(windows[i]));
                rt =i;
            }
        }

        path = rt;
        System.out.println("Rectangle = " + rt);
       // System.out.println("Count = " + count);
    }


    // return the calculated path to the calling func/object
    public int getPath()
    {
        return path;
    }



    //release memory when the thread terminates
    private void releaseMemory()
    {
        if(inputFrame !=null){inputFrame.release();}
        if(prevFrame != null){prevFrame .release();}
        if(magnitude !=null){magnitude .release();}
        if(angle != null){angle.release();}
        if(hsvMap != null){hsvMap .release();}
        if(obstMap !=null){obstMap.release();}
        if(magFlow !=null){magFlow .release();}
        hsv.clear();
        xy.clear();

        System.gc();
    }

}
