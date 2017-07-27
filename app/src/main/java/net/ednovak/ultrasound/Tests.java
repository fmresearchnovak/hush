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

    private final static String test431 = "000111001011111010010110001111100010111011000100101111100010101100001011000011000011011001101100001011000001001100011100110011110011101010101010101000011010011001100000111110111000001001010010111011111010110000111110001111010011000110011110010000100011011010110110101110110011001000010001011011111001000111000101110111111011101110010111001000010010110110101001011010011110110001100001100011111101100100011101111000010101011110111011111110110001010101100100011101011011";
    private final static String test202 = "111010011010110001011000111110001011101100010010111110001010100100101100001101000000111011001010110000101100000010011000111001100111001110101101010101010000000111001101100100111101110000001001000101110111110101100001111110001110100111";
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
        s1.addTo(tmpSignal, 0);
        s2.addTo(tmpSignal, 0);

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


    public static void FFTTest(){

		//Simple FFT test
		int l = 1024;
		double[] signal = new double[l];
		Complex[] testD = new Complex[l];
		SubCarrier f = new SubCarrier(18000, Math.PI, Library.AMP_HIGH);
		f.addTo(signal, 0);

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

    public static void newFFTTest(){
        short[] in = new short[FFTtestIn.length];
        for(int i = 0; i < in.length; i++){
            in[i] = (short)(FFTtestIn[i].re());
        }

        NewFFT fourier = new NewFFT(in.length);
        Complex[] ans = fourier.audioFFT(in);

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
        s.addTo(tmp, 0);

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


    private static void analyzeErrorLong(String binary, int mode){
        String data = Library.getRandomBits(202);
        String size = Library.genSizeField(202, mode);
        String eccIn = size + data;
        String eccOut = "";
        for(int i = 0; i < 3; i++){
            String batch = eccIn.substring(i*70, ((i+1) * 70));
            String tmp = ECC.eccImplementation(batch, 7);
            eccOut = eccOut + tmp;
        }
        String gndTruth = eccOut;

        // Amplitude analysis
        Library.printErrorAnalysis(binary, gndTruth);
    }

    public static void analyzeError(String binary, int mode){

        Log.d(TAG, "--- Pre ECC Errors ---------------------------------------");


        if(mode == MODE_LONG){
            analyzeErrorLong(binary, mode);
            return;
        }

        // Unzip binary
        String[] unzipped = unzip(binary);
        String bitsA = unzipped[0];
        String bitsP = unzipped[1];

        // Generate ground truth
        // 431 maximum number of data bits (not counting size field and ecc bits) / packet
        String data = Library.getRandomBits(431);
        String size = Library.genSizeField(431, mode);
        String eccIn = size + data;
        String eccOut = "";
        for(int i = 0; i < 3; i++){
            // 147 = number of bits in each frame
            String batch = eccIn.substring(i*147, ((i+1)*147));
            //Log.d(TAG, "Batch: " + batch);
            // 8 = parity bits in each frame
            String tmp = ECC.eccImplementation(batch, 8);
            eccOut = eccOut + tmp;
            //Log.d(TAG, "ECC part:" + tmp);
        }

        String gndTruth =  eccOut;
        String[] gndUnzipped = unzip(gndTruth);
        String gndA = gndUnzipped[0];
        String gndP = gndUnzipped[1];

        Log.d(TAG, "bitsA length: " + bitsA.length() + "   bitsP length: " + bitsP.length() + "   binary length: " + binary.length() + "   gndTruth length: " + gndTruth.length());

        // Amplitude analysis
        Log.d(TAG, "Amplitude Error");
        Library.printErrorAnalysis(bitsA, gndA);
        Log.d(TAG, " ");

        // Phase analysis
        Log.d(TAG, "Phase Error");
        Library.printErrorAnalysis(bitsP, gndP);
        Log.d(TAG, " ");

        // Entire packet analysis
        Library.printErrorAnalysis(binary, gndTruth);
        Log.d(TAG, "---------------------------------------------------------");

    }

    public static void analyzeAfterECCError(String binary, int mode){

        // Entire packet analysis after ECC comparison
        Log.d(TAG, "--- Post ECC Errors -------------------------------------");
        int l = Library.getL(mode);
        String gndTruth = Library.genSizeField(l, mode) + Library.getRandomBits(l);
        Library.printErrorAnalysis(binary, gndTruth);
        Log.d(TAG, "---------------------------------------------------------");

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

    public static void testAudioBlockingList(){
        final BlockingAudioList<Short> data = new BlockingAudioList<Short>(12);

        Thread producer = new Thread(new Runnable(){
            @Override
            public void run() {
                Short c = 0;
                boolean resp;
                while(true){
                    Log.d(TAG, "Inserting...");
                    resp = data.offer(c++);
                    if(!resp){
                        Log.d(TAG, "List is full!  Cannot insert!");
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e){};
                }
            }
        });

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Log.d(TAG, "eating...");
                        data.eat(1);
                        Thread.sleep(2500);
                    }
                    catch(InterruptedException e){}
                }
            }
        });

        Thread shower = new Thread(new Runnable() {
            @Override
            public void run() {
                short[] tmp;
                StringBuilder sb;
                while(true){
                    sb = new StringBuilder(data.size()*2);
                    sb.append(data.size() + " ");
                    try{
                        if(data.size() == 0){
                            Log.d(TAG, "[]");
                            Thread.sleep(400);
                            continue;
                        }
                        tmp = data.slice(0, data.size()+1);
                        sb.append("[");
                        for(int i = 0; i < tmp.length; i++){
                            sb.append(tmp[i] + ",");
                        }
                        sb.deleteCharAt(sb.length()-1);
                        sb.append("]");
                        Log.d(TAG, sb.toString());
                        Thread.sleep(400);
                    } catch (InterruptedException e){};
                }
            }
        });

        producer.start();
        consumer.start();
        shower.start();
    }
}
