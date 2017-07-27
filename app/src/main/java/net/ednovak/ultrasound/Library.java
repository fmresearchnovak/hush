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
        Random r = new Random(calendar.get(Calendar.MINUTE));
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
		// Filter attempt 1, doesn't work too great
        //double[] b = {0.0536284937329220, 0.994873850798756, -0.710280527878075, -0.744040602743060, 1.51729330245694, -0.0268176491321003, -2.21174591798505, 1.88994832258316, 2.74697675339359, -8.95059850862559, 11.8815249667970, -8.95059850862559, 2.74697675339359, 1.88994832258316, -2.21174591798505, -0.0268176491321003, 1.51729330245694, -0.744040602743060, -0.710280527878075, 0.994873850798756, 0.0536284937329220};

        // Filter attempt 2, seems to work much better yay!
        //double[] b = {0.000462369270529331918319349004065088593, -0.003213784042746324186995421356982660654, 0.009717264370402479284338781440055754501, -0.017892766757331843990863617932518536691,  0.019688035060145894772354324686602922156, -0.005609686725785459522619635919227221166, -0.023846828258206095862981044319894863293,  0.04910919714488416049080754532951686997 , -0.037791632254296145254635064247850095853, -0.031694228776250076007325873206355026923,  0.145730535624873702715831313980743288994, -0.253992159591074850855108024916262365878,  0.298575056199755495267567084738402627409, -0.253992159591074850855108024916262365878,  0.145730535624873702715831313980743288994, -0.031694228776250076007325873206355026923, -0.037791632254296145254635064247850095853,  0.04910919714488416049080754532951686997 , -0.023846828258206095862981044319894863293, -0.005609686725785459522619635919227221166,  0.019688035060145894772354324686602922156, -0.017892766757331843990863617932518536691,  0.009717264370402479284338781440055754501, -0.003213784042746324186995421356982660654,  0.000462369270529331918319349004065088593};

        // Filter attempt 3, seems to work best out of the three.
        //double[] b = { 0.000006599712519865587084973157505629615, -0.000065573690553586850963442567152839047,  0.000300677155666139437652228938446796747, -0.000930323971539700513011528215656653629,  0.002186594696199962734733945879384009459, -0.004105513373585184359881861126950752805,  0.006262479011205654942651133154640774592, -0.007666560599684643304008169195640221005,  0.007059753070861844378991456494532030774, -0.00367620311848283363651002098038134136 , -0.0018709252876125900561660175824840735  ,  0.007244332437753037839955894128252111841, -0.009185380945451468784335169459609460318,  0.005435234495264893057886457228278231923,  0.003200314192088761239968208727191267826, -0.012200275219092145476129651626706618117,  0.015208638198903782351445457265981531236, -0.008026190987650977382017458694463130087, -0.007514566547809715425787491227538339444,  0.022754349645013629416245848346989077982, -0.026166465792231956982716312154479965102,  0.010780603582356712913381535656753840158,  0.019115739310922641447376690848614089191, -0.046852262646401084122960156719273072667,  0.050087271917447988289762150770911830477, -0.012879429564384394651943566145746444818, -0.063361473182473732057573556630813982338,  0.156944974742485920948809052788419649005, -0.233918068813033980246629539578862022609,  0.263662509322675930878432382087339647114, -0.233918068813033980246629539578862022609,  0.156944974742485920948809052788419649005, -0.063361473182473732057573556630813982338, -0.012879429564384394651943566145746444818,  0.050087271917447988289762150770911830477, -0.046852262646401084122960156719273072667,  0.019115739310922641447376690848614089191,  0.010780603582356712913381535656753840158, -0.026166465792231956982716312154479965102,  0.022754349645013629416245848346989077982, -0.007514566547809715425787491227538339444, -0.008026190987650977382017458694463130087,  0.015208638198903782351445457265981531236, -0.012200275219092145476129651626706618117,  0.003200314192088761239968208727191267826,  0.005435234495264893057886457228278231923, -0.009185380945451468784335169459609460318,  0.007244332437753037839955894128252111841, -0.0018709252876125900561660175824840735  , -0.00367620311848283363651002098038134136 ,  0.007059753070861844378991456494532030774, -0.007666560599684643304008169195640221005,  0.006262479011205654942651133154640774592, -0.004105513373585184359881861126950752805,  0.002186594696199962734733945879384009459, -0.000930323971539700513011528215656653629,  0.000300677155666139437652228938446796747, -0.000065573690553586850963442567152839047,  0.000006599712519865587084973157505629615};

        // Filter attempt 4, seems to work better than the rest, but still not amazing in terms of FPR
        //double[] b = {-0.000000015596704355176626824976073189266,  0.000000154809431926378886129453912347265, -0.000000848985680921443501404689835343476,  0.000003341905365192616999972866076440425, -0.000010428247731127557218120359416868581,  0.00002709899026071940954111041954810446 , -0.000060209967397594484712821522043313394,  0.000115796140224890659574386031493986593, -0.000192750850451926136102168141128743173,  0.00027291439595771211680530488052909277 , -0.000310419747923808535955414455997924961,  0.000226684298817032478783745075290312343,  0.000080309524064589894059608410348261032, -0.00070561637651516209441410198976996071 ,  0.001686851639830026696603715308242499304, -0.002947696324903359074709463527597108623,  0.004262478122441122445340777602496018517, -0.005273479024644104652674858613181640976,  0.005580704972070009255158407057706426713, -0.004893727093251094839576609274445218034,  0.003195939474094513718660515166902769124, -0.000842597245282021524090398933992673847, -0.001482589302500132667425591925791650283,  0.002979933570954417139386993795824309927, -0.003061642862218595835532841675785675761,  0.001661524127426568372620230285008346982,  0.000638175476749772304339847206477998043, -0.002775501397621947183291801763971307082,  0.003654913692749699703765653424625270418, -0.002695767483999684469703206346480328648,  0.000209618066612235785317955305551151923,  0.00263408075909268846181987555610248819 , -0.004315386141517316143101368197676492855,  0.003750683185287959769960153266765701119, -0.000966361866960176391430636932966535824, -0.002745291301742831206050832193454880326,  0.005370052934180460577073823458249535179, -0.005223357331686927576552825769340415718,  0.001983784388145583103824076687260458129,  0.002902117600130276267589302108262927504, -0.006790898783312771490316972489154068171,  0.007216997284815888057118993259564376785, -0.003388950111433077991285323093961778795, -0.003066454019407708220940067533888395701,  0.0086847646849361366527819683369671111  , -0.009943744453124169282998146002228168072,  0.005374295343966928144296968383741841535,  0.003211975253403144547892367199892760254, -0.011266098103282830636406863789034105139,  0.013787692118516076122958224914327729493, -0.008273649174923727520059024698184657609, -0.003328975991665256064372391264782891085,  0.015007585280075532579990849058049207088, -0.019580889493674941831402591674304858316,  0.0128223392772810543260852966795937391  ,  0.003415669805721203381582151692441584601, -0.021094366172004250931104607502675207797,  0.029508774441482775452438858110326691531, -0.021074920551550562625164886298989586066, -0.003472695997769396653725992862860039168,  0.033449012547281713192415253388389828615, -0.051629938195158132929751815254348912276,  0.041700641428096610341391681231471011415,  0.003505621685073015674632967986212861433, -0.077001762295719075601674319386802380905,  0.15867671280833631208828649050701642409 , -0.222424493208385226639123288805421907455,  0.246484567251166380996707516715105157346, -0.222424493208385226639123288805421907455,  0.15867671280833631208828649050701642409 , -0.077001762295719075601674319386802380905,  0.003505621685073015674632967986212861433,  0.041700641428096610341391681231471011415, -0.051629938195158132929751815254348912276,  0.033449012547281713192415253388389828615, -0.003472695997769396653725992862860039168, -0.021074920551550562625164886298989586066,  0.029508774441482775452438858110326691531, -0.021094366172004250931104607502675207797,  0.003415669805721203381582151692441584601,  0.0128223392772810543260852966795937391  , -0.019580889493674941831402591674304858316,  0.015007585280075532579990849058049207088, -0.003328975991665256064372391264782891085, -0.008273649174923727520059024698184657609,  0.013787692118516076122958224914327729493, -0.011266098103282830636406863789034105139,  0.003211975253403144547892367199892760254,  0.005374295343966928144296968383741841535, -0.009943744453124169282998146002228168072,  0.0086847646849361366527819683369671111  , -0.003066454019407708220940067533888395701, -0.003388950111433077991285323093961778795,  0.007216997284815888057118993259564376785, -0.006790898783312771490316972489154068171,  0.002902117600130276267589302108262927504,  0.001983784388145583103824076687260458129, -0.005223357331686927576552825769340415718,  0.005370052934180460577073823458249535179, -0.002745291301742831206050832193454880326, -0.000966361866960176391430636932966535824,  0.003750683185287959769960153266765701119, -0.004315386141517316143101368197676492855,  0.00263408075909268846181987555610248819 ,  0.000209618066612235785317955305551151923, -0.002695767483999684469703206346480328648,  0.003654913692749699703765653424625270418, -0.002775501397621947183291801763971307082,  0.000638175476749772304339847206477998043,  0.001661524127426568372620230285008346982, -0.003061642862218595835532841675785675761,  0.002979933570954417139386993795824309927, -0.001482589302500132667425591925791650283, -0.000842597245282021524090398933992673847,  0.003195939474094513718660515166902769124, -0.004893727093251094839576609274445218034,  0.005580704972070009255158407057706426713, -0.005273479024644104652674858613181640976,  0.004262478122441122445340777602496018517, -0.002947696324903359074709463527597108623,  0.001686851639830026696603715308242499304, -0.00070561637651516209441410198976996071 ,  0.000080309524064589894059608410348261032,  0.000226684298817032478783745075290312343, -0.000310419747923808535955414455997924961,  0.00027291439595771211680530488052909277 , -0.000192750850451926136102168141128743173,  0.000115796140224890659574386031493986593, -0.000060209967397594484712821522043313394,  0.00002709899026071940954111041954810446 , -0.000010428247731127557218120359416868581,  0.000003341905365192616999972866076440425, -0.000000848985680921443501404689835343476,  0.000000154809431926378886129453912347265, -0.000000015596704355176626824976073189266};

        // Filter attempt 5, Good balance of efficiency (number of coefficients and effectiveness)
        //double[] b = {-0.000478531045705492263930835861174273305,  0.000628822151194006876730180355394850267,  0.004513919280098038430693030420570721617, -0.021306579846291723301376919152971822768,  0.044127631507743847461000541443354450166, -0.044437843027665097084266676574770826846, -0.013989943561821821180202185530561109772,  0.134414443419061391260527216218179091811, -0.262583661827578251912740370244137011468,  0.318175967328249265086981267813825979829, -0.262583661827578251912740370244137011468,  0.134414443419061391260527216218179091811, -0.013989943561821821180202185530561109772, -0.044437843027665097084266676574770826846,  0.044127631507743847461000541443354450166, -0.021306579846291723301376919152971822768,  0.004513919280098038430693030420570721617,  0.000628822151194006876730180355394850267, -0.000478531045705492263930835861174273305};

        // Filter attempt 6, only 14 coefficients)
        double[] b = {0.000156776347133373577949835842026971022, -0.010826237132878363533805554652644786984, 0.037274590305959170999372531696280930191, -0.055110893911357039520737544080475345254,  0.011116328450365395991150663235202955548,  0.11940578609490477834942367962867137976 , -0.276622159032096193165273234626511111856,  0.348238588984696895156645268798456527293, -0.276622159032096193165273234626511111856,  0.11940578609490477834942367962867137976 ,  0.011116328450365395991150663235202955548, -0.055110893911357039520737544080475345254,  0.037274590305959170999372531696280930191, -0.010826237132878363533805554652644786984,  0.000156776347133373577949835842026971022};

        short[] y = new short[x.length];
        for(int i = 0; i < x.length; i++) {
            double cur = 0;
            for (int k = 0; k < b.length && k <= i; k++) {
                cur = cur + (b[k] * x[i - k]);
            }

            y[i] = (short)cur; // loss of precision, I don't care.
        }


        // New version may be faster because it does not create a new array
        /*
        for(int i = 0; i < x.length; i++){
            double cur = 0;
            for(int k = 0; k < b.length && k <= i; k++){
                cur = cur + (b[k] * x[i-k]);
            }
            x[i] = (short)cur;
        }
        */
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


    public static Complex[] doubleArray2ComplexArray(double[] data){
        Complex[] out = new Complex[data.length];
        for(int i = 0; i < data.length; i++){
            out[i] = new Complex(data[i], 0);
        }
        return out;
    }


    public static byte[] doubleArray2IntByteArray(double[] data){
        // To implement this I don't care about the values past the decimal point
        // and these values are very large (thousands and tens of thousands)
        // so, they're too big for shorts.  I will cast them to ints (4 bytes / int)
        // which drops the values after the decimal, and then cast each int as four bytes.
        ByteBuffer b = ByteBuffer.allocate(data.length * 4);
        b.order(ByteOrder.LITTLE_ENDIAN);
        for(int i = 0; i < data.length; i++){
            //b.putDouble(data[i]);
            int tmp = (int)data[i];
            Log.d(TAG, "val: " + tmp);
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


    public static String getErrorLocations(String a, String b){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < a.length(); i++){
            try {
                if (a.charAt(i) != b.charAt(i)) {
                    sb.append(String.valueOf(i) + ", ");
                }
            } catch (StringIndexOutOfBoundsException e1){
                break;
            }

        }
        return sb.toString();
    }

    public static int getL(int mode){
        int l = 0;
        if(mode == Library.MODE_SHORT){
            l = 431;
        } else if (mode == Library.MODE_LONG){
            l = 202;
        } else {
            throw new IllegalStateException("Invalid mode: " + mode);
        }
        return l;
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
            maxL = 8;
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
            return 78;
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


    public static void TestHilbert(){
        double[] randVals = new double[]{1.0, 2.0, 3.2, 2.2, 1.0, 2.0, 3.2, 2.2};

        Complex[] out = Hilbert.transform(randVals);
        for(int i = 0; i < out.length; i++){
            Log.d(TAG, "abs(out[i]): "  + out[i].abs());
        }


    }

    public static int errCnt(String errString){
        int cnt = 0;
        for(int i = 0; i < errString.length(); i++){
            if(errString.charAt(i) == '1'){
                cnt++;
            }
        }
        return cnt;
    }

    public static double errPer(String errString){
        double sum = (double)errCnt(errString);
        double per = (sum / errString.length()) * 100;
        return per;
    }


    public static void printErrorAnalysis(String rec, String gnd){
        String err = Library.getErrors(rec, gnd);
        Log.d(TAG, "rec : " + rec);
        Log.d(TAG, "gnd : " + gnd);
        Log.d(TAG, "err : " + err);
        String locations = Library.getErrorLocations(rec, gnd);
        Log.d(TAG, "Errors at: " + locations);
        Log.d(TAG, errCnt(err) + " / " + err.length() + " Errors.  Percentage: " + String.format("%2.4f",Library.errPer(err)) + "%");
    }
}
