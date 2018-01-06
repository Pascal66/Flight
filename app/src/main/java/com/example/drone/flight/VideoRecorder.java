package com.example.drone.flight;

import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.sax.StartElementListener;
import android.util.Log;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.ardatatransfer.ARDATATRANSFER_ERROR_ENUM;
import com.parrot.arsdk.ardatatransfer.ARDataTransferException;
import com.parrot.arsdk.ardatatransfer.ARDataTransferManager;
import com.parrot.arsdk.armedia.ARMediaObject;
import com.parrot.arsdk.arutils.ARUtilsException;
import com.parrot.arsdk.arutils.ARUtilsFtpConnection;
import com.parrot.arsdk.arutils.ARUtilsManager;

import java.util.ArrayList;

/**
 * Created by drone on 3/28/16.
 */
public class VideoRecorder {

    private static final String  TAG = "Video Recorder";

    private static final int DEVICE_PORT = 21;
    private static final String MEDIA_FOLDER = "internal_000";

    private AsyncTask<Void, Float, ArrayList<ARMediaObject>> getMediaAsyncTask;
    private AsyncTask<Void, Float, Void> getThumbnailAsyncTask;
    private Handler mFileTransferThreadHandler;
    private HandlerThread mFileTransferThread;
    private boolean isRunning = false;
    private boolean isDownloading = false;
    private final Object lock = new Object();

    private ARDataTransferManager dataTransferManager;
    private ARUtilsManager ftpListManager;
    private ARUtilsManager ftpQueueManager;

    ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM record = ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_START;


    VideoRecorder()
    {
        Log.i(TAG, " created successfully");
    }

    public void record() {
        if ((ControlActivity.deviceController != null) && (ControlActivity.deviceController.getFeatureARDrone3() != null)) {

            ControlActivity.deviceController.getFeatureARDrone3().sendMediaRecordVideoV2(record);
        }
    }

    private void createDataTransferManager() {
        String productIP = "192.168.42.1";  // TODO: get this address from libARController

        ARDATATRANSFER_ERROR_ENUM result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK;
        try
        {
            dataTransferManager = new ARDataTransferManager();
        }
        catch (ARDataTransferException e)
        {
            e.printStackTrace();
            result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_ERROR;
        }

        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            try
            {
                ftpListManager = new ARUtilsManager();
                ftpQueueManager = new ARUtilsManager();

                ftpListManager.initWifiFtp(productIP, DEVICE_PORT, ARUtilsFtpConnection.FTP_ANONYMOUS, "");
                ftpQueueManager.initWifiFtp(productIP, DEVICE_PORT, ARUtilsFtpConnection.FTP_ANONYMOUS, "");
            }
            catch (ARUtilsException e)
            {
                e.printStackTrace();
                result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_ERROR_FTP;
            }
        }
        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            // direct to external directory
            String externalDirectory = Environment.getExternalStorageDirectory().toString().concat("/ARSDKMedias/");
            try
            {
                dataTransferManager.getARDataTransferMediasDownloader().createMediasDownloader(ftpListManager, ftpQueueManager, MEDIA_FOLDER, externalDirectory);
            }
            catch (ARDataTransferException e)
            {
                e.printStackTrace();
                result = e.getError();
            }
        }

        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            // create a thread for the download to run the download runnable
            mFileTransferThread = new HandlerThread("FileTransferThread");
            mFileTransferThread.start();
            mFileTransferThreadHandler = new Handler(mFileTransferThread.getLooper());
        }

        if (result != ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            // clean up here because an error happened
        }
    }

}
