package net.ednovak.ultrasound;


import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by enovak on 12/14/16.
 * In these methods they all should be atomic
 * So it's necessary to lock
 * For blocking methods we want for conditions (while holding
 * the lock, not sure how that works)
 */

public class BlockingAudioList<E extends Short> {
    private final static String TAG = BlockingAudioList.class.getName();

    private final E[] items;
    private int takeIndex; // back
    private int putIndex;  // front
    private int count;     // num of items

    private int capacity;

    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition hasMore;

    // Utility methods

    // These do not offer any protection!
    // Be sure to check conditions like is full and so on
    private void implant(E x){ // Do not call without lock
        items[putIndex] = x;
        putIndex = inc(putIndex);
        count++;
        hasMore.signal();
    }

    private E extract(){ // Do not call without lock
        E x = items[takeIndex];
        items[takeIndex] = null;
        takeIndex = inc(takeIndex);
        count--;
        notFull.signal();
        return x;
    }

    private void showData(int s, int e){
        int times = (e-s);
        int cur = s;
        for(int i = 0; i < times; i++){
            Log.d(TAG, "items[" + cur + "]:" + items[cur]);
            cur = inc(cur);
        }
    }

    // Incrementer, wraps around 0 for proper array indicies
    final int inc(int i) {
        return (++i == items.length) ? 0 : i;
    }


    // Special value, do not call without lock
    private int fastForward(int i, int times) {
        for (int j = 0; j < times; j++) {
            // i may be greater than putIndex
            // For example, putIndex wraps from n -> 0 on some implant()
            // But! takeIndex is at n-1 (where n is the largest index of the array)
            // Actually, takeIndex is still "behind" putIndex, but takeIndex > putIndex
            // == is the correct check here.  In any case, if takeIndex == putIndex
            // we must wait, AND, both are only ever incremented by one at a time
            // using inc().  Also, neither one can move backwards.
            if (i == putIndex) {
                return -1;
            }
            i = inc(i);
        }
        return i;
    }

    // Blocks, do not call without lock
    private int slowForward(int i, int times) throws InterruptedException {
        for (int j = 0; j < times; j++) {
            try {
                // See comment for fastForward()
                while (i == putIndex) {
                    //Log.d(TAG, "Waiting for more samples because i == putIndex");
                    hasMore.await();
                }
                i = inc(i);
            } catch (InterruptedException ie){
                hasMore.signal();
                throw ie;
            }

        }
        return i;
    }
    // End of utility methods

    // Constructor
    public BlockingAudioList(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("Invalid capacity: " + cap);
        }
        this.items = (E[]) new Short[cap];
        capacity = cap;
        lock = new ReentrantLock(false); // When contention, lock is given to longest waiting if true
        notFull = lock.newCondition();
        hasMore = lock.newCondition();
    }


    public E get(int i) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == 0) {
                return null;
            }

            int realIdx = fastForward(takeIndex, i);
            if (realIdx == -1 || realIdx == putIndex) {
                return null;
            }

            return items[realIdx];
        } finally {
            lock.unlock();
        }
    }


    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return this.count;
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return this.capacity;
    }

    public int getCapacityRemaining() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return capacity - count;
        } finally {
            lock.unlock();
        }
    }


    public void insert(E[] newItems) throws InterruptedException {
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            try {
                for (int i = 0; i < newItems.length; i++) {
                    while (count == items.length) {
                        notFull.await();
                    }
                    implant(newItems[i]);
                }

            } catch (InterruptedException ie) {
                notFull.signal();
                throw ie;
            }
        } finally {
            lock.unlock();
        }
    }

    // e is non-inclusive
    public short[] slice(int s, int e) throws InterruptedException {
        if (s < 0 || e < 0) {
            throw new IndexOutOfBoundsException();
        } else if (s >= e) {
            throw new IllegalArgumentException();
        }

        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int cur = takeIndex;
            cur = slowForward(cur, s); // Blocking, must have lock, may be interrupted
            //Log.d(TAG, "s: " + s + "   cur: " + cur + "   takeIndex: " + takeIndex + "   putIndex: " + putIndex);
            //showData(cur-5, cur + 5);
            short[] ans = new short[((e - 1) - s)];
            for (int i = 0; i < ans.length; i++) {
                // See comment in fast forward
                while (cur == putIndex) {
                    hasMore.await();
                }
                ans[i] = items[cur];
                //assert(ans[i] != null);
                cur = inc(cur);

            }
            return ans;

        } catch (InterruptedException ie) {
            // I'm not sure what to do if slowForward is interrupted
            // Maybe I should signal?  Maybe not?  I don't really get what signal() is for
            // Also the hasMore.await() in the while loop above may get interrupted
            hasMore.signal();
            throw ie;
        } finally {
            lock.unlock();
        }

    }

    public void eat(int n) throws InterruptedException {
        if (n < 0 || n > capacity) {
            throw new IllegalArgumentException();
        }
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            try {
                for (int i = 0; i < n; i++) {
                    while (count == 0) {
                        hasMore.await();
                    }
                    extract();
                }
            } catch (InterruptedException ie) {
                hasMore.signal();
                throw ie;
            }
        } finally {
            lock.unlock();
        }
    }


    public boolean writeToFile(String pathName){

        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            try {
                byte[] byteData = new byte[this.size() * 2];
                int cur = takeIndex;
                for (int i = 0; i < this.size(); i++) {
                    Short val = items[cur];
                    byteData[i * 2] = (byte) (val & 0x00FF);
                    byteData[(i * 2) + 1] = (byte) (val >> 8);
                    cur = inc(cur);
                }

                FileOutputStream fos = new FileOutputStream(pathName);
                fos.write(byteData);
                fos.close();
            }
            catch (FileNotFoundException e1){ return false; }
            catch (IOException e2) { return false; }
            Log.d(TAG, "File dumped successfully to: " + pathName);
            return true;
        } finally {
            lock.unlock();
        }

    }


    // Implant, non-blocking (special value)
    public boolean offer(E x){
        if(x==null) throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try{
            if (count == items.length){
                return false;
            } else {
                implant(x);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }


    // This is the status-quo method of use on the outside for implanting
    public void putNext(E item) throws InterruptedException {
        if(item == null) { throw new NullPointerException(); }

        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try{
            try{
                while(count == capacity){
                    notFull.await();
                }
            } catch (InterruptedException ie){
                notFull.signal();
                throw ie;
            }
            implant(item);
        } finally {
            lock.unlock();
        }
    }

    // This is the status-quo method of use on the outside for extracting
    public E getNext() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try{
            try {
                while (count == 0) {
                    hasMore.await();
                }
            } catch (InterruptedException ie){
                hasMore.signal();
                throw ie;
            }
            E x = extract();
            return x;
        } finally {
            lock.unlock();
        }
    }
}
