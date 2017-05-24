package net.ednovak.ultrasound.deprecated;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

public class ListenDeprecated extends Activity {


	/*
	// Debug stuff
	private final static String TAG = ListenDeprecated.class.getName();

	private AudioRecord ar;
	private short[] audioBuffer; // short is a signed 16bit value
	private FileOutputStream fos = null;
	//private Complex[] fft;
	private boolean recording = false;
	private TextView table;
	private int recCount = 0;
	private Thread t;
	
	//private BaseStation baseStation;
	private final BlockingQueue<Short> buffer = new ArrayBlockingQueue<Short>((int)Library.SAMPLE_RATE*2);
	private final ArrayList<ArrayList<Short>> packets = new ArrayList<ArrayList<Short>>();
	private volatile boolean extractPackets = false;
	private final int chunkSize = 441;
	private final int rmsThresh = 1200;
	private volatile boolean keepForPacket = false;
	private int packetCount = 0;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_listen);
		table = (TextView) findViewById(R.id.transTable);
		//d = new Decoder(this, h);
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// Save UI state changes to the savedInstanceState.   
		// This bundle will be passed to onCreate if the process is  
		// killed and restarted.
		savedInstanceState.putInt("recCount", recCount);
		savedInstanceState.putString("table", table.getText().toString());
	
		super.onSaveInstanceState(savedInstanceState);  
	}  

	@Override  
	public void onRestoreInstanceState(Bundle savedInstanceState) {  
		super.onRestoreInstanceState(savedInstanceState);  
		// Restore UI state from the savedInstanceState.  
		// This bundle has also been passed to onCreate.  
		recCount = savedInstanceState.getInt("recCount");
		table.setText(savedInstanceState.getString("table"));
	}
	
	
	private void onRecord(boolean start) {
		Log.d(TAG, "recNumber: " + recCount);
		if (start) {
			startRecording();
		} else {
			stopRecording();
		}
	}


	// This writes the raw data to a file instead of doing any processing
	private void readMic() {
		long begin = 0;
		while (recording) {
			//Log.d(TAG, "new samples!");
			int a = ar.read(audioBuffer, 0, audioBuffer.length/2);
			//Log.d(TAG, "a: " + a + "  audioBuffer: " + audioBuffer.length);
			Short[] chunk = new Short[a];
			byte[] bBuffer = new byte[a*2];
			long start = System.currentTimeMillis();
			for (int i = 0; i < a; i++) {
				// Store in files (as bytes);
				// To play these files, use aplay -f cd -c 1 "filename" on a linux system
				// They are 16 Bit PCM files
				short val = audioBuffer[i] ;
				bBuffer[i*2] = (byte) (val & 0x00FF);
				bBuffer[(i*2) + 1] = (byte) (val >> 8);
				
				chunk[i] = audioBuffer[i];
			}
			
			// Packet Logic
			if(extractPackets){
				double rms = RMS(0, chunk.length, chunk);
				Log.d(TAG, "RMS: " + rms);
				if(!keepForPacket && (rms > rmsThresh) ){
					onReceive(true);
					keepForPacket = true;
					packets.add(new ArrayList<Short>());
					begin = System.currentTimeMillis();
				}
				
				if(keepForPacket){
					packets.get(packetCount).addAll(Arrays.asList(chunk));
					long now = System.currentTimeMillis();
					Log.d(TAG, "packet length: " + (now - begin));
					if( ((now - begin) > 2000)  && (rms < (rmsThresh/4.0)) ){ // Two seconds and quiet
						keepForPacket = false;
						packetCount++;
						onReceive(false);
					}
				}
			}
			
			// Write to file
			long end = System.currentTimeMillis();
			Log.d(TAG, "Recording Loop Delay: " + (end - start) + "ms");
			try{ // write the file
				fos.write(bBuffer);
			} catch (IOException e){
				e.printStackTrace();
				Log.d(TAG, "error writing file");
			}
		}
	}
	
	
	private Short[] pop(int size){
		if(size > buffer.size()){
			return null;
		}
		
		Short[] chunk = new Short[size];
		for(int i = 0; i < size; i++){
			chunk[i] = buffer.remove();
		}
		return chunk;
	}
	
	// To Do: Remove this function from this file and use the one(s) in Library.java
	public static double RMS(int s, int e, Short[] arr){

		//long start = System.currentTimeMillis();
		if(s < 0 || e > arr.length){
			throw new ArrayIndexOutOfBoundsException("s: " + s + "  e: " + e + "  arr.len: " + arr.length);
		}
		
		long sum = 0;
		for(int i = s; i < e; i++){
			sum = sum + (long)Math.pow(arr[i], 2);
		}
		
		double l = (double)(e - s);
		//long end = System.currentTimeMillis();
		//Log.d(TAG, "RMS Comp Time: " + (end - start) + "ms");
		return Math.sqrt((double)sum / l);
		
	}
	
	/*
	private void dumpPacketsToFiles(){
		FileOutputStream fos = null;
		
		
		for(int i = 0; i<packets.size(); i++){
			// choose file to store data at
			EasyFile gf = EasyFile.getInstance(this);
			String absPath = gf.getABSPath(EasyFile.RECORDING_CROP) + (i+1);
			
			// To play these files, use aplay -f cd -c 1 "filename" on a linux system
			// Store data
			ArrayList<Short> p = packets.get(i);
			
			byte[] bBuffer = new byte[p.size() * 2]; 
			for(int j = 0; j < p.size(); j++){
				bBuffer[j*2] = (byte) (p.get(j) & 0x00FF);
				bBuffer[(j*2) + 1] = (byte) (p.get(j) >> 8);
			}
			
			try {
				fos = new FileOutputStream(absPath);
				fos.write(bBuffer);
				fos.close();
			} 
			catch(FileNotFoundException e){
				e.printStackTrace();
				return;
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			Log.d(TAG, "Packet Stored Here: " + absPath + "  size: " + p.size());
		}
		
	}

	
	
	private void onReceive(final boolean s){
		runOnUiThread(new Runnable(){
			@Override
			public void run(){
				final TextView bsRec = (TextView)findViewById(R.id.baseStationReceiving);
				if(s){
					bsRec.setVisibility(View.VISIBLE);
					Animation a = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.blink);
					bsRec.startAnimation(a);
				}
				else if(!s){
					bsRec.clearAnimation();
					bsRec.setVisibility(View.INVISIBLE);
				}
			}
		});
	}
	
	
	

	private void startRecording() {
		ImageView iv = (ImageView)findViewById(R.id.rec_image);
		Library.blinkView(true, this, iv);
		
		final int bufferSize = AudioRecord.getMinBufferSize((int)Library.SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		audioBuffer = new short[bufferSize*10];
		Log.d(TAG, "AudioBuffer Size: " + audioBuffer);
		recCount++;
		
		// choose file to store data at
		EasyFile gf = EasyFile.getInstance(this);
		String absPath = gf.getABSPath(EasyFile.RECORDING_FULL) + recCount;
		Log.d(TAG, "Recording Stored Here: " + absPath);
		try {
			fos = new FileOutputStream(absPath);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		// Create the instance
		ar = new AudioRecord(MediaRecorder.AudioSource.MIC, (int)Library.SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 
				bufferSize);

		recording = true;
		ar.startRecording();
		t = new Thread(new Runnable() {
			public void run() {
				readMic();
			}
		}, "Audio Recorder Thread");
		t.start();
		// Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "Things that shouldn't be null t: " + t.toString() + "  ar: " + ar.toString() + "  fos: " +fos.toString());
	}
	*/

	
	
