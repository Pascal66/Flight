package com.example.drone.flight;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.content.IntentFilter;

import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import java.util.HashMap;
import java.util.Iterator;




public class ControlActivity extends Activity implements ARDeviceControllerListener
{
    private static String TAG = ControlActivity.class.getSimpleName();
    public static String EXTRA_DEVICE_SERVICE = "pilotingActivity.extra.device.service";
    public static Context mContext;


    public static ARDeviceController deviceController;
    public ARDiscoveryDeviceService service;
    public ARDiscoveryDevice device;

    private Button emergencyBt;
    private Button takeoffBt;
    private Button landingBt;

    private Button gazUpBt;
    private Button gazDownBt;
    private Button yawLeftBt;
    private Button yawRightBt;

    private Button forwardBt;
    private Button backBt;
    private Button rollLeftBt;
    private Button rollRightBt;

    private Button autonomousBt;
    private Button manualBt;

    private TextView batteryLabel;
    private TextView rollLabel;
    private TextView yawLabel;

    private AlertDialog alertDialog;

    public static RelativeLayout view;

    // video vars
    private VideoReceiverDisplay videoReceiverDisplay;


    private HashMap<UsbDevice, UsbDataBinder> mHashMap = new HashMap<UsbDevice, UsbDataBinder>();
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.mContext = this;
        setContentView(R.layout.activity_piloting);


        initIHM();

        Intent intent = getIntent();
        service = intent.getParcelableExtra(EXTRA_DEVICE_SERVICE);


        try
        {
            device = new ARDiscoveryDevice();
            ARDiscoveryDeviceNetService netDeviceService = (ARDiscoveryDeviceNetService) service.getDevice();
            ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());

            device.initWifi(product, netDeviceService.getName(), netDeviceService.getIp(), netDeviceService.getPort());
        }
        catch (ARDiscoveryException e)
        {
            e.printStackTrace();
            Log.e(TAG, "Error: " + e.getError());
        }


        if (device != null)
        {
            try
            {
                //create the deviceController
                deviceController = new ARDeviceController (device);
                deviceController.addListener(this);

                //initialize video stream listener and add the stream listener to devicecontroller
                videoReceiverDisplay = new VideoReceiverDisplay();


            }
            catch (ARControllerException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void usbConnection() {
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbAttachReceiver , filter);
        filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbDetachReceiver , filter);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        showDevices();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
// Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbDetachReceiver);
        unregisterReceiver(mUsbAttachReceiver);
        unregisterReceiver(mUsbReceiver);
    };

    BroadcastReceiver mUsbDetachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
// call your method that cleans up and closes communication with the device
                    UsbDataBinder binder = mHashMap.get(device);
                    if (binder != null) {
                        binder.onDestroy();
                        mHashMap.remove(device);
                    }
                }
            }
        }
    };

    BroadcastReceiver mUsbAttachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                showDevices();
            }
        }
    };

    private static final String ACTION_USB_PERMISSION = "com.dynamsoft.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
