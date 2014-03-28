package what.whatandroid.torrentgroup;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.view.Window;
import android.widget.Toast;
import api.soup.MySoup;
import api.torrents.torrents.Torrents;
import what.whatandroid.R;
import what.whatandroid.announcements.AnnouncementsActivity;
import what.whatandroid.artist.ArtistActivity;
import what.whatandroid.callbacks.ViewArtistCallbacks;
import what.whatandroid.login.LoggedInActivity;
import what.whatandroid.profile.ProfileActivity;
import what.whatandroid.search.SearchActivity;
import what.whatandroid.settings.SettingsActivity;

/**
 * View information about a torrent group and the torrents in it
 */
public class TorrentGroupActivity extends LoggedInActivity implements ViewArtistCallbacks, DownloadDialog.DownloadDialogListener {
	/**
	 * Param to pass the torrent group id to be shown
	 */
	public final static String GROUP_ID = "what.whatandroid.GROUP_ID";
	private TorrentGroupFragment fragment;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_frame);
		setupNavDrawer();
		setTitle(getTitle());

		fragment = TorrentGroupFragment.newInstance(getIntent().getIntExtra(GROUP_ID, 1));
		FragmentManager manager = getSupportFragmentManager();
		manager.beginTransaction().add(R.id.container, fragment).commit();
	}

	@Override
	public void onLoggedIn(){
		fragment.onLoggedIn();
	}

	@Override
	public void onNavigationDrawerItemSelected(int position){
		if (navDrawer == null){
			return;
		}
		//Pass an argument to the activity telling it which to show?
		String selection = navDrawer.getItem(position);
		if (selection.equalsIgnoreCase(getString(R.string.announcements))){
			//Launch AnnouncementsActivity viewing announcements
			//For now both just return to the announcements view
			Intent intent = new Intent(this, AnnouncementsActivity.class);
			intent.putExtra(AnnouncementsActivity.SHOW, AnnouncementsActivity.ANNOUNCEMENTS);
			startActivity(intent);
		}
		else if (selection.equalsIgnoreCase(getString(R.string.blog))){
			//Launch AnnouncementsActivity viewing blog posts
			Intent intent = new Intent(this, AnnouncementsActivity.class);
			intent.putExtra(AnnouncementsActivity.SHOW, AnnouncementsActivity.BLOGS);
			startActivity(intent);
		}
		else if (selection.equalsIgnoreCase(getString(R.string.profile))){
			//Launch profile view activity
			Intent intent = new Intent(this, ProfileActivity.class);
			intent.putExtra(ProfileActivity.USER_ID, MySoup.getUserId());
			startActivity(intent);
		}
		else if (selection.equalsIgnoreCase(getString(R.string.torrents))){
			Intent intent = new Intent(this, SearchActivity.class);
			intent.putExtra(SearchActivity.SEARCH, SearchActivity.TORRENT);
			startActivity(intent);
		}
		else if (selection.equalsIgnoreCase(getString(R.string.artists))){
			Intent intent = new Intent(this, SearchActivity.class);
			intent.putExtra(SearchActivity.SEARCH, SearchActivity.ARTIST);
			startActivity(intent);
		}
		else if (selection.equalsIgnoreCase(getString(R.string.users))){
			Intent intent = new Intent(this, SearchActivity.class);
			intent.putExtra(SearchActivity.SEARCH, SearchActivity.USER);
			startActivity(intent);
		}
	}

	@Override
	public void viewArtist(int id){
		Intent intent = new Intent(this, ArtistActivity.class);
		intent.putExtra(ArtistActivity.ARTIST_ID, id);
		startActivity(intent);
	}

	@Override
	public void sendToPywa(Torrents t){
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		String host = preferences.getString(getString(R.string.key_pref_pywhat_host), "");
		String port = preferences.getString(getString(R.string.key_pref_pywhat_port), "");
		String pass = preferences.getString(getString(R.string.key_pref_pywhat_password), "");
		if (host.isEmpty() || port.isEmpty() || pass.isEmpty()){
			Toast.makeText(getApplicationContext(), "Please fill out your PyWA server information",
				Toast.LENGTH_LONG).show();
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
		}
		else {
			new SendToPyWA().execute(host, port, pass, t.getId().toString());
		}
	}

	@Override
	public void downloadToPhone(Torrents t){
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(t.getDownloadLink()));
		startActivity(intent);
	}

	/**
	 * Async task to instruct the user's PyWA server to download some torrent from the site
	 * params should be: 0: host, 1: port, 2: PyWA password, 3: torrent id
	 */
	private class SendToPyWA extends AsyncTask<String, Void, Boolean> {
		@Override
		protected Boolean doInBackground(String... params){
			String url = params[0] + ":" + params[1] + "/dl.pywa?pass=" + params[2]
				+ "&site=whatcd&id=" + params[3];
			try {
				String result = MySoup.scrapeOther(url);
				if (result.contains("success")){
					return true;
				}
			}
			catch (Exception e){
				e.printStackTrace();
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean status){
			if (status){
				Toast.makeText(TorrentGroupActivity.this, "Torrent sent", Toast.LENGTH_SHORT).show();
			}
			else {
				Toast.makeText(TorrentGroupActivity.this, "Failed to send torrent, check PyWA settings",
					Toast.LENGTH_SHORT).show();
			}
		}
	}
}