

package org.strongswan.android.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.R;
import org.strongswan.android.data.ManagedConfiguration;
import org.strongswan.android.data.ManagedConfigurationService;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnProfileSource;
import org.strongswan.android.logic.StrongSwanApplication;
import org.strongswan.android.ui.adapter.VpnProfileAdapter;
import org.strongswan.android.utils.Constants;
import org.strongswan.android.utils.Utils;
import org.strongswan.android.v2ray.V2RayProfileListFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class VpnProfileListFragment extends Fragment implements MenuProvider
{
	private static final String SELECTED_KEY = "SELECTED";

	private List<VpnProfile> mVpnProfiles;
	private VpnProfileDataSource mDataSource;
	private VpnProfileAdapter mListAdapter;
	private ListView mListView;
	private OnVpnProfileSelectedListener mListener;
	private Set<Integer> mSelected;
	private boolean mReadOnly;

	private ManagedConfigurationService mManagedConfigurationService;
private final androidx.activity.result.ActivityResultLauncher<String> mExportLauncher =
	registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
		uri -> {
			if (uri == null) return;
			java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
				try
				{
					int n = ProfileBackup.export(requireContext(), uri);
					if (getActivity() != null)
					{
						getActivity().runOnUiThread(() ->
							Toast.makeText(getActivity(), getString(R.string.backup_done, n), Toast.LENGTH_LONG).show());
					}
				}
				catch (Exception e)
				{
					if (getActivity() != null)
					{
						getActivity().runOnUiThread(() ->
							Toast.makeText(getActivity(), getString(R.string.backup_failed, e.getMessage()), Toast.LENGTH_LONG).show());
					}
				}
			});
		});

