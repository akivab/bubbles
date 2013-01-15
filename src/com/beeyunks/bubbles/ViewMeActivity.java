package com.beeyunks.bubbles;

import java.io.IOException;

import jibe.sdk.client.JibeIntents;
import jibe.sdk.client.JibeServiceListener.ConnectFailedReason;
import jibe.sdk.client.events.JibeSessionEvent;
import jibe.sdk.client.simple.ChallengeDialog;
import jibe.sdk.client.simple.SimpleApi;
import jibe.sdk.client.simple.SimpleConnectionStateListener;
import jibe.sdk.client.simple.session.JibeBundle;
import jibe.sdk.client.simple.session.JibeBundleTransferConnection;
import jibe.sdk.client.simple.session.JibeBundleTransferConnection.JibeBundleTransferConnectionListener;
import jibe.sdk.client.simple.videocall.VideoCallConnection;
import jibe.sdk.client.video.CameraMediaSource;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

public class ViewMeActivity extends Activity {

	private static final String TAG = ViewMeActivity.class.getSimpleName();

	public boolean mIsSender;
	private String mChallengeReceiverDisplayName = null;
	private String mChallengeReceiverPhoneNumber = null;
	private String mChallengerDisplayName = null;

	private ProgressDialog mWaitingForReceiverToAcceptDialog = null;
	private ChallengeDialog mIncomingChallengeDialog = null;

	private JibeBundleTransferConnection mBundleConnection = null;
	private VideoCallConnection mVideoConnection = null;

	public boolean mIsNetworkFailure = false;
	public boolean mGameStart = false;

	private SurfaceView mLocalViewSurface;
	private SurfaceView mRemoteViewSurface;

