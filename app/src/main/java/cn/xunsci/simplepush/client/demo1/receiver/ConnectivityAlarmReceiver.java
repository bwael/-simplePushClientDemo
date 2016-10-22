package cn.xunsci.simplepush.client.demo1.receiver;

import cn.xunsci.simplepush.client.demo1.Util;
import cn.xunsci.simplepush.client.demo1.service.OnlineService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectivityAlarmReceiver extends BroadcastReceiver {

	public ConnectivityAlarmReceiver() {
		super();
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		if(Util.hasNetwork(context) == false){
			return;
		}
		Intent startSrv = new Intent(context, OnlineService.class);
		startSrv.putExtra("CMD", "RESET");
		context.startService(startSrv);
	}

}
