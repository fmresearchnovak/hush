package net.ednovak.ultrasound;

import android.util.Log;

import net.ednovak.ultrasound.deprecated.FasterFFT;

import java.util.Random;

import static net.ednovak.ultrasound.Library.MODE_LONG;

/**
 * Created by enovak on 5/11/17.
 */

public final class Tests {
    private final static String TAG = Tests.class.getName().toString();



    private final static Complex[] FFTtestIn = {new Complex(16891, 0), new Complex(15934, 0), new Complex(-7063, 0), new Complex(10189, 0), new Complex(-21549, 0), new Complex(13503, 0), new Complex(-30682, 0), new Complex(-14620, 0)};
    //private final static float[] testIn = {16891, 15934, -7063, 10189, -21549, 13503, -30682, -14620};
    private final static Complex[] FFTtestAns = {new Complex(-17395, 0), new Complex(22616, -42880), new Complex(33087, -33868), new Complex(54264, 4358), new Complex(-67410, 0), new Complex(54264, -4358), new Complex(33087, 33868), new Complex(22616, 42880)};
    //private final static float[] testAns = {-17397, -67409, 22616, 42880, 33087, 33868, 54264, -4358, 67410, 0, 54624, 4358, 33087, 33868, 22616, 42880};


    public static void playRecWindowHail(){
        short[] tmp = new short[Library.HAIL_SIZE];

        final double freqDelta = 1000.0 / Library.HAIL_SIZE; // change 1000Hz across HAIL_SIZE = 300 samples
        double f; // cur frequency

        for(int i = 0; i < tmp.length; i++){
            f = 18000 + (freqDelta*i);
            double val = Library.getSample(i, f, 0) * Library.MAXIMUM;
            tmp[i] = Library.double2Short(val);
        }

        Library.playSound(tmp);
    }


    public static void playOldWindowHail() {
        short[] sigOrig = Library.makeHail(Library.HAIL_TYPE_SWEEP);

        short[] sig = new short[sigOrig.length];
        for(int i = 0; i < sig.length; i++){
            sig[i] = sigOrig[i];
        }

        Library.playSound(sig);
    }


    public static void playHanWindowHail(){
        short[] sig = new short[Library.HAIL_SIZE];

        final double freqDelta = 1000.0 / Library.HAIL_SIZE; // change 1000Hz across HAIL_SIZE = 300 samples
        double f; // cur frequency

        for(int i = 0; i < sig.length; i++){
            f = 18000 + (freqDelta*i);
            double val = Library.getSample(i, f, 0) * Library.MAXIMUM;
            sig[i] = Library.double2Short(val);
        }

        // Apply Hann window
        Library.hannWindow(sig);
        Library.playSound(sig);
    }


    public static void FIR(){


		// Simple FIR test
        SubCarrier s1 = new SubCarrier(9878, 0, 1, false);
        SubCarrier s2 = new SubCarrier(18040, 0, 1, false);

        double[] tmpSignal = new double[Library.DATA_FRAME_SIZE];
        s1.addTo(tmpSignal);
        s2.addTo(tmpSignal);

        // Copy to short array
        short[] signal = new short[tmpSignal.length];
        for(int i = 0; i < signal.length; i++){
            signal[i] = Library.double2Short(tmpSignal[i] * 1000);
        }


        Long s = System.currentTimeMillis();
        short[] filteredSignal = Library.FIR(signal);
        Long e = System.currentTimeMillis();
        Log.d(TAG, "Time To Filter: " + (e - s) + "ms");
        Log.d(TAG, "Before: " + Library.toString(signal));
        Log.d(TAG, "After: " + Library.toString(filteredSignal));

    }


    public static void FFT(){

		//Simple FFT test
		int l = 1024;
		double[] signal = new double[l];
		Complex[] testD = new Complex[l];
		SubCarrier f = new SubCarrier(18000, Math.PI, Library.AMP_HIGH);
		f.addTo(signal);

		for(int i = 0; i < l; i++){
			testD[i] = new Complex(signal[i], 0);
		}

		Complex[] y = FFT.fft(testD);

		double[] amp = new double[l];
		for(int i = 0; i < l; i++){
			amp[i] = y[i].abs();
		}
    }