// call method to set up device communication
                            UsbDataBinder binder = new UsbDataBinder(mUsbManager, device);
                            mHashMap.put(device, binder);
                        }
                    } else {
// Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void showDevices() {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            mUsbManager.requestPermission(device, mPermissionIntent);
            //your code

        }
    }

    @Override
    protected void onResume() {
// TODO Auto-generated method stub
        super.onResume();
    }

    private void initIHM ()
    {
        view = (RelativeLayout) findViewById(R.id.piloting_view);
        rollLabel = (TextView) findViewById(R.id.rollLabel);
        //rollLabel.setTextColor(0xff0000ff);
        yawLabel = (TextView) findViewById(R.id.yawLabel);
        //yawLabel.setTextColor(0xff0000ff);
        batteryLabel = (TextView) findViewById(R.id.batteryLabel);


        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbConnection();

        emergencyBt = (Button) findViewById(R.id.emergencyBt);
        emergencyBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                if ((deviceController != null) && (deviceController.getFeatureARDrone3() != null))
                {
                    ARCONTROLLER_ERROR_ENUM error = deviceController.getFeatureARDrone3().sendPilotingEmergency();
                }
            }
        });

        takeoffBt = (Button) findViewById(R.id.takeoffBt);
        takeoffBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if ((deviceController != null) && (deviceController.getFeatureARDrone3() != null)) {
                    //send takeOff
                    ARCONTROLLER_ERROR_ENUM error = deviceController.getFeatureARDrone3().sendPilotingTakeOff();
                }
            }
        });
        landingBt = (Button) findViewById(R.id.landingBt);
        landingBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if ((deviceController != null) && (deviceController.getFeatureARDrone3() != null)) {
                    //send landing
                    ARCONTROLLER_ERROR_ENUM error = deviceController.getFeatureARDrone3().sendPilotingLanding();
                }
            }
        });

        gazUpBt = (Button) findViewById(R.id.gazUpBt);
        gazUpBt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null) {
                            deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) 50);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null) {
                            deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) 0);

                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        gazDownBt = (Button) findViewById(R.id.gazDownBt);
        gazDownBt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) -50);

                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) 0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        yawLeftBt = (Button) findViewById(R.id.yawLeftBt);
        yawLeftBt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) -50);

                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        yawRightBt = (Button) findViewById(R.id.yawRightBt);
        yawRightBt.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 50);

                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        forwardBt = (Button) findViewById(R.id.forwardBt);
        forwardBt.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 50);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 0);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        backBt = (Button) findViewById(R.id.backBt);
        backBt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) -50);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte)1);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 0);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte)0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        rollLeftBt = (Button) findViewById(R.id.rollLeftBt);
        rollLeftBt.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte) -50);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte) 0);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        rollRightBt = (Button) findViewById(R.id.rollRightBt);
        rollRightBt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte)50);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte)1);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (deviceController != null)
                        {
                            deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte)0);
                            deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte)0);
                        }
                        break;

                    default:

                        break;
                }

                return true;
            }
        });


        autonomousBt = (Button) findViewById(R.id.autonomousBt);
        autonomousBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                v.setPressed(true);
                yawLeftBt.setEnabled(false);
                yawLeftBt.setVisibility(View.INVISIBLE);

                yawRightBt.setEnabled(false);
                yawRightBt.setVisibility(View.INVISIBLE);

                gazDownBt.setEnabled(false);
                gazDownBt.setVisibility(View.INVISIBLE);

                gazUpBt.setEnabled(false);
                gazUpBt.setVisibility(View.INVISIBLE);

                forwardBt.setEnabled(false);
                forwardBt.setVisibility(View.INVISIBLE);

                backBt.setEnabled(false);
                backBt.setVisibility(View.INVISIBLE);

                rollLeftBt.setEnabled(false);
                rollLeftBt.setVisibility(View.INVISIBLE);

                rollRightBt.setEnabled(false);
                rollRightBt.setVisibility(View.INVISIBLE);

                landingBt.setEnabled(false);
                landingBt.setVisibility(View.INVISIBLE);

                takeoffBt.setEnabled(false);
                takeoffBt.setVisibility(View.INVISIBLE);

                rollLabel.setVisibility(View.INVISIBLE);
                yawLabel.setVisibility(View.INVISIBLE);

                manualBt.setEnabled(true);
                manualBt.setVisibility(View.VISIBLE);

                autonomousBt.setEnabled(false);
                autonomousBt.setVisibility(View.INVISIBLE);

                AutonomousControl autoControl = new AutonomousControl(deviceController);

                autoControl.runAutonomous();

            }
        });

        manualBt = (Button) findViewById(R.id.manualBt);
        manualBt.setEnabled(false);
        manualBt.setVisibility(View.INVISIBLE);
        manualBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                v.setPressed(true);
                yawLeftBt.setEnabled(true);
                yawLeftBt.setVisibility(View.VISIBLE);

                yawRightBt.setEnabled(true);
                yawRightBt.setVisibility(View.VISIBLE);

                gazDownBt.setEnabled(true);
                gazDownBt.setVisibility(View.VISIBLE);

                gazUpBt.setEnabled(true);
                gazUpBt.setVisibility(View.VISIBLE);

                forwardBt.setEnabled(true);
                forwardBt.setVisibility(View.VISIBLE);

                backBt.setEnabled(true);
                backBt.setVisibility(View.VISIBLE);

                rollLeftBt.setEnabled(true);
                rollLeftBt.setVisibility(View.VISIBLE);

                rollRightBt.setEnabled(true);
                rollRightBt.setVisibility(View.VISIBLE);

                landingBt.setEnabled(true);
                landingBt.setVisibility(View.VISIBLE);

                takeoffBt.setEnabled(true);
                takeoffBt.setVisibility(View.VISIBLE);

                rollLabel.setVisibility(View.VISIBLE);
                yawLabel.setVisibility(View.VISIBLE);

                autonomousBt.setEnabled(true);
                autonomousBt.setVisibility(View.VISIBLE);

                manualBt.setEnabled(false);
                manualBt.setVisibility(View.INVISIBLE);

            }
        });

    }

    @Override
    public void onStart()
    {
        super.onStart();

        //start the deviceController
        if (deviceController != null)
        {
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ControlActivity.this);

            // set title
            alertDialogBuilder.setTitle("Connecting ...");


            // create alert dialog
            alertDialog = alertDialogBuilder.create();
            alertDialog.show();

            ARCONTROLLER_ERROR_ENUM error = deviceController.start();

            if (error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK)
            {
                finish();
            }
        }
    }

    private void stopDeviceController()
    {
        if (deviceController != null)
        {
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ControlActivity.this);

            // set title
            alertDialogBuilder.setTitle("Disconnecting ...");

            // show it
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // create alert dialog
                    alertDialog = alertDialogBuilder.create();
                    alertDialog.show();

                    ARCONTROLLER_ERROR_ENUM error = deviceController.stop();

                    if (error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                        finish();
                    }
                }
            });
            //alertDialog.show();

        }
    }


    @Override
    protected void onStop()
    {
        if (deviceController != null)
        {
            deviceController.stop();
            ObstacleMap.stop = true;
        }

        super.onStop();
    }

    @Override
    public void onBackPressed()
    {

        stopDeviceController();

    }

    public void onUpdateBattery(final int percent)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                batteryLabel.setText(String.format("%d%%", percent));
            }
        });

    }

    @Override
    public void onStateChanged (ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error)
    {
        Log.i(TAG, "onStateChanged ... newState:" + newState + " error: " + error);

        switch (newState)
        {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                //The deviceController is started
                Log.i(TAG, "ARCONTROLLER_DEVICE_STATE_RUNNING ....." );
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //alertDialog.hide();
                        alertDialog.dismiss();
                    }
                });
                deviceController.getFeatureARDrone3().sendMediaStreamingVideoEnable((byte)1);
                break;

            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                //The deviceController is stoped
                Log.i(TAG, "ARCONTROLLER_DEVICE_STATE_STOPPED ....." );

                deviceController.dispose();
                deviceController = null;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        //alertDialog.hide();
                        alertDialog.dismiss();
                        finish();
                    }
                });
                break;

            default:
                break;
        }
    }

    @Override
    public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error)
    {
        // Nothing to do here since we don't want to connect to the Bebop through a SkyController
    }

    @Override
    public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary)
    {
        if (elementDictionary != null)
        {
            if (commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED)
            {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);

                if (args != null)
                {

                    Integer batValue = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);

                    onUpdateBattery(batValue);
                }
            }
        }
        else
        {
            Log.e(TAG, "elementDictionary is null");
        }
    }



}

