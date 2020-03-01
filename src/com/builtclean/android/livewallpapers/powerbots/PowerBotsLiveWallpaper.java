package com.builtclean.android.livewallpapers.powerbots;

import java.util.ArrayList;
import java.util.Random;

import org.anddev.andengine.engine.camera.Camera;
import org.anddev.andengine.engine.options.EngineOptions;
import org.anddev.andengine.engine.options.EngineOptions.ScreenOrientation;
import org.anddev.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.anddev.andengine.entity.primitive.Rectangle;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.scene.Scene.IOnAreaTouchListener;
import org.anddev.andengine.entity.scene.Scene.IOnSceneTouchListener;
import org.anddev.andengine.entity.scene.Scene.ITouchArea;
import org.anddev.andengine.entity.scene.background.ColorBackground;
import org.anddev.andengine.entity.shape.Shape;
import org.anddev.andengine.entity.sprite.AnimatedSprite;
import org.anddev.andengine.entity.util.FPSLogger;
import org.anddev.andengine.extension.physics.box2d.PhysicsConnector;
import org.anddev.andengine.extension.physics.box2d.PhysicsFactory;
import org.anddev.andengine.extension.physics.box2d.PhysicsWorld;
import org.anddev.andengine.extension.ui.livewallpaper.BaseLiveWallpaperService;
import org.anddev.andengine.input.touch.TouchEvent;
import org.anddev.andengine.opengl.texture.Texture;
import org.anddev.andengine.opengl.texture.TextureOptions;
import org.anddev.andengine.opengl.texture.region.TextureRegionFactory;
import org.anddev.andengine.opengl.texture.region.TiledTextureRegion;
import org.anddev.andengine.sensor.accelerometer.AccelerometerData;
import org.anddev.andengine.sensor.accelerometer.IAccelerometerListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.FixtureDef;

