package net.ednovak.ultrasound;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by enovak on 12/21/16.
 */

public class SubCarrier {
    private final static String TAG = SubCarrier.class.getName();

    public final static int MIN_FREQ = 17500;
    public final static int MAX_FREQ = 20931;

    public final static double CAL_1_FREQ = 18647.75390625;
    public final static double CAL_2_FREQ = 19810.546875;

    private double f;
    private double p;
    private double a;


    public SubCarrier(double newF, double newP, double newA, boolean protectOnOff){
        if(protectOnOff){
            protect(newF, newP, newA);
        }
        init(newF, newP, newA);
    }

    public SubCarrier(double newF, double newP, double newA){
        protect(newF, newP, newA);
        init(newF, newP, newA);
    }

    private void protect(double newF, double newP, double newA) {
        if(newF < MIN_FREQ || newF >= MAX_FREQ){
            throw new IllegalArgumentException("Invalid frequency: " + newF);
        }
        if(newP < 0 || newP > (2*Math.PI)){
            throw new IllegalArgumentException("Invalid phase: " + newP);
        }
        if(newA < 0 || newA > 1){
            throw new IllegalArgumentException("Invalid amplitude: " + newA);
        }
    }

    private void init(double newF, double newP, double newA){
        f = newF;
        p = newP;
        a = newA;
    }

    public void addTo(double[] someSig, int offset){
        // This offset is used to delay the signal so that the "true" starting point
        // the wave at sample index 0, occurs at the section of the data frame that
        // I want.  This is necessary because of the noise reducing window.
        for(int i = 0; i < someSig.length; i++){
            someSig[i] = someSig[i] + getSample(i + offset);
        }
    }

    // Between 0 and 1 (since it's a double)
    private double getSample(int i){
        double s = (Math.sin( 2 * Math.PI * i * (f / Library.SAMPLE_RATE) + p)) * a;
        assert s >= 0 && s <= 1;
        return s;
    }

    public double getF() { return f; }
    public double getP() { return p; }
    public double getA() { return a; }

    public static double calcVirtualPhase(double f, double p, double idx){
        double val = (2 * Math.PI * (f / Library.SAMPLE_RATE) * idx) + p;
        val = val % (2 * Math.PI);
        return val;
    }

    public double calcVirtualPhase(double idx){
        //Log.d(TAG, "Calculating virtual phase at offset: " + idx);
        //Log.d(TAG, "f: "+ this.f + "  orig phase: " + this.p);
        double val = (2 * Math.PI * (this.f / Library.SAMPLE_RATE) * idx) + this.p;
        val = val % (2 * Math.PI);
        return val;
    }

}
