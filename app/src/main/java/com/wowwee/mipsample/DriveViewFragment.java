package com.wowwee.mipsample;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;

//import com.wowwee.bluetoothrobotcontrolpubliclib.MipRobot;
//import com.wowwee.bluetoothrobotcontrolpubliclib.MipRobotFinder;
import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.async.AsyncMessage;
import com.orbotix.async.DeviceSensorAsyncMessage;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.sensor.DeviceSensorsData;
import com.orbotix.common.sensor.SensorFlag;
import com.orbotix.response.DeviceResponse;
import com.orbotix.subsystem.SensorControl;
import com.wowwee.bluetoothrobotcontrollib.sdk.MipRobot;
import com.wowwee.bluetoothrobotcontrollib.sdk.MipRobotFinder;
import com.wowwee.mipsample.JoystickData.TYPE;

public class DriveViewFragment extends Fragment implements OnTouchListener, RobotChangedStateListener, ResponseListener {
	protected SurfaceView touchArea;
	
	protected JoystickData leftJoystickData;
	protected JoystickData rightJoystickData;
	
	protected JoystickDrawer leftJoystickDrawer;
	protected JoystickDrawer rightJoystickDrawer;
	
	protected Bitmap outerRingBitmap;
	protected Bitmap leftBitmap;
	protected Bitmap rightBitmap;
	
	protected int leftJoystickDrawableId;
	protected int rightJoystickDrawableId;
	protected int singleJoystickDrawableId;
	protected int outerRingDrawableId;
	
	protected float[] movementVector = new float[]{0, 0};
	protected Timer joystickTimer;
	protected boolean moveMip;
	protected boolean isJoystickTimerRunning = false;
	
	protected boolean isOpening = false;
	
	//default drive mode is dual
	protected kDriveMode driveMode = kDriveMode.kDualJoystick;
	protected boolean driveEnabled = true;
	
	protected Rect viewRect;

	//ROBOT
	private static final int REQUEST_CODE_LOCATION_PERMISSION = 42;

	private DualStackDiscoveryAgent mDiscoveryAgent;
	private ConvenienceRobot mRobot;

	public DriveViewFragment() {
		super();
		leftJoystickDrawableId = R.drawable.img_joystick_left;
		singleJoystickDrawableId = R.drawable.img_joystick_single;
		rightJoystickDrawableId = R.drawable.img_joystick_right;
		outerRingDrawableId = R.drawable.img_joystick_outer_ring;
	}

	protected void setJoystickMode(kDriveMode driveMode) {
		if(this.driveMode != driveMode) {
			this.driveMode = driveMode;
			BitmapFactory.Options bitmapFactoryOption = new BitmapFactory.Options();
			bitmapFactoryOption.inScaled = false;
			if(driveMode == kDriveMode.kDualJoystick) {
				leftBitmap = BitmapFactory.decodeResource(getResources(), leftJoystickDrawableId, bitmapFactoryOption);
			}
			else {
				leftBitmap = BitmapFactory.decodeResource(getResources(), singleJoystickDrawableId, bitmapFactoryOption);
			}
			leftJoystickDrawer.setJoystickBitmap(leftBitmap);
		}
	}

