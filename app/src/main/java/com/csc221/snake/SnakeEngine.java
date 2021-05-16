package com.csc221.snake;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;
import java.security.SecureRandom;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;


public class SnakeEngine extends SurfaceView implements Runnable {

    // Our game thread for the main game loop
    private Thread thread = null;

    // To hold a reference to the Activity
    private Context context;

    // For playing sound effects
    private SoundPool soundPool;
    private int eat_apple = -1;
    private int snake_crash = -1;

    // For tracking movement Heading
    public enum Heading {UP, RIGHT, DOWN, LEFT}
    // Start by heading to the right
    private Heading heading = Heading.RIGHT;

    // To hold the screen size in pixels
    private int screenX;
    private int screenY;

    // How long is the snake
    private int snakeLength;

    // Location of the Apple
    private int appleX;
    private int appleY;

    // Location of the Poisoned Apple
    private int poisonedX;
    private int poisonedY;

    // The size in pixels of a snake segment
    private int blockSize;

    // The size in segments of the playable area
    private final int NUM_BLOCKS_WIDE = 40;
    private int numBlocksHigh;

    // Control pausing between updates
    private long nextFrameTime;
    // Update the game 10 times per second
    private final long FPS = 10;
    // There are 1000 milliseconds in a second
    private final long MILLIS_PER_SECOND = 1000;

    // How many points does the player have
    private int score;
    // The high score of the current session
    private int highScore;

    // The location in the grid of the snake segments
    private int[] snakeXs;
    private int[] snakeYs;

    // Everything we need for drawing
// Is the game currently playing?
    private volatile boolean isPlaying;

    // A canvas for the paint
    private Canvas canvas;

    // Required to use canvas
    private SurfaceHolder surfaceHolder;

    // Paint for the canvas
    private Paint paint;

    // Used to pause in between rounds
    private Object pauseLock;
    private boolean paused;

    public SnakeEngine(Context context, Point size) {
        super(context);

        context = context;

        pauseLock = new Object();
        paused = false;

        screenX = size.x;
        screenY = size.y;

        // Work out how many pixels each block is
        blockSize = screenX / NUM_BLOCKS_WIDE;
        // How many blocks of the same size will fit into the height
        numBlocksHigh = screenY / blockSize;

        // Set the sound up
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        try {
            // Create objects of the 2 required classes
            // Use m_Context because this is a reference to the Activity
            AssetManager assetManager = context.getAssets();
            AssetFileDescriptor descriptor;

            // Prepare the two sounds in memory
            descriptor = assetManager.openFd("eat_apple.ogg");
            eat_apple = soundPool.load(descriptor, 0);

            descriptor = assetManager.openFd("snake_crash.ogg");
            snake_crash = soundPool.load(descriptor, 0);

        } catch (IOException e) {
            // Error
        }


        // Initialize the drawing objects
        surfaceHolder = getHolder();
        paint = new Paint();

        // If you score 200 you are rewarded with a crash achievement!
        snakeXs = new int[200];
        snakeYs = new int[200];

        // Start the game
        newGame();
    }

    @Override
    public void run() {

        while (isPlaying) {

            // Updates 10 times a second
            if(updateRequired()) {
                update();
                draw();
            }

            // For pausing the thread
            synchronized (pauseLock) {
                while (paused) {
                    try {
                        pauseLock.wait();
                    } catch (InterruptedException ie) {
                    }
                }
            }
        }
    }

    // Pauses the looping thread
    public void onPause() {
        synchronized (pauseLock) {
            paused = true;
        }
    }

