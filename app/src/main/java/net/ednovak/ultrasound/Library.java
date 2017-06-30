package net.ednovak.ultrasound;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public class Library {
	private static String TAG = Library.class.getName();
	
	// 882 samples @ 44.1khz = 0.02 seconds = 20.0ms
	// 1024 samples @ 44.1khz = 0.02 seconds = 23.2ms
	// 1536 samples @ 44.1khz = 0.03 seconds = 34.5ms
	// 1764 samples @ 441.khz = 0.04 seconds = 40ms
	// 995 @ 44.1khz = 0.023 seconds = 22.5ms but 18k+19k is near 0 at 995 samples
	// 4082 @ 44.1khz = 0.093 seconds = 92.5ms and 18k + 19k is near 0 at 4082 samples
	public final static double SAMPLE_RATE = 44100.0;
	public final static int HAIL_SIZE = 300;
	public final static int DATA_FRAME_SIZE = 1024;
    public final static int RAMP_SIZE = 10;
    public final static int FOOTER_SIZE= 4096;
	public final static int MAXIMUM = Short.MAX_VALUE - 1767; // Making it too loud causes problems
    public final static double SubCarrier_DELTA = SAMPLE_RATE / DATA_FRAME_SIZE;
    // 44100 / 1024 (samples) = 43.0664 Hz / sub-carrier = SubCarrier_DELTA

	public final static double AMP_LOW = 0.19;
	public final static double AMP_HIGH = 1.0;
	
	public final static int HAIL_TYPE_SWEEP = 1;
	public final static int HAIL_TYPE_STATIC = 2;

    public static final int MODE_SHORT = 1;
    public static final int MODE_LONG = 2;


    // Binary counting (3 bits)
	public final static String DEBUG_BINARY = "000001010011100101110111";

    // Binary counting (5 bits)
    // This long sequence debug string _almost_ fits in exactly 1 frame (152 bits).
    // I had to chop off the last two numbers ( last 10 binary digits ) to make room
    // for the sField binary (8 bits);
	// It is now 150 bits so that frame it generates is 150 + 8 (8 size field bits)
    public final static String DEBUG_BINARY_LONG = "000000000100010000110010000101001100011101000010010101001011011000110101110011111000010001100101001110100101011011010111110001100111010110111110011101";
    // AMP_GND includes the size-field but not the calibration sub-carriers
    public final static String AMP_GND = "100100000000010100001001000100001101010010111000010001110001100110010100111101";
    public final static String PHS_GND = "011000001010010001101011100011100110011110110010101101001110111110101111011011";

	public static AudioTrack getAudioTrack(int sizeInBytes){
		AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, (int)Library.SAMPLE_RATE,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT, sizeInBytes,
				AudioTrack.MODE_STATIC);

		return at;
	}

	public static void playSound(short[] sound){
        int lenInBytes= sound.length * 2;
        AudioTrack at = getAudioTrack(lenInBytes);
        at.write(sound, 0, sound.length);
        at.play();
    }
	
    public static short[] makeHail(int type){
    	
    	short[] tmp = new short[HAIL_SIZE];
    	
		int thirdIndex = tmp.length / 3;
		final double volDelta = 1.0 / (double)thirdIndex;
		double volume = 0;
		
		final double freqDelta = 1000.0 / HAIL_SIZE; // change 1000Hz across HAIL_SIZE = 300 samples
		double f; // cur frequency
    	
		for(int i = 0; i < tmp.length; i++){
			if (i < thirdIndex) { //increase volume
				volume += volDelta;
			}
		
			if (i > thirdIndex*2){ // decrease volume
				volume -= volDelta;
			}
			double amp = volume * MAXIMUM;

			// Create sample
			if(type == HAIL_TYPE_STATIC){ // always 18kHz
				double val = getSample(i, 18000, 0) * amp;
				tmp[i] = double2Short(val);
			}
			
			else if(type == HAIL_TYPE_SWEEP){ // Sweeps between 18kHz and ...
				f = 18000 + (freqDelta*i);
				double val = getSample(i, f, 0) * amp;
				tmp[i] = double2Short(val);
			}
		}

    	return tmp;
    }

    
    // Gets one sample from a sin wave
    // output (double s) should be between 0 and 1
	// where 0 is silent (nonsense) and 1 is full volume
    public static double getSample(int sampleIndex, double frequency, double shift){
    	if (frequency > SAMPLE_RATE/2){
    		throw new IllegalArgumentException("SubCarrier above nyquist");
    	}
		double s = Math.sin( 2 * Math.PI * sampleIndex * (frequency / SAMPLE_RATE) + shift);
		assert s >= 0 && s <= 1;
		return s;
    }
    
    private static void addFreq(double[] curSignalSeg, double newFreq, double p, double vol, int offset){
    	for(int i = 0; i < curSignalSeg.length; i++){
    		curSignalSeg[i] += getSample(i+offset, newFreq, p) * vol;
    	}
    }
    
    
    public static double fadeIn(int input, double fadeL){
    	double ans = Math.sqrt((1.0/fadeL) * input);
    	//Log.d(TAG, "input: " + input + " fadeL: " + fadeL + " ans: " + ans);
    	return ans;
    }
    
    
    // Returns the magnitude of the entire transmission
    private static int getL(double freqsPerSeg, int bitStringLength){
    	double bitsPerSeg = freqsPerSeg*2; // ( amp & phase on all sub-carriers)

    	int durs = (int) (Math.ceil(bitStringLength / bitsPerSeg));
    	int l = (durs * DATA_FRAME_SIZE) + HAIL_SIZE;
    	return l;
    }
    
    public static double getABSMax(double[] input){
    	double max = 0;
    	for(int i = 0; i < input.length; i++){
    		if(Math.abs(input[i]) > max){
    			max = Math.abs(input[i]);
    		}
    	}
    	return max;
    }
    
    public static double getABSMax(short[] input){
    	double max = 0;
    	for(int i = 0; i < input.length; i++){
    		if(Math.abs(input[i]) > max){
    			max = Math.abs(input[i]);
    		}
    	}
    	return max;
    }
    
    private static char getBit(String bitString, int index){
    	try{
    		return bitString.charAt(index);
    	}
    	catch (StringIndexOutOfBoundsException e){
    		return '1';
    	}
    }
    
    public static String getRandomBits(int l){
    	StringBuilder sb = new StringBuilder();

        Calendar calendar = Calendar.getInstance();
        //int hrSeed = calendar.get(Calendar.HOUR);
        // Random selected but CONSTANT seed (constant across all devices)
        Random r = new Random(77);
    	while(sb.length() < l){
    		sb.append((String.valueOf(r.nextInt(2))));
    	}
    	return sb.toString();
    }
    
    public static void blinkView(boolean on, Context ctx, View v){
    	if(on){
    		Animation a = AnimationUtils.loadAnimation(ctx, R.anim.blink);
    		v.startAnimation(a);
    	}
    	else{
    		v.clearAnimation();
    	}
    }




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


	public static short[] FIR(final short[] x){
		// coefficients from matlab filter designer
		double[] b = {0.0536284937329220, 0.994873850798756, -0.710280527878075, -0.744040602743060, 1.51729330245694, -0.0268176491321003, -2.21174591798505, 1.88994832258316, 2.74697675339359, -8.95059850862559, 11.8815249667970, -8.95059850862559, 2.74697675339359, 1.88994832258316, -2.21174591798505, -0.0268176491321003, 1.51729330245694, -0.744040602743060, -0.710280527878075, 0.994873850798756, 0.0536284937329220};


		short[] y = new short[x.length];
        for(int i = 0; i < x.length; i++) {
            double cur = 0;
            for (int k = 0; k < b.length && k <= i; k++) {
                cur = cur + (b[k] * x[i - k]);
            }

            y[i] = (short)cur; // loss of precision, I don't care.
        }
        return y;
	}


	// To Do
	// There is probably some fancy way to combine this with the method above
	public static double RMS(int s, int e, short[] arr){

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


	public static Short[] ObjToShortArray(Object[] input){
		if(input == null){
			return null;
		}
		Short[] tmp = new Short[input.length];
		for(int i = 0; i < input.length; i++){
			tmp[i] = (Short)input[i];
		}
		return tmp;
	}

	public static short[] ShortListToArray(ArrayList<Short> in){
        short[] out = new short[in.size()];
        for(int i = 0; i < in.size(); i++) {
            out[i] = in.get(i);
        };
        return out;
    }

    public static Complex[] shortArrayToComplexArray(short[] in){
		Complex[] out = new Complex[in.length];
		for(int i = 0; i < out.length; i++){
			out[i] = new Complex(in[i], 0);
		}
		return out;
	}


	// This returns a Short (object) but actually it can be
    // used to assign a short and Java will auto-cast it for me
    // I think this feature is called "unpacking"
	public static Short double2Short(double val){
		if(val < Short.MIN_VALUE || val > Short.MAX_VALUE){
			throw new IllegalArgumentException("Invalid value for Short: " + val);
		}
		return new Short((short)val);
	}

    // This returns a Short (object) but actually it can be
    // used to assign a short and Java will auto-cast it for me
    // I think this feature is called "unpacking"
	public static Short integer2Short(int val){
		if(val < 0 || val > Short.MAX_VALUE){
			throw new IllegalArgumentException("Invalid value for Short: " + val);
		}
		return new Short((short)val);
	}

	public static void print(double[] arr){
		for(int i = 0; i < arr.length; i++){
			Log.d(TAG, "arr [" + i + "]: " + arr[i]);

			Integer tmp = new Integer(0);
		}
	}

	public static String toString(ArrayList<Short> arr){
        StringBuilder sb = new StringBuilder(arr.size() * 2);
        sb.append("[");
        for(int i = 0; i < arr.size(); i++){
            sb.append(arr.get(i) +", ");
        }

        // Remove last comma and space
        sb.deleteCharAt(sb.length()-1);
        sb.deleteCharAt(sb.length()-1);

        sb.append("]");

        return sb.toString();
    }


    public static String toString(Short[] arr){
        StringBuilder sb = new StringBuilder(arr.length * 2);
        sb.append("[");
        for(int i = 0; i < arr.length; i++){
            sb.append(arr[i] + ", ");
        }

        // Remove last comma and space
        sb.deleteCharAt(sb.length()-1);
        sb.deleteCharAt(sb.length()-1);

        sb.append("]");

        return sb.toString();
    }

    public static String toString(short[] arr){
        StringBuilder sb = new StringBuilder(arr.length * 2);
        sb.append("[");
        for(int i = 0; i < arr.length; i++){
            sb.append(arr[i] + ", ");
        }

        // Remove last comma and space
        sb.deleteCharAt(sb.length()-1);
        sb.deleteCharAt(sb.length()-1);

        sb.append("]");

        return sb.toString();
    }


    public static byte[] doubleArray2ByteArray(double[] data){
        // To implement this I don't care about the values past the decimal point
        // and these values are very large (thousands and tens of thousands)
        // so, they're too big for shorts.  I will cast them to ints (4 bytes / int)
        // which drops the values after the decimal, and then cast each int as four bytes.
        ByteBuffer b = ByteBuffer.allocate(data.length * 4);
        b.order(ByteOrder.LITTLE_ENDIAN);
        for(int i = 0; i < data.length; i++){
            int tmp = (int)data[i];
            //Log.d(TAG, "val: " + tmp);
            b.putInt(tmp);

        }
        return b.array();
    }

	public static byte[] intArray2ByteArray(int[] data){
        ByteBuffer b = ByteBuffer.allocate(data.length * 4);
        b.order(ByteOrder.LITTLE_ENDIAN);
        for(int i = 0; i < data.length; i++){
            b.putInt(data[i]);
        }
        byte[] result = b.array();
        return result;
    }


    public static byte[] shortArray2ByteArray(short[] data){
        byte[] byteData = new byte[data.length * 2]; // 2 bytes in a short
        //Log.d(TAG, "byteData length: " + byteData.length);
        //Log.d(TAG, "short[] data.length: " + data.length + "   * 2 = " + data.length*2);

        for(int i = 0; i <  data.length; i++){
            short val = data[i];
            // To play these files, use aplay -f cd -c 1 "filename" on a linux system
            // or sound(data, Fs) in matlab
            // Values in data are assumed to be in the range -1.0 <=> 1.0
            byteData[i*2] = (byte)(val & 0x00FF);
            byteData[(i*2)+1] = (byte) (val >> 8);
        }
        return byteData;
    }

    public static Short[] byteArray2ShortArray(byte[] data){
        Short[] output = new Short[data.length/2];
        for(int i = 0; i < output.length; i++){
            byte b1 = data[i*2];
            byte b2 = data[(i*2) + 1];
            output[i] = (short)(((short)(b1 & 0x00FF)) + ((short)(b2 << 8)));
        }

        // I think the bit shift is like this:
        // b1 = (i*2)
        // b2 = (i*2) + 1
        // s = (b1 & 0x00FF) || (b2 >> 8);

        return output;
    }


    // Dr. Novak
    // Convenience function, dump this data to give file
    public static void writeToFile(String name, byte[] data){
        // Make sure it exists
        File dir = new File("/sdcard/ultrasound");
        if(!dir.exists()){
            dir.mkdirs();
        }

        try {
            File f = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();
            Log.d(TAG, "File dumped successfully to: " + f.getAbsolutePath());
        }
        catch(FileNotFoundException e1){
            e1.printStackTrace();
        }
        catch (IOException e2) {
            e2.printStackTrace();
        }
    }



    public static void novakWindow(short[] input){
        // This is designed for a signal that has an additional
        // rampSize number of (repetitive) samples at each end
        // It is a 1/2 of a hann window over the extra samples
        // And rectangular in the middle like this:
        /*
                    _______________
                  *`               `*
                 /                   \
                /                     \
           ----/                       \-------
        */
        // Please forgive my crude ascii!
        int rampSize = RAMP_SIZE;
        for(int i = 0; i < input.length; i++){
            double inside;
            double c;

            if(i < rampSize) { // beginning samples
                inside = ((2 * Math.PI * i) / (rampSize*2));
                c = 0.5 * (1 - Math.cos(inside));
            }
            else if(i > (input.length-rampSize)){ // end samples
                int j = input.length - i;
                inside = ( (2 * Math.PI * j) / (rampSize*2) );
                c = 0.5*(1 - Math.cos(inside));
            }
            else { // middle samples
                c = 1;
            }
            //Log.d(TAG, "i: " + i + "   c: " + c);
            input[i] = Library.double2Short(input[i] * c);

        }
    }

    public static void welchWindow(short[] input){
        double L = input.length;
        for(int i = 0; i < input.length; i++) {
            double side = ( (i - ((L-1)/2)) / ((L-1)/2) );
            double c = 1 - Math.pow(side, 2);
            input[i] = Library.double2Short(input[i] * c);
        }
    }


    public static void hammingWindow(short[] input){
        double alpha = 0.54;
        double beta = 1-alpha;
        double L = input.length;

        for(int i = 0; i < input.length; i++) {
            double c = alpha - beta * Math.cos((2 * Math.PI * i) / (L - 1));
            input[i] = Library.double2Short(input[i] * c);
        }
    }

	// In place change
	public static void hannWindow(short[] input){
        for(int i = 0; i < input.length; i++){
            double inside = ( (2 * Math.PI * i) / input.length );
            double c = 0.5*(1 - Math.cos(inside));
            input[i] = Library.double2Short(input[i] * c);
        }
    }

    public static void triWindow(short[] input){
        double L = input.length;
        for(int i = 0; i < input.length; i++){
            double c = 1 - Math.abs( (i - ((L-1)/2)) / (L/2) );
            Log.d(TAG, "c: " + c);

            input[i] = Library.double2Short(input[i] * c);
        }
    }

    public static void rectWindow(short[] input){
        for(int i = 0; i < input.length; i++){
            input[i] = input[i];
        }
    }


    // Outputs bitwise XNOR of the input strings a and b
    // When a != b XNOR outputs 1.  So 1's indicate errors
    public static String getErrors(String a, String b){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < a.length(); i++){
            try {
                if (a.charAt(i) == b.charAt(i)) {
                    sb.append("0");
                } else {
                    sb.append("1");
                }
            } catch (StringIndexOutOfBoundsException e1){
                break;
            }

        }
        return sb.toString();
    }


    public static String getErrorLocation(String a, String b){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < a.length(); i++){
            try {
                if (a.charAt(i) != b.charAt(i)) {
                    sb.append(String.valueOf(i) + " ,");
                }
            } catch (StringIndexOutOfBoundsException e1){
                break;
            }

        }
        return sb.toString();
    }

    public static double errPer(String errString){
        double sum = 0;
        for(int i = 0; i < errString.length(); i++){
            if(errString.charAt(i) == '1'){
                sum++;
            }
        }
        double per = (sum / errString.length()) * 100;
        return per;
    }


    // The size field should indicate how many payload sub-carriers are actually
    // being used to transmit the message.  With 1024 samples, I'll use 80 sub-carriers.
    // This leaves 76 for data (76 data + 4 calibration = 80)
    //
    // Actually, I have 160 bits in a frame (at most) 80 amp bits and 80 phase bits.
    // This does not include calibration bits (which are in the physical layer)
    // But a frame uses 8 of the 160 bits to encode the size (size field of sField)
    // This leaves 152 data bits.  So, I need 8 bits (unsigned integer) to represent this
    // 8 bits in unsigned binary -> 255     7 bits in unsigned binary -> 127
    // This is a small issue about if there happens to be a multiple of 160 bits
    // for example: a message of 304 = 2 frames of (152 data + 8 sField) = 320 bits = 160 * 2
    // How can we identify the final frame?
    // Naively: send a third frame with 0 bits stored in it.
    public static String genSizeField(Integer val, int mode){
        String binary = Integer.toBinaryString(val);

        int maxL = 0;
        if(mode == MODE_SHORT){
            maxL = 10;
        } else if (mode == MODE_LONG){
            maxL = 3;
        }

        if (binary.length() <= maxL) {
            String fmt = "%" + maxL + "s";
            binary = String.format(fmt, binary).replace(' ', '0');
        } else {
            throw new IllegalArgumentException("Cannot represent this value (" + val + ") using " + maxL + " bits.");
        }

        return binary;
    }


    public static double findStartingF(){
        // Finds the bin nearest but > 18kHz to start on
        // Not doing this before may have been the source of a lot of problems!
        double cur = 0;
        while(cur < SubCarrier.MIN_FREQ){
            cur = cur + Library.SubCarrier_DELTA;
        }
        return cur;
    }

    public static int switchMode(int cur){
        if(cur == MODE_LONG){
            return MODE_SHORT;
        } else if (cur == MODE_SHORT){
            return MODE_LONG;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + cur);
        }
    }

    public static int bitsPerFrame(int curMode){
        // Consult the MAP .ods file for explanation
        if(curMode == MODE_LONG){
            return 26;
        } else if(curMode == MODE_SHORT){
            return 156;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + curMode);
        }
    }

    /**
     * Takes a binary string and convert to a ascii string
     * @param binary  a binary string that only contains the actually data bits that encode ascii symbols
     * @return a ascii string
     */
    public static String binary2ascii(String binary){
        String ascii = "";
        char nextChar;

        for(int i = 0; i <= binary.length()-8; i += 8) //this is a little tricky.  we want [0, 7], [9, 16], etc (increment index by 9 if bytes are space-delimited)
        {
            nextChar = (char)Integer.parseInt(binary.substring(i, i+8), 2);
            ascii += nextChar;
        }

        return ascii;

    }
}