	protected void setDriveEnabled(boolean driveEnabled) {
		this.driveEnabled = driveEnabled;
		if(!driveEnabled) {
			movementVector[0] = 0;
			movementVector[1] = 0;
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		if(leftJoystickDrawer != null) {
			leftJoystickDrawer.destroy();
			leftJoystickDrawer = null;
		}
		if(rightJoystickDrawer != null) {
			rightJoystickDrawer.destroy();
			rightJoystickDrawer = null;
		}
		outerRingBitmap.recycle();
		outerRingBitmap = null;
		leftBitmap.recycle();
		leftBitmap = null;
		rightBitmap.recycle();
		rightBitmap = null;
		if(joystickTimer != null) {
			joystickTimer.cancel();
			joystickTimer.purge();
			joystickTimer = null;
		}
		isJoystickTimerRunning = false;
		mDiscoveryAgent.addRobotStateListener(null);

	}

	@Override
	public void onPause()
	{
		super.onPause();
		joystickTimer.cancel();
		joystickTimer.purge();
		joystickTimer = null;
		isJoystickTimerRunning = false;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if(!isJoystickTimerRunning) {
			joystickTimer = new Timer();
			joystickTimer.schedule(new JoystickTimerCallback(), 50, 50);
			isJoystickTimerRunning = true;
		}

		setDriveEnabled(true);
		startDiscovery();
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (container == null)
			return null;

		Log.i(DriveViewFragment.class.getName(), "onCreateView start");

		View view = inflater.inflate(R.layout.drive_view, container, false);

		Button backBtn = (Button)view.findViewById(R.id.back_btn);
		backBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				DriveViewFragment.this.getFragmentManager().popBackStack();
				FragmentTransaction transaction = DriveViewFragment.this.getFragmentManager().beginTransaction();
				transaction.remove(DriveViewFragment.this);
				transaction.commit();
			}
		});

		viewRect = new Rect();
		getActivity().getWindowManager().getDefaultDisplay().getRectSize(viewRect);

		//handle the touch area
		touchArea = (SurfaceView)view.findViewById(R.id.view_id_touch_area);
		touchArea.setZOrderOnTop(true);
		touchArea.getHolder().setFormat(PixelFormat.TRANSLUCENT);
		
		//create the joystick data
		leftJoystickData = new JoystickData(TYPE.LEFT);
		rightJoystickData = new JoystickData(TYPE.RIGHT);
		
		//create the bitmps for joystick
		BitmapFactory.Options bitmapFactoryOption = new BitmapFactory.Options();
		bitmapFactoryOption.inScaled = false;
		outerRingBitmap = BitmapFactory.decodeResource(getResources(), outerRingDrawableId, bitmapFactoryOption);
		if(driveMode == kDriveMode.kDualJoystick) {
			leftBitmap = BitmapFactory.decodeResource(getResources(), leftJoystickDrawableId, bitmapFactoryOption);
		}
		else {
			leftBitmap = BitmapFactory.decodeResource(getResources(), singleJoystickDrawableId, bitmapFactoryOption);
		}
		rightBitmap = BitmapFactory.decodeResource(getResources(), rightJoystickDrawableId, bitmapFactoryOption);
		
		//create the joystick drawer
		leftJoystickDrawer = new JoystickDrawer(outerRingBitmap, leftBitmap);
		rightJoystickDrawer = new JoystickDrawer(outerRingBitmap, rightBitmap);
		
		//compute the draw ratio for joystick
		float drawRatio = (viewRect.width() / 2.0f < outerRingBitmap.getWidth())?0.5f:1.0f;
		
		leftJoystickDrawer.setDrawRatio(drawRatio);
		rightJoystickDrawer.setDrawRatio(drawRatio);
		leftJoystickData.setMaxJoystickValue(leftJoystickDrawer.getMaxJoystickValue());
		rightJoystickData.setMaxJoystickValue(rightJoystickDrawer.getMaxJoystickValue());
		
		//handle the touches
		touchArea.setOnTouchListener(this);
		
		// Start joystick send BLE data loop
		joystickTimer = new Timer();
		joystickTimer.schedule(new JoystickTimerCallback(), 50, 50);
		isJoystickTimerRunning = true;

		//Sphero Robot
		mDiscoveryAgent = new DualStackDiscoveryAgent();
		mDiscoveryAgent.addRobotStateListener(this);
		startDiscovery();
		return view;
	}

	private void startDiscovery() {
		//If the DiscoveryAgent is not already looking for robots, start discovery.
		if (!mDiscoveryAgent.isDiscovering()) {
			try {
				mDiscoveryAgent.startDiscovery(this.getActivity().getBaseContext());
			} catch (DiscoveryException e) {
				Log.e("Sphero", "DiscoveryException: " + e.getMessage());
			}
		}
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event)
	{
		if (touchArea.getHolder().isCreating())
		{
			return true;
		}
		
		Canvas canvas = touchArea.getHolder().lockCanvas();
		if(canvas != null) {
			canvas.drawColor(0, PorterDuff.Mode.CLEAR);
		}
		if (v == touchArea && driveEnabled)
		{
			int pointerIndex = event.getActionIndex();
			
			switch(event.getActionMasked())
			{
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_POINTER_DOWN:
				{
					//set start point
					TYPE joystickType;
					if(driveMode == kDriveMode.kDualJoystick) {
						joystickType = (event.getX(pointerIndex) < viewRect.width()/2)?TYPE.LEFT:TYPE.RIGHT;
					}
					else {
						joystickType = TYPE.LEFT;
					}
					
					JoystickData joystickData = joystickType==TYPE.LEFT?leftJoystickData:rightJoystickData;
					if (joystickData.pointerId == JoystickData.INVALID_POINTER_ID)
					{
						joystickData.pointerId = event.getPointerId(pointerIndex);
						joystickData.setStartPoint((int)event.getX(pointerIndex), (int)event.getY(pointerIndex));
					}
					
				}	
					break;
				case MotionEvent.ACTION_MOVE:
				{
					//update the drag points
					for (int i=0; i<event.getPointerCount(); i++)
					{
						JoystickData joystickData = null; 
						
						if(event.getPointerId(i) == leftJoystickData.pointerId) {
							joystickData = leftJoystickData;
						}
						else if(event.getPointerId(i) == rightJoystickData.pointerId) {
							joystickData = rightJoystickData;
						}
						if(joystickData == null) {
							continue;
						}
						joystickData.setDragPoint((int)event.getX(i), (int)event.getY(i));
						
						float[] moveVector = joystickData.getMoveVector();
						
						moveMip = true;
						
						if(driveMode == kDriveMode.kDualJoystick) {
							if (joystickData.type == TYPE.LEFT) {
								movementVector[1] = moveVector[1] * -1;
							}
							else {
								movementVector[0] = moveVector[0];
							}
						}
						else {
							movementVector[0] = moveVector[0];
							movementVector[1] = moveVector[1] * -1;
						}
					}
				}	
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
				case MotionEvent.ACTION_CANCEL:
				{
					//cancel the touch
					JoystickData joystickData = (leftJoystickData.pointerId == event.getPointerId(pointerIndex))?leftJoystickData:rightJoystickData;
					if(driveMode == kDriveMode.kDualJoystick) {
						if((leftJoystickData.pointerId == event.getPointerId(pointerIndex))) {
							// is left
							movementVector[1] = 0;
						}
						else {
							// is right
							movementVector[0] = 0;
						}
					}
					else {
						if((leftJoystickData.pointerId == event.getPointerId(pointerIndex))) {
							movementVector[0] = 0;
							movementVector[1] = 0;
						}
					}
					joystickData.reset();
				}
					break;
			}
		}

		if(!isOpening && driveEnabled && canvas != null) {
			//draw the joysticks
			if(leftJoystickDrawer != null) {
				leftJoystickDrawer.drawJoystick(canvas, leftJoystickData);
			}
			if(rightJoystickDrawer != null) {
				rightJoystickDrawer.drawJoystick(canvas, rightJoystickData);
			}
			
			//handle extra onTouchEvent
			extraDrawForOnTouch(canvas, v, event);
		}
		
		touchArea.getHolder().unlockCanvasAndPost(canvas);
		
		return true;
	}
	
	protected void extraDrawForOnTouch(Canvas canvas, View v, MotionEvent event)
	{
		
	}

	@Override
	public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
		switch (type) {
			case Online: {
				SensorFlag sensorFlag = new SensorFlag(SensorFlag.SENSOR_FLAG_QUATERNION
						, SensorFlag.SENSOR_FLAG_ACCELEROMETER_NORMALIZED
						, SensorFlag.SENSOR_FLAG_GYRO_NORMALIZED
						, SensorFlag.SENSOR_FLAG_MOTOR_BACKEMF_NORMALIZED
						, SensorFlag.SENSOR_FLAG_ATTITUDE);

				//Save the robot as a ConvenienceRobot for additional utility methods
				mRobot = new ConvenienceRobot(robot);

				//Remove stabilization so the robot can be turned in all directions without correcting itself
				mRobot.enableStabilization(false);

				//Enable sensors based on the flag defined above, and stream their data ten times a second to the mobile device
				mRobot.enableSensors(sensorFlag, SensorControl.StreamingRate.STREAMING_RATE10);

				//Listen to data responses from the robot
				mRobot.addResponseListener(this);

				break;
			}
		}

	}

	@Override
	public void handleResponse(DeviceResponse deviceResponse, Robot robot) {
	}

	@Override
	public void handleStringResponse(String s, Robot robot) {

	}

	@Override
	public void handleAsyncMessage(AsyncMessage asyncMessage, Robot robot) {
		if (asyncMessage == null)
			return;

		//Check the asyncMessage type to see if it is a DeviceSensor message
		if (asyncMessage instanceof DeviceSensorAsyncMessage) {
			DeviceSensorAsyncMessage message = (DeviceSensorAsyncMessage) asyncMessage;

			if (message.getSensorDataFrames() == null
					|| message.getSensorDataFrames().isEmpty()
					|| message.getSensorDataFrames().get(0) == null)
				return;

			//Retrieve DeviceSensorsData from the async message
			DeviceSensorsData data = message.getSensorDataFrames().get(0);
		}
	}

	class JoystickTimerCallback extends TimerTask {
		  public void run() {
			  if(moveMip && (movementVector[0] != 0 || movementVector[1] != 0)) {
//				  Log.d("Battle", "movementVector = " + movementVector[0] + ", " + movementVector[1]);
				  mipDrive(movementVector);
			  }
		  }
	}


	@Override
	public void onStop() {
		//If the DiscoveryAgent is in discovery mode, stop it.
		if (mDiscoveryAgent.isDiscovering()) {
			mDiscoveryAgent.stopDiscovery();
		}

		//If a robot is connected to the device, disconnect it
		if (mRobot != null) {
			mRobot.disconnect();
			mRobot = null;
		}

		super.onStop();
	}

	public void mipDrive(float[] vector) {
		List<MipRobot> mips = MipRobotFinder.getInstance().getMipsConnected();
		for (MipRobot mip : mips) {
			mip.mipDrive(vector);
		}
		if(mRobot != null)
		    mRobot.drive(vector[0], vector[1]);
	}
	
	public enum kDriveMode {
		kDualJoystick(0),
		kSingleJoystick(1);
		
		int value;
		kDriveMode(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return value;
		}
		
		public static kDriveMode getParamWithValue(int value)
		{
			for (kDriveMode param : kDriveMode.values())
			{
				if (param.value == value)
				{
					return param;
				}
			}
			return null;
		}
	}

}
