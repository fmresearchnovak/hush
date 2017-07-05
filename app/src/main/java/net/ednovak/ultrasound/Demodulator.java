package net.ednovak.ultrasound;

import android.util.Log;

/**
 * Created by enovak on 12/12/16.
 */

// Producer/consumer model. This is the Consumer Thread
class Demodulator implements Runnable{
    private final static String TAG = Demodulator.class.getName();

    private BlockingAudioList<Short> a_data;
    // The hail signal is 300 samples and the loud part is 100 samples long
    // So 50 samples will definitely land in / on that 100 sample portion
    // It might take at most 3 sets of 50 samples before we reach the height of the hail signal
    // FYI: 50 samples at 44100 = 0.0011337 seconds = 1.1337 ms ELITE!
    private int numFrames = 3;
    private boolean running = false;

    private final static int STATE_LISTENING = 1;
    private final static int STATE_DECODING = 2;
    private int STATE = 1;
    private long transitionTS;
    private int mode;

    //Test
    StringBuilder eccString;


    public Demodulator(BlockingAudioList newBuffer, int newMode){
        super();
        a_data = newBuffer;

        if(newMode != Library.MODE_LONG && newMode != Library.MODE_SHORT){
            throw new IllegalArgumentException("Invalid MODE");
        }
        mode = newMode;

    }

    private void stopSelf(){
        running = false;
        Thread.currentThread().interrupt();
        return;
    };

    public void run() {
        running = true;
        while (running) {
            //Log.d(TAG, " ");
            //Log.d(TAG, "Demod Runnable iteration");

            try {
                readQueue();
            } catch (InterruptedException ie){
                Log.d(TAG, "Consumer interrupted!!");
                running = false;
                break;
            }
        }

        Log.d(TAG, "Demod thread (consumer) finished.");
    }