private final androidx.activity.result.ActivityResultLauncher<String[]> mImportLauncher =
	registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
		uri -> {
			if (uri == null) return;
			java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
				try
				{
					int n = ProfileBackup.restore(requireContext(), uri);
					if (getActivity() != null)
					{
						getActivity().runOnUiThread(() ->
							Toast.makeText(getActivity(), getString(R.string.restore_done, n), Toast.LENGTH_LONG).show());
					}
				}
				catch (Exception e)
				{
					if (getActivity() != null)
					{
						getActivity().runOnUiThread(() ->
							Toast.makeText(getActivity(), getString(R.string.backup_failed, e.getMessage()), Toast.LENGTH_LONG).show());
					}
				}
			});
		});
	private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> mAppFilterLauncher =
	registerForActivityResult(
		new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
		result -> {
			if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null)
			{
				return;
			}
			java.util.ArrayList<String> list = result.getData().getStringArrayListExtra(
				org.strongswan.android.data.VpnProfileDataSource.KEY_SELECTED_APPS_LIST);
			java.util.HashSet<String> set = new java.util.HashSet<>();
			if (list != null)
			{
				set.addAll(list);
			}
			requireContext().getSharedPreferences("app_filter", android.content.Context.MODE_PRIVATE)
				.edit()
				.putStringSet("packages", set)
				.apply();
			Toast.makeText(getActivity(), R.string.app_filter_saved, Toast.LENGTH_LONG).show();
		});
	private final BroadcastReceiver mProfilesChanged = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String uuid;
			String[] uuids;

			if ((uuid = intent.getStringExtra(Constants.VPN_PROFILES_SINGLE)) != null)
			{
				VpnProfile profile = mDataSource.getVpnProfile(uuid);
				if (profile != null)
				{	/* in case this was an edit, we remove it first */
					mVpnProfiles.remove(profile);
					mVpnProfiles.add(profile);
					mListAdapter.notifyDataSetChanged();
				}
			}
			else if ((uuids = intent.getStringArrayExtra(Constants.VPN_PROFILES_MULTIPLE)) != null)
			{
				for (final String id : uuids)
				{
					final Iterator<VpnProfile> profiles = mVpnProfiles.iterator();
					while (profiles.hasNext())
					{
						final VpnProfile profile = profiles.next();
						if (Objects.equals(profile.getUUID().toString(), id))
						{	/* in case this was an edit, we remove it first */
							profiles.remove();
							break;
						}
					}

					VpnProfile profile = mDataSource.getVpnProfile(id);
					if (profile != null)
					{
						mVpnProfiles.add(profile);
					}
				}
				mListAdapter.notifyDataSetChanged();
			}
		}
	};

	/**
	 * The activity containing this fragment should implement this interface
	 */
	public interface OnVpnProfileSelectedListener
	{
		void onVpnProfileSelected(VpnProfile profile);
	}

	@Override
	public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState)
	{
		super.onInflate(context, attrs, savedInstanceState);
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Fragment);
		mReadOnly = a.getBoolean(R.styleable.Fragment_read_only, false);
		a.recycle();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.profile_list_fragment, null);

		mListView = view.findViewById(R.id.profile_list);
		mListView.setAdapter(mListAdapter);
		mListView.setEmptyView(view.findViewById(R.id.profile_list_empty));
        mListView.setOnItemClickListener(mVpnProfileClicked);

       View emptyBtn = view.findViewById(R.id.empty_fetch_servers);
       if (emptyBtn instanceof android.widget.Button)
       {
        org.strongswan.android.ui.widget.UiStyle.fetchButton((android.widget.Button) emptyBtn);
       }
       if (emptyBtn != null)
       {
        emptyBtn.setOnClickListener(v -> showFetchDialog());
         }

		// FAB for get server when list is not empty
		try {
			android.view.ViewGroup root = (android.view.ViewGroup) view;
			android.content.Context fabContext = new android.view.ContextThemeWrapper(
				requireContext(), com.google.android.material.R.style.Theme_MaterialComponents_DayNight);
			com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fab =
				new com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(fabContext);
			fab.setText(R.string.awg_get_server);
			fab.setIconResource(R.drawable.ic_add);
			fab.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#04210F")));
			fab.setTextColor(android.graphics.Color.parseColor("#04210F"));
			fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4ADE80")));
			fab.setOnClickListener(v -> showFetchDialog());
			android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			flp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
			int m = (int) (16 * getResources().getDisplayMetrics().density);
			flp.setMargins(m, m, m, m);
			root.addView(fab, flp);
			mListView.setPadding(0, 0, 0, (int) (88 * getResources().getDisplayMetrics().density));
			// hide FAB when empty (empty view shows the button)
			mListView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
				boolean empty = mListAdapter == null || mListAdapter.getCount() == 0;
				fab.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
			});
		} catch (Exception ignored) {}

		Utils.applyWindowInsetsAsPaddingForLists(mListView);

		if (!mReadOnly)
		{
			requireActivity().addMenuProvider(this, getViewLifecycleOwner());
			mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
			mListView.setMultiChoiceModeListener(mVpnProfileSelected);
		}
		view.post(() -> checkUpdate(true));
		return view;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args != null)
		{
			mReadOnly = args.getBoolean("read_only", mReadOnly);
		}

		if (!mReadOnly)
		{
			ArrayList<Integer> selected = null;
			if (savedInstanceState != null)
			{
				selected = savedInstanceState.getIntegerArrayList(SELECTED_KEY);
			}
			mSelected = selected != null ? new HashSet<>(selected) : new HashSet<>();
		}

		mDataSource = new VpnProfileSource(this.getActivity());
		mDataSource.open();

		mManagedConfigurationService = StrongSwanApplication.getInstance().getManagedConfigurationService();

		/* cached list of profiles used as backend for the ListView */
		mVpnProfiles = mDataSource.getAllVpnProfiles();

		mListAdapter = new VpnProfileAdapter(getActivity(), R.layout.profile_list_item, mVpnProfiles);
		mListAdapter.setActionListener(new VpnProfileAdapter.ActionListener()
		{
			@Override
			public void onEdit(VpnProfile profile)
			{
				Intent edit = new Intent(getActivity(), VpnProfileDetailActivity.class);
				edit.putExtra(VpnProfileDataSource.KEY_UUID, profile.getUUID().toString());
				startActivity(edit);
			}

			@Override
			public void onShare(VpnProfile profile)
			{
				shareProfile(profile);
			}

			@Override
			public void onDelete(final VpnProfile profile)
			{
				new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
					.setMessage(org.strongswan.android.utils.Str.get(R.string.awg_delete_confirm, profile.getName()))
					.setPositiveButton(R.string.action_delete, (d, w) -> {
						mDataSource.deleteVpnProfile(profile);
						Intent intent = new Intent(Constants.VPN_PROFILES_CHANGED);
						intent.putExtra(Constants.VPN_PROFILES_MULTIPLE, new String[]{profile.getUUID().toString()});
						LocalBroadcastManager.getInstance(requireActivity()).sendBroadcast(intent);
						Toast.makeText(getActivity(), R.string.profile_deleted, Toast.LENGTH_SHORT).show();
					})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
			}
		});

		IntentFilter profileChangesFilter = new IntentFilter(Constants.VPN_PROFILES_CHANGED);
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mProfilesChanged, profileChangesFilter);
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (!mReadOnly)
		{
			outState.putIntegerArrayList(SELECTED_KEY, new ArrayList<>(mSelected));
		}
	}

	private VpnStateService mStateService;
	private final VpnStateService.VpnStateListener mStateListener = new VpnStateService.VpnStateListener()
	{
		@Override
		public void stateChanged()
		{
			updateActiveProfile();
		}
	};
	private final android.content.ServiceConnection mStateConnection = new android.content.ServiceConnection()
	{
		@Override
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service)
		{
			mStateService = ((VpnStateService.LocalBinder) service).getService();
			mStateService.registerListener(mStateListener);
			updateActiveProfile();
		}

		@Override
		public void onServiceDisconnected(android.content.ComponentName name)
		{
			mStateService = null;
		}
	};

	private void updateActiveProfile()
	{
		final android.app.Activity activity = getActivity();
		if (activity == null)
		{
			return;
		}
		activity.runOnUiThread(() -> {
			if (mListAdapter == null)
			{
				return;
			}
			String uuid = null;
			VpnStateService service = mStateService;
			if (service != null && service.getState() == VpnStateService.State.CONNECTED && service.getProfile() != null)
			{
				uuid = service.getProfile().getUUID().toString();
			}
			mListAdapter.setActiveUuid(uuid);
		});
	}

	@Override
	public void onStart()
	{
		super.onStart();
		requireContext().getApplicationContext().bindService(
			new Intent(requireContext(), VpnStateService.class), mStateConnection, android.app.Service.BIND_AUTO_CREATE);
	}

	@Override
	public void onStop()
	{
		if (mStateService != null)
		{
			mStateService.unregisterListener(mStateListener);
			mStateService = null;
		}
		try
		{
			requireContext().getApplicationContext().unbindService(mStateConnection);
		}
		catch (Exception ignored)
		{
		}
		super.onStop();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mDataSource.close();
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mProfilesChanged);
	}

	@Override
	public void onAttach(Context context)
	{
		super.onAttach(context);

		if (context instanceof OnVpnProfileSelectedListener)
		{
			mListener = (OnVpnProfileSelectedListener)context;
		}
	}

	@Override
	public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater)
	{
		menuInflater.inflate(R.menu.profile_list, menu);
	}

	@Override
	public void onPrepareMenu(@NonNull Menu menu)
	{
	}

