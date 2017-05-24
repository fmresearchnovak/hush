package net.ednovak.ultrasound.deprecated;

// This is based on the open source ArrayBlockingQueue that ships with Java.
// I am extending it to include a few useful functions (peek(i) and peek(a, b)
// as well as functionality for my modem.
// That is why it's called DemodQueue, it's used for demodulation

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */


import android.util.Log;

import net.ednovak.ultrasound.Library;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;
import java.util.*;

/**
 * A bounded {@linkplain BlockingQueue blocking queue} backed by an
 * array.  This queue orders elements FIFO (first-in-first-out).  The
 * <em>head</em> of the queue is that element that has been on the
 * queue the longest time.  The <em>tail</em> of the queue is that
 * element that has been on the queue the shortest time. New elements
 * are inserted at the tail of the queue, and the queue retrieval
 * operations obtain elements at the head of the queue.
 *
 * <p>This is a classic &quot;bounded buffer&quot;, in which a
 * fixed-sized array holds elements inserted by producers and
 * extracted by consumers.  Once created, the capacity cannot be
 * increased.  Attempts to <tt>put</tt> an element into a full queue
 * will result in the operation blocking; attempts to <tt>take</tt> an
 * element from an empty queue will similarly block.
 *
 * <p> This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to <tt>true</tt> grants threads access in FIFO order. Fairness
 * generally decreases throughput but reduces variability and avoids
 * starvation.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class DemodQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    private final static String TAG = DemodQueue.class.getName();

    /**
     * Serialization ID. This class relies on default serialization
     * even for the items array, which is default-serialized, even if
     * it is empty. Otherwise it could not be declared final, which is
     * necessary here.
     */
    private static final long serialVersionUID = -817911632652898426L;

    /** The queued items  */
    private final E[] items;
    /** items index for next take, poll or remove */
    private int takeIndex;
    /** items index for next put, offer, or add. */
    private int putIndex;
    /** Number of items in the queue */
    private int count;

    /*
     * Concurrency control uses the classic two-condition algorithm
     * found in any textbook.
     */

    /** Main lock guarding all access */
    private final ReentrantLock lock;
    /** Condition for waiting takes */

    /** Condition for waiting puts */
    private final Condition notFull;
    /** Condition for waiting takes */
    //private final Condition notEmpty;
    /** Condition for waiting large takes */
    private final Condition hasMore;

    // Internal helper methods

    /**
     * Circularly increment i.
     */
    final int inc(int i) {
        return (++i == items.length)? 0 : i;
    }

    /**
     * Inserts element at current put position, advances, and signals.
     * Call only when holding lock.
     */
    private void insert(E x) {
        items[putIndex] = x;
        putIndex = inc(putIndex);
        ++count;
        hasMore.signal();
    }

    /**
     * Extracts element at current take position, advances, and signals.
     * Call only when holding lock.
     */
    private E extract() {
        final E[] items = this.items;
        E x = items[takeIndex];
        items[takeIndex] = null;
        takeIndex = inc(takeIndex);
        --count;
        notFull.signal();
        return x;
    }

    /**
     * Utility for remove and iterator.remove: Delete item at position i.
     * Call only when holding lock.
     */
    void removeAt(int i) {
        final E[] items = this.items;
        // if removing front item, just advance
        if (i == takeIndex) {
            items[takeIndex] = null;
            takeIndex = inc(takeIndex);
        } else {
            // slide over all others up through putIndex.
            for (;;) {
                int nexti = inc(i);
                if (nexti != putIndex) {
                    items[i] = items[nexti];
                    i = nexti;
                } else {
                    items[i] = null;
                    putIndex = i;
                    break;
                }
            }
        }
        --count;
        notFull.signal();
    }

    /**
     * Creates an <tt>ArrayBlockingQueue</tt> with the given (fixed)
     * capacity and default access policy.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than 1
     */
    public DemodQueue(int capacity) {
        this(capacity, false);
    }

    /**
     * Creates an <tt>ArrayBlockingQueue</tt> with the given (fixed)
     * capacity and the specified access policy.
     *
     * @param capacity the capacity of this queue
     * @param fair if <tt>true</tt> then queue accesses for threads blocked
     *        on insertion or removal, are processed in FIFO order;
     *        if <tt>false</tt> the access order is unspecified.
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than 1
     */
    public DemodQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.items = (E[]) new Object[capacity];
        lock = new ReentrantLock(fair);
        //notEmpty = lock.newCondition();
        notFull =  lock.newCondition();
        hasMore = lock.newCondition();
    }

    /**
     * Creates an <tt>ArrayBlockingQueue</tt> with the given (fixed)
     * capacity, the specified access policy and initially containing the
     * elements of the given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param capacity the capacity of this queue
     * @param fair if <tt>true</tt> then queue accesses for threads blocked
     *        on insertion or removal, are processed in FIFO order;
     *        if <tt>false</tt> the access order is unspecified.
     * @param c the collection of elements to initially contain
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than
     *         <tt>c.size()</tt>, or less than 1.
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public DemodQueue(int capacity, boolean fair,
                              Collection<? extends E> c) {
        this(capacity, fair);
        if (capacity < c.size())
            throw new IllegalArgumentException();

        for (Iterator<? extends E> it = c.iterator(); it.hasNext();)
            add(it.next());
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning <tt>true</tt> upon success and throwing an
     * <tt>IllegalStateException</tt> if this queue is full.
     *
     * @param e the element to add
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     * @throws IllegalStateException if this queue is full
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return super.add(e);
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning <tt>true</tt> upon success and <tt>false</tt> if this queue
     * is full.  This method is generally preferable to method {@link #add},
     * which can fail to insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == items.length)
                return false;
            else {
                insert(e);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * for space to become available if the queue is full.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            try {
                while (count == items.length)
                    notFull.await();
            } catch (InterruptedException ie) {
                notFull.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            insert(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * up to the specified wait time for space to become available if
     * the queue is full.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {

        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                if (count != items.length) {
                    insert(e);
                    return true;
                }
                if (nanos <= 0)
                    return false;
                try {
                    nanos = notFull.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    notFull.signal(); // propagate to non-interrupted thread
                    throw ie;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == 0)
                return null;
            E x = extract();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            try {
                while (count == 0)
                    hasMore.await();
            } catch (InterruptedException ie) {
                hasMore.signal(); // propagate to non-interrupted thread
                throw ie;
            }
            E x = extract();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                if (count != 0) {
                    E x = extract();
                    return x;
                }
                if (nanos <= 0)
                    return null;
                try {
                    nanos = hasMore.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    hasMore.signal(); // propagate to non-interrupted thread
                    throw ie;
                }

            }
        } finally {
            lock.unlock();
        }
    }

    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (count == 0) ? null : items[takeIndex];
        } finally {
            lock.unlock();
        }
    }

    // Dr. Novak
    // Convenience function.  Eat a chunk of this buffer (special value)
    public boolean eat(int numItems){
        final ReentrantLock l = this.lock;
        l.lock();
        int ans = fastForward(takeIndex, numItems, putIndex);
        if(ans != -1){
            takeIndex = ans;
            count = count - numItems;
        }
        l.unlock();
        return ans != -1;

    }

    private void validateStartEnd(int s, int e){
        if(s < 0 || e < 0){
            throw new IndexOutOfBoundsException("Index out of bounds.  start: " + s + "  end: " + e);
        } else if ( s >= e ){
            throw new IllegalArgumentException("Invalid start: " + s + "  or end: " + e);
        }
    }

    // Dr. Novak
    // Convenience function.  View at a range of values
    // Similar to peek, except it's blocking
    public E[] view(int n) throws InterruptedException {

        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        // Necessary because I can't:  new E[]
        E[] ans = (E[])(new Object[n]);

        int cur = takeIndex;
        for(int i = 0; i < ans.length; i++){
            try{
                while(cur > putIndex) {
                    hasMore.await();
                }
            } catch (InterruptedException ie){
                hasMore.signal();
                throw ie;
            }

            ans[i] = items[cur];
            cur = inc(cur);
        }

        lock.unlock();
        return ans;

    }


    // Dr. Novak
    // Convenience function.  View at a range of values
    // Similar to peek, except it's blocking
    // Similar to view(n), except can specify start and end
    public E[] view(int s, int e) throws InterruptedException{
        validateStartEnd(s, e);
        int len = e - s;
        if(len > items.length){
            throw new IllegalArgumentException("Requested length (e - s): " + (e-s) + "  is greater than max capacity: " + items.length);
        }

        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            int realS = takeIndex;
            for (int i = 0; i < s; i++) {
                realS = inc(realS);
                while (realS > putIndex) {
                    hasMore.await();
                }
            }

            E[] ans = (E[]) new Object[len];
            int idx = realS;
            for(int i = 0; i < len; i++){
                E cur = items[idx];
                if(cur == null){
                    Log.d(TAG, "Something went wrong!");
                }
                ans[i] = items[idx];
                idx = inc(idx);
                while(idx > putIndex){ // Wait for more items if necessary
                    hasMore.await();
                }
            }
            return ans;


        } catch (InterruptedException ie){
            hasMore.signal(); // Propogate to non-interrupted thread
            throw ie;
        } finally {
            lock.unlock();
        }
    }


    // Dr. Novak
    // Convenience function.  Peek at a range of values
    // Similar to View except it's not blocking (special value)
    public E[] peek(int s, int e){
        validateStartEnd(s, e);

        final ReentrantLock l = this.lock;
        l.lock();

        if(s > count || e > count){
            l.unlock();
            return null;
        }

        int realS = fastForward(takeIndex, s, putIndex);
        int len = e - s;
        int realE = fastForward(realS, len, putIndex);
        if(realS == -1 || realE == -1){
            l.unlock();
            return null;
        }

        // Object array cast as E[] because I cannot instantiate a type paramter array
        int cur = realS;
        E[] copy = (E[])(new Object[len]);
        for(int i = 0; i < len; i++){
            copy[i] = items[cur];
            cur = inc(cur);
        }
        l.unlock();
        return copy;

    }

    // Dr. Novak
    // Convenience function, dump this data to give file
    public void writeToFile(String path){

        byte[] byteData = new byte[this.size() * 2]; // 2 bytes in a short
        Log.d(TAG, "byteData length: " + byteData.length);
        Log.d(TAG, "q.size(): " + this.size() + "   * 2 = " + this.size()*2);

        try {
            E[] shortData = this.view(this.size());
            for(int i = 0; i < shortData.length; i++){
                short val = (Short)shortData[i];
                // To play these files, use aplay -f cd -c 1 "filename" on a linux system
                byteData[i*2] = (byte)(val & 0x00FF);
                byteData[(i*2)+1] = (byte) (val >> 8);
            }

            FileOutputStream fos = new FileOutputStream(path);
            fos.write(byteData);
            fos.close();

        } catch (InterruptedException ie){ return;}
        catch(FileNotFoundException e){ return;}
        catch (IOException e) { return;}
        Log.d(TAG, "File dumped successfully to: " + path);
    }


    // Dr. Novak
    // Convenience function.  Peek at a single value at any location
    public E peek(int i){
        final ReentrantLock l = this.lock;
        l.lock();
        if (count == 0){
            l.unlock();
            return null;
        }
        int realIdx = fastForward(takeIndex, i, putIndex);
        if(realIdx == -1){
            l.unlock();
            return null;
        }

        l.unlock();
        return items[realIdx];
    }


    // Dr. Novak
    // Convenience function.
    // Call inc "times" but do not cross guard value (ret -1 if we cross it)
    // Only call while locked
    private int fastForward(int idx, int times, int guard){
        for(int j = 0; j < times; j++){
            idx = inc(idx);
            if(idx == guard){
                return -1;
            }
        }
        return idx;
    }


    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current <tt>size</tt> of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting <tt>remainingCapacity</tt>
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.length - count;
        } finally {
            lock.unlock();
        }
    }


    // Dr. Novak
    public int maxCapacity(){
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.length;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element <tt>e</tt> such
     * that <tt>o.equals(e)</tt>, if this queue contains one or more such
     * elements.
     * Returns <tt>true</tt> if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return <tt>true</tt> if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o == null) return false;
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int k = 0;
            for (;;) {
                if (k++ >= count)
                    return false;
                if (o.equals(items[i])) {
                    removeAt(i);
                    return true;
                }
                i = inc(i);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if this queue contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this queue contains
     * at least one element <tt>e</tt> such that <tt>o.equals(e)</tt>.
     *
     * @param o object to be checked for containment in this queue
     * @return <tt>true</tt> if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int k = 0;
            while (k++ < count) {
                if (o.equals(items[i]))
                    return true;
                i = inc(i);
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] a = new Object[count];
            int k = 0;
            int i = takeIndex;
            while (k < count) {
                a[k++] = items[i];
                i = inc(i);
            }
            return a;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * <tt>null</tt>.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose <tt>x</tt> is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of <tt>String</tt>:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to
     * <tt>toArray()</tt>.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (a.length < count)
                a = (T[])java.lang.reflect.Array.newInstance(
                        a.getClass().getComponentType(),
                        count
                );

            int k = 0;
            int i = takeIndex;
            while (k < count) {
                a[k++] = (T)items[i];
                i = inc(i);
            }
            if (a.length > count)
                a[count] = null;
            return a;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return super.toString();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int k = count;
            while (k-- > 0) {
                items[i] = null;
                i = inc(i);
            }
            count = 0;
            putIndex = 0;
            takeIndex = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int n = 0;
            int max = count;
            while (n < max) {
                c.add(items[i]);
                items[i] = null;
                i = inc(i);
                ++n;
            }
            if (n > 0) {
                count = 0;
                putIndex = 0;
                takeIndex = 0;
                notFull.signalAll();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final E[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = takeIndex;
            int n = 0;
            int sz = count;
            int max = (maxElements < count)? maxElements : count;
            while (n < max) {
                c.add(items[i]);
                items[i] = null;
                i = inc(i);
                ++n;
            }
            if (n > 0) {
                count -= n;
                takeIndex = i;
                notFull.signalAll();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }


    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The returned <tt>Iterator</tt> is a "weakly consistent" iterator that
     * will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return new Itr();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Iterator for ArrayBlockingQueue
     */
    private class Itr implements Iterator<E> {
        /**
         * Index of element to be returned by next,
         * or a negative number if no such.
         */
        private int nextIndex;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         */
        private E nextItem;

        /**
         * Index of element returned by most recent call to next.
         * Reset to -1 if this element is deleted by a call to remove.
         */
        private int lastRet;

        Itr() {
            lastRet = -1;
            if (count == 0)
                nextIndex = -1;
            else {
                nextIndex = takeIndex;
                nextItem = items[takeIndex];
            }
        }

        public boolean hasNext() {
            /*
             * No sync. We can return true by mistake here
             * only if this iterator passed across threads,
             * which we don't support anyway.
             */
            return nextIndex >= 0;
        }

        /**
         * Checks whether nextIndex is valid; if so setting nextItem.
         * Stops iterator when either hits putIndex or sees null item.
         */
        private void checkNext() {
            if (nextIndex == putIndex) {
                nextIndex = -1;
                nextItem = null;
            } else {
                nextItem = items[nextIndex];
                if (nextItem == null)
                    nextIndex = -1;
            }
        }

        public E next() {
            final ReentrantLock lock = DemodQueue.this.lock;
            lock.lock();
            try {
                if (nextIndex < 0)
                    throw new NoSuchElementException();
                lastRet = nextIndex;
                E x = nextItem;
                nextIndex = inc(nextIndex);
                checkNext();
                return x;
            } finally {
                lock.unlock();
            }
        }

        public void remove() {
            final ReentrantLock lock = DemodQueue.this.lock;
            lock.lock();
            try {
                int i = lastRet;
                if (i == -1)
                    throw new IllegalStateException();
                lastRet = -1;

                int ti = takeIndex;
                removeAt(i);
                // back up cursor (reset to front if was first element)
                nextIndex = (i == ti) ? takeIndex : i;
                checkNext();
            } finally {
                lock.unlock();
            }
        }
    }


    // Dr. Novak
    // Find hail signal in this data
    public double[] findHail(int searchLen){

        // Maybe look for header with FFT?
        // Maybe I need to lock here?
        while(count < searchLen){ // block until we can search!
            Log.d(TAG, "Waiting for more samples!  There are: "  + count + " and I need: " + searchLen);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e){
                return null;
            }
        }


        // Look for header with xcorr
        // Make hail signal (known / ground truth)
        short[] known = Library.makeHail(Library.HAIL_TYPE_SWEEP);



        double[] points = new double[searchLen];
        // We slide the known across the search length
        // from 0 (on the left) to searchLen (on the right)
        // So, in the final window position, the right edge of
        // known will be at searchLen and the left edge of the
        // known will be at searchLen - known.length
        for(int winOffset = 0; winOffset < searchLen-known.length; winOffset++) {
            try {
                 double xcorr = crossCorr(known, winOffset);
                points[winOffset] = xcorr;
            } catch (IllegalStateException e1){
                return null;
            } catch (InterruptedException e2){
                return null;
            }
            //Log.d(TAG, "xcorr: " + xcorr);
        }
        //http://stackoverflow.com/questions/23610415/time-delay-of-sound-files-using-cross-correlation

        return points;
    }

    // Dr. Novak
    // calculate the correlation between data and the items array at the given offset
    // Offset is samples from the front of the queue (samples from takeIndex)
    private double crossCorr(short[] data, int offSet) throws InterruptedException{
        // This function assumes that the data will be there in both data and items
        // Be sure that there are enough samples built it before calling this method
        // or you will get an exception!
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double syy = 0.0;
        double sxy = 0.0;

        final ReentrantLock l = this.lock;
        l.lock();
        int n = data.length;
        int qIdx = fastForward(takeIndex, offSet, putIndex);
        assert(qIdx != -1);

        for(int dataIdx = 0; dataIdx < n; dataIdx++) {
            double d1 = data[dataIdx];
            double d2 = ((Short)items[qIdx]).doubleValue();

            sx += d1;
            sy += d2;
            sxx += d1 * d1;
            syy += d2 * d2;
            sxy += d1 * d2;

            qIdx = inc(qIdx);
        }

        // covariation
        double cov = sxy / n - sx * sy / n / n;
        // standard error of x
        double sigmax = Math.sqrt(sxx / n -  sx * sx / n / n);
        // standard error of y
        double sigmay = Math.sqrt(syy / n -  sy * sy / n / n);

        // correlation is just a normalized covariation
        l.unlock();
        return cov / sigmax / sigmay;
    }

}

