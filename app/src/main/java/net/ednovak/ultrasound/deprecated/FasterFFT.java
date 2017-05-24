package net.ednovak.ultrasound.deprecated;

import net.ednovak.ultrasound.Complex;

public class FasterFFT {
	public static final String tag = FasterFFT.class.getName();
	
	static {
		System.loadLibrary("fft");
	}

	/**
	 * Calculates the complex spectrum from real-valued input.
	 *
	 * Returns [Re(0), Re(N/2), Re(0), Im(0), Re(1), Im(1), ...]
	 */
	public static native void fft(float[] inout);
	
	public static Complex[] cleanFFT(float[] fftIN){
		Complex[] complexY = new Complex[(fftIN.length)/2];
		int j = 0;
		//Log.d(tag, "FFT Length: " + fftIN.length);
		for(int i = 0; i < fftIN.length/2.0; i+=1){
			complexY[j] = new Complex(fftIN[i*2], -fftIN[i*2+1]);
			j++;
		}
		
		return complexY;
	}
}