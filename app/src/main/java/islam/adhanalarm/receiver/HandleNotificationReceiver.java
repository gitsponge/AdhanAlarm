package islam.adhanalarm.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.service.AdhanService;

public class HandleNotificationReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		// The Stop button keeps the prayer's notification; swiping it away removes it
		AdhanService.stop(context, CONSTANT.ACTION_NOTIFICATION_STOPPED.equals(intent.getAction()));
	}
}
