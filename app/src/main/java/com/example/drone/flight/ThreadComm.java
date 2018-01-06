/**
 * Created by poshnyamane on 1/24/16.
 * Interfaces different classes and does data conversion
 *
 */

package com.example.drone.flight;


import android.util.Log;
import org.opencv.core.Mat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;



public class ThreadComm {

    private static final String  TAG = "threadCommunication";
    private static final  int  queueSize =5;
    public static BlockingQueue <Mat> frameQueue= new ArrayBlockingQueue<>(queueSize);
    private static Mat outFrame = new Mat();
    private  static  BlockingQueue<Mat> frames = new ArrayBlockingQueue<Mat>(10);



    ThreadComm()
    {
        Log.i(TAG, "created successfully");

    }


   public static void setFrame(Mat frame)
   {
       try{
           if(frameQueue.size() == queueSize){
              // frameQueue.clear();
           }else{frameQueue.put(frame);}

       }catch (InterruptedException e){
           e.printStackTrace();
       }
   }

    public static Mat getFrame()
    {

        try {
            outFrame= frameQueue.take();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
       return outFrame;
    }
}
