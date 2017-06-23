package net.ednovak.ultrasound;

import android.util.Log;
import android.util.StringBuilderPrinter;

import java.util.ArrayList;

/**
 * Created by zhuofantang on 6/20/17.
 */

public class ECC {

    private final static String TAG = ECC.class.getName();

    public static int calcNumParityBits(int stringLength){

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(stringLength+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }

        return numParityBits;

    }


    public static String eccImplementation(String originalData, int numParityBits){

        Log.d(TAG, "NumParity bits: " + String.valueOf(numParityBits));
        //insert the parity bits into the data bits
        StringBuilder outputString = new StringBuilder("");

        int subStringIndex  = 0;
        outputString.append("0");
        for(int i = 1; i < numParityBits; i++){
            outputString.append("0");

            outputString.append(originalData.substring(subStringIndex, Math.min((subStringIndex + (int)Math.pow(2,i)-1),originalData.length())));
            Log.d(TAG, "Added: "+ originalData.substring(subStringIndex, Math.min((subStringIndex + (int)Math.pow(2,i)-1),originalData.length())));
            subStringIndex = subStringIndex + (int)Math.pow(2,i)-1;
        }


        int newECCStringLength = outputString.length();

        Log.d(TAG, "ECC String length: " + String.valueOf(newECCStringLength));

        //implement the values of the parity bits

        //Test
        StringBuilder test = new StringBuilder();

        for(int i = 0; i < numParityBits; i++){

            int step = (int)Math.pow(2, i);
            int sum = 0;
            int index = (int)Math.pow(2, i) - 1 ;

            while(index < newECCStringLength){

                //sum up the bits in one step
                for(int j = 0; j < step; j++){
                    if(index < newECCStringLength){
                        sum = sum + Integer.valueOf(outputString.substring(index, index +1));
                    }else{
                        break;
                    }

                    index += 1;

                }




                index = index + step;
            }

            Log.d(TAG, "sum is : " + String.valueOf(sum) + "; integer value is : " + String.valueOf(Integer.valueOf(originalData.substring((int)Math.pow(2, i)-1, (int)Math.pow(2, i)-1+1))));

            outputString.setCharAt((int)Math.pow(2, i)-1, Character.forDigit(sum%2, 10));
            test.append(Character.forDigit(sum%2, 10) + " ,");

        }

        Log.d(TAG, "Implementation Parity bits are : " + test.toString());

        return outputString.toString();

    }

    public static String eccChecking(String originalData){

        ArrayList<Integer> parityBits = new ArrayList();

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(originalData.length()+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }


        Log.d(TAG, "Checking, numParityBits: " + String.valueOf(numParityBits));

        //check for errors (Assume only one error now)
        int location = 0;

        //Test
        StringBuilder test = new StringBuilder();
        for(int i = 0; i < numParityBits; i++){

            int step = (int)Math.pow(2, i);
            int sum = 0;

            int index = (int)Math.pow(2, i) -1;
            int indexOfParityBit = index;

            sum = sum - Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1));

            while(index < originalData.length()){

                for(int j = 0; j < step; j++){
                    if(index < originalData.length()){
                        sum = sum + Integer.valueOf(originalData.substring(index, index+1));
                    }
                    index += 1;

                }

                index = index + step ;
            }


            if(Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1)) == sum%2){
                parityBits.add(0);
                test.append("0 ,");
            }else{
                parityBits.add(1);
                test.append("1 ,");
                location += (int)Math.pow(2, i);
                Log.d(TAG, "Locations is: " + String.valueOf(location));
            }

            Log.d(TAG, "sum is : " + String.valueOf(sum) + "; integer value is : " + String.valueOf(Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1))) + "Compare result is : " + String.valueOf(Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1)) == sum%2));



        }

        Log.d(TAG, "Checking Parity bits are : " + test.toString());

        StringBuilder output = new StringBuilder(originalData);


        //Correction
        if(location != 0 && location-1 < originalData.length()){
            if(Integer.valueOf(originalData.charAt(location-1)) == 0){
                output.setCharAt(location-1, "1".charAt(0));

                Log.d(TAG, "Corrected: " + "Previous was: " + String.valueOf(originalData.charAt(location-1)) + " Now is: 1");
            }else{
                output.setCharAt(location-1, "0".charAt(0));

                Log.d(TAG, "Corrected: " + "Previous was: " + String.valueOf(originalData.charAt(location-1)) + " Now is: 0");
            }


        }



        return output.toString();

    }


    public static String eccDataExtraction(String originalData){

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(originalData.length()+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }


        //Extract the data string
        StringBuilder dataString = new StringBuilder("");
        for(int i = 1; i < numParityBits; i++){

            dataString.append(originalData.substring((int)Math.pow(2, i), Math.min((int)Math.pow(2, i+1)-1, originalData.length())));
        }

        return dataString.toString();
    }

}
