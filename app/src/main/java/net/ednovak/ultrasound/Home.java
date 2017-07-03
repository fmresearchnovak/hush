package net.ednovak.ultrasound;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.DecimalFormat;


// Main activity, launched when user starts the app
// Allows the user to listen / receive data
public class Home extends AppCompatActivity {
	private final static String TAG = Home.class.getName();

	// Permissions can be added as they are needed by simply adding them to this array.
	public final static String[] PERMS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.RECORD_AUDIO};

	private ToggleButton tb;
    private ImageView signal;
	private BlockingAudioList<Short> dataBuffer;
	private Context ctx;

	private Thread cThread;
	private Thread pThread;
	private Thread updateUIThread;

    private int mode;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_home);

		ctx = getApplicationContext();
		tb = (ToggleButton)findViewById(R.id.home_listener_toggle);
        signal = (ImageView)findViewById(R.id.home_iv_signal);

		Log.d(TAG, "Ultrasound App Running!");


		Library.TestHilbert();
	}

	@Override
    protected void onResume(){
        super.onResume();
        SharedPreferences sharedPref = this.getSharedPreferences("edu.fandm.enovak.hush", Context.MODE_PRIVATE);
        mode = sharedPref.getInt("MODE", 1);
        updateUIMode();
		checkPerms();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.home, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
		switch(item.getItemId()){
			case R.id.Sender:
				i = new Intent(this, Send.class);
				startActivity(i);
				break;

            case R.id.Mode:
                mode = Library.switchMode(mode);
                SharedPreferences sharedPref = this.getSharedPreferences("edu.fandm.enovak.hush", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt("MODE", mode);
                editor.commit();
                updateUIMode();
                break;

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}


	private void updateUIMode(){
        // Get current mode and display to user
        TextView tv = (TextView)findViewById(R.id.home_tv_cur_mode);
        if(mode == Library.MODE_SHORT){
            tv.setText("mode: short range (fast)");
        } else if(mode == Library.MODE_LONG){
            tv.setText("mode: long range (slow)");
        }
    }


	public void listenerOnOff(View v){
		if(tb.isChecked()){
			Log.d(TAG, "Starting recording.");
			startRec();

            signal.setVisibility(View.VISIBLE);
            Animation a = AnimationUtils.loadAnimation(this, R.anim.blink);
            signal.setAnimation(a);
            signal.animate();


		} else{
			Log.d(TAG, "Stop recording.");
			pThread.interrupt();
			cThread.interrupt();

            signal.clearAnimation();
            signal.setVisibility(View.INVISIBLE);
			//updateUIThread.interrupt();
		}
	}


	private void startRec(){
		// Starts recording.
		// This modem uses a Producer/consumer model.  The microphone produces samples
		// The Demodulator consumes samples (and outputs the decoded signal binary).

		// My own implementation that allows peeking into a blocking queue
		dataBuffer = new BlockingAudioList(Demodulator.getMinQueueSize());

		MicRunnable producer = new MicRunnable(dataBuffer);
		Demodulator consumer = new Demodulator(dataBuffer, mode);

		pThread = new Thread(producer, "Audio Recording Thread");
		cThread = new Thread(consumer, "Demodulating Thread");
		//updateUIThread = new Thread(new UIRunnable());

		pThread.start();
		cThread.start();
		//updateUIThread.start();

        //producer.insertFromFile("/sdcard/ultrasound/origin.pcm");
	}


	public void onRequestPermissionsResult(int code, String[] perms, int[] results){
		if(code == 1){
			for(int i = 0; i < results.length; i++){
				if(results[i] == PackageManager.PERMISSION_DENIED){
					Toast.makeText(ctx, "All permissions are necessary.", Toast.LENGTH_SHORT).show();
					finish();
				}
			}
		}
	}

	private boolean checkPerms(){
		// check all permissions
		for(int i = 0; i < PERMS.length; i++){
			if(ContextCompat.checkSelfPermission(ctx, PERMS[i]) != PackageManager.PERMISSION_GRANTED){
				ActivityCompat.requestPermissions(this, PERMS, 1);
				return false;
			}
		}
		return true;
	}


	// Shows the % of the buffer that is currently available on the screen
	class UIRunnable implements Runnable {

		@Override
		public void run() {
			while(true){
				runOnUiThread(new bufferCapacityUpdater());

				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException e1){
					//e1.printStackTrace();
					Log.d(TAG, "UpdateUIThread interrupted!!");
					break;
				}
		}
	}

	    // sub-sub class... neat!
        class bufferCapacityUpdater implements Runnable {
            @Override
            public void run() {
                double per = (double) dataBuffer.getCapacityRemaining() / (double) dataBuffer.getCapacity();
                //Log.d(TAG, "per: " + per);
                DecimalFormat df = new DecimalFormat("#.0");
                String perFormatted = df.format(per*100);
				Log.d(TAG, "Buffer Capacity Remaining: " + perFormatted);

                //TextView tv = (TextView)findViewById(R.id.home_tv_capacity_status);
                //String s = "Buffer is: " + perFormatted + "% empty";
                //tv.setText(s);
            }
        }
	}
}
