/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.titanium.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.view.TiDrawableReference;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.webkit.URLUtil;

@SuppressWarnings("deprecation")
public class TiFileHelper implements Handler.Callback
{
	private static final String TAG = "TiFileHelper";

	public static final String TI_DIR = "tiapp";
	public static final String TI_DIR_JS = "tijs";
	private static final String MACOSX_PREFIX = "__MACOSX";
	private static final String TI_RESOURCE_PREFIX = "ti:";
	
	private static final int MSG_NETWORK_URL = 100;

	public static final String RESOURCE_ROOT_ASSETS = "file:///android_asset/Resources";
	public static final String SD_CARD_PREFIX = "/sdcard/Ti.debug";
	
	protected Handler runtimeHandler = null;

	static HashMap<String, Integer> systemIcons;

	private SoftReference<Context> softContext;
	private TiNinePatchHelper nph;

	private ArrayList<File> tempFiles = new ArrayList<File>();
	
	private static HashSet<String> resourcePathCache;
	private static HashSet<String> foundResourcePathCache;
	private static HashSet<String> notFoundResourcePathCache;
	private static TiFileHelper _instance = null;
	
	public TiFileHelper(Context context)
	{
		softContext = new SoftReference<Context>(context);
		this.nph = new TiNinePatchHelper();
		if (resourcePathCache == null) {
			resourcePathCache = new HashSet<String>();
			foundResourcePathCache = new HashSet<String>();
			notFoundResourcePathCache = new HashSet<String>();
		}

		if (resourcePathCache == null) {
			resourcePathCache = new HashSet<String>();
			foundResourcePathCache = new HashSet<String>();
			notFoundResourcePathCache = new HashSet<String>();
		}

		synchronized(TI_DIR) {
			if (systemIcons == null) {
				systemIcons = new HashMap<String, Integer>();
				systemIcons.put("ic_menu_camera", android.R.drawable.ic_menu_camera);
				//systemIcons.put("ic_menu_compose", android.R.drawable.ic_menu_compose);
				systemIcons.put("ic_menu_search", android.R.drawable.ic_menu_search);
				systemIcons.put("ic_menu_add", android.R.drawable.ic_menu_add);
				systemIcons.put("ic_menu_delete", android.R.drawable.ic_menu_delete);
				//systemIcons.put("ic_menu_archive", android.R.drawable.ic_menu_archive);
				//systemIcons.put("ic_menu_stop", android.R.drawable.ic_menu_stop);
				//systemIcons.put("ic_menu_refresh", android.R.drawable.ic_menu_refresh);
				systemIcons.put("ic_media_play", android.R.drawable.ic_media_play);
				systemIcons.put("ic_media_ff", android.R.drawable.ic_media_ff);
				systemIcons.put("ic_media_pause", android.R.drawable.ic_media_pause);
				systemIcons.put("ic_media_rew", android.R.drawable.ic_media_rew);
				systemIcons.put("ic_menu_edit", android.R.drawable.ic_menu_edit);
				systemIcons.put("ic_menu_close_clear_cancel", android.R.drawable.ic_menu_close_clear_cancel);
				systemIcons.put("ic_menu_save", android.R.drawable.ic_menu_save);
				//systemIcons.put("ic_menu_mark", android.R.drawable.ic_menu_mark);
				//systemIcons.put("ic_menu_back", android.R.drawable.ic_menu_back);
				//systemIcons.put("ic_menu_forward", android.R.drawable.ic_menu_forward);
				systemIcons.put("ic_menu_help", android.R.drawable.ic_menu_help);
				//systemIcons.put("ic_menu_home", android.R.drawable.ic_menu_home);
				systemIcons.put("ic_media_next", android.R.drawable.ic_media_next);
				systemIcons.put("ic_menu_preferences", android.R.drawable.ic_menu_preferences);
				systemIcons.put("ic_media_previous", android.R.drawable.ic_media_previous);
				systemIcons.put("ic_menu_revert", android.R.drawable.ic_menu_revert);
				systemIcons.put("ic_menu_send", android.R.drawable.ic_menu_send);
				systemIcons.put("ic_menu_share", android.R.drawable.ic_menu_share);
				systemIcons.put("ic_menu_view", android.R.drawable.ic_menu_view);
				systemIcons.put("ic_menu_zoom", android.R.drawable.ic_menu_zoom);
			}
		}
	}

