package com.invictrixrom.updater;

import android.content.res.AssetManager;
import android.os.AsyncTask;

import java.io.BufferedInputStream;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.io.FileInputStream;

//Sign boot.img
//Push boot.img to opposite partition

public class MagiskInstaller {

	private MagiskCallback callback;
	private Context context;

	public void setCallback(MagiskCallback callback) {
		this.callback = callback;
	}

	public MagiskInstaller(Context context) {
		this.context = context;
	}

	public void startDownload() {
		new MagiskDownloadTask(callback).execute();
	}

	public void installMagisk(String magiskPath, boolean postInstall) {
		new MagiskInstallTask(this.callback, postInstall).execute(magiskPath);
	}

	private class MagiskDownloadTask extends AsyncTask<Void, Integer, String> {
		private MagiskCallback callback;

		public MagiskDownloadTask(MagiskCallback callback) {
			this.callback = callback;
		}

		@Override
		protected String doInBackground(Void... params) {
			int count;
			try {
				File magiskDir = new File(context.createDeviceProtectedStorageContext().getFilesDir().getParent() + "/install");
				magiskDir.mkdir();
				String out = magiskDir.getAbsolutePath() + "/magisk.zip";
				URL url = new URL("https://tiny.cc/latestmagisk");

				URLConnection connection = url.openConnection();
				connection.connect();

				long contentLength = connection.getContentLengthLong();

				InputStream input = new BufferedInputStream(url.openStream());
				OutputStream output = new FileOutputStream(out);

				byte data[] = new byte[1024];
				long total = 0;
				while ((count = input.read(data)) != -1) {
					total += count;
					output.write(data, 0, count);
					publishProgress((int) (((float) total / (float) contentLength) * 100));
				}

				output.flush();
				output.close();
				input.close();

				return out;
			} catch (Exception e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			callback.magiskDownloadProgress(progress[0]);
		}

		@Override
		protected void onPostExecute(String outputFile) {
			callback.magiskDownloaded((new File(outputFile).exists()), outputFile);
		}

	}

	private class MagiskInstallTask extends AsyncTask<String, Integer, Boolean> {
		private MagiskCallback callback;
		private boolean postInstall;

		public MagiskInstallTask(MagiskCallback callback, boolean postInstall) {
			this.callback = callback;
			this.postInstall = postInstall;
		}

		private void extractMagisk(String magiskPath) {
			System.out.println("BLUG: extractMagisk(): magiskOut: " + new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout");
			try {
				File outDir = new File(new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout");
				outDir.mkdir();
				File outFile = new File(outDir.getAbsolutePath() + "/magiskboot");
				FileOutputStream magiskOut = new FileOutputStream(outFile);
				Utilities.extractFromZip(magiskPath, Utilities.getMagiskArch() + "/magiskboot", magiskOut);

				outFile = new File(outDir.getAbsolutePath() + "/magiskinit");
				magiskOut = new FileOutputStream(outFile);
				Utilities.extractFromZip(magiskPath, Utilities.getMagiskArch() + "/magiskinit", magiskOut);

				outFile = new File(outDir.getAbsolutePath() + "/boot_patch.sh");
				magiskOut = new FileOutputStream(outFile);
				Utilities.extractFromZip(magiskPath, "common/boot_patch.sh", magiskOut);

				outFile = new File(outDir.getAbsolutePath() + "/magisk.apk");
				magiskOut = new FileOutputStream(outFile);
				Utilities.extractFromZip(magiskPath, "common/magisk.apk", magiskOut);

				outFile = new File(outDir.getAbsolutePath() + "/util_functions.sh");
				magiskOut = new FileOutputStream(outFile);
				Utilities.extractFromZip(magiskPath, "common/util_functions.sh", magiskOut);

				outFile = new File(outDir.getAbsolutePath() + "/update-binary");
				magiskOut = new FileOutputStream(outFile);
				Utilities.extractFromZip(magiskPath, "META-INF/com/google/android/update-binary", magiskOut);
				System.out.println("BLUG: extractMagisk(): done");

			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println("BLUG: extractMagisk(): failed");
			}
		}

		private void modBootImage(String magiskPath) {
			System.out.println("BLUG: modBootImage(): magiskPath: " + magiskPath);
			Shell.runCommand("chmod 755 " + magiskPath + "/*");
			String currentSlot = Utilities.getSystemProperty(context.getString(R.string.slot_prop));
			if (postInstall) {
				if (currentSlot.equals("_b")) {
					currentSlot = "_a";
				} else {
					currentSlot = "_b";
				}
			}
			Utilities.pullBootimage(context.getString(R.string.boot_block_name) + currentSlot, magiskPath + "/boot.img");
			Shell.runCommand("cd " + magiskPath);

			boolean highcomp = false;

			Shell.runCommand("KEEPFORCEENCRYPT=false KEEPVERITY=false HIGHCOMP=" + highcomp + " sh " + magiskPath + "/update-binary indep " + magiskPath + "/boot_patch.sh " + magiskPath + "/boot.img");
			System.out.println("BLUG: modBootImage(): done");
		}

		private void signBootImage(String magiskPath) {
			System.out.println("BLUG: signBootImage(): magiskPath: " + magiskPath);
			File signed = new File(magiskPath + "/signed.img");
			AssetManager assets = context.getAssets();
			try (
						InputStream in = new FileInputStream(magiskPath + "/new-boot.img");
						OutputStream out = new BufferedOutputStream(new FileOutputStream(signed));
						InputStream keyIn = assets.open("private.key.pk8");
						InputStream certIn = assets.open("public.certificate.x509.pem")
			) {
				SignBoot.doSignature("/boot", in, out, keyIn, certIn);
			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println("BLUG: signBootImage(): failed");
			}
			System.out.println("BLUG: signBootImage(): done");
		}

		private void flashBoot(String magiskPath) {
			String currentSlot = Utilities.getSystemProperty(context.getString(R.string.slot_prop));
			if (postInstall) {
				if (currentSlot.equals("_b")) {
					currentSlot = "_a";
				} else {
					currentSlot = "_b";
				}
			}
			Utilities.pullBootimage(magiskPath + "/boot.img", context.getString(R.string.boot_block_name) + currentSlot);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			String magiskPath = params[0];

			publishProgress(R.string.extracting_magisk);
			extractMagisk(magiskPath);

			publishProgress(R.string.modifying_boot_image);
			modBootImage(new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout");

			boolean isSigned = false;
			try (InputStream in = new FileInputStream(new File(new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout/boot.img"))) {
				isSigned = SignBoot.verifySignature(in, null);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (isSigned) {
				publishProgress(R.string.signing_boot_image);
				signBootImage(new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout");

				Shell.runCommand("mv -f signed.img " + new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout/boot.img");
			} else {
				Shell.runCommand("mv -f new-boot.img " + new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout/boot.img");
			}
			Shell.closeShell();

			publishProgress(R.string.installing_boot_image);
			flashBoot(new File(magiskPath).getParentFile().getAbsolutePath() + "/magiskout");
			return true;
		}

		@Override
		protected void onProgressUpdate(Integer... status) {
			callback.magiskInstallStatusUpdate(status[0]);
		}

		@Override
		protected void onPostExecute(Boolean success) {
			callback.magiskInstallComplete(success);
		}

	}

}
