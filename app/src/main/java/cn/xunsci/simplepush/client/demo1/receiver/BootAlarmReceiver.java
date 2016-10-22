package cn.xunsci.simplepush.client.demo1.receiver;

import cn.xunsci.simplepush.client.demo1.service.OnlineService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootAlarmReceiver extends BroadcastReceiver {

	public BootAlarmReceiver() {

	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Intent startSrv = new Intent(context, OnlineService.class);
		startSrv.putExtra("CMD", "TICK");
		context.startService(startSrv);
	}

}
