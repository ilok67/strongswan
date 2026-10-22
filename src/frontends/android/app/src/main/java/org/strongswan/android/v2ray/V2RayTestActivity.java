package org.strongswan.android.v2ray;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class V2RayTestActivity extends Activity
{
	private static final int REQUEST_VPN = 1;

	private EditText input;
	private TextView status;
	private String pendingLink;

	private final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String error = intent.getStringExtra(SingBoxVpnService.EXTRA_ERROR);
			if (error != null)
			{
				status.setText("error: " + error);
			}
			else
			{
				status.setText(intent.getBooleanExtra(SingBoxVpnService.EXTRA_RUNNING, false) ? "connected" : "stopped");
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad, pad, pad);

		input = new EditText(this);
		input.setHint("vless:// vmess:// trojan:// ss:// hy2://");
		input.setMinLines(3);
		layout.addView(input);

		Button start = new Button(this);
		start.setText("Start");
		start.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				start();
			}
		});
		layout.addView(start);

		Button stop = new Button(this);
		stop.setText("Stop");
		stop.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(V2RayTestActivity.this, SingBoxVpnService.class);
				i.setAction(SingBoxVpnService.ACTION_STOP);
				startService(i);
			}
		});
		layout.addView(stop);

		status = new TextView(this);
		status.setText(SingBoxVpnService.isRunning() ? "connected" : "stopped");
		layout.addView(status);

		setContentView(layout);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		ContextCompat.registerReceiver(this, receiver, new IntentFilter(SingBoxVpnService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED);
	}

	@Override
	protected void onStop()
	{
		unregisterReceiver(receiver);
		super.onStop();
	}

	private void start()
	{
		pendingLink = input.getText().toString().trim();
		if (pendingLink.isEmpty())
		{
			status.setText("empty link");
			return;
		}
		Intent prepare = VpnService.prepare(this);
		if (prepare != null)
		{
			startActivityForResult(prepare, REQUEST_VPN);
		}
		else
		{
			launch();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_VPN && resultCode == RESULT_OK)
		{
			launch();
		}
	}

	private void launch()
	{
		status.setText("starting: " + V2RayConfigBuilder.name(pendingLink));
		Intent i = new Intent(this, SingBoxVpnService.class);
		i.setAction(SingBoxVpnService.ACTION_START);
		i.putExtra(SingBoxVpnService.EXTRA_LINK, pendingLink);
		ContextCompat.startForegroundService(this, i);
	}
}