@Override
public boolean onMenuItemSelected(@NonNull MenuItem menuItem)
{
	if (menuItem.getItemId() == R.id.check_update)
       {
	checkUpdate();
	return true;
      }  
	if (menuItem.getItemId() == R.id.backup_profiles)
{
	new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
		.setTitle(R.string.backup_title)
		.setItems(new CharSequence[]{
			getString(R.string.backup_export),
			getString(R.string.backup_import)
		}, (d, which) -> {
			if (which == 0)
			{
				mExportLauncher.launch("bardia-vpn-backup.bv");
			}
			else
			{
				mImportLauncher.launch(new String[]{"application/octet-stream", "*/*"});
			}
		})
		.show();
	return true;
}
	if (menuItem.getItemId() == R.id.app_filter)
{
	final android.content.SharedPreferences prefs =
		requireContext().getSharedPreferences("app_filter", android.content.Context.MODE_PRIVATE);
	final String[] modes = {"exclude", "only", "off"};
	int checked = 2;
	String current = prefs.getString("mode", "off");
	for (int i = 0; i < modes.length; i++)
	{
		if (modes[i].equals(current))
		{
			checked = i;
		}
	}
	new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
		.setTitle(R.string.app_filter)
		.setSingleChoiceItems(new CharSequence[]{
			getString(R.string.app_filter_exclude),
			getString(R.string.app_filter_only),
			getString(R.string.app_filter_off)
		}, checked, (dialog, which) -> {
			prefs.edit().putString("mode", modes[which]).apply();
			dialog.dismiss();
			if ("off".equals(modes[which]))
			{
				Toast.makeText(getActivity(), R.string.app_filter_saved, Toast.LENGTH_LONG).show();
				return;
			}
			java.util.ArrayList<String> list = new java.util.ArrayList<>(
				prefs.getStringSet("packages", java.util.Collections.emptySet()));
			android.content.Intent intent = new android.content.Intent(
				getActivity(), SelectedApplicationsActivity.class);
			intent.putStringArrayListExtra(
				org.strongswan.android.data.VpnProfileDataSource.KEY_SELECTED_APPS_LIST, list);
			mAppFilterLauncher.launch(intent);
		})
		.show();
	return true;
} 
if (menuItem.getItemId() == R.id.delete_no_ping)
{
	showDeleteNoPingDialog();
	return true;
}
if (menuItem.getItemId() == R.id.help)
{
	startActivity(new Intent(getActivity(), HelpActivity.class));
	return true;
}

if (menuItem.getItemId() == R.id.about)
{
    showAboutDialog();
    return true;
}
    return false;
}

	     private void checkUpdate()
{
	checkUpdate(false);
}