	private Handler getRuntimeHandler()
	{
		if (runtimeHandler == null) {
			runtimeHandler = new Handler(TiMessenger.getRuntimeMessenger().getLooper(), this);
		}
		return runtimeHandler;
	}

	/**
	 * Creates or retrieves the TiFileHelper instance.
	 * @return the TiFileHelper instance.
	 */
	public static TiFileHelper getInstance()
	{
		if (_instance == null) {
			_instance = new TiFileHelper(TiApplication.getInstance());
		}
		return _instance;
	}

	public InputStream openInputStream(String path, boolean report)
		throws IOException
	{
		InputStream is = null;

		Context context = softContext.get();
		if (context != null) {
			if (isTitaniumResource(path)) {
				String[] parts = path.split(":");
				if (parts.length != 3) {
					Log.w(TAG, "Malformed titanium resource url, resource not loaded: " + path);
					return null;
				}
				@SuppressWarnings("unused")
				String titanium = parts[0];
				String section = parts[1];
				String resid = parts[2];

				if (TI_RESOURCE_PREFIX.equals(section)) {
					is = TiFileHelper.class.getResourceAsStream("/org/appcelerator/titanium/res/drawable/" + resid + ".png");
				} else if ("Sys".equals(section)) {
				    Log.e(TAG, "Accessing Android system icons is deprecated. Instead copy to res folder.");
				    Integer id = systemIcons.get(resid);
				    if (id != null) {
				        is = Resources.getSystem().openRawResource(id);
				    } else {
				        Log.w(TAG, "Drawable not found for system id: " + path);
				    }
				} else {
					Log.e(TAG, "Unknown section identifier: " + section);
				}
			} else if (URLUtil.isNetworkUrl(path)) {
				if (TiApplication.isUIThread()) {
					is = (InputStream) TiMessenger.sendBlockingRuntimeMessage(getRuntimeHandler().obtainMessage(MSG_NETWORK_URL), path);
				} else {
					is = handleNetworkURL(path);
				}
			} else if (path.startsWith(RESOURCE_ROOT_ASSETS)) {
				int len = "file:///android_asset/".length();
				path = path.substring(len);
				boolean found = false;
				
				if (foundResourcePathCache.contains(path)) {
					found = true;
				} else if (!notFoundResourcePathCache.contains(path)) {
					String base = path.substring(0, path.lastIndexOf("/"));
					
					synchronized(resourcePathCache) {
						if (!resourcePathCache.contains(base)) {
							String[] paths = context.getAssets().list(base);
							for(int i = 0; i < paths.length; i++) {
								foundResourcePathCache.add(base + '/' + paths[i]);
							}
							resourcePathCache.add(base);
							if (foundResourcePathCache.contains(path)) {
								found = true;
							}
						}
						if (!found) {
							notFoundResourcePathCache.add(path);
						}
					}
				}
				if (found) {
					is = context.getAssets().open(path);
				}
			} else if (path.startsWith(SD_CARD_PREFIX)) {
				is = new FileInputStream(new File(path));
			} else if (URLUtil.isFileUrl(path)) {
				URL u = new URL(path);
				is = u.openStream();
			} else {
				path = joinPaths("Resources", path);
				is = context.getAssets().open(path);
			}
		}

		return is;
	}

	private InputStream handleNetworkURL(String path) throws IOException
	{
		InputStream is = null;
		try {
			URI uri = new URI(path);
			if (TiResponseCache.peek(uri)) {
				InputStream stream = TiResponseCache.openCachedStream(uri);
				if (stream != null) {
					// Fallback to actual download when null
					return stream;
				}
			}
		} catch (URISyntaxException uriException) {
		}

		URL u = new URL(path);
		InputStream lis = u.openStream();
		ByteArrayOutputStream bos = null;
		try {
			bos = new ByteArrayOutputStream(8192);
			int count = 0;
			byte[] buf = new byte[8192];

			while((count = lis.read(buf)) != -1) {
				bos.write(buf, 0, count);
			}

			is = new ByteArrayInputStream(bos.toByteArray());

		} catch (IOException e) {

			Log.e(TAG, "Problem pulling image data from " + path, e);
			throw e;
		} finally {
			if (lis != null) {
				try {
					lis.close();
					lis = null;
				} catch (Exception e) {
					// Ignore
				}
			}
			if (bos != null) {
				try {
					bos.close();
					bos = null;
				} catch (Exception e) {
					// ignore
				}
			}
		}
		return is;
	}
	
