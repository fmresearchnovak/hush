package net.ednovak.ultrasound;

import java.util.List;

/**
 * Created by enovak on 5/15/17.
 */

public class FiniteIntCache {
    // This should probably be implemented as a List
    // But, that forces me to be a collection of objects
    // and this is going to be used to store doubles from
    // the output of the FFT.

    private final int[] items;
    private int idx = 0;
    private boolean wrapped = false;


    private int incr(int in){
        int out = (in + 1) % items.length;
        if(out == 0){ // This occurs when when + 1 = length on the line above.
            wrapped = true;
        }
        return out;
    }


    public int numItems(){
        // If we haven't wrapped, only compute the values inserted so far.
        // In other words, the values up to the current item at idx
        // idx points at the next spot to be written to (the next open spot, aka the TOP).
        if(wrapped){
            return items.length;
        } else {
            return idx;
        }
    }

    public FiniteIntCache(int size){
        if(size <= 0){
            throw new IllegalArgumentException("Size must be a positive integer");
        }
        items = new int[size];
        idx = 0;
    }

    public void insert(int newInt){
        items[idx] = newInt;
        idx = incr(idx);
    }

    public double getAVG(){

        int end = numItems();

        // If we have not wrapped, we should simply sum all the
        // items.  The order doesn't matter, and end = both the stopping
        // point, AND the number of items in the list.  Handy!
        double sum = 0;
        for(int i = 0; i < end; i++){
            sum = sum + items[i];
        }

        return sum / end;
    }


    public void clear(){
        // Wow, amaze, such efficient.
        wrapped = false;
        idx = 0;
    }

    public String toString() {
        if(idx == 0 && wrapped == false){
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for(int i = 0; i < items.length; i++){
            sb.append(items[i] + ", ");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.deleteCharAt(sb.length()-1); // Delete trailing "," and " "
        sb.append("]");
        return sb.toString();
    }

    public boolean isWrapped(){
        return wrapped;
    }

    public int getTopIdx(){
        return idx;
    }

    public int getCapacity(){
        return items.length;
    }

}
