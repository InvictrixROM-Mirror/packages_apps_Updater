package com.invictrixrom.updater;

import android.app.Activity;
import android.widget.RadioGroup; 
import android.widget.RadioButton;
import android.widget.Button; 
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.view.View;

public class SecretActivity extends Activity {

	private RadioGroup radioSlot;
	private RadioButton slotAButton, slotBButton;
	private Button pickerButton, installButton;
	private String filePath;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.secret_activity);

		radioSlot = findViewById(R.id.radio_slot);
		installButton = findViewById(R.id.install_bootimage);
		pickerButton = findViewById(R.id.choose_bootimage);
		slotAButton = findViewById(R.id.slot_a);
		slotBButton = findViewById(R.id.slot_b);

		String defSlot = Utilities.getSystemProperty(getString(R.string.slot_prop));
		if(defSlot.equals("_a")) {
                        slotAButton.setChecked(true);
                } else {
                        slotBButton.setChecked(true);
                }

		installButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(!filePath.equals("")) {
					int selectedSlotId = radioSlot.getCheckedRadioButtonId();
					String currentSlot = "";
					if(selectedSlotId == R.id.slot_a) {
						currentSlot = "_a";
					} else {
						currentSlot = "_b";
					}
					Utilities.pullBootimage(filePath, getString(R.string.boot_block_name) + currentSlot);
				}
			}
		});

		pickerButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				intent.addCategory(Intent.CATEGORY_OPENABLE);

				try {
					startActivityForResult(Intent.createChooser(intent, "Choose a Boot Image"), 0);
				} catch (android.content.ActivityNotFoundException ex) {
					Toast.makeText(SecretActivity.this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case 0:
				if (resultCode == RESULT_OK) {
					Uri uri = data.getData();
					filePath = Utilities.getPath(this, uri);
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

}
