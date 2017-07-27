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
    private final short[] hail;
    private int mode;
    private int recNumber = 0;

    private NewFFT fourier = new NewFFT(1024);

    //Test
    StringBuilder eccString;


    public Demodulator(BlockingAudioList newBuffer, int newMode){
        super();
        a_data = newBuffer;

        if(newMode != Library.MODE_LONG && newMode != Library.MODE_SHORT){
            throw new IllegalArgumentException("Invalid MODE");
        }
        mode = newMode;
        hail = Library.makeHail(Library.HAIL_TYPE_SWEEP); // pre-compute hail for better efficiency

    }

    private void stopSelf(){
        running = false;
        Thread.currentThread().interrupt();
        return;
    };

    public void run() {
        running = true;

        // Eat the first few samples prevent initial touch false positive
        try{
            a_data.eat((int)Library.SAMPLE_RATE);
        } catch (InterruptedException e) {};


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

        short[] preChunk = a_data.slice(50, 150);  // I could try a larger range

        // Fast enough for all phones, kind of a lot of false positives
        // interesting result; my mouse click has a distinct and strong ultrasound signal!
        preChunk = Library.FIR(preChunk);
        double rms = Library.RMS(0, preChunk.length, preChunk);
        //Log.d(TAG, "rms: " + rms);

        // needs fixing still.  Too slow for samsung phone
        // double c = correlation(hail, preChunk, 0);


        if(rms < 500){
            a_data.eat(50); // No packet here, skip forward a bunch

        } else {
            Log.d(TAG, "Detected packet  rms: " + rms);

            // This means we've found the hail signal and we need to decode_short the subsequent data
            // of this packet
            short[] data = a_data.slice(0, 2049); // must be a power of two for findHail to finish
            int startGuess = findHail(data);
            startGuess = startGuess + Library.HAIL_SIZE + Library.RAMP_SIZE;
            Log.d(TAG, "startGuess: " + startGuess);

            // -- Dump the audio of this packet for debugging purposes ---------------------- //
            // Record a chunk of audio, this causes weirdness when "insertFromFile" is
            // called with the same file.  It does that repeating thing like when you
            // point two mirrors at each other.
            // The extra 100 samples is not really necessary.
            int L = startGuess + (Library.RAMP_SIZE * 2 * numFrames) + (Library.DATA_FRAME_SIZE * numFrames) + Library.FOOTER_SIZE;
            short[] chunk = a_data.slice(0, L);
            Library.writeToFile("recent" + (recNumber++) +".pcm", Library.shortArray2ByteArray(chunk));
            // ------------------------------------------------------------------------------ //


            // ---- Decode the Frames ------------------------------------------------------- //
            // Eat Hail and first ramp (before first frame)
            //Log.d(TAG, "EATING: " + startGuess);
            a_data.eat(startGuess);
            Log.d(TAG, "Ate: " + startGuess);

            StringBuilder sb = new StringBuilder();
            StringBuilder sbECC = new StringBuilder();

            // Eat the beginning ramp before the first frame
            a_data.eat(Library.RAMP_SIZE);
            Log.d(TAG, "Ate: " + Library.RAMP_SIZE);

            for(int i = 0; i < numFrames; i++) {
                Log.d(TAG, "Decoding frame: " + i);
                short[] frame = a_data.slice(0, Library.DATA_FRAME_SIZE+1);

                String unchecked = null;
                if(mode == Library.MODE_SHORT) {
                    unchecked = decode_short(frame);

                } else if(mode == Library.MODE_LONG) {
                    unchecked = decode_long(frame);
                }
                sb.append(unchecked);
                sbECC.append(ECC.eccCheckandExtract(unchecked));

                // Eat this frame and the ending ramp and the starting ramp of the next frame
                //Log.d(TAG, "EATING " + (Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE * 2)));
                a_data.eat(Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE * 2));
                Log.d(TAG, "Ate: " + (Library.DATA_FRAME_SIZE + (Library.RAMP_SIZE *2)));
            }

            String binary = sb.toString();
            Tests.analyzeError(binary, mode);
            Log.d(TAG, " ");
            Tests.analyzeAfterECCError(sbECC.toString(), mode);

            // ------------------------------------------------------------------------------ //



            // Possibly put in some sort of threshold here or something
            // to keep from getting re-triggered by the end of the frame
            a_data.eat(4096);
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
        //Library.writeToFile("abs.bin", Library.intArray2ByteArray(amp));

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
            } else {
                // Actual decoding of sub-carriers
                if (cur > thresh) { // 1
                    sb.append("1");
                    if (cur < (BETA * upAVG)) {
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
        Library.writeToFile("thresh.bin", Library.doubleArray2IntByteArray(threshArray));
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
        //Library.writeToFile("abs.bin", Library.intArray2ByteArray(amp));

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
        Library.writeToFile("thresh.bin", Library.doubleArray2IntByteArray(threshArray));
        */
        String bitsA = sb.toString();



        // The actual decoding / interpreting the signals --------------------------------------- //
        // ---- PHASE ----
        double[] phase = angle(fftData, fftData.length / 2);
        //Library.writeToFile("angle.bin", Library.doubleArray2IntByteArray(phase));


        // Find precise sub-sample offset
        CalibrationSubCarrier SC1 = new CalibrationSubCarrier(SubCarrier.CAL_1_FREQ, phase[433]);
        CalibrationSubCarrier SC2 = new CalibrationSubCarrier(SubCarrier.CAL_2_FREQ, phase[460]);
        double minDiff = Double.MAX_VALUE;
        double offset = 0;

        for(double virtualI = -10; virtualI < 2; virtualI = virtualI + 0.01){
            double diff = Math.abs(SC1.calcVirtualPhase(virtualI) - SC2.calcVirtualPhase(virtualI));
            if(diff < minDiff){
                minDiff = diff;
                offset = virtualI;
            }
        }
        //Log.d(TAG, "Original angle at CS1 (bin433): " + phase[433]);
        //Log.d(TAG, "0:" + SC1.calcVirtualPhase(0) + "  2: " + SC1.calcVirtualPhase(2));
        //Log.d(TAG, "0:" + SC2.calcVirtualPhase(0) + "  2: " + SC2.calcVirtualPhase(2));
        Log.d(TAG, "minDiff: " + minDiff + "   located at virtual index offset: " + offset);


        // Decode the phases
        sb = new StringBuffer();
        double cur;

        double[] orig = new double[234];
        double[] corr = new double[234];

        // setup angle reading for sub-carrier 1
        CalibrationSubCarrier cal = new CalibrationSubCarrier(SubCarrier.CAL_1_FREQ, phase[433]);
        double calibratedOne = cal.calcVirtualPhase(offset);
        double f = Library.findStartingF();
        int j = 0;
        for(int i = startIdx; i < 487; i++){
            orig[j] = phase[i];
            cur = SubCarrier.calcVirtualPhase(f, phase[i], offset);
            corr[j] = cur;
            j++;

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
        //Library.writeToFile("orig_p.bin", Library.doubleArray2IntByteArray(orig));
        //Library.writeToFile("corr_p.bin", Library.doubleArray2IntByteArray(corr));


        String bits = zip(bitsA, bitsP); // Just a simple interleave
        return bits;
    }


    // Dr. Novak
    // Find hail signal in this data
    private int findHail(short[] data) {
        // This function is kind of heavy and should not be done frequently.

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
            double xcorr = correlation(known, data, winOffset);
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

        int ans = maxIdx;
        return ans;
    }



    // Dr. Novak
    // calculate the correlation between data and the items array at the given offset
    // Offset is samples from the front of the queue (samples from takeIndex)
    //http://stackoverflow.com/questions/23610415/time-delay-of-sound-files-using-cross-correlation
    private double correlation(short[] known, short[] data, int offSet) {
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
        return (int)Library.SAMPLE_RATE * 2; // 10 seconds of space
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