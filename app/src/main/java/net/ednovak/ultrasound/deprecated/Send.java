package net.ednovak.ultrasound.deprecated;

import net.ednovak.ultrasound.Library;
import net.ednovak.ultrasound.SubCarrier;

import java.util.ArrayList;

/**
 * Created by enovak on 5/16/17.
 */

public class Send {

    	/*
    private static void addFreq(Short[] curSignalSeg, double newF, double p, double amp){
    	//Log.d("main:addFreq", "newFreq:" + newFreq + "  p: " + p + " vol: " + vol);
    	for(int i = 0; i < curSignalSeg.length; i++){
			double val = getSample(i, newF, p) * amp;
    		curSignalSeg[i] = curSignalSeg[i] + double2Short(val);
    	}
    }
    */

	/*
    // Generates a single frame
    private static Short[] makeDataOld(String ampBits, String phaseBits){
    	//Log.d("main:makeData", "  amp Bits: " + ampBits);
    	//Log.d("main:makeData", "phase Bits: " + phaseBits);

    	if (ampBits.length() != phaseBits.length()){
    		Log.d("main:makeData", "amps: " + ampBits.length() + "  phase: " + phaseBits.length());
    	}

    	Short[] tmp = new Short[DATA_FRAME_SIZE];

		// Calibration sub-carriers
    	addFreq(tmp, 18000, Math.PI, HIGH);
    	addFreq(tmp, 19000, Math.PI, HIGH);
    	addFreq(tmp, 20000, Math.PI, HIGH);
    	addFreq(tmp, 21000, Math.PI, HIGH);


    	int curFreq = 18050;
    	// Encode the bits yo
    	for(int i = 0; i < ampBits.length(); i++){
    		// Skip 19000 it's used for finding starting point
    		if(curFreq == 19000 || curFreq == 20000 || curFreq == 21000){
    			curFreq += WIDTH;
    		}

    		double vol = LOW; // amplitude
    		if ( getBit(ampBits, i) == '1' ){
    			vol = HIGH;
    		}

    		double p = 0;     // phase
    		if ( getBit(phaseBits, i) == '1' ){
    			p = Math.PI;
    		}

			addFreq(tmp, curFreq, p, vol);
	    	curFreq += WIDTH;
    	}

    	// Scale to full volume when finished
    	final double localMax = (MAXIMUM * (1/getABSMax(tmp)));
    	final double fadeL = 10;
    	final double volDelta = 1/fadeL;
    	double vol = 1.0;
    	for(int i = 0; i < tmp.length; i++){
    		if (i < fadeL){ // fast fade in
    			vol = 1 * fadeIn(i, fadeL);
    		}
    		if (i > fadeL && i <= (tmp.length - fadeL)){ // full volume
    			vol = 1.0;
    		}
    		if(i > (tmp.length - fadeL)){ // fast fade out
    			vol = 1 * fadeIn(tmp.length - i, fadeL);
    		}

			// Invalid volume!
			assert vol < 1.0000001 && vol >= 0.0;

    		tmp[i] = tmp[i] * localMax * vol;
    	}

    	if(getABSMax(tmp) > 31900){ // Not an assert because I want to see the max
    		throw new IllegalStateException("Signal Too Strong: " + getABSMax(tmp));
    	}

    	return tmp;
    }
    */

		/*
    // Generates the signal
	// This function could be made faster because I iterate through the entire signal
	// once for each frequency.  This isn't actually necessary, but conceptually it
	// is simpler.  Really I should find all the frequencies at a given point in the
	// sample and add them all up and create the sample that way.
    public static short[] genOFDM(String bitString){

    	// Build the preFrame and then the data and then combine them into a frame
    	// Store the frame in the instance field 'sample'
    	Short[] header = makeHail(HAIL_TYPE_SWEEP);

    	double freqsPerSeg = 65;
    	short[] sample = new short[getL(freqsPerSeg, bitString.length())];

    	// Copy the header, yay!
    	for(int i = 0; i < header.length; i++){ // Header
    		sample[i] = (short)header[i];
    	}


    	int start = 0; // start and end hold the index points in the bit string
    	int end = 0;
    	int sIndex = header.length; // sIndex holds the index point in the double[] signal
    	while (start < bitString.length()){
    		// freqsPerSeg gives us the number of amplitude bits in BASK
    		int cur = start;
    		end = (int)Math.min(cur + freqsPerSeg, bitString.length());
    		String a_chunk = bitString.substring(cur, end);
    		cur = end;
    		end = (int)Math.min(cur + freqsPerSeg, bitString.length());
    		String p_chunk = bitString.substring(cur, end);

    		//Log.d("main:makeData", "a_chunk: " + a_chunk);
    		//Log.d("main:makeData", "p_chunk: " + p_chunk);

    		// Generates a particular chunk of data
    		double[] signalSeg = makeData(a_chunk, p_chunk);
    		for(int i = 0; i < signalSeg.length; i++){
    			sample[sIndex++] = (short)signalSeg[i];
    		}
    		start = start + a_chunk.length() + p_chunk.length();
    	}

    	Log.d("main:genFrame", "sample length: " + sample.length + "  maxValue: " + Library.getABSMax(sample));
    	return sample;
    }


    public static Short[] makeAudioOld(ArrayList<SubCarrier> map){
        double[] signal = new double[Library.DATA_FRAME_SIZE];

        for(int i = 0; i < map.size(); i++){
            SubCarrier sc = map.get(i);
            sc.addTo(signal);
        }

        // Amplify (scale to full volume)
        // Scale to full volume when finished
        final double localMax = (Library.MAXIMUM * (1/Library.getABSMax(signal)));
        final double fadeL = 10;
        final double volDelta = 1/fadeL;
        double vol = 1.0;
        for(int i = 0; i < signal.length; i++){
            if (i < fadeL){ // fast fade in
                vol = 1 * Library.fadeIn(i, fadeL);
            }
            if (i > fadeL && i <= (signal.length - fadeL)){ // full volume
                vol = 1.0;
            }
            if(i > (signal.length - fadeL)){ // fast fade out
                vol = 1 * Library.fadeIn(signal.length - i, fadeL);
            }

            // Invalid volume!
            assert vol < 1.0000001 && vol >= 0.0;

            signal[i] = signal[i] * localMax * vol;
        }

        // Copy to short array
        Short[] output = new Short[signal.length];
        for(int i = 0; i < output.length; i++){
            output[i] = Library.double2Short(signal[i]);
        }

        return output;

    }
    */
}
