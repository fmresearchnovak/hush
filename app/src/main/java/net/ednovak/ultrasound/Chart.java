package net.ednovak.ultrasound;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;

/**
 * Created by enovak on 11/7/16.
 */

public class Chart extends SurfaceView implements SurfaceHolder.Callback {
    private final static String TAG = Chart.class.getName();

    private SurfaceHolder sh;
    private Context ctx;
    private int height;
    private int width;
    private Paint p;
    private long lastDrawTS;
    private ArrayList<Double> points = new ArrayList<Double>(1000);
    private int offset = 0;
    private float downPoint = 0;
    public boolean autoScroll = false;

    private int COLOR_BG;


    public Chart(Context ctx){
        super(ctx);
        init(ctx);
    }

    public Chart(Context ctx, AttributeSet attrs){
        super(ctx, attrs);
        init(ctx);
    }

    public Chart(Context ctx, AttributeSet attrs, int defStyle){
        super(ctx, attrs, defStyle);
        init(ctx);
    }

    private void init(Context newContext){
        sh = getHolder();
        sh.addCallback(this);

        ctx = newContext;

        p = new Paint();
        p.setColor(Color.GRAY);
        p.setStrokeWidth(5);

        lastDrawTS = System.currentTimeMillis();

        // Get background color
        TypedArray array = ctx.getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
        });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        int textColor = array.getColor(1, 0xFF00FF);
        array.recycle();
        COLOR_BG = backgroundColor;

        // For scrolling with finger (doesn't work well,
        // something wrong with input X,Y -> virtual X,Y conversion
        //mySurface = new MainSurface(this);
        //mySurface.setOnTouchListener(mySurface);
        //this.setOnTouchListener(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder nSH, int format, int nWidth, int nHeight){
        sh = nSH;
        height = nHeight;
        width = nWidth;
    }

    @Override
    public void surfaceCreated(SurfaceHolder sh){
        Log.d(TAG, "Surface Created");
        Canvas c = sh.lockCanvas();
        c.drawColor(COLOR_BG);
        sh.unlockCanvasAndPost(c);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder sh){

    }


    public void addPoint(double percentage){
        points.add(percentage);

        long now = System.currentTimeMillis();
        long delta = now - lastDrawTS;
                         // 41.6 is 24 FPS (1 Frame every 41 ms)
        if(delta > 41) { // 16.6 is 60 FPS (1 Frame every 16 ms)
            drawCurSection();
        }
    }

    public void addPoints(double[] newPoints){
        for(int i = 0; i < newPoints.length; i++){
            points.add(newPoints[i]);
        }
        drawCurSection();
    }

    public void clear(){
        points = new ArrayList<Double>(1000);
        Canvas c = sh.lockCanvas();
        c.drawColor(COLOR_BG);
        sh.unlockCanvasAndPost(c);
    }

    private void drawCurSection(){

        Canvas c = sh.lockCanvas();
        if(c == null){
            return;
        }


        int start;
        int end;
        if(autoScroll){
            start = points.size() - width;
            end = points.size();
        } else {
            start = offset;
            end = Math.min(offset+width, points.size());
        }

        c.drawColor(Color.WHITE);
        int x = 0;
        for(int i = start; i < end; i++){
            double cur = 0;
            try{
                cur = points.get(i);
            } catch (ArrayIndexOutOfBoundsException e1){
                cur = 0;
            } catch (IndexOutOfBoundsException e2) {
                cur = 0;
            }
            int y = (int)(height - (cur * height));
            c.drawPoint(x++, y, p);
        }

        lastDrawTS = System.currentTimeMillis();
        //Log.d(TAG, "Drawing new frame!");
        sh.unlockCanvasAndPost(c);
    }

    // For scrolling
    @Override
    public boolean onTouchEvent(MotionEvent evt){

        int action = evt.getAction();
        if(action == MotionEvent.ACTION_DOWN){
            //Log.d(TAG, "Finger down!");
            downPoint = evt.getX();
        }

        if(action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP){

            //Log.d(TAG, "Finger moved!  x: " + evt.getX() + "   y: " + evt.getY());
            int delta = (int)(downPoint - evt.getX());
            delta = delta / 50; // (many times the distance with finger to go the distance in graph)
            offset = offset + delta;
            //Log.d(TAG, "offset: " + offset);
            drawCurSection();

        }

        return true;
    }

    public static double[] normalize(double[] data){
        double[] normalized = new double[data.length];
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        int minIdx = 0;
        int maxIdx = 0;
        for(int i = 0; i < data.length; i++){
            double cur = data[i];
            if(cur > max){
                max = cur;
                maxIdx = i;
            }
            if(cur < min){
                min = cur;
                minIdx = i;
            }
        }

        double truemax = max + Math.abs(min);
        Log.d(TAG, "min: "  + min + "   max: "+ max + "   truemax: " + truemax);
        for(int i = 0; i < data.length; i++){
            double cur = data[i];
            cur = cur + Math.abs(min);
            normalized[i] = cur / truemax;
        }
        return normalized;
    }
}
