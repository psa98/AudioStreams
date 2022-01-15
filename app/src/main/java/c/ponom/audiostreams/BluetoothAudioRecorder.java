package c.ponom.audiostreams;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;


public class BluetoothAudioRecorder extends AppCompatActivity {

    Button buttonStartRecording, buttonStopRecording, buttonPlayLastRecordAudio,
            buttonStopPlayingRecording;
    String AudioSavePathInDevice = null;
    MediaRecorder mediaRecorder;
    public static final int RequestPermissionCode = 1;
    MediaPlayer mediaPlayer;
    AudioManager audioManager;
    boolean isAudioPlayInSameDevice = true;
    //    AudioRouter audioRouter;
    RadioGroup mRadioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_audio_recorder);

        buttonStartRecording = (Button) findViewById(R.id.button);
        buttonStopRecording = (Button) findViewById(R.id.button2);
        buttonPlayLastRecordAudio = (Button) findViewById(R.id.button3);
        buttonStopPlayingRecording = (Button) findViewById(R.id.button4);


        buttonStopRecording.setEnabled(false);
        buttonPlayLastRecordAudio.setEnabled(false);
        buttonStopPlayingRecording.setEnabled(false);

        buttonStartRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Check audio permission
                if (checkPermission()) {
                    AudioSavePathInDevice =
                            Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "AudioRecording.3gp";
                    // Start Media recorder
                    MediaRecorderReady();
                    try {
                        mediaRecorder.prepare();
                        mediaRecorder.start();
                    } catch (IllegalStateException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    buttonStartRecording.setEnabled(false);
                    buttonStopRecording.setEnabled(true);

                    Toast.makeText(BluetoothAudioRecorder.this, "Recording started",
                            Toast.LENGTH_LONG).show();
                } else {
                    requestPermission();
                }

            }
        });

        buttonStopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                buttonStopRecording.setEnabled(false);
                buttonPlayLastRecordAudio.setEnabled(true);
                buttonStartRecording.setEnabled(true);
                buttonStopPlayingRecording.setEnabled(false);
                // Stop Media recorder
                mediaRecorder.stop();
                Toast.makeText(BluetoothAudioRecorder.this, "Recording Completed",
                        Toast.LENGTH_LONG).show();
            }
        });

        buttonPlayLastRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {
                int selectedId = mRadioGroup.getCheckedRadioButtonId();

                isAudioPlayInSameDevice = true;


                audioManager.setBluetoothScoOn(false);
                audioManager.stopBluetoothSco();
                buttonStopRecording.setEnabled(false);
                buttonStartRecording.setEnabled(false);
                buttonStopPlayingRecording.setEnabled(true);

                mediaPlayer = new MediaPlayer();
                try {
                    // Start media player
                    System.out.println("Recorded Audio Path-" + AudioSavePathInDevice);
                    mediaPlayer.setDataSource(AudioSavePathInDevice);
                    if (isAudioPlayInSameDevice) {
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    }
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(BluetoothAudioRecorder.this, "Recording Playing",
                        Toast.LENGTH_LONG).show();
            }
        });

        buttonStopPlayingRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonStopRecording.setEnabled(false);
                buttonStartRecording.setEnabled(true);
                buttonStopPlayingRecording.setEnabled(false);
                buttonPlayLastRecordAudio.setEnabled(true);

                if (mediaPlayer != null) {
                    // Stop Media Player
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    MediaRecorderReady();
                }
            }
        });
    }

    private final BroadcastReceiver mBluetoothScoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            System.out.println("ANDROID Audio SCO state: " + state);
            if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                /*
                 * Now the connection has been established to the bluetooth device.
                 * Record audio or whatever (on another thread).With AudioRecord you can record with an object created like this:
                 * new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                 * AudioFormat.ENCODING_PCM_16BIT, audioBufferSize);
                 *
                 * After finishing, don't forget to unregister this receiver and
                 * to stop the bluetooth connection with am.stopBluetoothSco();
                 */
            }
        }
    };

    public void MediaRecorderReady() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);

        mediaRecorder.setOutputFile(AudioSavePathInDevice);
    }



    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        registerReceiver(mBluetoothScoReceiver, intentFilter);
        audioManager = (AudioManager) getApplicationContext()
                .getSystemService(getApplicationContext().AUDIO_SERVICE);
        // Start Bluetooth SCO.
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setBluetoothScoOn(true);
        audioManager.startBluetoothSco();
        // Stop Speaker.
        audioManager.setSpeakerphoneOn(false);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBluetoothScoReceiver);
        // Stop Bluetooth SCO.
        audioManager.stopBluetoothSco();
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setBluetoothScoOn(false);
        // Start Speaker.
        audioManager.setSpeakerphoneOn(true);
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(BluetoothAudioRecorder.this, new
                String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RequestPermissionCode) {
            if (grantResults.length > 0) {
                boolean StoragePermission = grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED;
                boolean RecordPermission = grantResults[1] ==
                        PackageManager.PERMISSION_GRANTED;

                    if (StoragePermission && RecordPermission) {
                        Toast.makeText(BluetoothAudioRecorder.this, "Permission Granted",
                                Toast.LENGTH_LONG).show();
                   } else {
                       Toast.makeText(BluetoothAudioRecorder.this,"Permission Denied",Toast.LENGTH_LONG).show();
                   }
            }
        }
    }

    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(),
                WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(),
                RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED &&
                result1 == PackageManager.PERMISSION_GRANTED;
    }
}