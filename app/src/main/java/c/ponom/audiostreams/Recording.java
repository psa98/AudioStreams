package c.ponom.audiostreams;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.widget.Toast;

public class Recording {

    static int count = 0;
    static String Shared;
    static String bFlag;
    public static int TIMEOUT = 3000;
    public static int COUNTDOWN_INTERVAL = 600;


    public static void checkAndRecord(Context context,
                                      OnBluetoothRecording BluetoothRecording, boolean resume) {

        // Check bluetooth flag And Bluetooth is ON or OFF
        if (getBluetoothFlag(context) && isBluetoothON()) {

            // Check for bluetooth and Record
            startBluetoothRecording(BluetoothRecording, resume, context);

        } else {

            // If Bluetooth is OFF Show Toast else Dont Show
            if (
                    //getBluetoothFlag(context) &&
                !isBluetoothON()
            ) {
                // false because recording not started
                Toast.makeText(context,
                        "Bluetooth is OFF. Recording from Phone MIC.",
                        Toast.LENGTH_SHORT).show();
                BluetoothRecording.onStartRecording(resume, false);
            } else {
                // false because recording not started
                BluetoothRecording.onStartRecording(resume, false);
            }
        }
    }

    private static void startBluetoothRecording(
            final OnBluetoothRecording BluetoothRecording, final boolean resume, Context context) {
        // TODO Auto-generated method stub

        final int MAX_ATTEMPTS_TO_CONNECT = 3;
        final AudioManager audioManager = (AudioManager) context
                .getSystemService(Context.AUDIO_SERVICE);
        final CountDownTimer timer = getTimer(BluetoothRecording, audioManager,
                resume);
        context.registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                    // cancel Timer
                    timer.cancel();
                    context.unregisterReceiver(this);
                    // pass through and true because
                    // recording from bluetooth so set 8000kHz

                    BluetoothRecording.onStartRecording(resume, true);

                } else if (AudioManager.SCO_AUDIO_STATE_DISCONNECTED == state) {
                    if (count > MAX_ATTEMPTS_TO_CONNECT) {
                        context.unregisterReceiver(this);
                        // Stop BluetoothSCO
                        audioManager.stopBluetoothSco();
                        // reset Counter
                        count = 0;
                        // stop timer
                        timer.cancel();
                        // false because still recording not started
                        BluetoothRecording.onStartRecording(resume, false);
                    } else {
                        // Increment Disconnect state Count
                        count++;

                    }
                }

            }
        }, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));

        // Start the timer
        timer.start();
        audioManager.startBluetoothSco();

    }

    // set the Timeout
    private static CountDownTimer getTimer(
            final OnBluetoothRecording BluetoothRecording,
            final AudioManager audioManager, final boolean resume) {
        // TODO Auto-generated method stub
        return new CountDownTimer(TIMEOUT, COUNTDOWN_INTERVAL) {

            @Override
            public void onTick(long millisUntilFinished) {
                // Do Nothing
            }

            @Override
            public void onFinish() {
                // stopBluetoothSCO() and start Normal Recording
                audioManager.stopBluetoothSco();
                // false because recording button is already clicked but still
                // not recording.
                BluetoothRecording.onStartRecording(resume, false);
            }
        };
    }

    // Return's the bluetooth state
    private static boolean isBluetoothON() {

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter
                .getDefaultAdapter();
        return bluetoothAdapter.isEnabled();
    }

    // Return's the bluetoothFlag state
    private static boolean getBluetoothFlag(Context context) {
        // shared pref
        SharedPreferences sp = context.getSharedPreferences(Shared,
                Context.MODE_PRIVATE);
        return sp.getBoolean(bFlag, false);

    }

    public interface OnBluetoothRecording {

        void onStartRecording(boolean state,boolean bluetoothFlag);
        void onCancelRecording();
    }

}
