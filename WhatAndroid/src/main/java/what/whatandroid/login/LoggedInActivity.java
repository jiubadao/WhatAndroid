package what.whatandroid.login;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import api.soup.MySoup;
import what.whatandroid.R;
import what.whatandroid.callbacks.OnLoggedInCallback;
import what.whatandroid.callbacks.SetTitleCallback;
import what.whatandroid.callbacks.ShowHiddenTagListener;
import what.whatandroid.comments.dialogs.HiddenTextDialog;
import what.whatandroid.errors.ErrorLogger;
import what.whatandroid.errors.ErrorReporterService;
import what.whatandroid.navdrawer.NavigationDrawerFragment;
import what.whatandroid.settings.SettingsActivity;
import what.whatandroid.settings.SettingsFragment;
import what.whatandroid.updater.UpdateBroadcastReceiver;
import what.whatandroid.updater.UpdateService;
import what.whatandroid.views.ImageDialog;

/**
 * Parent activity for ones that require the user to be logged in. If the user
 * enters a LoggedInActivity it will try to load their saved cookie, or if no
 * cookie is found will kick them to the login activity.
 */
public abstract class LoggedInActivity extends AppCompatActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks,
	SetTitleCallback, OnLoggedInCallback, ShowHiddenTagListener {

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
	 * Setup the error logger and run the error reporting and update checking services
	 */
	public static void launchServices(Context context){
		ErrorLogger reporter = new ErrorLogger(context);
		Thread.setDefaultUncaughtExceptionHandler(reporter);

		Intent checkReports = new Intent(context, ErrorReporterService.class);
		context.startService(checkReports);

		//Only set the periodic updater if it's enabled and we haven't already set it
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		if (!preferences.getBoolean(context.getString(R.string.key_pref_disable_updater), false)){
			Intent updater = new Intent(context, UpdateBroadcastReceiver.class);
			PendingIntent pending = PendingIntent.getBroadcast(context, 2, updater, PendingIntent.FLAG_NO_CREATE);
			if (pending == null){
				pending = PendingIntent.getBroadcast(context, 2, updater, 0);
				AlarmManager alarmMgr = (AlarmManager) context.getSystemService(ALARM_SERVICE);
				alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, AlarmManager.INTERVAL_DAY,
					AlarmManager.INTERVAL_DAY, pending);
			}
		}
		//If the periodic checker is disabled we do still want to autocheck occasionally
		else {
			Intent checkUpdates = new Intent(context, UpdateService.class);
			context.startService(checkUpdates);
		}
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
		navDrawer = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
		navDrawer.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));
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
			if (cookie == null){
				Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show();
				Intent intent = new Intent(this, LoginActivity.class);
				intent.putExtra(LoginActivity.LOGIN_REQUEST, true);
				startActivityForResult(intent, 0);
			}
			else {
				launchServices(this);
				loginTask = new Login();
				loginTask.execute();
			}
		}
		//If we're already logged in tell the activity it's ok to start loading if we haven't already told them
		else if (!calledLogin){
			calledLogin = true;
			onLoggedIn();
			navDrawer.updateNotifications(PreferenceManager.getDefaultSharedPreferences(this));
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
		//If we cancelled logging in then they're probably trying to quit out
		else {
			finish();
		}
	}

	@Override
	protected void onPause(){
		calledLogin = false;
		if (loginTask != null){
			loginTask.cancel(true);
		}
		if (logoutTask != null){
			logoutTask.dismissDialog();
		}
		super.onPause();
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

	@Override
	public void showText(String title, String text){
		HiddenTextDialog dialog = HiddenTextDialog.newInstance(title, text);
		dialog.show(getSupportFragmentManager(), "dialog");
	}

	@Override
	public void showImage(String url){
		ImageDialog dialog = ImageDialog.newInstance(url);
		dialog.show(getSupportFragmentManager(), "dialog");
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
		protected void onPostExecute(Status status){
			super.onPostExecute(status);
			if (status == Status.OK && !calledLogin){
				calledLogin = true;
				onLoggedIn();
			}
			else if (status == Status.COOKIE_EXPIRED){
				Toast.makeText(LoggedInActivity.this, "Cookie expired, please login", Toast.LENGTH_LONG).show();
				Intent intent = new Intent(LoggedInActivity.this, LoginActivity.class);
				intent.putExtra(LoginActivity.LOGIN_REQUEST, true);
				startActivityForResult(intent, 0);
			}
			else {
				Toast.makeText(LoggedInActivity.this, "Could not login", Toast.LENGTH_LONG).show();
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
				.apply();

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