public class PowerBotsLiveWallpaper extends BaseLiveWallpaperService implements
		SharedPreferences.OnSharedPreferenceChangeListener,
		IAccelerometerListener, IOnSceneTouchListener, IOnAreaTouchListener {

	public static final String SHARED_PREFS_NAME = "PowerBotsLiveWallpaperSettings";

	private static float mCameraWidth = 480;
	private static float mCameraHeight = 720;

	private Display display;

	private Camera mCamera;

	private ScreenOrientation mScreenOrientation;
	private ScreenOrientation mInitOrientation;

	private Texture mTexture;

	private TiledTextureRegion mBoxRobotTextureRegion;

	private Random mRnd = new Random();
	private ArrayList<AnimatedSprite> robotArray = new ArrayList<AnimatedSprite>();
	private int powerLevel;

	private PhysicsWorld mPhysicsWorld;

	private float mGravityX;
	private float mGravityY;

	private final Vector2 mTempVector = new Vector2();

	private SharedPreferences mSharedPreferences;
	private int robotColor;
	private int backgroundColor;

	private BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			int rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			int level = -1;
			if (rawlevel >= 0 && scale > 0) {
				level = (rawlevel * 100) / scale;
			}
			powerLevel = level;
			getEngine().runOnUpdateThread(new Runnable() {

				@Override
				public void run() {
					updateRobots();
				}
			});
		}
	};

	@Override
	public org.anddev.andengine.engine.Engine onLoadEngine() {

		display = ((WindowManager) getSystemService(WINDOW_SERVICE))
				.getDefaultDisplay();

		mCameraWidth = display.getWidth();
		mCameraHeight = display.getHeight();

		RatioResolutionPolicy ratio;

		int rotation = display.getRotation();
		if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
			mScreenOrientation = ScreenOrientation.LANDSCAPE;
			mCamera = new Camera(0, 0, mCameraWidth, mCameraHeight);
			ratio = new RatioResolutionPolicy(mCameraHeight, mCameraWidth);
		} else {
			mScreenOrientation = ScreenOrientation.PORTRAIT;
			mCamera = new Camera(0, 0, mCameraWidth, mCameraHeight);
			ratio = new RatioResolutionPolicy(mCameraWidth, mCameraHeight);
		}
		mInitOrientation = mScreenOrientation;

		final EngineOptions engineOptions = new EngineOptions(true,
				mScreenOrientation, ratio, mCamera);
		engineOptions.getTouchOptions().setRunOnUpdateThread(true);

		return new org.anddev.andengine.engine.Engine(engineOptions);
	}

	@Override
	public void onLoadResources() {

		mTexture = new Texture(1024, 128, TextureOptions.DEFAULT);
		TextureRegionFactory.setAssetBasePath("gfx/");
		mBoxRobotTextureRegion = TextureRegionFactory.createTiledFromAsset(
				mTexture, this, "bot_tiled.png", 0, 0, 6, 1);
		getEngine().getTextureManager().loadTexture(mTexture);

		enableAccelerometerSensor(this);

		mSharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, 0);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
	}

	/**
	 * @see org.anddev.andengine.ui.IGameInterface#onLoadScene()
	 */
	@Override
	public Scene onLoadScene() {

		getEngine().registerUpdateHandler(new FPSLogger());

		final Scene scene = new Scene(2);
		backgroundColor = mSharedPreferences.getInt(
				PowerBotsLiveWallpaperSettings.BACKGROUND_COLOR_PREFERENCE_KEY,
				PowerBotsLiveWallpaperSettings.BACKGROUND_COLOR_DEFAULT);

		scene.setBackground(new ColorBackground((float) ((float) Color
				.red(backgroundColor) / 255f), (float) ((float) Color
				.green(backgroundColor) / 255f), (float) ((float) Color
				.blue(backgroundColor) / 255f)));
		scene.setOnSceneTouchListener(this);

		mPhysicsWorld = new PhysicsWorld(new Vector2(0,
				SensorManager.GRAVITY_EARTH), false);

		final Shape roof = new Rectangle(0, 0, 0, mCameraHeight);
		final Shape right = new Rectangle(0, 0, mCameraWidth, 0);
		final Shape ground = new Rectangle(mCameraWidth, 0, 0, mCameraHeight);
		final Shape left = new Rectangle(0, mCameraHeight, mCameraWidth, 0);

		final FixtureDef wallFixtureDef = PhysicsFactory.createFixtureDef(0,
				0.5f, 0.5f);
		PhysicsFactory.createBoxBody(mPhysicsWorld, roof, BodyType.StaticBody,
				wallFixtureDef);
		PhysicsFactory.createBoxBody(mPhysicsWorld, right, BodyType.StaticBody,
				wallFixtureDef);
		PhysicsFactory.createBoxBody(mPhysicsWorld, ground,
				BodyType.StaticBody, wallFixtureDef);
		PhysicsFactory.createBoxBody(mPhysicsWorld, left, BodyType.StaticBody,
				wallFixtureDef);

		scene.getFirstChild().attachChild(roof);
		scene.getFirstChild().attachChild(right);
		scene.getFirstChild().attachChild(ground);
		scene.getFirstChild().attachChild(left);

		scene.registerUpdateHandler(mPhysicsWorld);

		scene.setOnAreaTouchListener(this);

		return scene;
	}

	@Override
	public void onLoadComplete() {

		robotColor = mSharedPreferences.getInt(
				PowerBotsLiveWallpaperSettings.ROBOT_COLOR_PREFERENCE_KEY,
				PowerBotsLiveWallpaperSettings.ROBOT_COLOR_DEFAULT);

		getEngine().runOnUpdateThread(new Runnable() {
			@Override
			public void run() {
				updateRobots();
			}
		});

		registerReceiver(batteryLevelReceiver, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));

	}

	@Override
	public void onDestroy() {

		getEngine().runOnUpdateThread(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; i < robotArray.size(); i++) {
					removeRobot();
				}
			}
		});

		Scene scene = getEngine().getScene();
		scene.reset();

		super.onDestroy();

	}

	@Override
	public void onAccelerometerChanged(
			final AccelerometerData pAccelerometerData) {

		if (display.getRotation() == Surface.ROTATION_0) {
			mGravityX = -pAccelerometerData.getX();
			mGravityY = pAccelerometerData.getY();
		} else if (display.getRotation() == Surface.ROTATION_90) {
			mGravityX = pAccelerometerData.getY();
			mGravityY = pAccelerometerData.getX();
		} else if (display.getRotation() == Surface.ROTATION_180) {
			mGravityX = pAccelerometerData.getX();
			mGravityY = -pAccelerometerData.getY();
		} else if (display.getRotation() == Surface.ROTATION_270) {
			mGravityX = -pAccelerometerData.getY();
			mGravityY = -pAccelerometerData.getX();
		}

		mTempVector.set(mGravityX, mGravityY);

		mPhysicsWorld.setGravity(mTempVector);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		backgroundColor = sharedPreferences.getInt(
				PowerBotsLiveWallpaperSettings.BACKGROUND_COLOR_PREFERENCE_KEY,
				PowerBotsLiveWallpaperSettings.BACKGROUND_COLOR_DEFAULT);

		final Scene scene = getEngine().getScene();
		scene.setBackground(new ColorBackground((float) ((float) Color
				.red(backgroundColor) / 255f), (float) ((float) Color
				.green(backgroundColor) / 255f), (float) ((float) Color
				.blue(backgroundColor) / 255f)));

		robotColor = sharedPreferences.getInt(
				PowerBotsLiveWallpaperSettings.ROBOT_COLOR_PREFERENCE_KEY,
				PowerBotsLiveWallpaperSettings.ROBOT_COLOR_DEFAULT);

		int size = robotArray.size();

		for (int i = 0; i < size; i++) {
			AnimatedSprite robot = robotArray.get(i);

			robot.setColor((float) ((float) Color.red(robotColor) / 255f),
					(float) ((float) Color.green(robotColor) / 255f),
					(float) ((float) Color.blue(robotColor) / 255f));

			robotArray.set(i, robot);
		}
	}

	private void addRobot(final float pX, final float pY, int r, int g, int b) {

		final Scene scene = getEngine().getScene();

		final AnimatedSprite robot;
		final Body body;

		final FixtureDef objectFixtureDef = PhysicsFactory.createFixtureDef(1,
				0.5f, 0.5f);

		robot = new AnimatedSprite(pX, pY, mBoxRobotTextureRegion);
		float scale = (float) Math.sqrt((mCameraWidth * mCameraHeight)
				/ (robot.getWidth() * robot.getHeight() * 100)) - 0.03f;
		robot.setScale(scale);
		robot.setRotation(mRnd.nextFloat());
		robot.setCurrentTileIndex(3);
		robot.setColor((float) ((float) Color.red(robotColor) / 255f),
				(float) ((float) Color.green(robotColor) / 255f),
				(float) ((float) Color.blue(robotColor) / 255f));

		body = PhysicsFactory.createBoxBody(mPhysicsWorld, robot,
				BodyType.DynamicBody, objectFixtureDef);

		scene.registerTouchArea(robot);
		scene.getLastChild().attachChild(robot);
		mPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(robot,
				body, true, true));
		robotArray.add(robot);
	}

	private void updateRobots() {
		int diff = powerLevel - robotArray.size();

		// Add robots if the power is more than the number of robots.
		for (int i = 0; i < diff; i++) {
			PowerBotsLiveWallpaper.this
					.addRobot(
							mRnd.nextInt((int) PowerBotsLiveWallpaper.mCameraWidth - 64),
							mRnd.nextInt((int) PowerBotsLiveWallpaper.mCameraHeight - 64),
							Color.red(robotColor), Color.green(robotColor),
							Color.blue(robotColor));
		}

		// Remove robots if the power is less than the number of robots.
		for (int i = diff; i < 0; i++) {
			removeRobot();
		}
	}

	private void removeRobot() {

		if (robotArray.size() == 0)
			return;

		final AnimatedSprite robot = robotArray.remove(robotArray.size() - 1);

		removeRobot(robot);

	}

	private void removeRobot(AnimatedSprite robot) {
		final Scene scene = getEngine().getScene();

		final PhysicsConnector robotPhysicsConnector = mPhysicsWorld
				.getPhysicsConnectorManager()
				.findPhysicsConnectorByShape(robot);

		mPhysicsWorld.unregisterPhysicsConnector(robotPhysicsConnector);
		mPhysicsWorld.destroyBody(robotPhysicsConnector.getBody());

		scene.unregisterTouchArea(robot);
		scene.getLastChild().detachChild(robot);
	}

	@Override
	public boolean onSceneTouchEvent(Scene arg0, TouchEvent arg1) {
		return false;
	}

	@Override
	public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
			final ITouchArea pTouchArea, final float pTouchAreaLocalX,
			final float pTouchAreaLocalY) {

		if (pSceneTouchEvent.getAction() == MotionEvent.ACTION_DOWN) {
			mPhysicsWorld.postRunnable(new Runnable() {
				@Override
				public void run() {
					final AnimatedSprite robot = (AnimatedSprite) pTouchArea;

					jumpRobot(robot);
				}
			});
		}
		return false;
	}

	private void jumpRobot(final AnimatedSprite robot) {

		final Body robotBody = mPhysicsWorld.getPhysicsConnectorManager()
				.findBodyByShape(robot);
		Vector2 impulse = new Vector2(mRnd.nextFloat(), mRnd.nextFloat());
		robotBody.applyLinearImpulse(
				impulse,
				new Vector2(robotBody.getWorldCenter().x, robotBody
						.getWorldCenter().y));

		robotBody.setLinearVelocity(mTempVector.set(mGravityX * -70, mGravityY
				* -70));
	}

	@Override
	public void onUnloadResources() {
	}

	@Override
	public void onGamePaused() {
	}

	@Override
	public void onGameResumed() {
	};

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		if (mInitOrientation == ScreenOrientation.PORTRAIT) {
			if (mScreenOrientation == ScreenOrientation.LANDSCAPE) {
				getEngine().getScene().setScaleX(mCameraWidth / mCameraHeight);
				getEngine().getScene().setScaleY(mCameraHeight / mCameraWidth);
			}
			if (mScreenOrientation == ScreenOrientation.PORTRAIT) {
				getEngine().getScene().setScale(1);
			}
		} else if (mInitOrientation == ScreenOrientation.LANDSCAPE) {
			if (mScreenOrientation == ScreenOrientation.PORTRAIT) {
				getEngine().getScene().setScale(1);
			}
			if (mScreenOrientation == ScreenOrientation.LANDSCAPE) {
				getEngine().getScene().setScaleX(mCameraWidth / mCameraHeight);
				getEngine().getScene().setScaleY(mCameraHeight / mCameraWidth);
			}
		}
	}

}