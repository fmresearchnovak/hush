package net.ednovak.ultrasound;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;



public class Send extends AppCompatActivity {
	private final static String TAG = Send.class.getName();

	private ArrayList<Short> audio;
    private int mode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_send);
        
        RadioGroup rg = (RadioGroup) findViewById(R.id.radio_group);
        rg.setOnCheckedChangeListener(new OnCheckedChangeListener(){
        	
        	public void onCheckedChanged(RadioGroup rg, int checkedId){
        		TextView tv = (TextView)findViewById(R.id.main_tv_header);
        		EditText et = (EditText)findViewById(R.id.data);
        		et.setText("");
            	switch(checkedId){
            	
            	case R.id.radio_msg:
            		tv.setText("Message");
            		et.setInputType(InputType.TYPE_CLASS_TEXT);
            		break;
            		
            	case R.id.radio_rand:
            		tv.setText("Number Of Bits");
            		et.setInputType(InputType.TYPE_CLASS_NUMBER);
            		break;

                // Binary counting (3 bits)
				case R.id.radio_debug:
					tv.setText("Predetermined Bit Sequence");
					et.setText(Library.DEBUG_BINARY);
					et.setInputType(InputType.TYPE_NULL);
					break;

                case R.id.radio_debug_long:
                    tv.setText("Predetermined Bit Sequence");
                    et.setText(Library.DEBUG_BINARY_LONG);
                    et.setInputType(InputType.TYPE_NULL);
                    break;
            	}
        	}
        });

        rg.check(R.id.radio_msg);
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
                this.recreate();
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

    protected void onResume(){
        super.onResume();
        SharedPreferences sharedPref = this.getSharedPreferences("edu.fandm.enovak.hush", Context.MODE_PRIVATE);
        mode = sharedPref.getInt("MODE", 1);
        updateUIMode();
    }

    private void updateUIMode(){
        // Get current mode and display to user
        TextView tv = (TextView)findViewById(R.id.send_tv_cur_mode);
        if(mode == Library.MODE_SHORT){
            tv.setText("mode: short range (fast)");
        } else if(mode == Library.MODE_LONG){
            tv.setText("mode: long range (slow)");
        }
    }

	public void genPacket(View v){
        // Generate bit sequence to be sent
        String binData = genPacketBinary();
        storeBits(binData); // store the bits in a file

        // I will definitely not have > SHORT.MAX_VALUE frames in a packet
        // It isn't strictly necessary to give the exact allocation size of the audio ArrayList
        int bitsPerFrame = Library.bitsPerFrame(mode);

        short numFrames = (short)Math.ceil(binData.length() / (double)bitsPerFrame);
        Log.d(TAG, "Creating " + numFrames + " frames");
        audio = new ArrayList<Short>(Library.HAIL_SIZE + (Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE * 2)) * numFrames);


        // ---- Header -------------------------------------------------------------------------- //
        short[] hail = Library.makeHail(Library.HAIL_TYPE_SWEEP);
        for(int i = 0; i < hail.length; i++){
            audio.add(hail[i]);
        }
        // -------------------------------------------------------------------------------------- //



        // ---- Frames -------------------------------------------------------------------------- //
        int s;
        int e;
        for(int i = 0; i < numFrames; i++){
            Log.d(TAG, "Frame " + i);
            s = i * bitsPerFrame;
            e = Math.min(((i * bitsPerFrame) + bitsPerFrame), binData.length());
            String curFrameBinary = binData.substring(s, e);
            Log.d(TAG, "binary: " + curFrameBinary);
            appendFrame(curFrameBinary);
        }
        // -------------------------------------------------------------------------------------- //



        // ---- Quieting Footer ----------------------------------------------------------------- //
        SubCarrier sc = new SubCarrier(21000, 0, 1, false);
        double[] footer = new double[Library.FOOTER_SIZE];
        sc.addTo(footer);
        double ampDelta = 1.0 / (double)Library.FOOTER_SIZE;
        double newAmp = 1;
        for(int i = 0; i < Library.FOOTER_SIZE; i++){
            short val = Library.double2Short(footer[i] * (newAmp * Library.MAXIMUM));
            audio.add(val);
            newAmp = newAmp - ampDelta;
        }
        // -------------------------------------------------------------------------------------- //


        // Done print some debug stuff
        Log.d(TAG, "Total length in samples: " + audio.size());
        // Write the signal to a file.
        short[] packet = new short[audio.size()];
        for(int i = 0; i < packet.length; i++){
            packet[i] = audio.get(i);
        }
        Library.writeToFile("pre.pcm", Library.shortArray2ByteArray(packet));
        Toast.makeText(this, "Signal Ready To Send!",  Toast.LENGTH_SHORT).show();

    }
    
    public void appendFrame(String binary){
        // The input binary here is NOT the direct user data
        // It should include the size field bits (if this is the first frame)
        // It does not include the calibration frequencies
        // (the loop below creates them)

        // Ensure we don't get too large
        int bitsPerFrame = Library.bitsPerFrame(mode);
        if( binary.length() < 0 || binary.length() > bitsPerFrame){
            throw new IllegalArgumentException("Too many or too few bits: " + binary.length() + "  Maximum bits per frame: " + bitsPerFrame);
        }


        ArrayList<SubCarrier> map = new ArrayList<SubCarrier>();
        if(mode == Library.MODE_SHORT){
            map = genFrameMapShort(binary);
        } else if(mode == Library.MODE_LONG){
            map = genFrameMapLong(binary);
        }


        // ---- Create the actual audio data from this sub-carrier mapping ---------------------- //
        double[] signal = new double[Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE*2)];

        for(int i = 0; i < map.size(); i++){
            SubCarrier sc = map.get(i);
            sc.addTo(signal);
        }

        // Amplify (scale to full volume)
        final double localMax = (Library.MAXIMUM * (1/Library.getABSMax(signal)));
        short[] output = new short[signal.length];
        double tmp;
        for(int i = 0; i < signal.length; i++){
            tmp = signal[i] * localMax;
            output[i] = Library.double2Short(tmp);
        }

        Library.novakWindow(output);
        //Log.d(TAG, "ABS after: " + Library.getABSMax(output));

		// put data in there
        for (int i = 0; i < output.length; i++) {
            audio.add(output[i]);
        }
        // -------------------------------------------------------------------------------------- //
    }


    private String genPacketBinary(){
        RadioButton rMessage = (RadioButton)findViewById(R.id.radio_msg);
        RadioButton rRandom =  (RadioButton)findViewById(R.id.radio_rand);
        RadioButton rDebug = (RadioButton)findViewById(R.id.radio_debug);
        RadioButton rDebugL = (RadioButton)findViewById(R.id.radio_debug_long);
        String bitString = "";

        if(rMessage.isChecked()){ // Message
            String message = ((EditText)findViewById(R.id.data)).getText().toString();
            bitString = ascii2Binary(message);
        } else if (rRandom.isChecked()){ // Random Bits
            try{
                int numberOfBits = Integer.valueOf(((EditText)findViewById(R.id.data)).getText().toString());
                bitString = Library.getRandomBits(numberOfBits);
            } catch (NumberFormatException e){
                Toast.makeText(this, "Please enter a number of bits", Toast.LENGTH_SHORT).show();
                return null;
            }
        } else if (rDebug.isChecked() || rDebugL.isChecked()) { // Debug binary
            EditText et = (EditText)findViewById(R.id.data);
            bitString = et.getText().toString();
        }
        else{ // Should be unreachable, only occurs when all radio buttons are not selected
            throw new IllegalStateException("No Message Selected!");
        }


        // Add the extra 8 size field bits
        String sField = Library.genSizeField(bitString.length());
        Log.d(TAG, "sField    : " + sField);
        Log.d(TAG, "data      : " + bitString);
        bitString = sField + bitString;
        Log.d(TAG, "bitString : " + bitString);
        Log.d(TAG, "bitString length: " + bitString.length());

        return bitString;
    }


    private ArrayList<SubCarrier> genFrameMapShort(String binary){
        // If the number is odd then the below loop will lose the last bit
        // The loop below stops at (binary.length() / 2) which is int divison
        // The size field at the beginning of the packet will tell the receiver
        // how many bits there actually are.  I can simply tack an extra 0 here
        // The result is that there are now an even number of bits and the final
        // sub-carrier will have phase = 0.  Which is arbitrary because at the rec
        // side the phase of the final sub-carrier will not be decoded.
        if(binary.length() % 2 == 1){
            binary = binary + "0";
        }

        // *** Data <-> Sub-Carrier Mapping *** \\
        //
        // (what amp, and phase for each sub-carrier)
        // This loop assumes.  There are 80 sub-carriers
        // 2 of which are used for calibration
        // String binary includes amp and phase bits, the protocol interleaves them
        ArrayList<SubCarrier> map = new ArrayList<SubCarrier>(80);
        double curF = Library.findStartingF();
        double amp;
        double phase;
        for(int i = 0; i < binary.length()/2; i++){
            if( (int)curF == 18647 || (int)curF == 19810){
                Log.d(TAG, curF + " calibration sub-carrier!");
                SubCarrier freq = new SubCarrier(curF, Math.PI, Library.AMP_HIGH);
                map.add(freq);
                curF += Library.SubCarrier_DELTA;
            }
            amp = binary.charAt(i*2) == '1' ? Library.AMP_HIGH : Library.AMP_LOW;
            phase = binary.charAt((i*2)+1) == '1' ? Math.PI : 0;

            Log.d(TAG, "f: " + String.format("%.3f", curF) + "   amp: " + amp + "   phase: " + phase);
            SubCarrier freq = new SubCarrier(curF, phase, amp);
            map.add(freq);
            curF += Library.SubCarrier_DELTA;
        }
        return map;
    }


    private ArrayList<SubCarrier> genFrameMapLong(String binary){
        return new ArrayList<SubCarrier>(0);
    }

    // Converts an ascii string to binary.  This is used for the user to send txt messages.
	private String ascii2Binary(String asciiString){

		byte[] bytes = asciiString.getBytes();
		StringBuilder binary = new StringBuilder();
		for (byte b : bytes)
		{
			int val = b;
			for (int i = 0; i < 8; i++)
			{
				binary.append((val & 128) == 0 ? 0 : 1); // found online, dunno how it works
				val <<= 1;
			}
		}
		//Log.d("main:getSample", "'" + asciiString + "' to binary: " + binary);
		return binary.toString();
	}
    
    private void storeBits(String bits){
        Library.writeToFile("bits.bin", bits.getBytes());
    }



    // Plays the sound stored in audio
	public void playAudio(View v){
        PlayTask pt = new PlayTask();
        pt.execute();
	}


	// Used here to play sounds and show a progress bar for
    // some user feedback
    private class PlayTask extends AsyncTask<Void, Integer, Void> {

        ProgressBar pb = (ProgressBar)findViewById(R.id.send_pb_playing);

        // When sound is not playing, this progress bar is invisible (see onPostExecute)
		protected void onPreExecute(){
            pb.setVisibility(View.VISIBLE);
            pb.setProgress(0);
		}

		// Plays the sound, uses the new Library static method
        // to get the AT instance.  The library  method knows the
        // several input parameters and makes it easier and
        // DRY to create an AT instance.
		protected Void doInBackground(Void... a) {
            Log.d(TAG, "playing in background");
            int sizeInBytes = audio.size() * 2;
            AudioTrack at = Library.getAudioTrack(sizeInBytes);
            short[] primitiveAudio = Library.ShortListToArray(audio);
            at.write(primitiveAudio,0, primitiveAudio.length);
            if(at.getState() == AudioTrack.STATE_INITIALIZED) {
                at.play();

                // This loop keeps the "doInBackground" thread alive
                // until the sound is done being played
                // This way, it can update the progressbar
                // but it will also make the var invisible once the sound
                // has stopped.  I am considering making this thread sleep
                Integer prog;
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                while( (prog = at.getPlaybackHeadPosition()) < primitiveAudio.length){
                    prog = (int)((prog / (float)primitiveAudio.length) * 100); // convert to percentage
                    publishProgress(prog);
                }
            }


            at.stop();
            at.flush();
            at.release();
            at = null;
            return null;
        }

        protected void onProgressUpdate(Integer prog){
            super.onProgressUpdate(prog);
            pb.setProgress(prog);
        }

        // This is not called until playing is done thanks to the while
        // loop in doInBackground
        protected void onPostExecute(Void res){
			Log.d(TAG, "Done playing");
			pb.setVisibility(View.INVISIBLE);
        }
	}
}
