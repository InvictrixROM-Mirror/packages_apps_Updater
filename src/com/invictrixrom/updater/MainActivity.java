package com.invictrixrom.updater;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.UpdateEngine;

import java.io.File;
import java.util.zip.ZipFile;

public class MainActivity extends Activity implements UpdaterListener, DeltaCallback, MagiskCallback {

	private TextView statusText;
	private Button installButton, chooseButton;
	private ProgressBar progressBar;

	private int secretMenuTaps = 0;
	private boolean postInstall = false, doPersistMagisk = false;

	private String filePath = "";

	private MagiskInstaller magiskInstaller;
	private NotificationManager mNotificationManager;
	private Notification.Builder mBuilder;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);

		doPersistMagisk = shouldPersistMagisk();

		chooseButton = findViewById(R.id.choose_zip);
		installButton = findViewById(R.id.install);
		statusText = findViewById(R.id.status);
		progressBar = findViewById(R.id.install_progress);

		statusText.setText(R.string.select_zip);

		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Utilities.createNotificationChannel(mNotificationManager);

		chooseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				intent.addCategory(Intent.CATEGORY_OPENABLE);

				try {
					startActivityForResult(
							Intent.createChooser(intent, getString(R.string.select_zip)),
							0);
				} catch (android.content.ActivityNotFoundException ex) {
					Toast.makeText(MainActivity.this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
				}
			}
		});

		installButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!filePath.isEmpty()) {
					secretMenuTaps = 0;
					statusText.setText(R.string.checking_file);
					mBuilder = Utilities.buildNotification(MainActivity.this, mNotificationManager, getString(R.string.installing_ota), R.drawable.ic_stat_system_update, getString(R.string.checking_file), true, true, true, true);
					disableButtons(true);
					progressBar.setIndeterminate(true);
					File cachedFile = new File(getApplicationInfo().dataDir + "/update.zip");
					if (filePath.endsWith(".delta")) {
						if (!cachedFile.exists()) {
							progressBar.setIndeterminate(false);
							progressBar.setMax(100);
							mBuilder = Utilities.finishNotification(mNotificationManager, mBuilder, getString(R.string.delta_failed), getString(R.string.delta_error_details), true, R.drawable.ic_stat_error, R.drawable.ic_stat_success, true);
							disableButtons(false);

							statusText.setText(R.string.delta_error_details);
						} else {
							DeltaPatcher patcher = new DeltaPatcher(cachedFile.getAbsolutePath(), filePath, cachedFile.getAbsolutePath() + "2");
							patcher.setCallback(MainActivity.this);
							mBuilder = Utilities.updateNotificationText(mNotificationManager, mBuilder, getString(R.string.patching_cached_build));
							patcher.patchUpdate();
						}
					} else {
						startUpdate(filePath);
					}
				} else {
					if(++secretMenuTaps >= 5) {
						Intent intent = new Intent(MainActivity.this, SecretActivity.class);
						startActivity(intent);
					}
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		MenuItem persistMagisk = menu.findItem(R.id.persist_magisk);
		persistMagisk.setChecked(doPersistMagisk);
		return true;
	}

	private void savePersist(boolean persist) {
		SharedPreferences prefs = this.getSharedPreferences("com.invictrixrom.updater", Context.MODE_PRIVATE);
		prefs.edit().putBoolean(getString(R.string.persist_magisk_key), persist).apply();
	}

	private boolean shouldPersistMagisk() {
		SharedPreferences prefs = this.getSharedPreferences("com.invictrixrom.updater", Context.MODE_PRIVATE);
		return prefs.getBoolean(getString(R.string.persist_magisk_key), false);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.persist_magisk:
				doPersistMagisk = true;
				if (item.isChecked())
					item.setChecked(false);
				else
					item.setChecked(true);
				savePersist(item.isChecked());
				return true;
			case R.id.install_magisk:
				installMagisk();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case 0:
				if (resultCode == RESULT_OK) {
					Uri uri = data.getData();
					statusText.setText(getString(R.string.chosen_zip) + Utilities.getPath(this, uri));
					filePath = Utilities.getPath(this, uri);
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void magiskDownloaded(boolean success, String magiskPath) {
		if (success) {
			updateStatusProgress(100, 0, true);
			updateStatusText(R.string.installing_magisk);
			magiskInstaller.installMagisk(magiskPath, postInstall);
		} else {
			finishTask(R.string.magisk_download_failed, R.string.download_failed, true);
		}
		Shell.runCommand("rm -rf " + magiskPath);
	}

	@Override
	public void magiskInstallStatusUpdate(int resStatus) {
		updateStatusText(resStatus);
	}

	@Override
	public void magiskInstallComplete(boolean success) {
		finishTask((success ? R.string.magisk_install_finished : R.string.magisk_download_failed), (success ? R.string.magisk_installed : R.string.error), !success);
		updateStatusProgress(100, 0, false);
	}

	@Override
	public void magiskDownloadProgress(int progress) {
		updateStatusProgress(100, progress, false);
	}


	private void startUpdate(String updateFile) {
		File cachedFile = new File(getApplicationInfo().dataDir + "/update.zip");
		ZipFile zipFile;
		try {
			zipFile = new ZipFile(updateFile);
			boolean isABUpdate = ABUpdate.isABUpdate(zipFile);
			zipFile.close();
			if (isABUpdate) {
				updateStatusText(R.string.caching_build);
				Utilities.copyFile(new File(updateFile), cachedFile);
				ABUpdate.start(updateFile, this);
			} else {
				updateStatusProgress(100, 0, false);
				finishTask(R.string.update_failed, R.string.not_ab_update, true);
			}
		} catch (Exception ex) {
			finishTask(R.string.update_failed, R.string.zip_open_failed, true);
		}
	}

	@Override
	public void updateProgress(int progress) {
		updateStatusProgress(100, progress, false);
	}

	@Override
	public void updateStatusChange(int status) {
		int statusString = Utilities.getUpdaterStatus(status);
		updateStatusText(statusString);
	}

	@Override
	public void updateComplete(int status) {
		int statusString = Utilities.getUpdaterCompleteStatus(status);
		boolean isError = true;
		if (status == UpdateEngine.ErrorCodeConstants.SUCCESS) {
			isError = false;
		}
		updateStatusText(statusString);
		updateStatusProgress(100, 100, false);
		finishTask((isError ? R.string.update_failed : R.string.update_success), statusString, isError);
		if (!isError) postInstall = true;
		if (!isError && doPersistMagisk) installMagisk();
	}

	private void installMagisk() {
		disableButtons(true);
		if (!postInstall) {
			mBuilder = Utilities.buildNotification(MainActivity.this, mNotificationManager, getString(R.string.installing_magisk), R.drawable.ic_stat_system_update, getString(R.string.downloading_magisk), true, true, true, true);
		}
		updateStatusTitle(R.string.installing_magisk);
		updateStatusText(R.string.downloading_magisk);
		updateStatusProgress(100, 0, true);

		String currentSlot = Utilities.getSystemProperty(getString(R.string.slot_prop));
		if (postInstall) {
			if (currentSlot.equals("_b")) {
				currentSlot = "_a";
			} else {
				currentSlot = "_b";
			}
		}
		updateStatusProgress(100, 0, true);
		updateStatusText(R.string.pulling_boot);
		updateStatusProgress(100, 0, false);
		updateStatusText(R.string.downloading_magisk);
		magiskInstaller = new MagiskInstaller(MainActivity.this);
		magiskInstaller.setCallback(this);
		magiskInstaller.startDownload();
	}

	@Override
	public void deltaDone(boolean success, String resultPath) {
		if (success) {
			updateStatusText(R.string.finished_patching);
			startUpdate(resultPath);
		} else {
			finishTask(R.string.patching_failed, R.string.error, true);

		}
		File resultFile = new File(resultPath);
		File cachedFile = new File(getApplicationInfo().dataDir + "/update.zip");
		resultFile.delete();
		Utilities.copyFile(resultFile, cachedFile);
	}

	private void updateStatusTitle(int resTitle) {
		mBuilder = Utilities.updateNotificationTitle(mNotificationManager, mBuilder, getString(resTitle));
	}

	private void updateStatusText(int resText) {
		statusText.setText(resText);
		mBuilder = Utilities.updateNotificationText(mNotificationManager, mBuilder, getString(resText));
	}

	private void updateStatusProgress(int max, int value, boolean indeterminate) {
		progressBar.setIndeterminate(indeterminate);
		progressBar.setMax(max);
		progressBar.setProgress(value, true);
		mBuilder = Utilities.updateNotificationProgress(mNotificationManager, mBuilder, value, indeterminate);
	}

	private void finishTask(int resTitle, int resStatus, boolean isError) {
		disableButtons(false);
		progressBar.setIndeterminate(false);
		progressBar.setMax(100);
		progressBar.setProgress(100);
		statusText.setText(resStatus);
		mBuilder = Utilities.finishNotification(mNotificationManager, mBuilder, getString(resTitle), getString(resStatus), isError, R.drawable.ic_stat_error, R.drawable.ic_stat_success, true);
	}

	private void disableButtons(boolean disable) {
		chooseButton.setEnabled(disable);
		installButton.setEnabled(disable);
	}
}
