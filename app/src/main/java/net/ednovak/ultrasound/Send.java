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

import junit.framework.Test;


public class Send extends AppCompatActivity {
	private final static String TAG = Send.class.getName();

    // Stops this from crashing in the AsyncTask becuase now audio is never null.
	private ArrayList<Short> audio = new ArrayList<Short>();
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
        //storeBits(binData); // store the bits in a file

        int bitsPerFrame = Library.bitsPerFrame(mode);
        short numFrames = 3;
        //short numFrames = (short)Math.ceil(binData.length() / (double)bitsPerFrame);
        Log.d(TAG, "Creating " + numFrames + " frames");
        audio = new ArrayList<Short>(Library.HAIL_SIZE + (Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE * 2)) * numFrames);

        // Little experiment for samsung phones, add something at the front

        for(int i = 0; i < Library.FOOTER_SIZE*2; i++) {
            audio.add((short)0);
        }

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

            int parityBitsPerFrame = ECC.calcNumParityBits(binData.length()/3); // per frame
            Log.d(TAG, "parity Bits: " + parityBitsPerFrame);
            int dataBitsPerFrame = bitsPerFrame - parityBitsPerFrame - 1; // additional -1 for overall parity bit

            s = i * dataBitsPerFrame;
            e = Math.min(((i * dataBitsPerFrame) + dataBitsPerFrame), binData.length());
            String curFrameBinary = binData.substring(s, e);

            Log.d(TAG, "Frame " + i + " containing " +  String.valueOf(curFrameBinary.length()) + " bits.");

            //ECC implementation
            Log.d(TAG, "curFrameBinary: " + curFrameBinary);
            String eccImplementedString = ECC.eccImplementation(curFrameBinary, parityBitsPerFrame);

            //Log.d(TAG, "Original binary: " + curFrameBinary);
            Log.d(TAG, "Final frame binary (after ECC and after size field in frame 1)");
            Log.d(TAG, eccImplementedString);

            appendFrame(eccImplementedString);
        }
        // -------------------------------------------------------------------------------------- //



        // ---- Quieting Footer ----------------------------------------------------------------- //

        SubCarrier sc = new SubCarrier(21000, 0, 1, false);
        double[] footer = new double[Library.FOOTER_SIZE];
        sc.addTo(footer, 0);
        double ampDelta = 1.0 / (double)Library.FOOTER_SIZE;
        double newAmp = 0.2;
        for(int i = 0; i < Library.FOOTER_SIZE; i++){
            short val = Library.double2Short(footer[i] * (newAmp * Library.MAXIMUM));
            audio.add(val);
            newAmp = newAmp - ampDelta;
        }
        // -------------------------------------------------------------------------------------- //


        // Done, print some debug stuff
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
            sc.addTo(signal, -Library.RAMP_SIZE);
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
        String sField = "";

        if(rMessage.isChecked()){ // Message
            String message = ((EditText)findViewById(R.id.data)).getText().toString();
            bitString = ascii2Binary(message);
            sField = Library.genSizeField(bitString.length()/8, mode);
        } else if (rRandom.isChecked()){ // Random Bits
            try{
                int numberOfBits = Integer.valueOf(((EditText)findViewById(R.id.data)).getText().toString());
                bitString = Library.getRandomBits(numberOfBits);
                sField = Library.genSizeField(bitString.length(), mode);
            } catch (NumberFormatException e){
                Toast.makeText(this, "Please enter a number of bits", Toast.LENGTH_SHORT).show();
                return null;
            }
        } else if (rDebug.isChecked() || rDebugL.isChecked()) { // Debug binary
            EditText et = (EditText)findViewById(R.id.data);
            bitString = et.getText().toString();
            sField = Library.genSizeField(bitString.length(), mode);
        }
        else{ // Should be unreachable, only occurs when all radio buttons are not selected
            throw new IllegalStateException("No Message Selected!");
        }


        String padded = pad(bitString, mode); // Pad to fill packet (all frames)
        String preECC = sField + padded; // Add the sizefield bits


        Log.d(TAG, "data length: " + bitString.length() + "   " + bitString);
        Log.d(TAG, "padded String length: " + padded.length() + "   " + padded);
        Log.d(TAG, "sField length: "+ sField.length() + "   " + sField );
        Log.d(TAG, "preECC bits length: " + preECC.length() + "    " + preECC);

        return preECC;
    }

    private String pad(String input, int mode){
        int l = Library.getL(mode);

        String appendString = "";
        if(input.length() < l){
            appendString = new String(new char[l-input.length()]).replace("\0", "0");
            input = input + appendString;
        }

        if(input.length() >l ){
            throw new IllegalArgumentException("Maximum packet data size is: " + l);
        }
        return input;
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
        // This loop assumes that there are 80 sub-carriers
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
            // Fancy ternary operator
            // x == y ? a : b  --MEANS-->   If x == y output a otherwise, output b.
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
        Log.d(TAG, "Long Range (slow)");

        // There is three frames / packet in this version.  So, the
        // first frame in the packet encodes 7 size field bits (max value = 127) and 71 data bits.
        // The second and third frame in the packet encode 0 size field bits and 78 data bits.
        ArrayList<SubCarrier> map = new ArrayList<SubCarrier>(78);
        double curF = Library.findStartingF();
        double amp;
        double phase = 0;
        for(int i = 0; i < binary.length(); i++){
            if( (int)curF == 18647 || (int)curF == 19810){
                Log.d(TAG, curF + " calibration sub-carrier!");
                SubCarrier freq = new SubCarrier(curF, Math.PI, Library.AMP_HIGH);
                map.add(freq);
                curF += Library.SubCarrier_DELTA;
            }
            // Ternary operator
            amp = binary.charAt(i) == '1' ? Library.AMP_HIGH : 0;
            // Phase also encodes the same bit (basically a clever way to get random phases)
            phase = binary.charAt(i) == '1' ? Math.PI : 0;

            Log.d(TAG, "f: " + String.format("%.3f", curF) + "   amp: " + amp + "   phase: " + phase);
            SubCarrier freq = new SubCarrier(curF, phase, amp);
            map.add(freq);
            curF += (Library.SubCarrier_DELTA);

            // Add the decimal portion of PI to the phase.  This "spreads" the phases out so that the sub-carriers do not
            // perform destructive interference with one another.
            // I added this because I noticed long range mode had worse error than short range mode's amplitude
            // Also it was noisy (probably because of interference as well).
        }
        return map;
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

    public void genTestSignal(View v){

        SubCarrier sc = new SubCarrier(2000, 0, 1.0, false);

        // ---- Create the actual audio data from this sub-carrier ----------------------------- //
        double[] signal = new double[(int)(Library.SAMPLE_RATE*1)];
        sc.addTo(signal, 0);
        Log.d(TAG, "signal.length: " + signal.length);

        // Amplify (scale to full volume)
        double localMax = (Library.MAXIMUM );
        short[] output = new short[signal.length];
        double tmp;
        for(int i = 0; i < signal.length; i++){
            tmp = signal[i] * localMax;
            output[i] = Library.double2Short(tmp);
        }


        double[] signal2 = new double[(int)(Library.SAMPLE_RATE*1)];
        SubCarrier sc2 = new SubCarrier(1000, 0, 1.0, false);
        sc2.addTo(signal2, 0);
        Log.d(TAG, "signal.length: " + signal2.length);
        localMax = Library.MAXIMUM;
        short[] output2 = new short[signal2.length];
        for(int i = 0; i < signal2.length; i++){
            tmp = signal2[i] * localMax;
            output2[i] = Library.double2Short(tmp);
        }


        // This step and previous step (amplify) could be combined into one loop
        // Put into audio ArrayList<Short> for transmission
        audio = new ArrayList<Short>(output.length);
        // put data in there
        for (int i = 0; i < output.length; i++) {
            audio.add(output[i]);
        }
        for(int i = 0; i < output2.length; i++){
            audio.add(output2[i]);
        }

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

        protected void onProgressUpdate(Integer... prog){
            int p = prog[0];
            //Log.d(TAG, "playing: " + p);
            super.onProgressUpdate(p);
            pb.setProgress(p);
        }

        // This is not called until playing is done thanks to the while
        // loop in doInBackground
        protected void onPostExecute(Void res){
			Log.d(TAG, "Done playing");
			pb.setVisibility(View.INVISIBLE);
        }
	}
}
