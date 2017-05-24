package net.ednovak.ultrasound;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import net.ednovak.ultrasound.deprecated.GmailSender;

public class UserStudy extends Activity {
	final private String TAG = UserStudy.class.getName();
	
	final private int RED = Color.rgb(127, 0, 0);
	final private int GREEN = Color.rgb(36, 172, 0);
	private ScrollView sv;
	private LayoutInflater inflater;

	final private String[] dataARR = new String[16];
	private int testCounter = 0;
	
	final private double MAX = Short.MAX_VALUE - 768;
	private AudioTrack at;
	private short[] upsignal = null;
	private short[] downsignal = null;
	
	final private int tLength = (int)Library.SAMPLE_RATE * 10; // 5 seconds
	final private double topFreq = 21500; //
	final private double baseFreq = 10000; // start at 10kHz
	final private double freqDelta = ((topFreq - baseFreq) / tLength) / 2.0;
	
	private boolean testRunning = false;
	private boolean modemReady = false;
	
	private int modemDur = 5; // 5 seconds
	private short[] modemSignal;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_userstudy);
		
		sv = (ScrollView)findViewById(R.id.scrollview);
		
		inflater = getLayoutInflater();
		inflater.inflate(R.layout.layout_userstudy_begin, (ViewGroup)sv, true);
		
		Thread t = new Thread(new Runnable(){
			public void run(){
				String bitString = Library.getRandomBits(modemDur*6500);
				
				//modemSignal = Library.genOFDM(bitString);
				modemReady = true;
			}
		});
		t.start();
		
	}
	
	public void begin(View v){
		if(storeAge()){
			sv.removeAllViews();
			inflater.inflate(R.layout.layout_userstudy_freq_up, (ViewGroup)sv, true);
		}
		else{
			Toast.makeText(this, "Please enter a valid age" , Toast.LENGTH_SHORT).show();
		}
	}
	
	
	private double getSample(int idx, double freq){
		double t = (double)idx / Library.SAMPLE_RATE;
		return Math.sin( 2 * Math.PI * freq * t);
	}
	
	private boolean storeAge(){
		try{
			EditText et = (EditText)findViewById(R.id.age);
			int age = Integer.valueOf(et.getText().toString());
			if(age < 1 || age > 120){
				throw new IllegalStateException("Invalid Age: " + age);
			}
			dataARR[15] = et.getText().toString();
			return true;
		} catch (Exception e){
			e.printStackTrace();
			return false;
		}
	}
	
	
	
	public void startFreqUp(View v){
		
		
		if(init(0.70)){
			// Generate Signal
			
			if(upsignal == null){
				upsignal = new short[tLength];
				double freq = baseFreq;
				for(int i = 0; i < tLength; i++){
					upsignal[i] = (short)(getSample(i, freq) * MAX);
					freq+=freqDelta;
				}
				Log.d(TAG, "last freq: " + freq);
			}
			//play it!
			playIt(upsignal);
			testRunning = true;
		}
	}
		
	public void startFreqDown(View v){
		
		if(init(0.70)){
			// Generate signal
			if(downsignal == null){
				downsignal = new short[tLength];
				double freq = topFreq;
				for(int i = 0; i < tLength; i++){
					downsignal[i] = (short)(getSample(i, freq) * MAX);
					freq-=freqDelta;
				}
			}
			
			// Play It!
			playIt(downsignal);
			testRunning = true;
		}
	}
		
	
	public void stopTest(View v){
		Animation anim = AnimationUtils.loadAnimation(this, R.anim.click_linearlayout);
		v.startAnimation(anim);
		
		if(!testRunning){
			Toast.makeText(this, "Click the Start Test Button First", Toast.LENGTH_SHORT).show();
		}
		else{
			testRunning = false;
			
			int pos = at.getPlaybackHeadPosition();
			at.pause();
			at.flush();
			at.stop();
			at.release();
			at = null;
			
			Log.d(TAG, "anything");
	
			String freq = getFreqThresh(pos);
			Toast.makeText(this, "SubCarrier: " + freq, Toast.LENGTH_SHORT).show();
			dataARR[testCounter] = String.valueOf(freq);
			
			Button tb = (Button)findViewById(R.id.testButt);
			testCounter++;
			tb.setText("Start Test " + (testCounter+1));
			
			//at.flush();
			if(testCounter == 5){
				sv.removeAllViews();
				inflater.inflate(R.layout.layout_userstudy_freq_down, (ViewGroup)sv, true);
			}
			
			if(testCounter == 10){
				// Set the content view to test the modem
				sv.removeAllViews();
				inflater.inflate(R.layout.layout_userstudy_modem, (ViewGroup)sv, true);
			}
		}
		
		showDataArray();
	}
	
	private String getFreqThresh(int pos){
		
		int freq = 0;
		if(testCounter < 6){
			double rel = ((double)pos / (double)tLength) * (topFreq - baseFreq);
			freq = (int)(baseFreq + rel);
		}
		else if (testCounter >= 6 && testCounter < 11){
			double rel = 1 - ((double)pos / (double)tLength);
			freq = (int)(baseFreq + (rel * (topFreq - baseFreq)));
		}
		return String.valueOf(freq);
	}
	
	private boolean init(double volume){
		
		if(testRunning){
			return false;
		}
		
		// Set Volume to max
		AudioManager audioManager = (AudioManager)getSystemService(this.AUDIO_SERVICE);
		int vol = (int)(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*volume);
		audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
		
		// Check headphones
		if(audioManager.isWiredHeadsetOn()){
			Toast.makeText(this, "Please disconnect headphones", Toast.LENGTH_SHORT).show();
			return false;
		}
		else{ return true; }
	}
	
	private void playIt(short[] signal){
		int lengthInBytes = signal.length*2;
		at = Library.getAudioTrack(lengthInBytes);
    	at.write(signal, 0, signal.length);
    	at.play();
	}
	
	
	public void startModem(View v){
		
		if(testCounter >= 2){
			SeekBar sb = (SeekBar) findViewById(R.id.bar);
			dataARR[testCounter++] = String.valueOf(sb.getProgress());
			showDataArray();
			sb.setProgress(0);
		}
		
		if(init(0.60) && modemReady){
    		
    		playIt(modemSignal);
    		
    		// animate it
    		TextView tv = (TextView)findViewById(R.id.status);
    		tv.setText("Transmitting");
    		Animation a = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
    		a.setRepeatCount(Animation.INFINITE);
    		a.setRepeatMode(Animation.REVERSE);
    		tv.startAnimation(a);
    		
    		TimerTask tt = new CustomTimerTask();
			// Timer for animation done and data collection
    		new Timer().schedule(tt, (modemDur)*1000);
			
		}
	}
	
	private class CustomTimerTask extends TimerTask{
		public void run(){
			
			if(testCounter == 15){
				sendemail();
			}
			
			runOnUiThread(new Runnable(){
				@Override
				public void run(){
					TextView tv = (TextView)findViewById(R.id.status);
					tv.clearAnimation();
					tv.setText("Finished");
					
					Button tb = (Button)findViewById(R.id.testButt);
					tb.setText("Submit & Start Test " + testCounter);
					
					if(testCounter == 15){
						sv.removeAllViews();
						inflater.inflate(R.layout.layout_userstudy_done, (ViewGroup)sv, true);	
					}
				}
			});
		}
	}
	
	private void showDataArray(){
		String s = dataArrayToString();
		Log.d(TAG, "data: " + s);
		Log.d(TAG, "testCounter: " + testCounter);
	}
	
	
	public String getDeviceName() {
		  String manufacturer = Build.MANUFACTURER;
		  String model = Build.MODEL;
		  return manufacturer + " " + model;
		}
	
	private String dataArrayToString(){
		String s= getDeviceName() + " ";
		for(int i = 0; i < dataARR.length; i++){
			s += dataARR[i] + " ";
		}
		return s;
	}
	
	private void sendemail(){
        try {   
            GmailSender sender = new GmailSender("ultrasounddatacollection@gmail.com", "ultrasoun");
            sender.sendMail("Data Collection Results",   
                    dataArrayToString(),   
                    "ultrasounddatacollection@gmail.com",
                    "ejnovak@email.wm.edu");   
        } catch (Exception e) {   
            Log.e("SendMail", e.getMessage(), e);   
        }
        Log.d(TAG, "Message Sent");
        
        
	}
}