private void checkUpdate(boolean silent)
{
	final android.app.Activity activity = getActivity();
	if (activity == null)
	{
		return;
	}

	android.content.SharedPreferences prefs =
		activity.getSharedPreferences("update", android.content.Context.MODE_PRIVATE);
	long now = System.currentTimeMillis();
	if (silent && now - prefs.getLong("last_check", 0) < 3L * 60 * 60 * 1000)
	{
		return;
	}

	if (!silent)
	{
		Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
	}

	final String current;
	try
	{
		current = activity.getPackageManager()
			.getPackageInfo(activity.getPackageName(), 0).versionName;
	}
	catch (Exception e)
	{
		return;
	}

	java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
		try
		{
			UpdateChecker.Result latest = UpdateChecker.latest();
			boolean newer = UpdateChecker.isNewer(latest.tag, current);
			prefs.edit().putLong("last_check", System.currentTimeMillis()).apply();

			activity.runOnUiThread(() -> {
				if (!isAdded() || activity.isFinishing())
				{
					return;
				}
				if (!newer)
				{
					if (!silent)
					{
						Toast.makeText(activity, R.string.update_latest, Toast.LENGTH_LONG).show();
					}
					return;
				}
				new androidx.appcompat.app.AlertDialog.Builder(activity)
					.setTitle(R.string.check_update)
					.setMessage(getString(R.string.update_available, latest.tag))
					.setPositiveButton(R.string.update_download, (d, w) ->
						startActivity(new android.content.Intent(
							android.content.Intent.ACTION_VIEW,
							android.net.Uri.parse(latest.apkUrl))))
					.setNegativeButton(android.R.string.cancel, null)
					.show();
			});
		}
		catch (Exception e)
		{
			if (!silent)
			{
				activity.runOnUiThread(() -> {
					String msg = e.getMessage() != null ? e.getMessage() : e.toString();
					Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
				});
			}
		}
	});
}
	private void showDeleteNoPingDialog()
	{
		new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
			.setTitle(R.string.delete_no_ping)
			.setItems(new CharSequence[]{
				getString(R.string.delete_no_ping_ikev2),
				getString(R.string.delete_no_ping_singbox),
				getString(R.string.delete_no_ping_awg)
			}, (dialog, which) -> {
				if (which == 0)
				{
					deleteNoPingIkev2();
				}
				else if (which == 1)
				{
					deleteNoPingSingBox();
				}
				else
				{
					deleteNoPingAwg();
				}
			})
			.show();
	}

	private void deleteNoPingIkev2()
	{
		if (mVpnProfiles == null || mVpnProfiles.isEmpty())
		{
			Toast.makeText(getActivity(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
			return;
		}
		if (!mListAdapter.isPingFinished())
		{
			Toast.makeText(getActivity(), R.string.delete_no_ping_run_first, Toast.LENGTH_SHORT).show();
			return;
		}

		final List<VpnProfile> targets = new ArrayList<>();
		for (VpnProfile profile : mVpnProfiles)
		{
			if (!profile.isReadOnly() && !mListAdapter.hasMsPing(profile.getUUID().toString()))
			{
				targets.add(profile);
			}
		}

		if (targets.isEmpty())
		{
			Toast.makeText(getActivity(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
			return;
		}

		new androidx.appcompat.app.AlertDialog.Builder(requireActivity())
			.setTitle(R.string.delete_no_ping)
			.setMessage(getString(R.string.delete_no_ping_confirm, targets.size()))
			.setPositiveButton(android.R.string.ok, (d, w) -> {
				String[] uuids = new String[targets.size()];
				for (int i = 0; i < targets.size(); i++)
				{
					VpnProfile profile = targets.get(i);
					uuids[i] = profile.getUUID().toString();
					mDataSource.deleteVpnProfile(profile);
				}
				Intent intent = new Intent(Constants.VPN_PROFILES_CHANGED);
				intent.putExtra(Constants.VPN_PROFILES_MULTIPLE, uuids);
				LocalBroadcastManager.getInstance(requireActivity()).sendBroadcast(intent);
				Toast.makeText(getActivity(), R.string.profiles_deleted, Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void deleteNoPingAwg()
	{
		Fragment f = requireActivity().getSupportFragmentManager().findFragmentById(R.id.awg_container);
		if (f instanceof org.strongswan.android.awg.AwgProfileListFragment)
		{
			((org.strongswan.android.awg.AwgProfileListFragment)f).deleteNoPingWithConfirm();
		}
		else
		{
			Toast.makeText(getActivity(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
		}
	}

	private void shareProfile(VpnProfile profile)
	{
		try
		{
			org.json.JSONObject o = new org.json.JSONObject();
			o.put("uuid", UUID.randomUUID().toString());
			o.put("name", profile.getName());
			o.put("type", profile.getVpnType().getIdentifier());
			org.json.JSONObject remote = new org.json.JSONObject();
			remote.put("addr", profile.getGateway());
			if (profile.getPort() != null)
			{
				remote.put("port", profile.getPort());
			}
			String remoteId = profile.getRemoteId();
			if (remoteId != null && !remoteId.isEmpty())
			{
				remote.put("id", remoteId);
			}
			o.put("remote", remote);
			org.json.JSONObject local = new org.json.JSONObject();
			String user = profile.getUsername();
			if (user != null && !user.isEmpty())
			{
				local.put("eap_id", user);
			}
			String pass = profile.getPassword();
			if (pass != null && !pass.isEmpty())
			{
				local.put("shared_secret", pass);
			}
			if (local.length() > 0)
			{
				o.put("local", local);
			}
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("text/plain");
			send.putExtra(Intent.EXTRA_TEXT, o.toString(2));
			startActivity(Intent.createChooser(send, getString(R.string.share_title)));
		}
		catch (Exception e)
		{
			Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void deleteNoPingSingBox()
	{
		Fragment f = requireActivity().getSupportFragmentManager().findFragmentById(R.id.v2ray_container);
		if (f instanceof V2RayProfileListFragment)
		{
			((V2RayProfileListFragment)f).deleteNoPingWithConfirm();
		}
		else
		{
			Toast.makeText(getActivity(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
		}
	}

public void pingAllPublic()
{
    pingAll();
}		
private void pingAll()
{
	if (mVpnProfiles == null || mVpnProfiles.isEmpty())
	{
		return;
	}

	final java.util.List<VpnProfile> copy = new java.util.ArrayList<>(mVpnProfiles);
	mListAdapter.clearPings();
	for (VpnProfile profile : copy)
	{
		mListAdapter.setPing(profile.getUUID().toString(), getString(R.string.ping_running));
	}

	java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
		java.util.concurrent.ExecutorService pool =
			java.util.concurrent.Executors.newFixedThreadPool(
				Math.min(16, Math.max(1, copy.size())));
		java.util.ArrayList<java.util.concurrent.Future<String>> futures =
			new java.util.ArrayList<>();
		try
		{
			for (final VpnProfile p : copy)
			{
				futures.add(pool.submit(() -> {
					try
					{
						return HostPing.ping(p.getGateway(), p.getPort());
					}
					catch (Exception e)
					{
						return "timeout";
					}
				}));
			}

			for (int i = 0; i < copy.size(); i++)
			{
				String result;
				try
				{
					result = futures.get(i).get(8, java.util.concurrent.TimeUnit.SECONDS);
				}
				catch (Exception e)
				{
					result = "timeout";
				}
				if ("timeout".equals(result))
				{
					result = getString(R.string.ping_timeout);
				}
				final String uuid = copy.get(i).getUUID().toString();
				final String text = result;
				if (getActivity() == null)
				{
					return;
				}
				getActivity().runOnUiThread(() -> mListAdapter.setPing(uuid, text));
			}
		}
		finally
		{
			pool.shutdownNow();
		}
	});
}
	private final OnItemClickListener mVpnProfileClicked = new OnItemClickListener()
	{
		@Override
		public void onItemClick(AdapterView<?> a, View v, int position, long id)
		{
			if (mListener != null)
			{
				mListener.onVpnProfileSelected((VpnProfile)a.getItemAtPosition(position));
			}
		}
	};

	private final MultiChoiceModeListener mVpnProfileSelected = new MultiChoiceModeListener()
	{
		private MenuItem mEditProfile;
		private MenuItem mCopyProfile;
		private MenuItem mDeleteProfile;

		private boolean mCanEdit;
		private boolean mCanCopy;
		private boolean mCanDelete;

		private int mReadOnlyCount;

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu)
		{
			mEditProfile.setEnabled(mCanEdit);
			mCopyProfile.setEnabled(mCanCopy);
			mDeleteProfile.setEnabled(mCanDelete);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode)
		{
			mReadOnlyCount = 0;
			mSelected.clear();
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu)
		{
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.profile_list_context, menu);
			mEditProfile = menu.findItem(R.id.edit_profile);
			mCopyProfile = menu.findItem(R.id.copy_profile);
			mDeleteProfile = menu.findItem(R.id.delete_profile);
			mode.setTitle(R.string.select_profiles);
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item)
		{
			switch (item.getItemId())
			{
				case R.id.edit_profile:
				{
					int position = mSelected.iterator().next();
					VpnProfile profile = (VpnProfile)mListView.getItemAtPosition(position);
					Intent connectionIntent = new Intent(getActivity(), VpnProfileDetailActivity.class);
					connectionIntent.putExtra(VpnProfileDataSource.KEY_UUID, profile.getUUID().toString());
					startActivity(connectionIntent);
					break;
				}
				case R.id.copy_profile:
				{
					int position = mSelected.iterator().next();
					VpnProfile profile = (VpnProfile)mListView.getItemAtPosition(position);
					profile = profile.clone();
					profile.setUUID(UUID.randomUUID());
					profile.setName(String.format(getString(R.string.copied_name), profile.getName()));
					mDataSource.insertProfile(profile);

					Intent intent = new Intent(Constants.VPN_PROFILES_CHANGED);
					intent.putExtra(Constants.VPN_PROFILES_SINGLE, profile.getUUID().toString());
					LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);

					Intent connectionIntent = new Intent(getActivity(), VpnProfileDetailActivity.class);
					connectionIntent.putExtra(VpnProfileDataSource.KEY_UUID, profile.getUUID().toString());
					startActivity(connectionIntent);
					break;
				}
				case R.id.delete_profile:
				{
					ArrayList<VpnProfile> profiles = new ArrayList<>();
					for (int position : mSelected)
					{
						profiles.add((VpnProfile)mListView.getItemAtPosition(position));
					}
					String[] uuids = new String[profiles.size()];
					for (int i = 0; i < profiles.size(); i++)
					{
						VpnProfile profile = profiles.get(i);
						uuids[i] = profile.getUUID().toString();
						mDataSource.deleteVpnProfile(profile);
					}
					Intent intent = new Intent(Constants.VPN_PROFILES_CHANGED);
					intent.putExtra(Constants.VPN_PROFILES_MULTIPLE, uuids);
					LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
					Toast.makeText(VpnProfileListFragment.this.getActivity(),
								   R.string.profiles_deleted, Toast.LENGTH_SHORT).show();
					break;
				}
				default:
					return false;
			}
			mode.finish();
			return true;
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position,
											  long id, boolean checked)
		{
			VpnProfile profile = (VpnProfile)mListView.getItemAtPosition(position);

			if (checked)
			{
				mSelected.add(position);
				mReadOnlyCount += profile.isReadOnly() ? 1 : 0;
			}
			else
			{
				mSelected.remove(position);
				mReadOnlyCount -= profile.isReadOnly() ? 1 : 0;
			}
			final int checkedCount = mSelected.size();
			switch (checkedCount)
			{
				case 0:
					mode.setSubtitle(R.string.no_profile_selected);
					break;
				case 1:
					mode.setSubtitle(R.string.one_profile_selected);
					break;
				default:
					mode.setSubtitle(String.format(getString(R.string.x_profiles_selected), checkedCount));
					break;
			}

			mCanEdit = checkedCount == 1;
			mCanCopy = checkedCount == 1 && mReadOnlyCount == 0;
			mCanDelete = checkedCount > 0 && mReadOnlyCount == 0;

			mode.invalidate();
		}
	};

	private void showFetchDialog()
	{
		Context context = requireContext();
		int pad = (int) (20 * getResources().getDisplayMetrics().density);

		android.widget.LinearLayout box = new android.widget.LinearLayout(context);
		box.setOrientation(android.widget.LinearLayout.VERTICAL);
		box.setPadding(pad, pad, pad, pad / 2);

		android.widget.TextView title = new android.widget.TextView(context);
		title.setText(R.string.fetch_servers);
		title.setTextSize(18);
		title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
		title.setPadding(0, 0, 0, pad / 2);
		box.addView(title);

		android.widget.TextView hint = new android.widget.TextView(context);
		hint.setText(R.string.fetch_servers_dialog_hint);
		hint.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
		hint.setTextSize(13);
		hint.setPadding(0, 0, 0, pad);
		box.addView(hint);

		final android.widget.Spinner spinner = new android.widget.Spinner(context);
		String[] allNames = getResources().getStringArray(R.array.vpn_location_names);
		String[] allCodes = getResources().getStringArray(R.array.vpn_location_codes);
		java.util.ArrayList<String> names = new java.util.ArrayList<>();
		final java.util.ArrayList<String> codes = new java.util.ArrayList<>();
		for (int i = 0; i < allCodes.length; i++)
		{
			String c = allCodes[i];
			if ("v2ray".equals(c))
			{
				continue;
			}
			names.add(allNames[i]);
			codes.add(c);
		}
		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
			context, android.R.layout.simple_spinner_dropdown_item, names);
		spinner.setAdapter(adapter);
		box.addView(spinner);

		android.widget.LinearLayout buttons = new android.widget.LinearLayout(context);
		buttons.setOrientation(android.widget.LinearLayout.HORIZONTAL);
		buttons.setGravity(android.view.Gravity.END);
		android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(
			android.view.ViewGroup.LayoutParams.MATCH_PARENT,
			android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
		blp.topMargin = pad;
		box.addView(buttons, blp);

		android.widget.Button cancel = new android.widget.Button(context);
		cancel.setText(android.R.string.cancel);
		cancel.setAllCaps(false);
		cancel.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
		cancel.setBackgroundResource(android.R.color.transparent);

		android.widget.Button getBtn = new android.widget.Button(context);
		getBtn.setText(R.string.fetch_servers);
		org.strongswan.android.ui.widget.UiStyle.fetchButton(getBtn);

		android.widget.LinearLayout.LayoutParams clp = new android.widget.LinearLayout.LayoutParams(
			android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
			android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
		clp.setMarginEnd(pad / 2);
		buttons.addView(cancel, clp);
		buttons.addView(getBtn);

		final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
			.setView(box)
			.setCancelable(true)
			.create();
		if (dialog.getWindow() != null)
		{
			dialog.getWindow().setBackgroundDrawableResource(R.drawable.state_card);
		}
		cancel.setOnClickListener(v -> dialog.dismiss());
		getBtn.setOnClickListener(v -> {
			int pos = spinner.getSelectedItemPosition();
			if (pos < 0 || pos >= codes.size())
			{
				return;
			}
			final String location = codes.get(pos);
			dialog.dismiss();
			fetchIkev2InBackground(location);
		});
		dialog.show();
		if (dialog.getWindow() != null)
		{
			android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
			lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
			dialog.getWindow().setAttributes(lp);
		}
	}


	private void fetchIkev2InBackground(final String location)
	{
		final android.app.Activity activity = getActivity();
		if (activity == null)
		{
			return;
		}
		Toast.makeText(activity, R.string.fetch_servers_loading, Toast.LENGTH_SHORT).show();
		final android.content.Context appCtx = activity.getApplicationContext();
		java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
			try
			{
				final int count = FreeServersActivity.importIkev2Profiles(appCtx, location);
				activity.runOnUiThread(() ->
					Toast.makeText(activity, getString(R.string.fetch_servers_done, count), Toast.LENGTH_LONG).show());
			}
			catch (Exception e)
			{
				final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
				activity.runOnUiThread(() ->
					Toast.makeText(activity, getString(R.string.fetch_servers_failed, msg), Toast.LENGTH_LONG).show());
			}
		});
	}

	private void showAboutDialog()
{
    android.content.Context context = requireContext();
    int pad = (int) (20 * getResources().getDisplayMetrics().density);

    android.widget.LinearLayout box = new android.widget.LinearLayout(context);
    box.setOrientation(android.widget.LinearLayout.VERTICAL);
    box.setPadding(pad, pad, pad, pad / 2);

 
    box.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LOCALE);

    
    android.widget.TextView title = new android.widget.TextView(context);
    title.setText(R.string.menu_about);
    title.setTextSize(18);
    title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
    title.setPadding(0, 0, 0, pad / 2);
    
    title.setGravity(android.view.Gravity.START);
    box.addView(title);

    
    String version = "?";
    try
    {
        version = context.getPackageManager()
            .getPackageInfo(context.getPackageName(), 0).versionName;
    }
    catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}

    
    android.widget.TextView text = new android.widget.TextView(context);
    text.setText(context.getString(R.string.about_text, version));
    text.setTextSize(14);
    text.setTextColor(android.graphics.Color.WHITE);
    text.setLineSpacing(0, 1.2f);
    
    text.setGravity(android.view.Gravity.START);
    text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    text.setPadding(0, 0, 0, pad);
    box.addView(text);

    
    android.widget.LinearLayout buttons = new android.widget.LinearLayout(context);
    buttons.setOrientation(android.widget.LinearLayout.HORIZONTAL); 
    buttons.setGravity(android.view.Gravity.END);
    android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    blp.topMargin = pad;
    box.addView(buttons, blp);

    android.widget.Button close = new android.widget.Button(context);
    close.setText(android.R.string.ok);
    close.setAllCaps(false);
    org.strongswan.android.ui.widget.UiStyle.fetchButton(close);
    buttons.addView(close);

    
    final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
        .setView(box)
        .setCancelable(true)
        .create();
    if (dialog.getWindow() != null)
    {
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.state_card);
    }

    close.setOnClickListener(v -> dialog.dismiss());

    dialog.show();
    if (dialog.getWindow() != null)
    {
        android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
        dialog.getWindow().setAttributes(lp);
    }
	}

}
