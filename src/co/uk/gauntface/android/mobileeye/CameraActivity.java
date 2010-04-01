package co.uk.gauntface.android.mobileeye;

import co.uk.gauntface.android.mobileeye.bluetooth.BluetoothConnectionThread;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

public class CameraActivity extends Activity implements Callback
{
	/**
	 * Handler arg1 Values
	 */
	public static final int START_AUTO_FOCUS = 0;
	public static final int DRAW_IMAGE_PROCESSING = 1;
	
	public static final int AUTO_FOCUS_SUCCESSFUL = 0;
	public static final int AUTO_FOCUS_UNSUCCESSFUL = 1;
	
	public static final int BLUETOOTH_BYTES_RECEIVED = 2;
	public static final int BLUETOOTH_STREAMS_INIT = 3;
	public static final int BLUETOOTH_CONNECTION_LOST = 4;
	
	private SurfaceView mSurfaceView;
	private ImageView mImageProcessedSurfaceView;
	private boolean mStartPreviewFail;
	private CameraWrapper mCamera;
	private SurfaceHolder mSurfaceHolder;
	private Handler mHandler;
	private Button mOutputHistogramBtn;
	
	private BluetoothConnectionThread mBluetoothConnection;
	
    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);
        
        initActivity();
    }
    
    @Override
    public void onStart()
    {
    	super.onStart();
    	
    	Log.v(Singleton.TAG, "MobileEye - onStart");
    }
    
    @Override
    public void onResume() {
        super.onResume();

        Log.v(Singleton.TAG, "MobileEye - onResume");
        
        // Start the preview if it is not started.
        if ((mCamera.isPreviewing() == false) && (mStartPreviewFail == false)) {
            try
            {
                mCamera.startPreview(mSurfaceHolder);
            }
            catch(CameraHardwareException e)
            {
                // Show Error and finish
                return;
            }
        }
    }
    
    @Override
    protected void onPause()
    {
    	Log.v(Singleton.TAG, "MobileEye - onPause");
    	
        mCamera.stopPreview();
        
        // Close the camera now because other activities may need to use it.
        mCamera.closeCamera();

        super.onPause();
    }
    
    protected void onStop()
    {
    	super.onStop();
    	
    	if(mBluetoothConnection != null)
    	{
    		mBluetoothConnection.cancel();
    	}
    }
    
    protected void onDestroy()
    {
    	super.onDestroy();
    	
    	if(mBluetoothConnection != null)
    	{
    		mBluetoothConnection.cancel();
    	}
    }
    
    private void initActivity()
    {
    	mHandler = new Handler(){
    		
    		public void handleMessage(Message msg)
    		{
    			if(msg.arg1 == START_AUTO_FOCUS)
    			{
    				// Was prev auto_focus successful
    				if(msg.arg2 == AUTO_FOCUS_SUCCESSFUL)
    				{
    					// Previous auto focus successful
    				}
    				else
    				{
    					// Previous auto focus unsuccessful
    				}
    				
    				// Start Auto Focus
    				if(mCamera.isNull() == false && mCamera.isPreviewing() == true)
    				{
    					//mCamera.startAutoFocus();
    				}
    			}
    			else if(msg.arg1 == DRAW_IMAGE_PROCESSING)
    			{
    				mHandler.post(new Runnable(){

						public void run()
						{
							mImageProcessedSurfaceView.setImageBitmap(Singleton.updateImageView);
						}
    					
    				});
    			}
    			else if(msg.arg1 == BLUETOOTH_CONNECTION_LOST)
    			{
    				Intent intent = new Intent(getApplicationContext(), MobileEye.class);
    				startActivity(intent);
    				
    				finish();
    			}
    		}
    		
    	};
    	
    	mBluetoothConnection = Singleton.getBluetoothConnection();
    	if(mBluetoothConnection != null)
    	{
    		mBluetoothConnection.setHandler(mHandler);
    	}
    	
    	mCamera = new CameraWrapper(mHandler);
    	mSurfaceView = (SurfaceView) findViewById(R.id.CameraSurfaceView);
    	
    	mImageProcessedSurfaceView = (ImageView) findViewById(R.id.ImageProcessedSurfaceView);
    	
    	/*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        Thread startPreviewThread = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    mStartPreviewFail = false;
                    mCamera.startPreview(mSurfaceHolder);
                }
                catch (CameraHardwareException e)
                {
                    mStartPreviewFail = true;
                }
            }
        });
        startPreviewThread.start();
        
        SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    	
    	// Make sure preview is started.
        try
        {
            startPreviewThread.join();
            
            if (mStartPreviewFail == true)
            {
                //showCameraErrorAndFinish();
            	Log.e(Singleton.TAG, "ERROR: Start Preview of the camera failed");
            	finish();
                return;
            }
        }
        catch (InterruptedException ex)
        {
            // ignore
        }
        
        mOutputHistogramBtn = (Button) findViewById(R.id.OutputHistogramBtn);
        mOutputHistogramBtn.setOnClickListener(new OnClickListener(){

			public void onClick(View view)
			{
				mCamera.logHistogram();
			}
        	
        });
    }
    
    
    /**
     * The SurfaceView Callback methods
     */
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
	{
		Log.v(Singleton.TAG, "MobileEye - surfaceChanged");
		
		if(mCamera.isNull() == true)
		{
			// TODO: Return Error
			return;
		}
		
		// Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null)
        {
            Log.d(Singleton.TAG, "Camera surfaceChanged holder.getSurface() == null");
            return;
        }
        
		mSurfaceHolder = holder;
		
		if(holder.isCreating() == true)
		{
			mCamera.setPreviewDisplay(mSurfaceHolder);
		}
		
		mCamera.startAutoFocus();
	}

	public void surfaceCreated(SurfaceHolder holder)
	{
		
	}

	public void surfaceDestroyed(SurfaceHolder holder)
	{
		Log.v(Singleton.TAG, "MobileEye - surfaceDestroyed");
		
		mCamera.stopPreview();
        mSurfaceHolder = null;
	}
}