    public static void randomFFTTest(){
        int w = 1024;
        float[] input = new float[w];

        Random r = new Random();
        for(int i = 0; i < input.length; i++){
            input[i] =  r.nextFloat();
        }

        float[] inout = input.clone();
        long s = System.currentTimeMillis();
        FasterFFT.fft(inout);
        long e = System.currentTimeMillis();
        String t = String.valueOf(e - s);

        Log.d(TAG, "Finshed in " + t + "ms");
    }

    public static void staticFFTTest(){
        //Complex[] inout = testIn.clone();

        Complex[] ans = FFT.fft(FFTtestIn);

        //Log.d(tag, inout.toString());
        //Log.d(tag, testAns.toString());

        boolean flag = true;
        for(int i = 0; i < ans.length; i++){
            if(!ans[i].equals(FFTtestAns[i])){
                flag = false;
                Log.d(TAG, "Result Incorrect!!  Index: " + i + "  ans[i]: " + ans[i] + "  testAns[i]: " + FFTtestAns[i]);
            }
        }

        if(flag){
            Log.d(TAG, "Result Correct!");
        }
    }

    public static void FFTSinTest(){
        SubCarrier s = new SubCarrier(387, 0, 1, false);
        double[] tmp = new double[1024];
        s.addTo(tmp);

        short[] signal = new short[1024];
        for(int i = 0; i < signal.length; i++){
            signal[i] = Library.double2Short(tmp[i] * Short.MAX_VALUE);
        }


        byte[] signalBytes = Library.shortArray2ByteArray(signal);
        Library.writeToFile("raw.pcm", signalBytes);

        Complex[] signalComplex = Library.shortArrayToComplexArray(signal);
        Complex[] y = FFT.fft(signalComplex);

        Log.d(TAG, "y[0]: " + y[0] + "  y[1]: " + y[1] + "  y[2]: " + y[2]);
        Log.d(TAG, "abs y[0]: " + y[0].abs() + "  y[1]: " + y[1].abs());


        int[] fftData = new int[y.length];
        for(int i = 0; i < fftData.length; i++){
            fftData[i] = (int)(y[i].abs());
        }

        Log.d(TAG, "int fftData[0]: " + fftData[0] + "  fftData[1]: " + fftData[1] + "  fftData[2]: " + fftData[2]);

        byte[] fftBytes = Library.intArray2ByteArray(fftData);

        Library.writeToFile("fft.bin", fftBytes);
    }

    public static void finiteIntCacheTest(){
        FiniteIntCache fic = new FiniteIntCache(3);
        Log.d(TAG, fic.toString());
        Log.d(TAG, "wrapped: " + fic.isWrapped());
        fic.insert(4);
        Log.d(TAG, "inserted 4: " + fic.toString());
        Log.d(TAG, "avg: " + fic.getAVG());
        Log.d(TAG, "wrapped: " + fic.isWrapped());
        fic.insert(5);
        Log.d(TAG, "inserted 5: " + fic.toString());
        Log.d(TAG, "avg: " + fic.getAVG());
        Log.d(TAG, "wrapped: " + fic.isWrapped());
        fic.insert(2);
        Log.d(TAG, "inserted 2: " + fic.toString());
        Log.d(TAG, "avg: " + fic.getAVG());
        Log.d(TAG, "wrapped: " + fic.isWrapped());
        fic.insert(24);
        Log.d(TAG, "inserted 24: " + fic.toString());
        Log.d(TAG, "avg: " + fic.getAVG());
        Log.d(TAG, "wrapped: " + fic.isWrapped());
    }


