package com.example.drone.flight;

import android.util.Log;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureARDrone3;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.arsal.ARSALPrint;

/**
 * Created by drone on 3/10/16.
 */

public class AutonomousControl {

    private static final String  TAG = "autonomousControl";
    private ARDeviceController deviceController;
    private boolean collision_detected = false;

    static final int FRONT = 0;
    static final int FRONT_LEFT = 1;
    static final int FRONT_RIGHT = 2;
    static final int LEFT = 3;
    static final int RIGHT = 4;
    static final int BACK_LEFT = 5;
    static final int BACK_RIGHT = 6;
    static final int STEP = 10;


    AutonomousControl(ARDeviceController deviceControl){
        deviceController = deviceControl;
        Log.i(TAG, "created successfully");
    }

    // Starts up Autonomous Flight
    public void runAutonomous() {

        takeoff();

        try{
            System.out.println("Sleeping");
            Thread.sleep(5000); //sleep for 3 seconds
            System.out.println("Awake");
        }
        catch(InterruptedException e){
            System.out.println("got interrupted!");
        }

        // Test for calibration
        calibrate();

        // Makes drone run
        fly();

        land();

    }

    private void takeoff()
    {
        if (ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED.equals(getPilotingState()))
        {
            ARCONTROLLER_ERROR_ENUM error = deviceController.getFeatureARDrone3().sendPilotingTakeOff();

            if (!error.equals(ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK))
            {
                ARSALPrint.e(TAG, "Error while sending take off: " + error);
            }
        }
    }

    // Drone waits. Time is in milliseconds
    private void wait_awhile(int time)
    {
        try{
            System.out.println("Sleeping");
            Thread.sleep(time);
            System.out.println("Awake");
        }
        catch(InterruptedException e){
            System.out.println("got interrupted!");
        }
    }

    // Calibrates forward speed and turning speed
    private void calibrate()
    {
        move_forward(STEP * 2);

        wait_awhile(2500);

        stop_go_dir();

        wait_awhile(3000);

        turn_right(STEP * 5);

        wait_awhile(3750);

        stop_turn_dir();

        wait_awhile(1500);

        move_forward(STEP * 2);

        wait_awhile(2500);

        stop_go_dir();

        wait_awhile(2000);

    }


    // tests drone flight by going down a hallway and back
    private void test_loop()
    {
        // currently goes forward and comes back (sort of)

        move_forward(STEP);

        wait_awhile(2000);

        stop_go_dir();

        wait_awhile(2000);

        turn_right(STEP);

        wait_awhile(3500);

        stop_turn_dir();

        wait_awhile(1000);

        move_right(STEP);

        wait_awhile(1000);

        stop_move_dir();

        wait_awhile(1000);

        move_forward(STEP);

        wait_awhile(2000);

        stop_go_dir();

        wait_awhile(1500);

    }

    /* cant use this function without collision detection
    private void fly()
    {

        while(true) {
            move_forward(STEP);
            if (check_collision()) {
                handle_collision();//stop moving and handle collision
                collision_detected = false;
            }
        }
    }
    */

    // Sends drone forward for some distance, stops, loses altitude, moves forward, gains altitude
    // Simulates moving under the bridge
    // Notes: You need to wait some time after each movement otherwise all the commands will run quickly and it'll look like it did nothing
    private void fly()
    {
        // No collision detection so repeat 3 times
        for (int i = 0; i < 3; i++) {
            move_forward(STEP * 2);

            wait_awhile(2500);

            stop_go_dir();

            wait_awhile(2000);

            // Maneuver under crossbar

            decrease_altitude(STEP);

            wait_awhile(1250);

            stop_alt_dir();

            wait_awhile(1000);

            move_forward(STEP * 2);

            wait_awhile(2500);

            stop_go_dir();

            wait_awhile(2000);

            increase_altitude(STEP);

            wait_awhile(1250);

            stop_alt_dir();

            wait_awhile(1000);
        }

    }

    private boolean check_collision()
    {
        /* checks if Arduino triggered collision flag */
        return collision_detected;
    }

    private void handle_collision()
    {
        int triggered_sensor = getTriggeredSensor();

        switch (triggered_sensor) {
            case FRONT:
                move_back(50);
                break;
            case FRONT_LEFT:
                move_back(50);
                move_right(50);
                break;
            case FRONT_RIGHT:
                move_back(50);
                move_left(50);
                break;
            case LEFT:
                move_right(50);
                break;
            case RIGHT:
                move_left(50);
                break;
            case BACK_LEFT:
                move_forward(50);
                move_right(50);
                break;
            case BACK_RIGHT:
                move_forward(50);
                move_left(50);
                break;
            default:
                break;
        }
    }

    public int getTriggeredSensor()
    {
        /* figures out what sensor was triggered via Arduino response */
        return 0;
    }


/* Notes with movement:
        setPilotingPCMDFlag sets whether or not a button is being pressed down
*/
    private void move_forward(int amount)
    {
        /* Might need to do some distance calculations using amount and distance away from sensor */

        deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) amount);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);
    }

    private void move_back(int amount)
    {
        /* Might need to do some distance calculations using amount and distance away from sensor */

        deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) -amount);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);
    }

    private void stop_go_dir()
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 0);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 0);
    }

    private void move_left(int amount)
    {
        /* Might need to do some distance calculations using amount and distance away from sensor */

        deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte) -amount);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);

        //turn_left(STEP);
    }

    private void move_right(int amount)
    {
        /* Might need to do some distance calculations using amount and distance away from sensor */

        deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte) amount);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);

        //turn_right(STEP);
    }

    private void stop_move_dir()
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDRoll((byte) 0);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 0);
    }

    private void turn_left(int amount)
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) -amount);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);
    }

    private void turn_right(int amount)
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) amount);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 1);
    }

    private void stop_turn_dir()
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
        deviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 0);
    }

    private void u_turn()
    {
        turn_right(100);
        stop_turn_dir();
    }

    private void increase_altitude(int amount)
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) amount);
    }

    private void decrease_altitude(int amount)
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) -amount);
    }

    private void stop_alt_dir()
    {
        deviceController.getFeatureARDrone3().setPilotingPCMDGaz((byte) 0);
    }

    private void land()
    {
        ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM flyingState = getPilotingState();
        if (ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING.equals(flyingState) ||
                ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING.equals(flyingState))
        {
            ARCONTROLLER_ERROR_ENUM error = deviceController.getFeatureARDrone3().sendPilotingLanding();

            if (!error.equals(ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK))
            {
                ARSALPrint.e(TAG, "Error while sending take off: " + error);
            }
        }
    }


    private ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM getPilotingState()
    {
        ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM flyingState = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.eARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_UNKNOWN_ENUM_VALUE;
        if (deviceController != null)
        {
            try
            {
                ARControllerDictionary dict = deviceController.getCommandElements(ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED);
                if (dict != null)
                {
                    ARControllerArgumentDictionary<Object> args = dict.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                    if (args != null)
                    {
                        Integer flyingStateInt = (Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE);
                        flyingState = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue(flyingStateInt);
                    }
                }
            }
            catch (ARControllerException e)
            {
                e.printStackTrace();
            }
            return flyingState;
        }
       return null;
    }

    private void moveForward(){

    }
}

