package net.ednovak.ultrasound;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class SPSTestActivity extends AppCompatActivity {
    private final static String TAG = SPSTestActivity.class.getName().toString();

    private final static int SAMPLE_RATE = 44100;

    private AudioRecord ar;
    // 1 second hint to buffer size = 44100
    private final int minBSize = AudioRecord.getMinBufferSize(44100,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private int sampleCounter = 0;
    private long startTime = 0;

    private Activity host = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_spstest);
    }

    protected void onResume(){
        super.onResume();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                startMic();
            }
        });
        t.start();
    }


    protected void onPause(){
        super.onPause();
        stopMic();
    }


    // Start me in a thread!
    private void startMic(){
        Log.d(TAG, "Mic started!");

        int size = minBSize * 2;
        try {
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size*2); // size is size in bytes

            assert(ar.getState() == AudioRecord.STATE_INITIALIZED);
            ar.startRecording();

        } catch (IllegalStateException e){
            Log.d(TAG, "Could not start mic!");
            e.printStackTrace();
            return;
        }

        short[] buff = new short[size];
        long dt;
        double rate;
        int a = 0;
        while(true) {
            //Log.d(TAG, "Mic Runnable iteration");
            // Check if we're interrupted
            if(Thread.currentThread().isInterrupted()){
                Log.d(TAG, "Producer interrupted!!");
                ar.stop();
                break;
            }

            if(startTime == 0){
                startTime = System.currentTimeMillis();
            }
            a = ar.read(buff, 0, buff.length); // This should be a blocking call
            sampleCounter = sampleCounter + a;
            dt = System.currentTimeMillis() - startTime;
            rate = (sampleCounter / (dt / 1000.0));
            Log.d(TAG, "rate: " + String.format("%6.3f", rate) + "   samples: " + sampleCounter + "   time: " +dt);

            if(a < 0){
                break;
            }

            if(dt > 10000){ // reset every 10 seconds
                resetCounters();
            }
        }

        // I may want to release if I never enter the loop
        ar.release();
        ar = null;
        Log.d(TAG, "Mic thread (producer) finished.");

    }

    private void stopMic() {
        resetCounters();

        try{
            ar.stop();
            ar.release();
         } catch (IllegalStateException e){
            Log.d(TAG, "Could not stop mic!");
            e.printStackTrace();
        }
    }


    private void resetCounters(){
        startTime = 0;
        sampleCounter = 0;
    }
}