    private static void analyzeErrorLong(String binary){
        String gndTruth = Library.genSizeField(7, MODE_LONG) + Library.getRandomBits(7);


        // Amplitude analysis
        String errA = Library.getErrors(binary, gndTruth);
        Log.d(TAG, "bitsA: " + binary);
        Log.d(TAG, "gnd A: " + gndTruth);
        Log.d(TAG, "err A: " + errA);
        for(int i = 0; i < errA.length(); i++){
            if(errA.charAt(i) == '1'){
                Log.d(TAG, "First error on bit at index number: " + i);
                break;
            }
        }
        Log.d(TAG, " " + Library.errPer(errA) + "%");
        Log.d(TAG, " ");
        Log.d(TAG, " ");
    }

    public static void analyzeError(String binary, int mode){



        if(mode == MODE_LONG){
            analyzeErrorLong(binary);
            return;
        }



        // Unzip binary
        String[] unzipped = unzip(binary);
        String bitsA = unzipped[0];
        String bitsP = unzipped[1];

        // unzip ground truth
        String gndTruth = Library.genSizeField(binary.length()-10, Library.MODE_SHORT) + Library.getRandomBits(431); // Just assume it's this one for now
        String[] gndUnzipped = unzip(gndTruth);
        String gndA = gndUnzipped[0];
        String gndP = gndUnzipped[1];

        Log.d(TAG, "bitsA length: " + bitsA.length() + "   bitsP length: " + bitsP.length() + "   binary length: " + binary.length() + "   gndTruth length: " + gndTruth.length());


        // Amplitude analysis
        String errA = Library.getErrors(bitsA, gndA);
        Log.d(TAG, "bitsA: " + bitsA);
        Log.d(TAG, "gnd A: " + gndA);
        Log.d(TAG, "err A: " + errA);
        for(int i = 0; i < errA.length(); i++){
            if(errA.charAt(i) == '1'){
                Log.d(TAG, "First error on bit at index number: " + i);
                break;
            }
        }
        Log.d(TAG, " " + Library.errPer(errA) + "%");
        Log.d(TAG, " ");


        // Phase analysis
        String errP = Library.getErrors(bitsP, gndP);
        Log.d(TAG, "bitsP: " + bitsP);
        Log.d(TAG, "gnd P: " + gndP);
        Log.d(TAG, "err P: " + errP);
        for(int i = 0; i < errP.length(); i++){
            if(errP.charAt(i) == '1'){
                Log.d(TAG, "First error on bit at index number: " + i);
                break;
            }
        }
        Log.d(TAG, " " + Library.errPer(errP) + "%");
        Log.d(TAG, " ");

        // Entire packet analysis and ECC comparison
        Log.d(TAG, "bits: " + binary);
        Log.d(TAG, "gnd : " + gndTruth);
        String errors = Library.getErrors(binary, gndTruth);
        String errorLocations = Library.getErrorLocation(binary, gndTruth);
        Log.d(TAG, "errs: " + errors);
        Log.d(TAG, "Error Locations: " + errorLocations);
        Log.d(TAG, "Error percentage: " + String.format("%2.3f", Library.errPer(errors)));

    }

    public static void analyzeAfterECCError(String binary){
        String gndTruth = Library.genSizeField(431, Library.MODE_SHORT) + Library.getRandomBits(431);
        // Entire packet analysis and ECC comparison
        Log.d(TAG, "ECC bits: " + binary);
        Log.d(TAG, "gnd : " + gndTruth);
        String errors = Library.getErrors(binary, gndTruth);
        String errorLocations = Library.getErrorLocation(binary, gndTruth);
        Log.d(TAG, "ECC errs: " + errors);
        Log.d(TAG, "ECC Error Locations: " + errorLocations);
        Log.d(TAG, "ECC Error percentage: " + String.format("%2.3f", Library.errPer(errors)));


        //Text message test
        Log.d(TAG, "---------------------------TEXT MESSAGE TEST----------------");
        decodeTextMessageTest(binary);



    }

