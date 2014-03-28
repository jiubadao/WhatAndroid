package what.whatandroid.login;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import api.soup.MySoup;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import what.whatandroid.NavigationDrawerFragment;
import what.whatandroid.R;
import what.whatandroid.callbacks.OnLoggedInCallback;
import what.whatandroid.callbacks.SetTitleCallback;
import what.whatandroid.errors.ErrorLogger;
import what.whatandroid.errors.ErrorReporterService;
import what.whatandroid.settings.SettingsActivity;
import what.whatandroid.settings.SettingsFragment;
import what.whatandroid.updater.UpdateService;

/**
 * Parent activity for ones that require the user to be logged in. If the user
 * enters a LoggedInActivity it will try to load their saved cookie, or if no
 * cookie is found will kick them to the login activity.
 */
public abstract class LoggedInActivity extends ActionBarActivity
	implements NavigationDrawerFragment.NavigationDrawerCallbacks, SetTitleCallback, OnLoggedInCallback {

	//TODO: Developers put your local Gazelle install IP here instead of testing on the live site
	//I recommend setting up with Vagrant: https://github.com/dr4g0nnn/VagrantGazelle
	//public static final String SITE = "192.168.1.125:8080/";
	public static final String SITE = "what.cd";
	protected NavigationDrawerFragment navDrawer;
	/**
	 * Used to store the last screen title, for use in restoreActionBar
	 */
	private CharSequence title;
	/**
	 * Prevent calling the onLoggedIn method multiple times
	 */
	private boolean calledLogin = false;
	/**
	 * Login and logout tasks so we can dismiss the dialogs if activity is paused ie. user changes orientation or such
	 */
	private Login loginTask;
	private LogoutTask logoutTask;

	/**
	 * Initialize MySoup so that we can start making API requests
	 */
	public static void initSoup(){
		MySoup.setSite(SITE, true);
		MySoup.setUserAgent("WhatAndroid Android");
	}

	/**
	 * Initialize universal image loader
	 *
	 * @param context context to get the application context from
	 */
	public static void initImageLoader(Context context){
		//Setup Universal Image loader global config
		DisplayImageOptions options = new DisplayImageOptions.Builder()
			.cacheOnDisc(true)
			.cacheInMemory(true)
			.build();
		ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(context.getApplicationContext())
			.defaultDisplayImageOptions(options)
			.denyCacheImageMultipleSizesInMemory()
			.memoryCache(new LruMemoryCache(5 * 512 * 512))
			.discCacheExtraOptions(512, 512, Bitmap.CompressFormat.JPEG, 75, null)
			.discCacheSize(50 * 512 * 512)
			.build();
		ImageLoader.getInstance().init(config);
	}

	/**
	 * Setup the error logger and run the error reporting and update checking services
	 */
	public static void launchServices(Context context){
		ErrorLogger reporter = new ErrorLogger(context.getApplicationContext());
		Thread.setDefaultUncaughtExceptionHandler(reporter);

		Intent checkReports = new Intent(context, ErrorReporterService.class);
		context.startService(checkReports);

		Intent checkUpdates = new Intent(context, UpdateService.class);
		context.startService(checkUpdates);
	}

	@Override
	protected void onResume(){
		super.onResume();
		checkLoggedIn();
	}

	/**
	 * Setup the nav drawer and title, should be called after setting content view
	 */
	protected void setupNavDrawer(){
		navDrawer = (NavigationDrawerFragment)getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
		navDrawer.setUp(R.id.navigation_drawer, (DrawerLayout)findViewById(R.id.drawer_layout));
		title = getTitle();
	}

	/**
	 * Check if the user is logged in, if they aren't kick them to the login activity
	 */
	private void checkLoggedIn(){
		if (!MySoup.isLoggedIn()){
			//If don't have the user's information we need to kick them to the login activity
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			String cookie = preferences.getString(SettingsFragment.USER_COOKIE, null);
			String username = preferences.getString(SettingsFragment.USER_NAME, null);
			String password = preferences.getString(SettingsFragment.USER_PASSWORD, null);
			if (cookie == null || username == null || password == null){
				Intent intent = new Intent(this, LoginActivity.class);
				intent.putExtra(LoginActivity.LOGIN_REQUEST, true);
				startActivityForResult(intent, 0);
			}
			else {
				initSoup();
				initImageLoader(this);
				launchServices(this);
				loginTask = new Login();
				loginTask.execute(username, password);
			}
		}
		//If we're already logged in tell the activity it's ok to start loading if we haven't already told them
		else if (!calledLogin){
			calledLogin = true;
			onLoggedIn();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		if (resultCode == Activity.RESULT_OK){
			//Alert the activity it's ok to start loading if we haven't already
			if (!calledLogin){
				calledLogin = true;
				onLoggedIn();
			}
		}
	}

	@Override
	protected void onPause(){
		super.onPause();
		calledLogin = false;
		if (loginTask != null){
			loginTask.dismissDialog();
		}
		if (logoutTask != null){
			logoutTask.dismissDialog();
		}
	}

	@Override
	public void setTitle(String t){
		title = t;
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null){
			actionBar.setTitle(title);
		}
	}

	public void restoreActionBar(){
		ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setTitle(title);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		if (navDrawer == null || !navDrawer.isDrawerOpen()){
			getMenuInflater().inflate(R.menu.what_android, menu);
			restoreActionBar();
			return true;
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		Intent intent;
		switch (item.getItemId()){
			case R.id.action_settings:
				intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.action_logout:
				logoutTask = new LogoutTask();
				logoutTask.execute();
				return true;
			case R.id.action_feedback:
				intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "whatcdandroid@gmail.com", null));
				intent.putExtra(Intent.EXTRA_SUBJECT, "WhatAndroid Feedback");
				startActivity(Intent.createChooser(intent, "Send email"));
				return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Login async task, takes user's username and password and logs them
	 * into the site via the api library
	 */
	private class Login extends LoginTask {

		public Login(){
			super(LoggedInActivity.this);
		}

		@Override
		protected void onPostExecute(Boolean status){
			super.onPostExecute(status);
			if (status && !calledLogin){
				calledLogin = true;
				onLoggedIn();
			}
			else {
				//kick them to login activity?
				Toast.makeText(getApplicationContext(), "Login failed, check username and password", Toast.LENGTH_LONG).show();
				Intent intent = new Intent(LoggedInActivity.this, LoginActivity.class);
				intent.putExtra(LoginActivity.LOGIN_REQUEST, true);
				startActivityForResult(intent, 0);
			}
		}
	}

	/**
	 * Async task for logging out the user
	 */
	private class LogoutTask extends AsyncTask<Void, Void, Boolean> {
		private ProgressDialog dialog;

		@Override
		protected void onPreExecute(){
			dialog = new ProgressDialog(LoggedInActivity.this);
			dialog.setIndeterminate(true);
			dialog.setMessage("Logging out...");
			dialog.show();
		}

		/**
		 * Once we've logged out clear the saved cookie, name and password and head to the home screen
		 */
		@Override
		protected void onPostExecute(Boolean status){
			if (dialog.isShowing()){
				dialog.dismiss();
			}
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LoggedInActivity.this);
			preferences.edit()
				.remove(SettingsFragment.USER_COOKIE)
				.remove(SettingsFragment.USER_NAME)
				.remove(SettingsFragment.USER_PASSWORD)
				.commit();

			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_HOME);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}

		@Override
		protected Boolean doInBackground(Void... params){
			Boolean status = false;
			try {
				status = MySoup.logout("logout.php");
			}
			catch (Exception e){
				e.printStackTrace();
			}
			return status;
		}

		public void dismissDialog(){
			if (dialog.isShowing()){
				dialog.dismiss();
			}
		}
	}
}