    // Resumes the looping thread
    public void onResume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public void pause() {
        isPlaying = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            // Error
        }
    }

    public void resume() {
        isPlaying = true;
        thread = new Thread(this);
        thread.start();
    }

    public void newGame() {
        // Start with a single snake segment
        snakeLength = 1;
        snakeXs[0] = NUM_BLOCKS_WIDE / 2;
        snakeYs[0] = numBlocksHigh / 2;

        // Gets the Apple and the Poisoned Apple ready
        spawnApple();
        spawnPoison();

        // Reset the score and updates high score if needed
        if (score > highScore) {
            highScore = score;
        }
        score = 0;

        // Setup nextFrameTime so an update is triggered
        nextFrameTime = System.currentTimeMillis();
    }

    public void spawnApple() {
        SecureRandom sRand = new SecureRandom();
        appleX = sRand.nextInt(NUM_BLOCKS_WIDE - 1) + 1;
        appleY = sRand.nextInt(numBlocksHigh - 1) + 1;
    }

    private void eatApple(){
        // Increase the size of the snake
        snakeLength++;
        //replace Apple
        spawnApple();
        //add to the score
        score = score + 1;
        soundPool.play(eat_apple, 1, 1, 0, 0, 1);
    }

    public void spawnPoison() {
        SecureRandom sRand = new SecureRandom();
        poisonedX = sRand.nextInt(NUM_BLOCKS_WIDE - 1) + 1;
        poisonedY = sRand.nextInt(numBlocksHigh - 1) + 1;
    }

    private void eatPoison(){
        // Decrease the size of the snake
        snakeLength--;
        //replace Poisoned Apple
        spawnPoison();
        //subtract from the score
        score = score - 1;
    }

    private void moveSnake(){
        // Move the body
        for (int i = snakeLength; i > 0; i--) {
            // Start at the back and move it
            // to the position of the segment in front of it
            snakeXs[i] = snakeXs[i - 1];
            snakeYs[i] = snakeYs[i - 1];

            // Exclude the head because
            // the head has nothing in front of it
        }

        // Move the head in the appropriate heading
        switch (heading) {
            case UP:
                snakeYs[0]--;
                break;

            case RIGHT:
                snakeXs[0]++;
                break;

            case DOWN:
                snakeYs[0]++;
                break;

            case LEFT:
                snakeXs[0]--;
                break;
        }
    }

    private boolean detectDeath(){
        // Has the snake died?
        boolean dead = false;

        // Ate poisoned apple when the length of snake is 1
        if(snakeLength == 0) dead = true;

        // Hit the screen edge
        if (snakeXs[0] == -1) dead = true;
        if (snakeXs[0] > NUM_BLOCKS_WIDE + 1) dead = true;
        if (snakeYs[0] == -1) dead = true;
        if (snakeYs[0] > numBlocksHigh) dead = true;

        // Eaten itself?
        for (int i = snakeLength - 1; i > 0; i--) {
            if ((i > 4) && (snakeXs[0] == snakeXs[i]) && (snakeYs[0] == snakeYs[i])) {
                dead = true;
            }
        }

        return dead;
    }

    public void update() {
        // Did the head of the snake eat the apple?
        if (snakeXs[0] == appleX && snakeYs[0] == appleY) {
            eatApple();
        }

        // Did the head of the snake eat the poisoned apple?
        if (snakeXs[0] == poisonedX && snakeYs[0] == poisonedY) {
            eatPoison();
        }

        moveSnake();

        if (detectDeath()) {
            //start again
            soundPool.play(snake_crash, 1, 1, 0, 0, 1);
            onPause();
            newGame();
        }
    }

    public void draw() {
        // Get a lock on the canvas
        if (surfaceHolder.getSurface().isValid()) {
            canvas = surfaceHolder.lockCanvas();

            // Background is set to blue
            canvas.drawColor(Color.argb(255, 26, 128, 182));

            // Set the color of the paint to draw the snake white
            paint.setColor(Color.argb(255, 255, 255, 255));

            // Scale the HUD text
            paint.setTextSize(90);
            canvas.drawText("Score:" + score, 10, 75, paint);
            canvas.drawText("High Score:" + highScore, screenX - 600, 75, paint);

            // Draw the snake one block at a time
            for (int i = 0; i < snakeLength; i++) {
                canvas.drawRect(snakeXs[i] * blockSize,
                        (snakeYs[i] * blockSize),
                        (snakeXs[i] * blockSize) + blockSize,
                        (snakeYs[i] * blockSize) + blockSize,
                        paint);
            }

            // Set the color of the paint to draw Apple red
            paint.setColor(Color.argb(255, 255, 0, 0));

            // Draw Apple
            canvas.drawRect(appleX * blockSize,
                    (appleY * blockSize),
                    (appleX * blockSize) + blockSize,
                    (appleY * blockSize) + blockSize,
                    paint);

            // Set the color of the paint to draw Poisoned Apple green
            paint.setColor(Color.argb(255, 0, 255, 0));

            // Draw Poisoned Apple
            canvas.drawRect(poisonedX * blockSize,
                    (poisonedY * blockSize),
                    (poisonedX * blockSize) + blockSize,
                    (poisonedY * blockSize) + blockSize,
                    paint);

            // Unlock the canvas and reveal the graphics for this frame
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    public boolean updateRequired() {

        // Are we due to update the frame
        if(nextFrameTime <= System.currentTimeMillis()){
            // Tenth of a second has passed

            // Setup when the next update will be triggered
            nextFrameTime =System.currentTimeMillis() + MILLIS_PER_SECOND / FPS;

            // Return true so that the update and draw
            // functions are executed
            return true;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {

        if ((motionEvent.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            // resumes the thread if it was paused
            onResume();
            if (motionEvent.getX() >= screenX / 2) {
                switch (heading) {
                    case UP:
                        heading = Heading.RIGHT;
                        break;
                    case RIGHT:
                        heading = Heading.DOWN;
                        break;
                    case DOWN:
                        heading = Heading.LEFT;
                        break;
                    case LEFT:
                        heading = Heading.UP;
                        break;
                }
            } else {
                switch (heading) {
                    case UP:
                        heading = Heading.LEFT;
                        break;
                    case LEFT:
                        heading = Heading.DOWN;
                        break;
                    case DOWN:
                        heading = Heading.RIGHT;
                        break;
                    case RIGHT:
                        heading = Heading.UP;
                        break;
                }
            }
        }
        return true;
    }
}