    public static void decodeTextMessageTest(String binary){
        int size = Integer.parseInt(binary.substring(0,10), 2);

        Log.d(TAG, "Size is " + String.valueOf(size));

        if(10+size*8 < 441){
            String dataBinary = binary.substring(10, 10+size*8);
            String asciiString = Library.binary2ascii(dataBinary);

            Log.d(TAG, "Converted back is " + asciiString);
        }

       }

    private static String[] unzip(String binary){
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();

        for(int i = 0; i < binary.length()/2; i++){
            a.append(binary.charAt(i*2));
            b.append(binary.charAt((i*2)+1));
        }

        String bitsA = a.toString();
        String bitsB = b.toString();
        return new String[]{bitsA, bitsB};

    }

    public static void virtualPhaseTest(){

        SubCarrier sc = new SubCarrier(17528.02734375, 5.12, 1);
        Log.d(TAG, "vPhase at idx+1: " +  sc.calcVirtualPhase(1));

    }

    public static void printTest(){
        Log.d(TAG, "This is just a test!");
    }

    public static short[] distanceTest(){

        SubCarrier sc = new SubCarrier(18217, 0, 1.0, false);

        // ---- Create the actual audio data from this sub-carrier ----------------------------- //
        double[] signal = new double[(int)(Library.SAMPLE_RATE*3)];
        sc.addTo(signal);
        Log.d(TAG, "signal.length: " + signal.length);

        // Amplify (scale to full volume)
        final double localMax = (Library.MAXIMUM );
        short[] output = new short[signal.length];
        double tmp;
        for(int i = 0; i < signal.length; i++){
            tmp = signal[i] * localMax;
            output[i] = Library.double2Short(tmp);
        }


        return output;


    }

    public static String eccErrorGen(int numberOfError, String orginalString){

        StringBuilder errorString = new StringBuilder(orginalString);

        Random rand = new Random();
        int previousError = 0;
        for(int i = 0; i < numberOfError; i++){

            int  errorLocation = rand.nextInt(130);
            while(errorLocation == previousError){
                errorLocation = rand.nextInt(130);
            }
            if(Integer.valueOf(errorString.substring(errorLocation, errorLocation+1)) == 0){
                errorString.replace(errorLocation, errorLocation+1, "1");
            }else{
                errorString.replace(errorLocation, errorLocation+1, "0");
            }
        }

        return errorString.toString();
    }

    public static void eccTest(String gndTruthString){

        int sum = 0;
        String implementedString = ECC.eccImplementation(gndTruthString, ECC.calcNumParityBits(gndTruthString.length()));
        for(int j = 0; j<100; j++){

            StringBuilder errorString = new StringBuilder(implementedString);

            Random rand = new Random();
            int previousError = 0;
            for(int i = 0; i < 2; i++){

                int  errorLocation = rand.nextInt(130);
                while(errorLocation == previousError){
                    errorLocation = rand.nextInt(130);
                }
                if(Integer.valueOf(errorString.substring(errorLocation, errorLocation+1)) == 0){
                    errorString.replace(errorLocation, errorLocation+1, "1");
                }else{
                    errorString.replace(errorLocation, errorLocation+1, "0");
                }

                previousError = errorLocation;

                Log.d(TAG, "Error Location is " + String.valueOf(errorLocation));
            }

            if(eccDoubleErrorCheck(errorString.toString().substring(0, errorString.length()-1), implementedString.substring(implementedString.length() -1, implementedString.length()))){
                sum += 1;
            }
        }

        if(sum == 100){
            Log.d(TAG, "ECC TEST WORKING FOR SINGLE ERROR CORRECTION!!!!!");
        }else{
            Log.d(TAG, "ECC TEST NOT WORKING FOR SINGLE ERROR CORRECTION!!!!!");
            Log.d(TAG, "Sum is " + String.valueOf(sum));
        }



    }

    public static boolean eccDoubleErrorCheck(String uncheckedString, String overallParityBit){
        String checkingOverallParityBit = ECC.eccGetOverallParityBits(uncheckedString);
        if(Integer.valueOf(checkingOverallParityBit) == 0){
            return false;
        }else{
            return true;
        }
    }
}
