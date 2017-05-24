package net.ednovak.ultrasound;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class TestEnergyServer extends Activity {
	private final static String TAG = TestEnergyServer.class.getName();
	
	private final IntentFilter intentFilter = new IntentFilter();
	private int dataBitCount;
	private int btBitCount;
	private final byte[] trash = new byte[100000];
	private BluetoothAdapter btAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_test_energy_server);
		
		
		// ListenDeprecated for incoming WiFi direct / P2P messages
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
		
		
		// Check for data connection!
		ConnectivityManager m = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
		if(		!(m.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected()) && 
				!(m.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected())){
			Toast.makeText(this, "There is no Internet connection!", Toast.LENGTH_SHORT).show();
			return;
		}
		startDataThread();

		// Check for BT connection
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		if(btAdapter == null){
			Toast.makeText(this, "Device does not support bluetooth", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Check if it's enabled
		if(!btAdapter.isEnabled()){
			Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(btIntent, TestEnergy.REQUEST_ENABLE_BT);
		}
		else{
			startBTThread(btAdapter);
		}
		
		
		
		// ListenDeprecated for NFC messages
		Intent i = getIntent();
		String action = i.getAction();
		if(action != null){
			if(action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)){
				Log.d(TAG, "extras keyset: " + i.getExtras().keySet());
				Log.d(TAG, "TAG: " + i.getExtras().get("android.nfc.extra.TAG"));
				Log.d(TAG, "ID: " + i.getExtras().get("android.nfc.extra.ID"));
				Log.d(TAG, "NDEF_MESSAGES: " + i.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES));
				
				Parcelable[] msgs = i.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
				byte[] payload = ((NdefMessage)msgs[0]).getRecords()[0].getPayload();
				Toast.makeText(this, "Recieved " + payload.length + " NFC bytes", Toast.LENGTH_LONG).show();
			}
		}
	}
	
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		Log.d(TAG, "requestCode: " +  requestCode + "  resultCode: " + resultCode);
		
		switch(requestCode){
			case TestEnergy.REQUEST_ENABLE_BT:
				if(resultCode == RESULT_OK){// The bluetooth radio was turned on
					startBTThread(btAdapter);
				}
				
				else{
					Toast.makeText(this, "Unable to start bluetooth", Toast.LENGTH_SHORT).show();
					return;
				}
			break;
		}
	}
	
	private void startDataThread(){
		//Start IP (data/WiFi) connection server
		Thread dataThread = new Thread(new Runnable(){
			
			private boolean receiving = false;
			@Override
			public void run(){
				final int SOCKET_PORT = 8080;
				try{
					ServerSocket s = new ServerSocket(SOCKET_PORT);
					updateStatus("WiFi Listening On: " + s.getLocalSocketAddress(), R.id.dataServerStatus);
					while(true){
						Socket client = s.accept();
						updateStatus("Wifi Data Connection From: " + client.getInetAddress() + ":" + client.getPort(), R.id.dataServerStatus);
						InputStream is = client.getInputStream();
						receiving = true;
						while(receiving){
							int bytesRead = is.read(trash);
							//Log.d(TAG, "bytes read: " + bytesRead + "  bits read: " + bytesRead * 8);
							dataBitCount += (bytesRead * 8);
							updateStatus("Recieved: " + dataBitCount+ " WiFi bits", R.id.dataServerStatus);
							if(dataBitCount >= 8000000){
								receiving = false;
								dataBitCount = 0;
								updateStatus("Finished WiFi Chunk", R.id.dataServerStatus);
							}
						}
						
					}
				} catch(Exception e){
					e.printStackTrace();
				}
				
			}

		});
		dataThread.start();
	}
	
	private void startBTThread(BluetoothAdapter btAdapter){
		Log.d(TAG, "starting bluetooth");
		
		BluetoothServerSocket tmp = null;
		try{
			tmp = btAdapter.listenUsingRfcommWithServiceRecord("Energy Test", UUID.fromString("283aef66-5e1a-4094-aa61-01dd61e273f3"));
		} catch (Exception e){
			e.printStackTrace();
		}
		
		updateStatus("Bluetooth Listening", R.id.btServerStatus);
		final BluetoothServerSocket btServerSocket = tmp;
		
		// Start the BT connection server
		Thread btThread = new Thread(new Runnable(){
			
			private boolean receiving = false;
		
			@Override
			public void run(){
				BluetoothSocket s = null;
				InputStream tmp = null;
				while(true){
					try{
						Log.d(TAG, "Trying to get new connection");
						s = btServerSocket.accept();
						tmp = s.getInputStream();
					} catch (Exception e){
						e.printStackTrace();
						break;
					}
					
					final InputStream is = tmp;
					receiving = true;
					while(receiving){
						int bytesRead = -1;
						try {
							bytesRead = is.read(trash);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						//Log.d(TAG, "bytes read: " + bytesRead + "  bits read: " + bytesRead * 8);
						btBitCount += (bytesRead * 8);
						updateStatus("Recieved: " + btBitCount+ " BT bits", R.id.btServerStatus);
						if(btBitCount >= 8000000){
							receiving = false;
							btBitCount = 0;
							updateStatus("Finished  Bluetooth Chunk", R.id.btServerStatus);
						}
					}
				}
			}
		});
		btThread.start();
	}
		
	private void updateStatus(final String msg, final int viewID){
		runOnUiThread(new Runnable(){
			@Override
			public void run(){
				TextView tv = (TextView)findViewById(viewID);
				tv.setText(msg);
			}
		});
	}
	
	public void clearStatus(View v){
		TextView tv = (TextView) findViewById(R.id.dataServerStatus);
		tv.setText("");
		tv = (TextView) findViewById(R.id.btServerStatus);
		tv.setText("");
	}
}
