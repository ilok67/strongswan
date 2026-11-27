/*
 * Copyright (C) 2012-2018 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 *
 * Copyright (C) secunet Security Networks AG
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import org.strongswan.android.utils.Constants;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.strongswan.android.R;
import org.strongswan.android.data.ManagedConfiguration;
import org.strongswan.android.data.ManagedConfigurationService;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.StrongSwanApplication;
import org.strongswan.android.logic.TrustedCertificateManager;
import org.strongswan.android.ui.VpnProfileListFragment.OnVpnProfileSelectedListener;
import org.strongswan.android.utils.Utils;
import org.strongswan.android.v2ray.SingBoxVpnService;
import org.strongswan.android.awg.AwgProfileListFragment;
import org.strongswan.android.awg.AwgVpnService;
import org.strongswan.android.v2ray.V2RayProfileListFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.view.View;
import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity implements OnVpnProfileSelectedListener
{
	public static final String CONTACT_EMAIL = "test@test.com";
	public static final String EXTRA_CRL_LIST = "org.strongswan.android.CRL_LIST";
    private int mTab;
	/**
	 * Use "bring your own device" (BYOD) features
	 */
	public static final boolean USE_BYOD = true;

	private static final String DIALOG_TAG = "Dialog";

	private ManagedConfigurationService mManagedConfigurationService;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		CrashReporter.install(this);
		setContentView(R.layout.main);
		WindowCompat.enableEdgeToEdge(getWindow());
		Utils.applyWindowInsetsAsMarginsForLists(findViewById(R.id.layout));

		ActionBar bar = getSupportActionBar();
		bar.setDisplayShowHomeEnabled(true);
		bar.setDisplayHomeAsUpEnabled(true);
		bar.setHomeAsUpIndicator(R.drawable.ic_menu);
		bar.setDisplayShowTitleEnabled(false);
		bar.setIcon(R.mipmap.ic_launcher);
		/* load CA certificates in a background thread */
		((StrongSwanApplication)getApplication()).getExecutor().execute(() -> {
			TrustedCertificateManager.getInstance().load();
		});

		mManagedConfigurationService = StrongSwanApplication.getInstance().getManagedConfigurationService();
        TabLayout tabs = findViewById(R.id.tabs);