    private void readQueue() throws InterruptedException{
        switch(STATE){
            case STATE_LISTENING:
                // Get a chunk;  I grab 100 - 165 because I have to use a power of 2 and
                // This guaranteed to not possibly "miss" the strongest part of the hail signal
                // which is the middle 100 samples long (the entire hail is 300 samples).
                // When identified, I will need the hail signal starting at the beginning
                // which is it's weakest point (in theory exactly 100 samples before
                // the beginning of preChunk)
                short[] preChunk = a_data.slice(100, 165);


                // --- Option 1, using FFT and window ------------------------------------------- //
                // Old way, use filter
                // Use HighPass FIR filter to muffle low-freq noise
                //short[] filteredSignal = Library.FIR(preChunk);
                Library.hannWindow(preChunk);

                // This could be more efficient by using the Goertzel algorithm to compute
                // the FFT only for the values we're interested in (since we're reading only
                // two bins.  Also, the Goertzel algorithm would be more accurate; calculating
                // the response of exactly 18k.

                Complex[] in = Library.shortArrayToComplexArray(preChunk);
                final Complex[] fftData = FFT.fft(in);
                double valNeighbor = fftData[22].abs();
                double valHail = fftData[26].abs();
                //Log.d(TAG, "amb: " + valAmbient + "   hail: " + valHail);


                // --- Old method used RMS, that approach is not ideal, even with a great filter //
                // Test RMS of this chunk, the threshold could use some adjusting
                // New filter coefficients should help cut out false positives and false negatives
                //double rms = Library.RMS(0, filteredSignal.length, filteredSignal);
                //double per = rms / Short.MAX_VALUE;
                //Log.d(TAG, "RMS: " + rms + "    per: " + per);
                // ------------------------------------------------------------------------------ //

                //int slope = (int)((valHail - valNeighbor) / 2);
                double diff = valHail - valNeighbor;

                if( diff > 500){
                    Log.d(TAG, "Hail signal heard!   valHail: " + valHail + "   valNeighbor: " + valNeighbor + "   diff: " + diff);
                    //Library.writeToFile("filtered" + recCount + ".pcm", filteredSignal);
                    //Library.writeToFile("unfiltered" + recCount + ".pcm", preChunk);
                    incState();
                    //a_data.eat(64);
                } else{
                    // If this isn't loud enough there isn't a hail signal here and we can move forward
                    a_data.eat(64);
                    //Log.d(TAG, "Ate 64");
                }
                break;


            // This means we've found the hail signal and we need to decode_short the subsequent data
            // of this packet
            case STATE_DECODING:
                short[] data = a_data.slice(0, 513); // must be a power of two for findHail to finish
                int startGuess = findHail(data);
                startGuess = startGuess + Library.HAIL_SIZE + Library.RAMP_SIZE;
                Log.d(TAG, "startGuess: " + startGuess);

                // -- Dump the audio of this packet for debugging purposes ---------------------- //
                // Record a chunk of audio, this causes weirdness when "insertFromFile" is
                // called with the same file.  It does that repeating thing like when you
                // point two mirrors at each other.
                // The extra 100 samples is not really necessary.
                int L = startGuess + (Library.RAMP_SIZE * 2 * numFrames) + (Library.DATA_FRAME_SIZE * numFrames) + Library.FOOTER_SIZE + 100;
                short[] chunk = a_data.slice(0, L);
                Library.writeToFile("recent.pcm", Library.shortArray2ByteArray(chunk));
                // ------------------------------------------------------------------------------ //


                // ---- Decode the Frames ------------------------------------------------------- //
                // Eat Hail and first ramp (before first frame)
                a_data.eat(startGuess);

                StringBuilder sb = new StringBuilder();
                StringBuilder sbECC = new StringBuilder();

                //Test
                for(int i = 0; i < numFrames; i++) {
                    Log.d(TAG, "Decoding frame: " + i);
                    short[] frame = a_data.slice(0, Library.DATA_FRAME_SIZE+1);


                    if(mode == Library.MODE_SHORT) {
                        String unchecked = decode_short(frame);
                        sb.append(unchecked);
                        sbECC.append(ECC.eccCheckandExtract(unchecked));
                    } else if(mode == Library.MODE_LONG) {
                        sb.append(decode_long(frame));
                    }

                    // Eat this frame and the ending ramp and the starting ramp of the next frame
                    a_data.eat(Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE * 2));
                }

                String binary = sb.toString();
                Tests.analyzeError(binary, mode);


                // ECC

                Tests.analyzeAfterECCError(sbECC.toString());

                // ------------------------------------------------------------------------------ //



                // Possibly put in some sort of threshold here or something
                // to drop out and erase samples if there is a false positive.

                resetState();
                break;
        }
    }

    private String decode_long(short[] frameAudio){
        Complex[] in = Library.shortArrayToComplexArray(frameAudio);
        final Complex[] fftData = FFT.fft(in);
        final int startIdx = (int)(Math.round(Library.findStartingF() / (Library.SAMPLE_RATE / (fftData.length))));
        //Log.d(TAG, "startIDX: " + startIdx);


        // The actual decoding / interpreting the signals --------------------------------------- //
        // ---- AMPLITUDE ----
        // Take ABS value (to get amplitudes)
        int[] amp = abs(fftData, fftData.length / 2);
        Library.writeToFile("abs.bin", Library.intArray2ByteArray(amp));

        final int ALPHA = 2;
        final int BETA = 10;
        final double GAMMA = 0.35;

        //ArrayList<Double> thresholds = new ArrayList<Double>(80);
        FiniteIntCache upList = new FiniteIntCache(ALPHA);
        FiniteIntCache downList = new FiniteIntCache(ALPHA);
        downList.insert(0);
        upList.insert(amp[433]); // bin of calibration sub-carrier freq 1

        StringBuffer sb = new StringBuffer();
        double thresh;
        for(int i = startIdx; i <= 425; i = i + 2){
            int cur = amp[i];
            double upAVG = upList.getAVG();
            double downAVG = downList.getAVG();

            thresh = ((upAVG - downAVG) * GAMMA ) + downAVG;
            //thresholds.add(thresh);
            //Log.d(TAG, "----");
            //Log.d(TAG, "upList; " + upList);
            //Log.d(TAG, "downList: " + downList);
            //Log.d(TAG, "i: " + i + "  f: " + i * Library.SubCarrier_DELTA + "  cur: " + cur + "  thresh: " + thresh + "  upAvg: " + upAVG + "  downAvg: " + downAVG);


            // Actual decoding of sub-carriers
            if(cur > thresh){ // 1
                sb.append("1");
                if(cur < (BETA * upAVG)){
                    upList.insert(cur);
                }
            } else { // 0
                sb.append("0");
                downList.insert(cur);
            }
        }

        /*
        double[] threshArray = new double[thresholds.size()];
        for(int i = 0; i < thresholds.size(); i++){
            threshArray[i] = thresholds.get(i);
        }
        Library.writeToFile("thresh.bin", Library.doubleArray2ByteArray(threshArray));
        */
        String bitsA = sb.toString();
        return bitsA;
    }

    private String decode_short(short[] frameAudio){
        Complex[] in = Library.shortArrayToComplexArray(frameAudio);
        final Complex[] fftData = FFT.fft(in);
        final int startIdx = (int)(Math.round(Library.findStartingF() / (Library.SAMPLE_RATE / (fftData.length))));
        //Log.d(TAG, "startIDX: " + startIdx);


        // The actual decoding / interpreting the signals --------------------------------------- //
        // ---- AMPLITUDE ----
        // Take ABS value (to get amplitudes)
        // convert to double in one step
        int[] amp = abs(fftData, fftData.length / 2);
        Library.writeToFile("abs.bin", Library.intArray2ByteArray(amp));

        final int ALPHA = 2;
        final int BETA = 10;
        final double GAMMA = 0.35;

        //ArrayList<Double> thresholds = new ArrayList<Double>(80);
        FiniteIntCache upList = new FiniteIntCache(ALPHA);
        FiniteIntCache downList = new FiniteIntCache(ALPHA);
        downList.insert(0);
        upList.insert(amp[433]); // 433 is bin of calibration sub-carrier 1

        StringBuffer sb = new StringBuffer();
        double thresh;
        for(int i = startIdx; i < 487; i++){
            int cur = amp[i];
            double upAVG = upList.getAVG();
            double downAVG = downList.getAVG();

            thresh = ((upAVG - downAVG) * GAMMA ) + downAVG;
            //thresholds.add(thresh);
            //Log.d(TAG, "----");
            //Log.d(TAG, "upList; " + upList);
            //Log.d(TAG, "downList: " + downList);
            //Log.d(TAG, "i: " + i + "  f: " + i * Library.SubCarrier_DELTA + "  cur: " + cur + "  thresh: " + thresh + "  upAvg: " + upAVG + "  downAvg: " + downAVG);

            // Just a little funny business for the calibration sub-carriers
            // Calibration sub-carriers
            if(i == 433 || i == 460){ // Bin of the calibration sub-carriers, skip
                continue;
            }

            else { // Actual decoding of sub-carriers
                if(cur > thresh){ // 1
                    sb.append("1");
                    if(cur < (BETA * upAVG)){
                        upList.insert(cur);
                    }
                } else { // 0
                    sb.append("0");
                    downList.insert(cur);
                }
            }
        }

        /*
        double[] threshArray = new double[thresholds.size()];
        for(int i = 0; i < thresholds.size(); i++){
            threshArray[i] = thresholds.get(i);
        }
        Library.writeToFile("thresh.bin", Library.doubleArray2ByteArray(threshArray));
        */
        String bitsA = sb.toString();



        // The actual decoding / interpreting the signals --------------------------------------- //
        // ---- PHASE ----
        double[] phase = angle(fftData, fftData.length / 2);
        Library.writeToFile("angle.bin", Library.doubleArray2ByteArray(phase));


        // Find precise sub-sample offset
        CalibrationSubCarrier SC1 = new CalibrationSubCarrier(SubCarrier.CAL_1_FREQ, phase[433]);
        CalibrationSubCarrier SC2 = new CalibrationSubCarrier(SubCarrier.CAL_2_FREQ, phase[460]);
        double minDiff = Double.MAX_VALUE;
        double offset = 0;

        for(double virtualI = -10; virtualI < 10; virtualI = virtualI + 0.01){
            double diff = Math.abs(SC1.calcVirtualPhase(virtualI) - SC2.calcVirtualPhase(virtualI));
            if(diff < minDiff){
                minDiff = diff;
                offset = virtualI;
            }
        }
        //Log.d(TAG, "Original angle at CS1 (bin433): " + phase[433]);
        //Log.d(TAG, "0:" + SC1.calcVirtualPhase(0) + "  2: " + SC1.calcVirtualPhase(2));
        //Log.d(TAG, "0:" + SC2.calcVirtualPhase(0) + "  2: " + SC2.calcVirtualPhase(2));
        //Log.d(TAG, "minDiff: " + minDiff + "   located at virtual index offset: " + offset);


        // Decode the phases
        sb = new StringBuffer();
        double cur;

        // setup angle reading for sub-carrier 1
        CalibrationSubCarrier cal = new CalibrationSubCarrier(SubCarrier.CAL_1_FREQ, phase[433]);
        double calibratedOne = cal.calcVirtualPhase(offset);
        double f = Library.findStartingF();
        for(int i = startIdx; i < 487; i++){
            cur = SubCarrier.calcVirtualPhase(f, phase[i], offset);
            //Log.d(TAG, "f: " + f + "   Original angle reading: " + phaseArr[i] +  "   adjusted: " + cur);
            f = f + Library.SubCarrier_DELTA;

            if(i == 444){ // 1/2 way point, switch to other calibration sub-carrier
                cal = new CalibrationSubCarrier(SubCarrier.CAL_2_FREQ, phase[460]);
                calibratedOne = cal.calcVirtualPhase(offset);
            }

            // Calibration sub-carriers
            if(i == 433 || i == 460){
                //Log.d(TAG, "Calibration, resetting current known angle");
                continue;
            }

            // Other sub-carriers
            else {
                double diff = Math.abs(calibratedOne - cur);
                if(diff > Math.PI){ diff = (2* Math.PI) - diff; }

                if( diff > (0.5*Math.PI) ){
                    sb.append("0");
                } else {
                    sb.append("1");
                }
            }
        }
        String bitsP = sb.toString();

        String bits = zip(bitsA, bitsP); // Just a simple interleave
        return bits;
    }


    // Dr. Novak
    // Find hail signal in this data
    private int findHail(short[] data) {
        // This function is kind of heavy and should not be done frequently.ß

        // Look for header with xcorr
        // Make hail signal (known / ground truth)
        short[] known = Library.makeHail(Library.HAIL_TYPE_SWEEP);
        double[] xcorrData = new double[data.length];

        // We slide the known across the search length
        // from 0 (on the left) to searchLen (on the right)
        // So, in the final window position, the right edge of
        // known will be at searchLen and the left edge of the
        // known will be at searchLen - known.length
        for(int winOffset = 0; winOffset < data.length-known.length; winOffset++) {
            double xcorr = crossCorr(known, data, winOffset);
            xcorrData[winOffset] = xcorr;
            //Log.d(TAG, "xcorr: " + xcorr);
        }

        // Envelope
        Complex[] env = Hilbert.transform(xcorrData);

        // Find location of maximum value in envelop
        double max = Double.MIN_VALUE;
        int maxIdx = 0;
        for(int i = 0; i < data.length; i++){
            double cur = env[i].abs();
            if(cur > max){
                maxIdx = i;
                max = cur;
            }
        }

        int ans = maxIdx - 0;
        return ans;
    }



    // Dr. Novak
    // calculate the correlation between data and the items array at the given offset
    // Offset is samples from the front of the queue (samples from takeIndex)
    //http://stackoverflow.com/questions/23610415/time-delay-of-sound-files-using-cross-correlation
    private double crossCorr(short[] known, short[] data, int offSet) {
        // This function assumes that the data will be there in both data and items
        // Be sure that there are enough samples built it before calling this method
        // or you will get an exception!
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double syy = 0.0;
        double sxy = 0.0;

        int n = known.length;

        for(int dataIdx = 0; dataIdx < n; dataIdx++) {
            double d1 = known[dataIdx];
            double d2 = (double)(data[dataIdx + offSet]);

            sx += d1;
            sy += d2;
            sxx += d1 * d1;
            syy += d2 * d2;
            sxy += d1 * d2;
        }

        // covariation
        double cov = sxy / n - sx * sy / n / n;
        // standard error of x
        double sigmax = Math.sqrt(sxx / n -  sx * sx / n / n);
        // standard error of y
        double sigmay = Math.sqrt(syy / n -  sy * sy / n / n);

        // correlation is just a normalized covariation
        return cov / sigmax / sigmay;
    }


    private int[] abs(Complex[] input, int end){
        // The second 1/2 of the values are harmonics / great than nyquist.
        int[] output = new int[end];
        for(int i = 0; i < end; i++){
            output[i] = (int)(input[i].abs()); // get ABS and convert to int
        }
        return output;
    }


    private double[] angle(Complex[] input, int end){
        // The second 1/2 of the values are harmones / greater than the nyquist.
        double[] output = new double[end];
        for(int i = 0; i < end; i++){
            output[i] = input[i].phase();
            //output[i] = output[i] + Math.PI;
        }
        //Log.d(TAG, "phase[432]:  " + output[432]);
        return output;
    }


    private int incState(){
        Log.d(TAG, "Transitioning from: " + STATE + " to: " + (STATE+1));
        STATE++;
        if(STATE > STATE_DECODING){
            throw new IllegalStateException("Cannot increment past " + STATE_DECODING + " (DECODING)");
        }
        else{
            transitionTS = System.currentTimeMillis();
        }
        return STATE;
    }


    private int decState(){
        Log.d(TAG, "Transitioning from: " + STATE + " to: " + (STATE-1));
        STATE--;
        if(STATE < STATE_LISTENING){
            throw new IllegalStateException("Cannot decrement past " + STATE_LISTENING + " (PRE");
        } else {
            transitionTS = System.currentTimeMillis();
        }
        return STATE;
    }


    private int resetState(){
        Log.d(TAG, "Transitioning from: " + STATE + " to: " + STATE_LISTENING);
        STATE = STATE_LISTENING;
        transitionTS = System.currentTimeMillis();
        return STATE;
    }


    private int getMaxIndex(double[] data){
        double max = Double.MIN_VALUE;
        int maxIdx = 0;
        for(int i = 0; i < data.length; i++){
            double cur = data[i];
            if(cur > max){
                max = cur;
                maxIdx = i;
            }
        }
        return maxIdx;
    }


    public static int getMinQueueSize(){
        // The hail signal is 300 samples and the loud part is 100 samples long
        // So 50 samples will definitely land in / on that 100 sample portion
        // It might take at most 2 sets of 50 samples before we reach the height of the hail signal
        // FYI: 50 samples at 44100 = 0.0011337 seconds = 1.1337 ms ELITE!
        // The mic might output minBSize in one go, so it's a good idea to have
        // room for at least twice that (in case it gets called again before any
        // data can be removed)
        return (int)Library.SAMPLE_RATE * 2; // 2 seconds of space
    }


    private String zip (String a, String b){
        if(a.length() != b.length()){
            throw new IllegalArgumentException("A and B must be equal length");
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < a.length(); i++) {
            sb.append(a.charAt(i));
            sb.append(b.charAt(i));
        }
        return sb.toString();
    }
}