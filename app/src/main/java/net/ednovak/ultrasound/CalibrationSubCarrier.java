package net.ednovak.ultrasound;

/**
 * Created by enovak on 5/22/17.
 */

public class CalibrationSubCarrier extends SubCarrier {

    public static final int TYPE_CAL1 = 1;
    public static final int CAL2 = 2;

    public CalibrationSubCarrier(double newF, double newP){
        super(newF, newP, 1, false);
        double f;
        if(newF != SubCarrier.CAL_1_FREQ && newF != SubCarrier.CAL_2_FREQ) {

            throw new IllegalArgumentException("Invalid calibration sub-carrier frequency: " + newF);
        }
    }
}
