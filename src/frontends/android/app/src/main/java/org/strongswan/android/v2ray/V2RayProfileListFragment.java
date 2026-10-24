package org.strongswan.android.v2ray;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.strongswan.android.R;
import org.strongswan.android.ui.FreeServersActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.strongswan.android.v2ray.V2RayConfigTester;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class V2RayProfileListFragment extends Fragment
{
    private static final int MENU_EDIT = 1001;
    private static final int MENU_COPY = 1002;
    private static final int MENU_DELETE = 1003;

    private V2RayProfileStore store;
    private final List<String> links = new ArrayList<>();
    private V2RayProfileAdapter adapter;
    private String pendingLink;

    private ListView profileList;
    private ActionMode actionMode;
    private volatile boolean pinging;

    private final ActivityResultLauncher<Intent> vpnPermission =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && pendingLink != null)
                        {
                            SingBoxVpnService.start(
                                    requireContext(),
                                    pendingLink
                            );
                        }
                    }
            );

    private final BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (adapter != null)
            {
                adapter.notifyDataSetChanged();
            }
        }
    };
    private final BroadcastReceiver profilesChangedReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (adapter == null)
            {
                return;
            }
            links.clear();
            links.addAll(store.load());
            adapter.notifyDataSetChanged();
        }
    };
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState)
    {
        Context context = requireContext();

        store = new V2RayProfileStore(context);

        links.clear();
        links.addAll(store.load());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        profileList = new ListView(context);
        profileList.setDivider(null);
        profileList.setDividerHeight(0);
        profileList.setClipToPadding(false);

        adapter = new V2RayProfileAdapter(context, links);
        profileList.setAdapter(adapter);

        LinearLayout emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        emptyView.setPadding(pad, pad, pad, pad);

        TextView hint = new TextView(context);
        hint.setText("No sing-box profiles");
        hint.setGravity(android.view.Gravity.CENTER);
        emptyView.addView(hint);

        Button getFree = new Button(context);
        getFree.setText("Get free servers");
        getFree.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), FreeServersActivity.class));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        emptyView.addView(getFree, lp);

        profileList.setEmptyView(emptyView);

        profileList.setOnItemClickListener(
                new AdapterView.OnItemClickListener()
                {
                    @Override
                    public void onItemClick(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id)
                    {
                        String link = adapter.getItem(position);

                        if (link == null)
                        {
                            return;
                        }

                        if (link.equals(SingBoxVpnService.getLink())
                                && SingBoxVpnService.isRunning())
                        {
                            SingBoxVpnService.stop(requireContext());
                        }
                        else
                        {
                            connect(link);
                        }
                    }
                }
        );


        profileList.setChoiceMode(
                ListView.CHOICE_MODE_MULTIPLE_MODAL
        );

        profileList.setMultiChoiceModeListener(
                new AbsListView.MultiChoiceModeListener()
                {
                    @Override
                    public void onItemCheckedStateChanged(
                            ActionMode mode,
                            int position,
                            long id,
                            boolean checked)
                    {
                        int count = profileList.getCheckedItemCount();

                        mode.setTitle(count + " selected");

                        MenuItem editItem =
                                mode.getMenu().findItem(MENU_EDIT);

                        if (editItem != null)
                        {
                            // Edit is available only for one profile.
                            editItem.setEnabled(count == 1);
                        }
                    }

                    @Override
                    public boolean onCreateActionMode(
                            ActionMode mode,
                            Menu menu)
                    {
                        actionMode = mode;

                        menu.add(
                                Menu.NONE,
                                MENU_EDIT,
                                0,
                                "Edit"
                        ).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_IF_ROOM
                        );

                        menu.add(
                                Menu.NONE,
                                MENU_COPY,
                                1,
                                "Copy"
                        ).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_IF_ROOM
                        );

                        menu.add(
                                Menu.NONE,
                                MENU_DELETE,
                                2,
                                "Delete"
                        ).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_IF_ROOM
                        );

                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(
                            ActionMode mode,
                            Menu menu)
                    {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(
                            ActionMode mode,
                            MenuItem item)
                    {
                        final List<String> selectedLinks =
                                new ArrayList<>();

                        android.util.SparseBooleanArray checkedItems =
                                profileList.getCheckedItemPositions();

                        for (int i = 0; i < profileList.getCount(); i++)
                        {
                            if (checkedItems.get(i))
                            {
                                String link = adapter.getItem(i);

                                if (link != null)
                                {
                                    selectedLinks.add(link);
                                }
                            }
                        }

                        if (selectedLinks.isEmpty())
                        {
                            mode.finish();
                            return true;
                        }

                        int itemId = item.getItemId();

                        /*
                         * EDIT
                         * Only one profile can be edited at a time.
                         */
                        if (itemId == MENU_EDIT)
                        {
                            if (selectedLinks.size() != 1)
                            {
                                return true;
                            }

                            final String selectedLink =
                                    selectedLinks.get(0);

                            mode.finish();

                            V2RayProfileEditor.show(
                                    requireContext(),
                                    selectedLink,
                                    false,
                                    (oldLink, newLink, copyMode) ->
                                    {
                                        int index = links.indexOf(oldLink);

                                        if (index < 0)
                                        {
                                            Toast.makeText(
                                                    requireContext(),
                                                    "Profile not found",
                                                    Toast.LENGTH_SHORT
                                            ).show();

                                            return;
                                        }

                                        if (!oldLink.equals(newLink)
                                                && links.contains(newLink))
                                        {
                                            Toast.makeText(
                                                    requireContext(),
                                                    "This profile already exists",
                                                    Toast.LENGTH_LONG
                                            ).show();

                                            return;
                                        }

                                        // Stop VPN if it uses the old link.
                                        if (oldLink.equals(
                                                SingBoxVpnService.getLink())
                                                && SingBoxVpnService.isRunning())
                                        {
                                            SingBoxVpnService.stop(
                                                    requireContext()
                                            );
                                        }

                                        links.set(index, newLink);

                                        store.save(links);
                                        adapter.notifyDataSetChanged();

                                        Toast.makeText(
                                                requireContext(),
                                                "Profile updated",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }
                            );

                            return true;
                        }


                        if (itemId == MENU_COPY)
                        {
                            StringBuilder clipboardText =
                                    new StringBuilder();

                            for (String link : selectedLinks)
                            {
                                if (clipboardText.length() > 0)
                                {
                                    clipboardText.append('\n');
                                }

                                clipboardText.append(link);
                            }

                            ClipboardManager clipboard =
                                    (ClipboardManager)
                                            requireContext().getSystemService(
                                                    Context.CLIPBOARD_SERVICE
                                            );

                            if (clipboard != null)
                            {
                                ClipData clip = ClipData.newPlainText(
                                        "V2Ray profile links",
                                        clipboardText.toString()
                                );

                                clipboard.setPrimaryClip(clip);

                                Toast.makeText(
                                        requireContext(),
                                        selectedLinks.size()
                                                + " profile link(s) copied",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            mode.finish();
                            return true;
                        }


                        if (itemId == MENU_DELETE)
                        {
                            mode.finish();

                            final int deleteCount = selectedLinks.size();

                            new AlertDialog.Builder(requireActivity())
                                    .setTitle("Delete profiles")
                                    .setMessage(
                                            "Delete "
                                                    + deleteCount
                                                    + " selected profile(s)?"
                                    )
                                    .setPositiveButton(
                                            "Delete",
                                            (dialog, which) ->
                                            {
                                                // Stop VPN if its profile
                                                // is among the deleted ones.
                                                String runningLink =
                                                        SingBoxVpnService.getLink();

                                                if (runningLink != null
                                                        && selectedLinks.contains(
                                                                runningLink)
                                                        && SingBoxVpnService.isRunning())
                                                {
                                                    SingBoxVpnService.stop(
                                                            requireContext()
                                                    );
                                                }

                                                links.removeAll(selectedLinks);

                                                store.save(links);
                                                adapter.notifyDataSetChanged();

                                                Toast.makeText(
                                                        requireContext(),
                                                        deleteCount
                                                                + " profile(s) deleted",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            }
                                    )
                                    .setNegativeButton(
                                            android.R.string.cancel,
                                            null
                                    )
                                    .show();

                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode)
                    {
                        actionMode = null;

                        profileList.clearChoices();
                        adapter.notifyDataSetChanged();
                    }
                }
        );

        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        root.addView(emptyView, fill);
        root.addView(profileList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        return root;
    }



    @Override
    public void onStart()
    {
        super.onStart();

        ContextCompat.registerReceiver(
                requireContext(),
                receiver,
                new IntentFilter(SingBoxVpnService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
        profilesChangedReceiver,
        new IntentFilter("org.strongswan.android.V2RAY_PROFILES_CHANGED"));
        if (adapter != null && store != null)
        {
            links.clear();
            links.addAll(store.load());
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop()
    {
       try
 {
     LocalBroadcastManager.getInstance(requireContext())
             .unregisterReceiver(profilesChangedReceiver);
 }
 catch (IllegalArgumentException ignored)
 {
 }
 		try
         {
             requireContext().unregisterReceiver(receiver);
         }
         catch (IllegalArgumentException ignored)
         {
             // Receiver was not registered.
         }

         super.onStop();
    }



    private void confirmDelete(final String link)
    {
        new AlertDialog.Builder(requireActivity())
                .setMessage(
                        "Delete " + V2RayConfigBuilder.name(link) + "?"
                )
                .setPositiveButton(
                        android.R.string.ok,
                        (dialog, which) ->
                        {
                            links.remove(link);
                            store.save(links);
                            adapter.notifyDataSetChanged();
                        }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    public void deleteNoPingWithConfirm()
    {
        if (adapter == null || links.isEmpty())
        {
            Toast.makeText(requireContext(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isPingFinished())
        {
            Toast.makeText(requireContext(), R.string.delete_no_ping_run_first, Toast.LENGTH_SHORT).show();
            return;
        }

        final String runningLink = SingBoxVpnService.isRunning() ? SingBoxVpnService.getLink() : null;
        final List<String> targets = new ArrayList<>();
        for (String link : links)
        {
            if (!adapter.hasMsPing(link) && !link.equals(runningLink))
            {
                targets.add(link);
            }
        }

        if (targets.isEmpty())
        {
            Toast.makeText(requireContext(), R.string.delete_no_ping_none, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.delete_no_ping)
                .setMessage(getString(R.string.delete_no_ping_confirm, targets.size()))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                {
                    links.removeAll(targets);
                    store.save(links);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), R.string.profiles_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

 public void pingAllPublic()
 {
     pingAll();
 }
  private void pingAll()
 {
     if (links.isEmpty() || pinging)
     {
         return;
     }
     if (SingBoxVpnService.isRunning())
     {
         Toast.makeText(requireContext(), R.string.ping_disconnect_first, Toast.LENGTH_SHORT).show();
         return;
     }

     final List<String> copy = new ArrayList<>(links);
     final Context appContext = requireContext().getApplicationContext();
     final String running = appContext.getString(R.string.ping_running);
     final String timeout = appContext.getString(R.string.ping_timeout);

     adapter.clearPings();

     for (String link : copy)
     {
         adapter.setPing(link, running);
     }

     pinging = true;
     new Thread(() ->
     {
         try
         {
             V2RayConfigTester.pingAll(appContext, copy, 5000, 16, (link, delay) ->
             {
                 final String result = delay >= 0 ? delay + " ms" : timeout;
                 Activity a = getActivity();
                 if (a == null)
                 {
                     return;
                 }
                 a.runOnUiThread(() ->
                 {
                     if (adapter != null)
                     {
                         adapter.setPing(link, result);
                     }
                 });
             });
         }
         finally
         {
             pinging = false;
         }
     }).start();
 }

    private void connect(String link)
    {
        pendingLink = link;

        Intent prepare = VpnService.prepare(requireContext());

        if (prepare != null)
        {
            vpnPermission.launch(prepare);
        }
        else
        {
            SingBoxVpnService.start(requireContext(), link);
        }
    }
}