	/**
	 * This is a wrapper method.
	 * Refer to {@link #loadDrawable(String, boolean, boolean)} for more details.
	 * @param path  url of the Drawable
	 * @param report  this is not being used.
	 * @return a Drawable instance.
	 */
	public Drawable loadDrawable(String path, boolean report) {
		return loadDrawable(path, report, false);
	}
	
	public Drawable loadDrawable(String path, boolean report, boolean checkForNinePatch)
	{
		return loadDrawable(path, report, checkForNinePatch, true);
	}

	/**
	 * This method creates a Drawable given the bitmap's path, and converts it to a NinePatch Drawable
	 * if checkForNinePatch param is true.
	 * @param path  the path/url of the Drawable 
	 * @param report  this is not being used. 
	 * @param checkForNinePatch  a boolean to determine whether the returning Drawable is a NinePatch Drawable.
	 * @param densityScaled  a boolean to determine whether the returning Drawable is scaled based on device density.
	 * @return  a Drawable instance.
	 */
	public Drawable loadDrawable(String path, boolean report, boolean checkForNinePatch, boolean densityScaled)
	{
		// Validate.
		if ((path == null) || (path.length() <= 0)) {
			return null;
		}

		// Create a new drawable wrapping the given image.
		Drawable d = null;
		try {
			// Determine if we need to process the referenced image as a 9-patch.
			// Note: Titanium does not require the ".9.png" suffix on an image file.
			boolean isNinePatchProcessingRequested = false;
			if (checkForNinePatch && !URLUtil.isNetworkUrl(path)) {
				if (path.endsWith(".png")) {
					if (!path.endsWith(".9.png")) {
						// If the given file path does not end with ".9.png",
						// then append it and check if a matching file exists.
						String apath = path.substring(0, path.lastIndexOf(".")) + ".9.png";
						InputStream is = null;
						try {
							is = openInputStream(apath, false);
							if (is != null) {
								path = apath;
							}
						} catch (IOException e) {
							Log.d(TAG, "path not found: " + apath);
						}
						finally {
							if (is != null) {
								try { is.close(); }
								catch (Exception ex) {}
							}
						}
					}
				}
				isNinePatchProcessingRequested = true;
			}

			// Load the image via "TiDrawableReference" which will auto-downscale the image
			// if is too large to be displayed by an Android view. (Can happen with large camera photos.)
			TiDrawableReference drawableLoader = TiDrawableReference.fromUrl(
					TiApplication.getInstance().getCurrentActivity(), path);
			if (drawableLoader != null) {
				if (densityScaled) {
					d = drawableLoader.getDensityScaledDrawable();
				}
				else {
					d = drawableLoader.getDrawable();
				}
				if (isNinePatchProcessingRequested && (d != null)) {
					d = this.nph.process(d);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to load drawable for path: " + path, e);
		}

		return d;
	}

	public boolean isTitaniumResource(String s) {
		boolean result = false;
		if (s != null && s.startsWith(TI_RESOURCE_PREFIX)) {
			result = true;
		}

		return result;
	}

	public Drawable getTitaniumResource(Context context, String s) {
		Drawable d = null;

		if (isTitaniumResource(s)) {

			String[] parts = s.split(":");
			if (parts.length != 2) {
				Log.w(TAG, "Malformed titanium resource url, resource not loaded: " + s);
				return null;
			}
			String section = parts[0];
			String resid = parts[1];

			if (TI_RESOURCE_PREFIX.equals(section)) {
				InputStream is = null;
				try {
					is = TiFileHelper.class.getResourceAsStream("/org/appcelerator/titanium/res/drawable/" + resid + ".png");
					d = new BitmapDrawable(is);
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							// Ignore
						}
					}
				}
			} else if ("Sys".equals(section)) {
			    Log.e(TAG, "Accessing Android system icons is deprecated. Instead copy to res folder.");
			    Integer id = systemIcons.get(resid);
			    if (id != null) {
			        d = Resources.getSystem().getDrawable(id);
			    } else {
			        Log.w(TAG, "Drawable not found for system id: " + s);
			    }
			} else {
				Log.e(TAG, "Unknown section identifier: " + section);
			}

		} else {
			Log.w(TAG, "Ignoring non titanium resource string id: " + s);
		}

		return d;
	}

	public String getResourceUrl(String path)
	{
        return joinPaths(RESOURCE_ROOT_ASSETS, path);
	}

