package net.ednovak.ultrasound;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ItemSelector extends Activity {
	private final static String TAG = ItemSelector.class.getName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_item_selector);

		Intent i = getIntent();
		ArrayList<CharSequence> devices = i.getCharSequenceArrayListExtra("items");
		final ListView lv = (ListView)findViewById(R.id.item_list);
		lv.setOnItemClickListener(new OnItemClickListener(){
			public void onItemClick(AdapterView<?> parent, View v, int pos, long id){
				//Log.d(TAG, "parent: " + parent + "  View: " +  v + "  pos: " + pos + "  id: " + id);
				String s = lv.getItemAtPosition(pos).toString();
				String[] lines = s.split("\\n");
				Intent response = new Intent();
				response.putExtra("item", lines[1]);
				setResult(RESULT_OK, response);
				finish();
			}
		});
		
		ArrayAdapter aa = new ArrayAdapter(this, R.layout.item_view, devices);
		lv.setAdapter(aa);
		
	}
	
	public void addNewItem(View v){
		
	}
	
	public void cancel(View v){
		setResult(RESULT_CANCELED);
		finish();
	}
	
}