tabs.addTab(tabs.newTab().setText("IKEv2"));
tabs.addTab(tabs.newTab().setText("sing-box"));
tabs.addTab(tabs.newTab().setText("AmneziaWG"));
if (savedInstanceState == null)
{
	getSupportFragmentManager().beginTransaction()
		.replace(R.id.v2ray_container, new V2RayProfileListFragment())
		.replace(R.id.awg_container, new AwgProfileListFragment()).commit();
}
tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener()
{
	@Override
	public void onTabSelected(TabLayout.Tab tab)
	{
		showTab(tab.getPosition());
	}

	@Override
	public void onTabUnselected(TabLayout.Tab tab)
	{
	}

	@Override
	public void onTabReselected(TabLayout.Tab tab)
	{
	}
});
int start = savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : (SingBoxVpnService.isRunning() ? 1 : (AwgVpnService.isActive() ? 2 : 0));
TabLayout.Tab selected = tabs.getTabAt(start);
if (selected != null)
{
	selected.select();
}
showTab(start);

		CrashReporter.showIfAny(this);
		if (savedInstanceState == null)
		{
			checkStartupPermissions();
			showChangelogIfNeeded();
		}
	}

	private void showChangelogIfNeeded()
{
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    int versionCode;
    try {
        versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
    } catch (PackageManager.NameNotFoundException e) {
        versionCode = 0;
    }
    final String key = "changelog_shown_v_" + versionCode;
    if (prefs.getBoolean(key, false))
    {
        return;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.changelog_title)
        .setMessage(R.string.changelog_body)
        .setPositiveButton(R.string.changelog_ok, (d, w) ->
            prefs.edit().putBoolean(key, true).apply())
        .setCancelable(false)
        .show();
}

	private Menu mOptionsMenu;
	private final java.util.List<MenuItem> mPanelItems = new java.util.ArrayList<MenuItem>();

	private void showMenuPanel()
	{
		if (mOptionsMenu == null || mPanelItems.isEmpty())
		{
			return;
		}
		int statusBar = 0;
		int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resId > 0)
		{
			statusBar = getResources().getDimensionPixelSize(resId);
		}
		ActionBar bar = getSupportActionBar();
		int barHeight = bar != null ? bar.getHeight() : 0;
		org.strongswan.android.ui.widget.MenuPanel.show(this, mOptionsMenu, new java.util.ArrayList<MenuItem>(mPanelItems), statusBar + barHeight);
	}

	private final ActivityResultLauncher<String> mNotificationPermission = registerForActivityResult(
		new ActivityResultContracts.RequestPermission(),
		granted -> askBatteryOptimization());

	private final ActivityResultLauncher<Intent> mBatteryRequest = registerForActivityResult(
		new ActivityResultContracts.StartActivityForResult(),
		result -> {
		});

	private void checkStartupPermissions()
	{
		SharedPreferences startup = getSharedPreferences("startup", MODE_PRIVATE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
			&& ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
			&& !startup.getBoolean("notification_asked", false))
		{
			startup.edit().putBoolean("notification_asked", true).apply();
			mNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
			return;
		}
		askBatteryOptimization();
	}

	private void askBatteryOptimization()
	{
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isFinishing())
		{
			return;
		}
		SharedPreferences startup = getSharedPreferences("startup", MODE_PRIVATE);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())
			|| pref.getBoolean(Constants.PREF_IGNORE_POWER_WHITELIST, false)
			|| startup.getBoolean("battery_asked", false))
		{
			return;
		}
		startup.edit().putBoolean("battery_asked", true).apply();
		new AlertDialog.Builder(this)
			.setTitle(R.string.power_whitelist_title)
			.setMessage(R.string.power_whitelist_text)
			.setPositiveButton(android.R.string.ok, (dialog, which) -> {
				Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
					Uri.parse("package:" + getPackageName()));
				mBatteryRequest.launch(intent);
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	@Override
public boolean onCreateOptionsMenu(Menu menu)
{
    MenuItem ping = menu.add(Menu.NONE, 9999, 0, R.string.menu_ping);
    ping.setIcon(R.drawable.ic_speed);
    ping.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

    return super.onCreateOptionsMenu(menu);
}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		mOptionsMenu = menu;
		mPanelItems.clear();
		for (int i = 0; i < menu.size(); i++)
		{
			MenuItem entry = menu.getItem(i);
			if (entry.getItemId() == 9999)
			{
				continue;
			}
			mPanelItems.add(entry);
			entry.setVisible(false);
		}
		final MenuItem importProfile = menu.findItem(R.id.menu_import_profile);
		if (importProfile != null)
		{
			final ManagedConfiguration managedConfiguration = mManagedConfigurationService.getManagedConfiguration();
			importProfile.setVisible(managedConfiguration.isAllowProfileImport());
			importProfile.setEnabled(managedConfiguration.isAllowProfileImport());
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home)
		{
			showMenuPanel();
			return true;
		}
    if (item.getItemId() == 9999)
    {
        if (mTab == 2)
        {
            Fragment awg = getSupportFragmentManager()
                    .findFragmentById(R.id.awg_container);
            if (awg instanceof AwgProfileListFragment)
            {
                ((AwgProfileListFragment) awg).pingAllPublic();
            }
            return true;
        }
        if (mTab == 1)
        {
            Fragment f = getSupportFragmentManager()
                    .findFragmentById(R.id.v2ray_container);
            if (f instanceof V2RayProfileListFragment)
            {
                ((V2RayProfileListFragment) f).pingAllPublic();
            }
        }
        else
        {
            Fragment f = getSupportFragmentManager()
                    .findFragmentById(R.id.profile_list_frag);
            if (f instanceof VpnProfileListFragment)
            {
                ((VpnProfileListFragment) f).pingAllPublic();
            }
        }
        return true;
    }
    
		switch (item.getItemId())
		{
			case R.id.menu_import_profile:
				Intent intent = new Intent(this, VpnProfileImportActivity.class);
				startActivity(intent);
				return true;
			case R.id.menu_manage_certs:
				Intent certIntent = new Intent(this, TrustedCertificatesActivity.class);
				startActivity(certIntent);
				return true;
			case R.id.menu_crl_cache:
				clearCRLs();
				return true;
			case R.id.menu_show_log:
				Intent logIntent = new Intent(this, LogActivity.class);
				startActivity(logIntent);
				return true;
			case R.id.menu_settings:
				Intent settingsIntent = new Intent(this, SettingsActivity.class);
				startActivity(settingsIntent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onVpnProfileSelected(VpnProfile profile)
	{
		final Intent intent = new Intent(this, VpnProfileControlActivity.class);
		intent.setAction(VpnProfileControlActivity.START_PROFILE);
		intent.putExtra(VpnProfileControlActivity.EXTRA_VPN_PROFILE_UUID, profile.getUUID().toString());
		if (SingBoxVpnService.hasSession() || AwgVpnService.isActive())
		{
			new Thread(() -> {
				if (SingBoxVpnService.hasSession())
				{
					SingBoxVpnService.stopBlocking(this, true);
				}
				if (AwgVpnService.isActive())
				{
					AwgVpnService.stopBlocking(this, true);
				}
				runOnUiThread(() -> startActivity(intent));
			}).start();
			return;
		}
		startActivity(intent);
	}

	private void showTab(int position)
{
	mTab = position;
	findViewById(R.id.profile_list_frag).setVisibility(position == 0 ? View.VISIBLE : View.GONE);
	findViewById(R.id.v2ray_container).setVisibility(position == 1 ? View.VISIBLE : View.GONE);
	findViewById(R.id.awg_container).setVisibility(position == 2 ? View.VISIBLE : View.GONE);
}

@Override
protected void onSaveInstanceState(Bundle outState)
{
	super.onSaveInstanceState(outState);
	outState.putInt("tab", mTab);
}
	/**
	 * Ask the user whether to clear the CRL cache.
	 */
	private void clearCRLs()
	{
		final String FILE_PREFIX = "crl-";
		ArrayList<String> list = new ArrayList<>();

		for (String file : fileList())
		{
			if (file.startsWith(FILE_PREFIX))
			{
				list.add(file);
			}
		}
		if (list.size() == 0)
		{
			Toast.makeText(this, R.string.clear_crl_cache_msg_none, Toast.LENGTH_SHORT).show();
			return;
		}
		removeFragmentByTag(DIALOG_TAG);

		Bundle args = new Bundle();
		args.putStringArrayList(EXTRA_CRL_LIST, list);

		CRLCacheDialog dialog = new CRLCacheDialog();
		dialog.setArguments(args);
		dialog.show(this.getSupportFragmentManager(), DIALOG_TAG);
	}

	/**
	 * Dismiss dialog if shown
	 */
	public void removeFragmentByTag(String tag)
	{
		FragmentManager fm = getSupportFragmentManager();
		Fragment login = fm.findFragmentByTag(tag);
		if (login != null)
		{
			FragmentTransaction ft = fm.beginTransaction();
			ft.remove(login);
			ft.commit();
		}
	}

	/**
	 * Confirmation dialog to clear CRL cache
	 */
	public static class CRLCacheDialog extends AppCompatDialogFragment
	{
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			final List<String> list = getArguments().getStringArrayList(EXTRA_CRL_LIST);
			String size;
			long s = 0;

			for (String file : list)
			{
				File crl = getActivity().getFileStreamPath(file);
				s += crl.length();
			}
			size = Formatter.formatFileSize(getActivity(), s);

			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
				.setTitle(R.string.clear_crl_cache_title)
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						dismiss();
					}
				})
				.setPositiveButton(R.string.clear, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int whichButton)
					{
						for (String file : list)
						{
							getActivity().deleteFile(file);
						}
					}
				});
			builder.setMessage(getActivity().getResources().getQuantityString(R.plurals.clear_crl_cache_msg, list.size(), list.size(), size));
			return builder.create();
		}
	}
}