	private CameraMediaSource mCameraMediaSource;

	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video);

		mBundleConnection = new JibeBundleTransferConnection(this,
				mBundleConnStateListener, mBundleListener);

		Intent i = getIntent();
		// Check if this activity was launched via the Arena friend list. If
		// yes, you are the sender/creator of the session
		mIsSender = i.getAction()
				.startsWith(JibeIntents.ACTION_ARENA_CHALLENGE);

		// Init surfaces and camera
		mLocalViewSurface = (SurfaceView) findViewById(R.id.video_local);
		mLocalViewSurface.setZOrderMediaOverlay(true);
		mRemoteViewSurface = (SurfaceView) findViewById(R.id.video_remote);
		mCameraMediaSource = new CameraMediaSource(
				CameraMediaSource.CAMERA_FRONT, mRemoteViewSurface);
		if (mIsSender) {
			mChallengeReceiverDisplayName = i
					.getStringExtra(JibeIntents.EXTRA_DISPLAYNAME);
			mChallengeReceiverPhoneNumber = i
					.getStringExtra(JibeIntents.EXTRA_USERID);
			showSenderSideDialog();
		} else {
			mChallengerDisplayName = i
					.getStringExtra(JibeIntents.EXTRA_DISPLAYNAME);
			showReceiverSideDialog();
		}
		
		WebView myWebView = (WebView) findViewById(R.id.gameplay);
		myWebView.loadUrl("http://html5-with-jibe.appspot.com/gameportal");
		WebSettings webSettings = myWebView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setDomStorageEnabled(true);
		myWebView.addJavascriptInterface(new JSInterface(this), "Android");
	}

	
	protected void onDestroy() {
		super.onDestroy();

		mBundleConnection.dispose();

		if (mVideoConnection != null) {
			mVideoConnection.dispose();
		}
	}
	
	protected void onPause() {
		super.onPause();
		finish();
	}


	private void showSenderSideDialog() {
		mWaitingForReceiverToAcceptDialog = new ProgressDialog(
				ViewMeActivity.this);
		if (mChallengeReceiverDisplayName != null) {
			mWaitingForReceiverToAcceptDialog.setMessage(getString(R.string.outgoing_challenge_dialog_message, mChallengeReceiverDisplayName));
		} else {
			mWaitingForReceiverToAcceptDialog.setMessage(getString(R.string.outgoing_challenge_dialog_search_message));
		}
		mWaitingForReceiverToAcceptDialog.setIndeterminate(true);
		mWaitingForReceiverToAcceptDialog.setCancelable(true);
		mWaitingForReceiverToAcceptDialog.setButton(
				DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_cancel),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}

				});
		mWaitingForReceiverToAcceptDialog
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					
					public void onCancel(DialogInterface dialog) {
						finish();
					}
				});
		mWaitingForReceiverToAcceptDialog.show();
	}

	private void showReceiverSideDialog() {
		mIncomingChallengeDialog = new ChallengeDialog(this);
		mIncomingChallengeDialog.setPlayRingtone(true);
		mIncomingChallengeDialog.setVibrate(true);
		mIncomingChallengeDialog.setMessage(getString(R.string.dlg_incoming_challenge) + " " +
				mChallengerDisplayName);
		mIncomingChallengeDialog.show(mChallengeDialogListener, getIntent());
	}

	private ChallengeDialog.Listener mChallengeDialogListener = new ChallengeDialog.Listener() {
		
		public void onAccepted(ChallengeDialog source, Intent intent) {
			try {
				mBundleConnection.start(getIntent());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		
		public void onRejected(ChallengeDialog source) {
			finish();
		}

		
		public void onError(ChallengeDialog source, int reason) {
			finish();
		}
	};

	private JibeBundleTransferConnectionListener mBundleListener = new JibeBundleTransferConnectionListener() {

		
		public void jibeBundleReceived(JibeBundle jibeBundle) {
			String s = jibeBundle.getString("jsString");
			Log.d(TAG, "Received bundle=" + s);
			Bundle bundle = new Bundle();
			bundle.putString("data_str", s);
			Message msg = new Message();
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		}

		
		public void acknowledgeReceived(int bundleNumber) {
			Log.d(TAG, "Received ack for bundle number: " + bundleNumber);
		}
	};

	private Handler mHandler = new Handler() {
		
		public void handleMessage(Message msg) {
			if (isFinishing()) {
				return;
			}


			sendJsMessage(msg.getData().getString("data_str"));
		}
	};

	public void onSendTimestampClick(View view) {
		sendCurrentTimestamp();
	}

	public void onSwitchCamClick(View view) {
		if (mCameraMediaSource.getCameraId() == CameraMediaSource.CAMERA_FRONT)
			mCameraMediaSource.switchCamera(CameraMediaSource.CAMERA_BACK);
		else
			mCameraMediaSource.switchCamera(CameraMediaSource.CAMERA_FRONT);
	}

	private void sendCurrentTimestamp() {
		JibeBundle jb = new JibeBundle();
		jb.putLong("ts", System.currentTimeMillis());
		try {
			int packetNumber = mBundleConnection.send(jb);
			Log.d(TAG, "Sent packet number: " + packetNumber);
		} catch (IOException e) {
			Log.e(TAG, "Trouble sending data packet", e);
			e.printStackTrace();
		}
	}

	private SimpleConnectionStateListener mBundleConnStateListener = new SimpleConnectionStateListener() {

		
		public void onInitialized(SimpleApi source) {
			Log.d(TAG, "onInitialized()");
			try {
				if (mIsSender) {
					mBundleConnection.start(getIntent());
				} else {
					mBundleConnection.monitor(getIntent());
				}
			} catch (Exception e) {
				Log.e(TAG, "Exception opening datagram", e);
			}
		}

		
		public void onInitializationFailed(SimpleApi source,
				ConnectFailedReason reason) {
			Log.e(TAG, "onInitializationFailed()");
		}

		
		public void onStarted(SimpleApi source) {
			Log.v(TAG, "onStarted()");
			doWhenDataConnectionStarted();
		}

		
		public void onStartFailed(SimpleApi source, int info) {
			Log.v(TAG, "onStartFailed(). JibeSessionEvent info:" + info);
			switch (info) {
			case JibeSessionEvent.INFO_SESSION_CANCELLED:
				showMessage("The sender has canceled the connection");
				break;
			case JibeSessionEvent.INFO_SESSION_REJECTED:
				showMessage("The receiver has rejected the connection/is busy");
				break;
			case JibeSessionEvent.INFO_USER_NOT_ONLINE:
				showMessage("Receiver is not online");
				break;
			case JibeSessionEvent.INFO_USER_UNKNOWN:
				showMessage("This phone number is not known");
				break;
			default:
				showMessage("Connection start failed.");
				break;
			}
			finish();
		}

		
		public void onTerminated(SimpleApi source, int info) {
			Log.v(TAG, "onTerminated(). JibeSessionEvent info:" + info);
			switch (info) {
			case JibeSessionEvent.INFO_GENERIC_EVENT:
				showMessage("You terminated the connection");
				break;
			case JibeSessionEvent.INFO_SESSION_TERMINATED_BY_REMOTE:
				showMessage("The remote party terminated the connection");
				break;
			default:
				showMessage("Connection terminated.");
				break;
			}
			finish();
		}
	};

	private void doWhenDataConnectionStarted() {
		if (mIsSender) {
			mWaitingForReceiverToAcceptDialog.dismiss();
		} else {
			mIncomingChallengeDialog.dismiss();
		}

		mVideoConnection = new VideoCallConnection(getApplicationContext(),
				mVideoConnStateListener, mCameraMediaSource, mLocalViewSurface);
		mVideoConnection.setAutoAccept(true);
	}

	private SimpleConnectionStateListener mVideoConnStateListener = new SimpleConnectionStateListener() {

		
		public void onInitialized(SimpleApi source) {
			Log.v(TAG, "onInitialized()");
			if (mIsSender) {
				try {
					mVideoConnection.start(mChallengeReceiverPhoneNumber);
				} catch (Exception e) {
					Log.e(TAG, "mTwoWayVideoConnection. Failed to open()", e);
					showMessage("Failed to open video connection");
				}
			}
		}

		
		public void onInitializationFailed(SimpleApi source,
				ConnectFailedReason reason) {
		}

		
		public void onStarted(SimpleApi source) {
			Log.v(TAG, "onStarted()");
			showMessage("Connection started");
		}

		
		public void onStartFailed(SimpleApi source, int info) {
			Log.e(TAG, "onStartFailed(). JibeSessionEvent info:" + info);
		}

		
		public void onTerminated(SimpleApi source, int info) {
			Log.v(TAG, "onTerminated(). JibeSessionEvent info:" + info);
		}
	};

	private void sendJsMessage(final String message) {
		if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
			runOnUiThread(new Runnable() {
				public void run() {
					sendJsMessage(message);
				}
			});

			return;
		}
		JSInterface jsInterface = new JSInterface(this);
		jsInterface.receiveMessage(message);
	}
	
	private void showMessage(final String message) {
		if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
			runOnUiThread(new Runnable() {
				public void run() {
					showMessage(message);
				}
			});

			return;
		}

		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
	
	private class JSInterface {

		public JSInterface(ViewMeActivity viewMeActivity) {
		}
		
		@SuppressWarnings("unused")
		public void sendMessage(String s) {
			JibeBundle jb = new JibeBundle();
			jb.putString("jsString", s);
			try {
				mBundleConnection.send(jb);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void redirect(String url) {
			WebView myWebView = (WebView) findViewById(R.id.gameplay);
			myWebView.loadUrl(url);
		}
		
		public void receiveMessage(String s) {
			WebView myWebView = (WebView) findViewById(R.id.gameplay);
			myWebView.loadUrl("javascript:jibe.receiveMessage('" + s + "')");
		}
		
	}
}
