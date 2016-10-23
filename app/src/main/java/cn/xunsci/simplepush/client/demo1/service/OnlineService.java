package cn.xunsci.simplepush.client.demo1.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.widget.Toast;

import org.ddpush.im.v1.client.appuser.Message;
import org.ddpush.im.v1.client.appuser.TCPClientBase;

import java.nio.ByteBuffer;

import cn.xunsci.simplepush.client.demo1.R;
import cn.xunsci.simplepush.client.demo1.DateTimeUtil;
import cn.xunsci.simplepush.client.demo1.MainActivity;
import cn.xunsci.simplepush.client.demo1.Params;
import cn.xunsci.simplepush.client.demo1.Util;
import cn.xunsci.simplepush.client.demo1.receiver.TickAlarmReceiver;

public class OnlineService extends Service {
	
	protected PendingIntent tickPendIntent;
	protected TickAlarmReceiver tickAlarmReceiver = new TickAlarmReceiver();
	WakeLock wakeLock;
	MyTcpClient myTcpClient;
	Notification n;

	/*
		继承TCPClientBase类，实现与simplePush服务通信交互
	 */
	public class MyTcpClient extends TCPClientBase {

		public MyTcpClient(byte[] uuid, int appid, String serverAddr, int serverPort)
				throws Exception {
			super(uuid, appid, serverAddr, serverPort, 10);

		}

		@Override
		public boolean hasNetworkConnection() {
			return Util.hasNetwork(OnlineService.this);
		}
		

		@Override
		public void trySystemSleep() {
			tryReleaseWakeLock();
		}

		@Override
		public void onPushMessage(Message message) {
			if(message == null){
				return;
			}
			if(message.getData() == null || message.getData().length == 0){
				return;
			}
			if(message.getCmd() == 16){// 0x10 通用推送信息
				notifyUser(16,"simplePush测试推送信息","时间："+ DateTimeUtil.getCurDateTime(),"收到测试推送信息");
			}
			if(message.getCmd() == 17){// 0x11 分组推送信息
				long msg = ByteBuffer.wrap(message.getData(), 5, 8).getLong();
				notifyUser(17,"simplePush分组推送信息",""+msg,"收到分组推送信息");
			}
			if(message.getCmd() == 32){// 0x20 自定义推送信息
				String str = null;
				try{
					str = new String(message.getData(),5,message.getContentLength(), "UTF-8");
				}catch(Exception e){
					str = Util.convert(message.getData(),5,message.getContentLength());
				}
				notifyUser(32,"simplePush自定义推送信息",""+str,"收到自定义推送信息");
			}
			setPkgsInfo();
		}

	}

	public OnlineService() {
	}

	@Override
	public void onCreate() {
		super.onCreate();
		this.setTickAlarm();

		//申请休眠锁，用于阻止系统休眠
		PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		/*PARTIAL_WAKE_LOCK :保持CPU 运转，屏幕和键盘灯是关闭的。
		SCREEN_DIM_WAKE_LOCK ：保持CPU 运转，允许保持屏幕显示但有可能是灰的，关闭键盘灯
		SCREEN_BRIGHT_WAKE_LOCK ：保持CPU 运转，保持屏幕高亮显示，关闭键盘灯
		FULL_WAKE_LOCK ：保持CPU 运转，保持屏幕高亮显示，键盘灯也保持亮度
		ACQUIRE_CAUSES_WAKEUP: 一旦有请求锁时，强制打开Screen和keyboard light
		ON_AFTER_RELEASE: 在释放锁时reset activity timer
		*/
		//按下power键也可继续运行
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnlineService");
		
		resetClient();

		//运行系统通知
		notifyRunning();
	}


	//拼了老命让你不休眠的逻辑
	@Override
	public int onStartCommand(Intent param, int flags, int startId) {
		if(param == null){
			return START_STICKY;
		}
		String cmd = param.getStringExtra("CMD");
		if(cmd == null){
			cmd = "";
		}
		if(cmd.equals("TICK")){
			if(wakeLock != null && wakeLock.isHeld() == false){
				//获取锁
				wakeLock.acquire();
			}
		}
		if(cmd.equals("RESET")){
			if(wakeLock != null && wakeLock.isHeld() == false){
				wakeLock.acquire();
			}
			resetClient();
		}
		if(cmd.equals("TOAST")){
			String text = param.getStringExtra("TEXT");
			if(text != null && text.trim().length() != 0){
				Toast.makeText(this, text, Toast.LENGTH_LONG).show();
			}
		}
		
		setPkgsInfo();

		return START_STICKY;
	}
	//设置收发包数量显示
	protected void setPkgsInfo(){
		if(this.myTcpClient == null){
			return;
		}
		long sent = myTcpClient.getSentPackets();
		long received = myTcpClient.getReceivedPackets();
		SharedPreferences account = this.getSharedPreferences(Params.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = account.edit();
		editor.putString(Params.SENT_PKGS, ""+sent);
		editor.putString(Params.RECEIVE_PKGS, ""+received);
		editor.commit();
	}

	//重置simplePush终端
	protected void resetClient(){
		SharedPreferences account = this.getSharedPreferences(Params.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
		String serverIp = account.getString(Params.SERVER_IP, "");
		String serverPort = account.getString(Params.SERVER_PORT, "");
		String pushPort = account.getString(Params.PUSH_PORT, "");
		String userName = account.getString(Params.USER_NAME, "");
		if(serverIp == null || serverIp.trim().length() == 0
				|| serverPort == null || serverPort.trim().length() == 0
				|| pushPort == null || pushPort.trim().length() == 0
				|| userName == null || userName.trim().length() == 0){
			return;
		}
		if(this.myTcpClient != null){
			try{myTcpClient.stop();}catch(Exception e){}
		}
		try{
			myTcpClient = new MyTcpClient(Util.md5Byte(userName), 1, serverIp, Integer.parseInt(serverPort));
			myTcpClient.setHeartbeatInterval(50);
			myTcpClient.start();
			SharedPreferences.Editor editor = account.edit();
			editor.putString(Params.SENT_PKGS, "0");
			editor.putString(Params.RECEIVE_PKGS, "0");
			editor.commit();
		}catch(Exception e){
			Toast.makeText(this.getApplicationContext(), "操作失败："+e.getMessage(), Toast.LENGTH_LONG).show();
		}
		Toast.makeText(this.getApplicationContext(), "simplepush：终端重置", Toast.LENGTH_LONG).show();
	}
	//释放锁
	protected void tryReleaseWakeLock(){
		if(wakeLock != null && wakeLock.isHeld() == true){
			wakeLock.release();
		}
	}

	//定时广播
	protected void setTickAlarm(){
		AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);  
		Intent intent = new Intent(this,TickAlarmReceiver.class);
		int requestCode = 0;  
		tickPendIntent = PendingIntent.getBroadcast(this,  
		requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);  
		//小米的MIUI操作系统，最短广播间隔为5分钟，少于5分钟的alarm会等到5分钟再触发！2014-04-28
		long triggerAtTime = System.currentTimeMillis();
		//5min
		int interval = 300 * 1000;  
		alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, interval, tickPendIntent);
	}
	
