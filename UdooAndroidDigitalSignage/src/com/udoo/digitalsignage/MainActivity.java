package com.udoo.digitalsignage;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import com.udoo.digitalsignage.R;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity implements Runnable {
	
	/*
	 *  USB accessory objects
	 */
	private static final String TAG = "UDOOandroidDigitalSignage";	
	private static final String ACTION_USB_PERMISSION = "com.udoo.digitalsignage.action.USB_PERMISSION";		 
	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;	 
	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;		
	
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);    
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {						
						Log.d(TAG, "permission denied for accessory " + accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	/*
	 *  Graphical Resources
	 */
	private ImageView backgroundImageview;	
	private ImageView upAndDown, rightSlide, leftSlide;
	Animation rightAnimation;
	Animation leftAnimation;
	Animation bounceAnimation;	
	
	Integer imagesResId[] = {
			R.raw.ny_mf_prima,
			R.raw.ny_mf_000,
			R.raw.ny_mf_001,
			R.raw.ny_mf_002,
			R.raw.ny_mf_003,
			R.raw.ny_mf_004
			};
	
	// Various vars
	int lastImage;
	private boolean running = true;		
	HashMap<Integer, Integer[]> imageResourcesAndColors = new HashMap<Integer, Integer[]>();	
	SlideTask task;	
	private Message m;
	
	


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Objects used for USB accessory management
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE); 
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		
		// This method setup accessory
		setupAccessory();
 
		if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}
		
		// This asynctask change picture on a separate thread
		task = new SlideTask();
		
		// Load images with an associated colors
		imageResourcesAndColors.put( imagesResId[0], new Integer[]{0xee0055} );
		imageResourcesAndColors.put( imagesResId[1], new Integer[]{0xff0000} );
        imageResourcesAndColors.put( imagesResId[2], new Integer[]{0xcc7700} );
        imageResourcesAndColors.put( imagesResId[3], new Integer[]{0x00cc33} );
        imageResourcesAndColors.put( imagesResId[4], new Integer[]{0xcccc00} );  
        imageResourcesAndColors.put( imagesResId[5], new Integer[]{0xaa00cc} );

        		
        // Inizialize imageviews
		backgroundImageview  = (ImageView) findViewById(R.id.background); 		
		upAndDown            = (ImageView) findViewById(R.id.twoHands);
		rightSlide           = (ImageView) findViewById(R.id.rightSlide);
		leftSlide            = (ImageView) findViewById(R.id.leftSlide);

		// Loading animations from xml
		rightAnimation  = AnimationUtils.loadAnimation(this, R.anim.right_slide);
		leftAnimation   = AnimationUtils.loadAnimation(this, R.anim.left_slide);
		bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce);
		
		
		// Create listeners for each animations
		bounceAnimation.setAnimationListener(new AnimationListener() { 
		    @Override
		    public void onAnimationEnd(Animation animation) {
		    	upAndDown.setVisibility(View.GONE);
		    }

		    @Override
		    public void onAnimationRepeat(Animation animation) { }

		    @Override
		    public void onAnimationStart(Animation animation) { 
		    	upAndDown.setVisibility(View.VISIBLE);
		    }
		});
				
		rightAnimation.setAnimationListener(new AnimationListener() {
		    @Override
		    public void onAnimationEnd(Animation animation) {
		    	rightSlide.setVisibility(View.GONE);
		    }

		    @Override
		    public void onAnimationRepeat(Animation animation) { }

		    @Override
		    public void onAnimationStart(Animation animation) { 
		    	rightSlide.setVisibility(View.VISIBLE);
		    }
		});
		
		leftAnimation.setAnimationListener(new AnimationListener() {
		    @Override
		    public void onAnimationEnd(Animation animation) {
		    	leftSlide.setVisibility(View.GONE);
		    }

		    @Override
		    public void onAnimationRepeat(Animation animation) { }

		    @Override
		    public void onAnimationStart(Animation animation) { 
		    	leftSlide.setVisibility(View.VISIBLE);
		    }
		});
		
		// Test all animation at first time
		upAndDown.startAnimation(bounceAnimation);
		// rightSlide.startAnimation(rightAnimation);
		// leftSlide.startAnimation(leftAnimation);
		
		// Remember last image id played
		lastImage = 0;
		slide(lastImage);
		
	}
	
	// In these methods we neet to manage connection or disconnection
	// on an accessory
	@Override
	public void onResume() {
		super.onResume();
 
		if (mInputStream != null && mOutputStream != null) {
			return;
		}
 
		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory, mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}
 
	@Override
	public void onPause() {
		super.onPause();
		closeAccessory();
	}
 
	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		running = false;
		super.onDestroy();
		closeApp();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
		
    @Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
			return mAccessory;
		} else {
			return super.onRetainNonConfigurationInstance();
		}
	}

 
	/* 
	 * USB Accessory Management
	 * 
	 */
    
	private void setupAccessory() {
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mPermissionIntent =PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}
	}
    
	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			
			// Streams Initialization
			mInputStream  = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			
			Thread thread = new Thread(null, this, "UDOODemoADKSlider");
			thread.start();			
			Log.d(TAG, "accessory opened");
		} else {
			Log.d(TAG, "accessory open fail");
		}
	}
		
	private void closeAccessory() {
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}
	
	// This handler manage asincronous messages readed from USB Accessory
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {			
			switch(m.arg1){

			case 1: 			
				lastImage -= 1;
				if (lastImage < 0)
					lastImage = imagesResId.length - 2;
				//leftSlide.startAnimation(leftAnimation);
				slide(lastImage);	
				
				break;
				
			case 2:  		
				lastImage+=1;		
				if (lastImage > imagesResId.length - 2)
					lastImage = 0;			
				//rightSlide.startAnimation(rightAnimation);		
				slide(lastImage);
				break;
				
			case 5:  		
				upAndDown.startAnimation(bounceAnimation);									
				break;	
				
			default:
				break;
			}
		}		
	};
	
	// This method kills all threads running on this app
	private void closeApp(){
		task = new SlideTask();
		task.execute(5);
		running = false;
		closeAccessory();
		finish();		
	}
	
	
	// Thread used to listen on ADK inputstream
	// Recieving massages from SAM3X
	public void run() {
		int ret = 0;
		byte[] buffer = new byte[1];
		
		while (running) { 
			try {
				ret = mInputStream.read();
			} catch (IOException e) {
				break;
			}
			if (ret != -1) {				
				m = Message.obtain(mHandler);
				m.arg1 = ret;
				mHandler.sendMessage(m);
				ret = 0;
			}		
		}
	}	
	
	
	void slide(int i){
		//sendColorToArduino(i);
	    backgroundImageview.setImageResource(imagesResId[i]);
		task = new SlideTask();
		task.execute(i);
		lastImage = i;
	}
	
	private class SlideTask extends AsyncTask<Integer, Integer, Void> {
		
		 protected Void doInBackground(Integer... urls) {
			publishProgress(urls[0]);
				
			Integer[] colors = imageResourcesAndColors.get(imagesResId[urls[0]]);
			int color = colors[0];
			byte[] colorByte = new byte[3];
			colorByte[0] = (byte)((color >> 16) & 0xff);
			colorByte[1] = (byte)((color >> 8) & 0xff);
			colorByte[2] = (byte)((color) & 0xff);	
			
			if (mOutputStream != null) {
				try {       					       					
					mOutputStream.write(colorByte);
				} catch (IOException e) {
					Log.e(TAG, "write failed");
				}
			}
			else{
				Log.i(TAG, "Outputstrem null, accessorio disconnesso");
			}	
			return null;
		 }
		 
		 protected void onProgressUpdate(Integer... values) {       	
			 backgroundImageview.setImageResource(imagesResId[values[0]]);	
	     }
				
	}
	
	private void sendColorToArduino(int i){
		// Read color from array
		Integer[] colors = imageResourcesAndColors.get(imagesResId[i]);
		int color = colors[0];
		
		// Pack color into byte array
		byte[] coloreByte = new byte[3];
		coloreByte[0] = (byte)((color >> 16) & 0xff);
		coloreByte[1] = (byte)((color >> 8) & 0xff);
		coloreByte[2] = (byte)((color) & 0xff);	
		
		// IF outputstream is not null it send color to SAM3X
		if (mOutputStream != null) {
			try {       					       					
				mOutputStream.write(coloreByte);
			} catch (IOException e) {
				Log.e(TAG, "Write failed on ADK");
			}
		}
		else{
			Log.i(TAG, "Outputstrem null, accessory disconnected");
		}	
	}
	
    private void showToastMessage(String message){
    	Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    
    
}
