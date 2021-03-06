package net.ednovak.ultrasound;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by enovak on 12/12/16.
 */

// Producer/consumer model.  This is the producer
class MicRunnable implements Runnable{
    private final static String TAG = MicRunnable.class.getName();

    private BlockingAudioList<Short> a_data;

    public boolean running = true;
    private final int minBSize = AudioRecord.getMinBufferSize((int)Library.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private short[] audioBuffer = new short[minBSize*2]; // Twice as big as minimum size
    private AudioRecord ar;

    // To display RMS;
    private final static int RMS_WINDOW = 1000;
    ArrayList<Short> rmsCache = new ArrayList<Short>(RMS_WINDOW*2);
    public Chart c = null;


    // To approximate sample rate
    private int sampleCounter;
    private long startTime;
    private Activity host;

    public MicRunnable(BlockingAudioList<Short> newData){
        super();
        a_data = newData;
    }


    public void run(){
        Log.d(TAG, "Recorder (producer) started!");
        resetCounters();
        running = true;
        ar = new AudioRecord(MediaRecorder.AudioSource.MIC, (int)Library.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBSize*2);
        // same as my buffer ^ ^ ^

        assert(ar.getState() == AudioRecord.STATE_INITIALIZED);
        try {
            ar.startRecording();
        } catch (IllegalStateException e){
            Log.d(TAG, "Could not ar.startRecording()!");
            running = false; // don't try to record, just exit
        }

        int s;
        long dt;
        double rate;
        while(running) {
            //Log.d(TAG, "Mic Runnable iteration");
            // Check if we're interrupted
            if(Thread.currentThread().isInterrupted()){
                Log.d(TAG, "Producer interrupted!!");
                running = false;
                ar.stop();
                break;
            }

            if (startTime == 0){
                startTime = System.currentTimeMillis();
            }
            s = readMic(ar, audioBuffer);
            sampleCounter = sampleCounter + s;
            dt = System.currentTimeMillis() - startTime;
            rate = (sampleCounter / (dt / 1000.00));
            show(rate);
            if(dt > 10000){ // 10000ms = 10 seconds
                resetCounters();
            }
            //Log.d(TAG, "Inserted.  Remaining space: " + q.remainingCapacity());
        }
        // I may want to release if I never enter the loop
        ar.release();
        ar = null;
        Log.d(TAG, "Mic thread (producer) finished.");
    }


    // Method of the runnable because it is only called inside this (the Audio Recording) thread)
    private int readMic(AudioRecord ar, short[] buff) {
        int a = ar.read(buff, 0, buff.length); // This should be a blocking call
        if(a>0) {
            boolean resp;
            for(int i = 0; i < a; i++){
                resp = a_data.offer(buff[i]);

                if(!resp){
                    Log.d(TAG, "BlockingAudioList is full!  Shutting down producer");
                    Thread.currentThread().interrupt();
                    return a;
                }

                // Plot RMS if c is set
                if(c != null){
                    rmsCache.add(buff[i]);
                    if(rmsCache.size() >= RMS_WINDOW){
                        plotRMSPoint();
                    }
                }
            }
        }
        return a;
    }

    private void resetCounters(){
        startTime = 0;
        sampleCounter = 0;
    }

    private void plotRMSPoint(){
        short[] tmp = new short[RMS_WINDOW];
        for(int i = 0; i < RMS_WINDOW; i++){
            tmp[i] = rmsCache.get(0);
            rmsCache.remove(0);
        }
        double rms = Library.RMS(0, tmp.length, tmp);
        double per = (rms / Short.MAX_VALUE) * 100;
        c.addPoint(per);
    }

    public void insertFromFile(String absPath) {
        File f = new File(absPath);
        byte[] data;
        try {
            FileInputStream fis = new FileInputStream(f);
            data = new byte[(int)f.length()];
            fis.read(data);
            Short[] sData = Library.byteArray2ShortArray(data);
            a_data.insert(sData);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            return;

        } catch (IOException e2) {
            e2.printStackTrace();
            return;

        } catch (InterruptedException e3){
            e3.printStackTrace();
            return;
        }
    }


    public void setHostActivity(Activity newA){
        host = newA;
    }

    private void show(final double val){
        if(host == null){
            return;
        }
        host.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = (TextView)host.findViewById(R.id.home_tv_rate);
                tv.setText("Sample Rate: " + String.format("%5.3f", val) + "Hz");
            }
        });
    }
}