	protected void cancelTickAlarm(){
		AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmMgr.cancel(tickPendIntent);  
	}


	/*
	protected void notifyRunning(){


		Notification.Builder builder = new Notification.Builder(this);
		builder.setContentInfo("补充内容");
		builder.setContentText("主内容区");
		builder.setContentTitle("通知标题");
		//builder.setSmallIcon(R.mipmap.icon_demo);
		builder.setTicker("simplePushDemo正在运行");
		builder.setAutoCancel(true);
		builder.setWhen(System.currentTimeMillis());
		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		builder.setContentIntent(pendingIntent);
		Notification notification = builder.build();
		manager.notify(NOTIFICATION_ID, notification);
	}
	*/
	/*
	protected void notifyRunning(){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);

		Notification.Builder builder = new Notification.Builder(this);//新建Notification.Builder对象
		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_ONE_SHOT);
		builder.setContentTitle("simplePushDemo");//设置标题
		builder.setContentText("正在运行");//设置内容
		builder.setSmallIcon(R.drawable.ic_launcher);//设置图片
		builder.setTicker("simplePushDemo正在运行");
		builder.setContentIntent(pendingIntent);//执行intent
		Notification notification = builder.getNotification();//将builder对象转换为普通的notification
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.flags |= Notification.FLAG_NO_CLEAR;
		startForeground(0,notification);
		notificationManager.notify(0,notification);
	}
	*/
	//通知栏通知设置
	protected void notifyRunning(){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		n = new Notification();
		Intent intent = new Intent(this,MainActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);
		n.contentIntent = pi;
		n.setLatestEventInfo(this, "simplePushDemo", "正在运行", pi);
		//n.defaults = Notification.DEFAULT_ALL;
		//n.flags |= Notification.FLAG_SHOW_LIGHTS;
		//n.flags |= Notification.FLAG_AUTO_CANCEL;
		n.flags |= Notification.FLAG_ONGOING_EVENT;
		n.flags |= Notification.FLAG_NO_CLEAR;
		//n.iconLevel = 5;

		n.icon = R.drawable.ic_launcher;
		n.when = System.currentTimeMillis();
		n.tickerText = "simplePushDemo正在运行";
		notificationManager.notify(0, n);
	}
	protected void cancelNotifyRunning(){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(0);
	}

	/*
	public void notifyUser(int id, String title, String content, String tickerText){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder builder = new Notification.Builder(this);



		//Notification n = new Notification();
		Intent intent = new Intent(this,MainActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);
		builder.setContentTitle(title);
		builder.setContentText(content);
		builder.setTicker(tickerText);
		builder.setContentIntent(pi);
		Notification notification = builder.getNotification();

		startForeground(id,notification);
		notificationManager.notify(id,notification);



		n.contentIntent = pi;

		n.setLatestEventInfo(this, title, content, pi);
		n.defaults = Notification.DEFAULT_ALL;
		n.flags |= Notification.FLAG_SHOW_LIGHTS;  
		n.flags |= Notification.FLAG_AUTO_CANCEL;

		n.icon = R.drawable.ic_launcher;  
		n.when = System.currentTimeMillis();
		n.tickerText = tickerText;
		notificationManager.notify(id, n);

	}
	*/
	public void notifyUser(int id, String title, String content, String tickerText){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification n = new Notification();
		Intent intent = new Intent(this,MainActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);
		n.contentIntent = pi;

		n.setLatestEventInfo(this, title, content, pi);
		n.defaults = Notification.DEFAULT_ALL;
		n.flags |= Notification.FLAG_SHOW_LIGHTS;
		n.flags |= Notification.FLAG_AUTO_CANCEL;

		n.icon = R.drawable.ic_launcher;
		n.when = System.currentTimeMillis();
		n.tickerText = tickerText;
		notificationManager.notify(id, n);
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		//this.cancelTickAlarm();
		cancelNotifyRunning();
		this.tryReleaseWakeLock();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


}