	public String joinPaths(String pre, String post) {
		StringBuilder sb = new StringBuilder();
		sb.append(pre);
		if (pre.endsWith("/") && !post.startsWith("/")) {
			sb.append(post);
		} else if (!pre.endsWith("/") && post.startsWith("/")) {
			sb.append(post);
		} else if (!pre.endsWith("/") && !post.startsWith("/")) {
			sb.append("/").append(post);
		} else {
			sb.append(post.substring(1));
		}
		return sb.toString();
	}

	public void deployFromAssets(File dest)
		throws IOException
	{
		Context ctx = softContext.get();
		if (ctx != null) {
			ArrayList<String> paths = new ArrayList<String>();
			AssetManager am = ctx.getAssets();
			walkAssets(am, "", paths);

			// TODO clean old dir
			wipeDirectoryTree(dest);

			// copy from assets to dest dir
			BufferedInputStream bis = null;
			FileOutputStream fos = null;
			byte[] buf = new byte[8096];
			try {
				int len = paths.size();
				for(int i = 0; i < len; i++) {
					String path = paths.get(i);
					File f = new File(path);
					if(f.getName().indexOf(".") > -1) {
						bis = new BufferedInputStream(am.open(path), 8096);
						File df = new File(dest, path);
						Log.d(TAG, "Copying to: " + df.getAbsolutePath(), Log.DEBUG_MODE);
						fos = new FileOutputStream(df);

						int read = 0;
						while((read = bis.read(buf)) != -1) {
							fos.write(buf, 0, read);
						}

						bis.close();
						bis = null;
						fos.close();
						fos = null;
					} else {
						File d = new File(dest,path);
						Log.d(TAG, "Creating directory: " + d.getAbsolutePath());
						d.mkdirs();
					}
				}
			} finally {
				if (bis != null) {
					try {
						bis.close();
					} catch (IOException e) {
						//Ignore
					}
					bis = null;
				}
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException e) {
						//Ignore
					}
					fos = null;
				}
			}
		}
	}

	public void deployFromZip(File fname, File dest)
		throws IOException
	{
		wipeDirectoryTree(dest);

		ZipInputStream zis = null;
		ZipEntry ze = null;
		byte[] buf = new byte[8096];

		try {
			// See if we need to strip off parent dir.
			zis = getZipInputStream(new FileInputStream(fname));
			String root = getRootDir(zis);
			int rootLen = root.length();
			zis.close();

			Log.d(TAG, "Zip file root: " + root, Log.DEBUG_MODE);

			// Process the file
			zis = getZipInputStream(new FileInputStream(fname));
			while((ze = zis.getNextEntry()) != null) {
				String name = ze.getName();
				if (name.startsWith(MACOSX_PREFIX)) {
					zis.closeEntry();
					continue;
				}

				name = name.substring(rootLen);

				if(name.length() > 0) {
					Log.d(TAG, "Extracting " + name, Log.DEBUG_MODE);
					if (ze.isDirectory()) {
						File d = new File(dest, name);
						d.mkdirs();
						Log.d(TAG, "Created directory " + d.toString(), Log.DEBUG_MODE);
						d = null;
					} else {
						FileOutputStream fos = null;
						try {
							fos = new FileOutputStream(new File(dest,name));
							int read = 0;
							while((read = zis.read(buf)) != -1) {
								fos.write(buf, 0, read);
							}
						} finally {
							if (fos != null) {
								try {
									fos.close();
								} catch (Throwable t) {
									//Ignore
								}
							}
						}
					}
				}

				zis.closeEntry();
			}
		} finally {
			if (zis != null) {
				try {
					zis.close();
				}catch (Throwable t) {
					//Ignore
				}
			}
		}
	}

	public void wipeDirectoryTree(File path)
	{
		TreeSet<String> dirs = new TreeSet<String>(new Comparator<String>(){

			public int compare(String o1, String o2) {
				return o1.compareTo(o2) * -1;
			}});

		wipeDirectoryTree(path, dirs);

		Iterator<String> d = dirs.iterator();
		while(d.hasNext()) {
			String fn = d.next();
			File f = new File(fn);
			Log.d(TAG, "Deleting Dir: " + f.getAbsolutePath(), Log.DEBUG_MODE);
			f.delete();
		}
	}

	public File getTempFile(String suffix, boolean destroyOnExit)
		throws IOException
	{
		File result = null;
		Context context = softContext.get();

		if(context != null) {
			result = getTempFile(context.getCacheDir(), suffix, destroyOnExit);
		}
		return result;
	}

	public File getTempFile(File dir, String suffix, boolean destroyOnExit)
		throws IOException
	{
		File result = null;
		Context context = softContext.get();
		if (context != null) {
			if (!dir.exists()) {
				Log.w(TAG, "getTempFile: Directory '" + dir.getAbsolutePath()
					+ "' does not exist. Call to File.createTempFile() will fail.");
			}
			result = File.createTempFile("tia", suffix, dir);

			if (destroyOnExit) {
				tempFiles.add(result);
			}
		}
		return result;
	}

	public File getTempFileFromInputStream(InputStream is, String suffix, boolean destroyOnExit)
	{
		try {
			File tempFile = getTempFile(suffix, destroyOnExit);

			if (tempFile.exists()) {
				byte[] bytes = new byte[1024];
				int length;
				FileOutputStream os = new FileOutputStream(tempFile);

				while ((length = is.read(bytes)) != -1) {
					os.write(bytes, 0, length);
				}
				os.close();
			}
			return tempFile;

		} catch (FileNotFoundException e) {
			Log.w(TAG, "Could not find temp file: " + suffix);
		} catch (IOException e) {
			Log.w(TAG, "Error occurred while creating output stream from temp file: " + suffix);
		}
		return null;
	}

	// Destroys all temporary files that have been created.
	// This is called when the application is exited/destroyed.
	public void destroyTempFiles()
	{
		for (File tempFile : tempFiles) {
			tempFile.delete();
		}

		tempFiles.clear();
	}

	/**
	 * Creates/retrieves a data directory in which the application can place its own custom data files.
	 * @param privateStorage  determines the location of the data directory. If this is true, the location is internal(app-data://),
	 * and external (SD) otherwise.
	 * @return  the data directory.
	 */
	public File getDataDirectory(boolean privateStorage)
	{
		File f = null;
		Context context = softContext.get();
		if (context != null) {

			if (privateStorage)
			{
				f = context.getDir("appdata",0);
			}
			else
			{
				File storageDir = Environment.getExternalStorageDirectory();
				f = new File(storageDir, context.getPackageName());
				if (!f.exists())
				{
					f.mkdirs();
				}
			}
		}
		return f;
	}
	private void wipeDirectoryTree(File path, SortedSet<String> dirs) {
		File[] files = path.listFiles();
		if (files != null) {
			int len = files.length;
			for (int i = 0; i < len; i++) {
				File f = files[i];
				if (f.isDirectory()) {
					dirs.add(f.getAbsolutePath());
					wipeDirectoryTree(f, dirs);
				} else {
					Log.d(TAG, "Deleting File: " + f.getAbsolutePath(), Log.DEBUG_MODE);
					f.delete();
				}
			}
		}
	}

	private void walkAssets(AssetManager am, String path, ArrayList<String> paths) throws IOException
	{
		if (titaniumPath(path)) {
			String[] files = am.list(path);
			if (files.length > 0) {
				for(int i = 0; i < files.length; i++) {
					String newPath = files[i];
					String todo = path;
					if (path.length() > 0) {
						todo = todo + "/" + newPath;
					} else {
						todo = newPath;
					}
					if (titaniumPath(todo)) {
						//Log.e(LCAT, todo);
						paths.add(todo);
						walkAssets(am, todo, paths);
					}
				}
			}
		}
	}

	private boolean titaniumPath(String path) {
		return path == "" || path.equals("tiapp.xml") || path.startsWith("Resources");
	}

	private ZipInputStream getZipInputStream(InputStream is)
		throws FileNotFoundException, IOException
	{
		return new ZipInputStream(is);
	}

	private String getRootDir(ZipInputStream zis)
		throws FileNotFoundException, IOException
	{
		String root = "";

		ZipEntry ze = null;
		while((ze = zis.getNextEntry()) != null) {
			String name = ze.getName();
			zis.closeEntry();

			if (name.startsWith(MACOSX_PREFIX)) {
				continue;
			} else {
				if(name.indexOf("tiapp.xml") > -1) {
					String [] segments = name.split("\\/");
					if (segments.length == 2) {
						root = segments[0] + "/";
						break;
					} else if (segments.length == 1) {
						break;
					}
				}
			}
		}
		return root;
	}

	public boolean handleMessage(Message msg)
	{		
		switch (msg.what) {
			case MSG_NETWORK_URL:
				AsyncResult result = (AsyncResult) msg.obj;
				try {
					result.setResult(handleNetworkURL(TiConvert.toString(result.getArg())));
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
		}
		return false;
	}
}