	/*
	private void stopRecording() {
		//Log.d("main:stopRecording", "stop Recording");
		ImageView iv = (ImageView)findViewById(R.id.rec_image);
		Library.blinkView(false, this, iv);
		iv.setVisibility(View.INVISIBLE);
		
		// stop recording
		recording = false;

		// Wait for thread to finish
		try {
			t.join();
			ar.stop();
			fos.close();
			ar.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} catch (IllegalStateException e){
			e.printStackTrace();
			return;
		} catch (IOException e){
			e.printStackTrace();
			return;
		} catch (NullPointerException e){
			e.printStackTrace();
			return;
		}
		
		dumpPacketsToFiles();
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);
		table.append("Rec: " + recCount + " ended @ " + dateFormat.format(new Date()) + "\n");
		EasyFile gf = EasyFile.getInstance(this);
		String absPath = gf.getABSPath(EasyFile.RECORDING_FULL);
		table.append("Location: " + absPath + recCount + "\n\n");
	}

	
	// Recording button
	public void recordButt(View v){
		//Log.d(tag, "record button pressed");
		boolean on = ((ToggleButton)v).isChecked();
		//onRecord(on);
	}


	// Button to erase recordings
	public void clearButt(View v){
		//Log.d(tag, "clear button pressed,  transcount: " + recCount);
		// choose file to store data at
		EasyFile gf = EasyFile.getInstance(this);
		
		while(recCount > 0){
			String absPathFull = gf.getABSPath(EasyFile.RECORDING_FULL) + recCount;
			File f_full = new File(absPathFull);
			f_full.delete();
			recCount --;
		}
		
		while(packetCount > 0){
			String absPathPacket = gf.getABSPath(EasyFile.RECORDING_CROP) + packetCount+1;
			File f_packet = new File(absPathPacket);
			f_packet.delete();
			packetCount--;
		}
		packets.clear();
		buffer.clear();
		table.setText("");
	}

	
	
	public void baseStationButt(View v){

		ToggleButton micButt = (ToggleButton)findViewById(R.id.micButt);
		if(((ToggleButton)v).isChecked()){
			buffer.clear();
			extractPackets = true;
			micButt.performClick();
			micButt.setClickable(false); // Temporarily
		}
		else{
			extractPackets = false;
			keepForPacket = false;
			TextView rec = (TextView)findViewById(R.id.baseStationReceiving);
			rec.clearAnimation();
			rec.setVisibility(View.INVISIBLE);
			micButt.setClickable(true);
			micButt.performClick();
		}
	}

	*/
}
