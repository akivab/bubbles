package com.beeyunks.bubbles;

import jibe.sdk.client.apptoapp.Config;
import android.app.Application;
import com.beeyunks.bubbles.R;

public class JibeApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		// set up App-ID and App-Secret in one central place.
		Config.getInstance().setAppToAppIdentifier("68d569f016724cdc9c34018e84a927f4", "1e3885a3e39e4a7aad83fe4799944887");
	}
}
