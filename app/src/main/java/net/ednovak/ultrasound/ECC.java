package net.ednovak.ultrasound;

import java.util.ArrayList;

/**
 * Created by zhuofantang on 6/20/17.
 */

public class ECC {


    public static String eccImplementation(String originalData){

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(originalData.length()+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }
        //insert the parity bits into the data bits
        StringBuilder outputString = new StringBuilder("");

        int subStringIndex  = 0;
        for(int i = 0; i < numParityBits; i++){
            outputString.append("0");
            if((int)Math.pow(2, i+1)  - (int)Math.pow(2, i) - 1 > 0){
                outputString.append(originalData.substring(subStringIndex, subStringIndex + (int)Math.pow(2, i+1)  - (int)Math.pow(2, i) - 1 ));

            }

        }

        int newECCStringLength = outputString.length();

        //implement the values of the parity bits
        for(int i = 0; i < numParityBits; i++){

            int step = i + 1;
            int sum = 0;
            int index = (int)Math.pow(2, i) -1;

            while(index < newECCStringLength){

                for(int j = 0; j <= i; j++){
                    if(index + j < newECCStringLength){
                        sum = sum + Integer.valueOf(outputString.substring(index+j, index + j +1));
                    }


                }
                index += i;
                index = index + step+1;
            }

            outputString.setCharAt((int)Math.pow(2, i)-1, Character.forDigit(sum%2, 10));

        }

        return outputString.toString();

    }

    public static String eccChecking(String originalData){

        ArrayList<Integer> parityBits = new ArrayList();

        //Calculate how many parity bits needed
        int numParityBits = 0;
        while(originalData.length()+ numParityBits > (int)Math.pow(2,numParityBits)){
            numParityBits +=1;
        }
        numParityBits -= 1;

        //check for errors (Assume only one error now)
        for(int i = 0; i < numParityBits; i++){

            int step = i + 1;
            int sum = 0;

            int index = (int)Math.pow(2, i) -1;
            int indexOfParityBit = index;

            //minus the value of parity bit
            sum = sum - Integer.valueOf(originalData.substring(index, index+1));

            while(index < originalData.length()){

                for(int j = 0; j <= i; j++){
                    if(index + j < originalData.length()){
                        sum = sum + Integer.valueOf(originalData.substring(index+j, index+j+1));
                    }

                }
                index += i;
                index = index + step + 1;
            }

            if(Integer.valueOf(originalData.substring(indexOfParityBit, indexOfParityBit+1)) == sum%2){
                parityBits.add(0);
            }else{
                parityBits.add(1);
            }

        }

        StringBuilder output = new StringBuilder(originalData);


        //Correct one error if there is any
        int location = 0;
        for(int i = 0; i < numParityBits; i++){
            if(parityBits.get(i) == 1){
                location += (int)Math.pow(2, i);
            }
        }


        //Correction
        if(location != 0){
            if(Integer.valueOf(originalData.charAt(location-1)) == 0){
                output.setCharAt(location-1, Character.forDigit(1, 10));
            }else{
                output.setCharAt(location-1, Character.forDigit(0, 10));
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
        numParityBits -= 1;

        //Extract the data string
        StringBuilder dataString = new StringBuilder("");
        for(int i = 1; i < numParityBits; i++){

            dataString.append(originalData.substring((int)Math.pow(2, i), (int)Math.pow(2, i+1)-2));
        }

        return dataString.toString();
    }

}
