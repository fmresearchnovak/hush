package net.ednovak.ultrasound;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.Toast;

public class TestEnergy extends Activity {
	private final static String TAG = TestEnergy.class.getName();
	private Context ctx;
	
	public static final int REQUEST_ENABLE_BT = 1;
	private final int REQUEST_CHOOSE_BT_DEVICE = 2;
	private final int REQUEST_CHOOSE_WIFI_DIRECT_DEVICE = 3;
	
	private final int WIFI = 10;
	private final int DATA = 11;
	private final int BLUETOOTH = 12;
	private final int NFC = 13;
	
	// Bluetooth
	private ConnectivityManager m;
	private BluetoothAdapter ba;
	private Set<BluetoothDevice> btDeviceList = null;
	private long btCheckTime = 0;
	
	// WiFi
	// I need to pick something that is available and up!
	private String addr = "128.239.134.200";
	private final int port = 8080;
	
	
	// WiFi Direct / P2P
	private final IntentFilter intentFilter = new IntentFilter();
	private boolean wifiDirectEnabled = false;
	private Collection<WifiP2pDevice> curPeers = new ArrayList<WifiP2pDevice>();
	private WifiP2pManager dManager;
	private Channel dChannel;
	private PeerListListener pl = new PeerListListener(){
		public void onPeersAvailable(WifiP2pDeviceList newPeers){
			curPeers.clear();
			curPeers = newPeers.getDeviceList();
			userPickWifiDirectDevice();
		}
	};
	
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_testenergy);
		m = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		ctx = this;
	}
	
	public void wifiDirectTest(View v){
		
		// ListenDeprecated for incoming WiFi direct / P2P messages
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
		
		// Get connmanager
		dManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		dChannel = dManager.initialize(this, getMainLooper(), null);
		
		// Watch for changes
		WifiP2pBroadcastReceiver r = new WifiP2pBroadcastReceiver();
		registerReceiver(r, intentFilter);
		
		// Initiate peer discovery
		dManager.discoverPeers(dChannel, new WifiP2pManager.ActionListener(){
			@Override
			public void onSuccess(){
				Log.d(TAG, "Successful Peer Discovery Init");
			}
			
			@Override
			public void onFailure(int reason){
				Log.d(TAG, "Failed Peer Discovery Init.  Reason: " + reason);
				Toast.makeText(ctx, "Wifi Direct Unsupported", Toast.LENGTH_SHORT).show();
			}
		});	
		
	}
	
	
	public void wifiTest(View v){
		
		// Check if we have a WiFi connection
		if(!m.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()){
			Toast.makeText(this, "There is no WiFi connection!", Toast.LENGTH_SHORT).show();
			return;
		}
		// Get IP
		//checkIPaddr();
		
		// Send Data!
		Thread t = new SocketThread(WIFI);
		t.start();
	}
	
	public void dataTest(View v){
		// Check if we have a WiFi connection
		if(m.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()){
			Toast.makeText(this, "Please deactivate WiFi", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if(!m.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected()){
			Toast.makeText(this, "There is no data connection!", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Check IP
		//checkIPaddr();
		
		// Send Data!
		Thread t = new SocketThread(DATA);
		t.start();
		
	}

	
	public void btTest(View v){
		// Check if we have Bluetooth Hardware
		ba = BluetoothAdapter.getDefaultAdapter();
		if(ba == null){
			Toast.makeText(this, "No Bluetooth Hardware", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Check if it's enabled
		if(!ba.isEnabled()){
			Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(i, REQUEST_ENABLE_BT);
		}
		else{
			userPickBTDevice();
		}	
	}
	
	@SuppressLint("NewApi")
	public void nfcTest(View v){
		// Check NFC hardware
		NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
		if(nfc == null){
			Toast.makeText(this, "No NFC Hardware", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Get Random data
		EditText et = (EditText)findViewById(R.id.nfcSize);
		final byte[] payload = getRandomBytesForTest(et); 
		
		// Create Message
		// Message is automatically sent if this application is running
		// and the two phones are brought within range of one another.
		
		
		NdefRecord p = NdefRecord.createExternal("net.ednovak.ultrasound", "randbytes", payload);
		//NdefMessage msg = new NdefMessage(new NdefRecord[]{
		//		createNewTextRecord("Test Finished", Locale.ENGLISH, true),
		//		p});
		NdefMessage msg = new NdefMessage(p);
		nfc.setNdefPushMessage(msg, this);
	}
	
	
	
	public static NdefRecord createNewTextRecord(String s, Locale l, boolean utf8){
        byte[] langBytes = l.getLanguage().getBytes(Charset.forName("US-ASCII"));
        
        Charset utfEncoding = utf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = s.getBytes(utfEncoding);
 
        int utfBit = utf8 ? 0 : (1 << 7);
        char status = (char)(utfBit + langBytes.length);
 
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte)status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);		
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		Log.d(TAG, "requestCode: " +  requestCode + "  resultCode: " + resultCode);
		
		switch(requestCode){
			case REQUEST_ENABLE_BT:
				if(resultCode == RESULT_OK){// The bluetooth radio was turned on
					userPickBTDevice();
				}
				
				else{
					Toast.makeText(this, "Unable to start bluetooth", Toast.LENGTH_SHORT).show();
					return;
				}
				break;
				
			case REQUEST_CHOOSE_BT_DEVICE: // User chose a bluetooth device
				if(resultCode == RESULT_OK){// Got a device MAC to pair with
					String s = data.getStringExtra("item");
					connectBTDevice(s);
				}
				else{ // User canceled
					return;
				}
				break;
				
			case REQUEST_CHOOSE_WIFI_DIRECT_DEVICE: // User choose a Wifi Direct device
				if(resultCode == RESULT_OK){
					String s = data.getStringExtra("item");
					Log.d(TAG, "WIFI DIRECT chose: " + s);
					connectWifiDirectDevice(s);
				}
				break;
				
				
			
		}
		
	}
	
	private void userPickDevice(ArrayList<String> l, int requestType){
		Intent i = new Intent(this, ItemSelector.class);
		i.putStringArrayListExtra("items", l);
		startActivityForResult(i, requestType);
	}
	
	private void userPickBTDevice(){
		// Choose a Device
		Set<BluetoothDevice> devices = getBTList();
		
		ArrayList<String> names = new ArrayList<String>();
		// If there are paired devices
		if (devices.size() > 0) {
		    // Loop through paired devices
		    for (BluetoothDevice d : devices) {
		        // Add the name and address to an array adapter to show in a ListView
		        names.add(d.getName() + "\n" + d.getAddress());
		    }
		}
		
		userPickDevice(names, REQUEST_CHOOSE_BT_DEVICE);
	}
	
	private void userPickWifiDirectDevice(){
		ArrayList<String> names = new ArrayList<String>();
		if(curPeers.size() > 0){
			for( WifiP2pDevice d : curPeers){
				names.add(d.deviceName + "\n" + d.deviceAddress);
			}	
		}
		
		userPickDevice(names, REQUEST_CHOOSE_WIFI_DIRECT_DEVICE);
	}
	
	
	private void connectBTDevice(String mac){
		Set<BluetoothDevice> devices = getBTList();
		if(devices.size() > 0){	
			for(BluetoothDevice d : devices){
				if (d.getAddress().equals(mac)){ // This is the device!
					sendBTTo(d);
					break;
				}
			}
		}
	}
	
	private void connectWifiDirectDevice(String s){
		for( WifiP2pDevice d : curPeers){
			if(d.deviceAddress.equals(s)){
				sendWifiDirectTo(d);
			}
		}
	}
	
	private void sendWifiDirectTo(final WifiP2pDevice d){
		
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = d.deviceAddress;
		config.wps.setup = WpsInfo.PBC;
		
		dManager.connect(dChannel, config, new ActionListener(){
			
			@Override
			public void onSuccess(){
				Log.d(TAG, "Wifi Direct Connection Init Succss");
			}
			
			@Override
			public void onFailure(int reason){
				Log.d(TAG, "Wifi Direct Connection Init Failure for: " + d.deviceName + "  reason: " + reason);
			}
		});
		
	}
	
	
	
	private Set<BluetoothDevice> getBTList(){
		long now = System.currentTimeMillis();
		long delay = now - btCheckTime;
		if(delay > 5000 || btDeviceList == null){
			btCheckTime = now;
			btDeviceList = ba.getBondedDevices();
		}

		return btDeviceList;
	}
	
	@SuppressLint("NewApi")
	private void sendBTTo(BluetoothDevice d){
		BluetoothSocket tmp = null;
		try{
			tmp = d.createRfcommSocketToServiceRecord(UUID.fromString("283aef66-5e1a-4094-aa61-01dd61e273f3"));
		} catch (IOException e){ }
		final BluetoothSocket s = tmp;
		
		EditText et = (EditText)findViewById(R.id.btSize);
		final byte[] bits = getRandomBytesForTest(et);
		
		Thread t = new Thread(new Runnable(){
			@Override
			public void run(){
				try{
					blinkTransmit(true, BLUETOOTH);
					s.connect();
					OutputStream os = s.getOutputStream();
					os.write(bits);

				} catch(IOException e){
					e.printStackTrace();
				} finally{
					try {
						s.close();
						blinkTransmit(false, BLUETOOTH);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
		t.start();
	}
	
	
	private byte[] getRandomBytesForTest(EditText et){
		int tmpSize = 0;
		try{
			tmpSize = (Integer.valueOf(et.getText().toString()))/8; // In bytes
		} catch (Exception e){
			Toast.makeText(this, "Please enter a number of bits", Toast.LENGTH_SHORT).show();
			return new byte[0];
		}
		final int size = tmpSize;
		
		Random r = new Random();
		byte[] buff = new byte[size];
		r.nextBytes(buff);
		return buff;
	}
	
	private void blinkTransmit(final boolean onOff, final int networkType){
		
		Log.d(TAG, "Changing Blink Status for " + networkType + ": " + onOff);

		runOnUiThread(new Runnable(){
				@Override
				public void run(){
				View v = null;
				switch(networkType){
				case WIFI:
					v = findViewById(R.id.wifiTrans);
					break;
					
				case DATA:
					v = findViewById(R.id.dataTrans);
					break;
					
				case BLUETOOTH:
					v = findViewById(R.id.btTrans);
					break;
					
				case NFC:
					v = findViewById(R.id.nfcTrans);
					break;
				}
				
				if(onOff){
					Animation a = AnimationUtils.loadAnimation(ctx, R.anim.blink);
					v.startAnimation(a);
				} else{
					v.clearAnimation();
					v.setVisibility(View.INVISIBLE);
				}
				
			}
		});
	}
	
	class SocketThread extends Thread{
		
		byte[] buff;
		int localNetworkType;
		
		public SocketThread(int networkType){
			
			
			EditText et = null;
			switch(networkType){
			case WIFI:
				et = (EditText)findViewById(R.id.wifiSize);
				break;
			case DATA:
				et = (EditText)findViewById(R.id.dataSize);
				break;
			}
			
			buff = getRandomBytesForTest(et);
			localNetworkType = networkType;
		}
		
		@Override
		public void run(){
			Socket s = null;
			try {
				s = new Socket(addr, port);
			} catch(Exception e){
				e.printStackTrace();
			}
			try {
				blinkTransmit(true, localNetworkType);
				OutputStream byteArrayOutputStream = s.getOutputStream();
				byteArrayOutputStream.write(buff);
			} catch (IOException e) {
				e.printStackTrace();
			}
			finally{
				try {
					s.close();
					blinkTransmit(false, localNetworkType);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private class WifiP2pBroadcastReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context ctx, Intent i){
			String action = i.getAction();
			if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)){
				int state = i.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
				if(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED){
					wifiDirectEnabled = true;
				}
				if(state == WifiP2pManager.WIFI_P2P_STATE_DISABLED){
					wifiDirectEnabled = false;
				}
			}
			else if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)){
				Log.d(TAG, "Peer List Changed");
				if(dManager != null){
					dManager.requestPeers(dChannel, pl);
				}
				
			}
			else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)){
				Log.d(TAG, "Connection State Changed");
				if(dManager == null){
					return;
				}
				NetworkInfo networkInfo = (NetworkInfo) i.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
				if(networkInfo.isConnected()){
					Log.d(TAG, "Wifi Direct Connection Established");
					dManager.requestConnectionInfo(dChannel, new ConnectionInfoListener(){
						@Override
						public void onConnectionInfoAvailable(final WifiP2pInfo info){
							String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();
							Log.d(TAG, "IP of owner: " + groupOwnerAddress);
							
							if(info.groupFormed && info.isGroupOwner){
								Log.d(TAG, "I am a group owner!");
							}
							else if(info.groupFormed){
								Log.d(TAG, "I am a client");
							}
						}
					});
				}
				
			}
			else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)){
				Log.d(TAG, "Device Changed Action");
			}
		}	
	}
	
	private void checkIPaddr(){
		if(addr == null){
			AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
			alert.setTitle("IP Address");
			alert.setMessage("What is the IP Address of the Server?");
			final EditText alertET = new EditText(ctx);
			alert.setView(alertET);
			
			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int whichButton){
					addr = alertET.getText().toString();
				}
			});
			alert.show();
		}
	}
}
