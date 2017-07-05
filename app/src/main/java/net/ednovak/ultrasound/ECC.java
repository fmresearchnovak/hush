package net.ednovak.ultrasound;

import android.util.Log;
import android.util.StringBuilderPrinter;

import java.util.ArrayList;

/**
 * Created by zhuofantang on 6/20/17.
 */

public class ECC {

    //ECC class use guide:
    //Implementation: take a original data string and use calcNumParityBits to get the number of parity bits needed and use eccImplementation to implement ecc
    //Check&Extract: take a ecc-implemented data string to use eccCheckandExtract to get the original and checked data string

    private final static String TAG = ECC.class.getName();

    /**
     * Calculate the number of parity bits needed for the data string (does not include the overall parity bit)
     * @param stringLength : the original data string's length
     * @return : the number of ecc bits needed for the data string
     */
    public static int calcNumParityBits(int stringLength){

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(stringLength+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }
        return numParityBits;

    }

    /**
     * Get the value of the overall parity bits
     * @param eccImplementedString: the ecc-implemented string
     * @return : the value of the overall parity bit (either 1 or 0)
     */
    public static String eccGetOverallParityBits(String eccImplementedString){

        int sum = 0;

        for(int i = 0; i < eccImplementedString.length(); i++){
            sum = sum + Integer.valueOf(eccImplementedString.substring(i, i +1));
        }

        if(sum%2 == 0){
            return "0";
        }else{
            return "1";
        }
    }

    /**
     * Insert ecc bits and one overall parity bit into a data string
     * @param originalData : original data string
     * @param numParityBits : the number of parity/ecc bits needed
     * @return a ecc implement string including the overall parity bit
     */
    public static String eccImplementation(String originalData, int numParityBits){

        //insert the parity bits into the data bits
        StringBuilder outputString = new StringBuilder("");

        int subStringIndex  = 0;
        outputString.append("0");
        for(int i = 1; i < numParityBits; i++){
            outputString.append("0");

            outputString.append(originalData.substring(subStringIndex, Math.min((subStringIndex + (int)Math.pow(2,i)-1),originalData.length())));
            subStringIndex = subStringIndex + (int)Math.pow(2,i)-1;
        }


        int newECCStringLength = outputString.length();
        //implement the values of the parity bits



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



            outputString.setCharAt((int)Math.pow(2, i)-1, Character.forDigit(sum%2, 10));


        }

        //add the overall parity bit
        String overallParityBits = eccGetOverallParityBits(outputString.toString());
        outputString.append(overallParityBits);

        return outputString.toString();

    }

    public static String eccChecking(String uncheckedString){

        String originalData = uncheckedString.substring(0, uncheckedString.length()-1); //exclude the overall parity bit

        ArrayList<Integer> parityBits = new ArrayList();

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(originalData.length()+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }


        //Log.d(TAG, "Checking, numParityBits: " + String.valueOf(numParityBits));

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
                //Log.d(TAG, "Locations is: " + String.valueOf(location));
            }

            //Log.d(TAG, "sum is : " + String.valueOf(sum) + "; integer value is : " + String.valueOf(Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1))) + "Compare result is : " + String.valueOf(Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1)) == sum%2));



        }


        String checkingOverallParityBit = eccGetOverallParityBits(uncheckedString);
        if(location != 0 && Integer.valueOf(checkingOverallParityBit) == 0){
            //Log.d(TAG, "There is two or more error. ");
        }


        //Log.d(TAG, "Checking Parity bits are : " + test.toString());

        StringBuilder output = new StringBuilder(originalData);


        //Correction
        if(location != 0 && location-1 < originalData.length()){
            if(originalData.substring(location-1, location).equals("0")){
                output.replace(location-1, location, "1");

                Log.d(TAG, "Corrected at Location: " + String.valueOf(location-1) + "Previous was: " + String.valueOf(originalData.charAt(location-1)) + " Now is: 1");
            }else{
                output.replace(location-1, location, "0");

                Log.d(TAG, "Corrected at Location: " + String.valueOf(location-1) + "Previous was: " + String.valueOf(originalData.charAt(location-1)) + " Now is: 0");
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


    /**
     * Check an ecc implemented string and correct 1 bit error
     * @param uncheckedString : must be a ecc-implemented string
     * @return the original and corrected data
     */
    public static String eccCheckandExtract(String uncheckedString){

        String checkedString = eccChecking(uncheckedString);
        String extractedString = eccDataExtraction(checkedString);

        return extractedString;

    }

}
