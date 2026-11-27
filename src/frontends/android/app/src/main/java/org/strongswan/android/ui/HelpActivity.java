package org.strongswan.android.ui;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.strongswan.android.R;
import org.strongswan.android.ui.widget.UiStyle;

public class HelpActivity extends AppCompatActivity
{
	private static final int[] ICONS = {
		R.drawable.ic_bolt, R.drawable.ic_shield, R.drawable.ic_cloud_download, R.drawable.ic_speed,
		R.drawable.ic_touch, R.drawable.ic_backup, R.drawable.ic_battery, R.drawable.ic_build, R.drawable.ic_bulb
	};
	private static final int[] TITLES = {
		R.string.help_s1_title, R.string.help_s2_title, R.string.help_s3_title, R.string.help_s4_title,
		R.string.help_s5_title, R.string.help_s6_title, R.string.help_s7_title, R.string.help_s8_title, R.string.help_s9_title
	};
	private static final int[] BODIES = {
		R.string.help_s1_body, R.string.help_s2_body, R.string.help_s3_body, R.string.help_s4_body,
		R.string.help_s5_body, R.string.help_s6_body, R.string.help_s7_body, R.string.help_s8_body, R.string.help_s9_body
	};
	private static final String[] COLORS = {
		"#4ADE80", "#38BDF8", "#A78BFA", "#FB923C", "#F472B6", "#FACC15", "#2DD4BF", "#F87171", "#818CF8"
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (getSupportActionBar() != null)
		{
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		final ScrollView scroll = new ScrollView(this);
		scroll.setBackgroundColor(Color.parseColor("#0F172A"));
		scroll.setClipToPadding(false);
		scroll.setFillViewport(true);

		final LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		int pad = UiStyle.dp(this, 14);
		column.setPadding(pad, pad, pad, pad);
		scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		setContentView(scroll);

		ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
			v.setPadding(bars.left, 0, bars.right, bars.bottom);
			return insets;
		});

		column.addView(buildHero());
		for (int i = 0; i < TITLES.length; i++)
		{
			View card = buildSection(i);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = UiStyle.dp(this, 12);
			column.addView(card, lp);
			card.setAlpha(0f);
			card.setTranslationY(UiStyle.dp(this, 24));
			card.animate().alpha(1f).translationY(0f).setStartDelay(80L * (i + 1)).setDuration(320).start();
		}

		TextView footer = new TextView(this);
		String version = "?";
		try
		{
			version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}
		catch (PackageManager.NameNotFoundException ignored)
		{
		}
		footer.setText(getString(R.string.app_name) + " \u00B7 " + getString(R.string.help_version, version));
		footer.setTextColor(Color.parseColor("#64748B"));
		footer.setTextSize(12);
		footer.setGravity(Gravity.CENTER);
		footer.setPadding(0, UiStyle.dp(this, 20), 0, UiStyle.dp(this, 8));
		column.addView(footer);
	}

	private View buildHero()
	{
		LinearLayout hero = new LinearLayout(this);
		hero.setOrientation(LinearLayout.HORIZONTAL);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		int p = UiStyle.dp(this, 20);
		hero.setPadding(p, p, p, p);
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
			new int[]{Color.parseColor("#4ADE80"), Color.parseColor("#38BDF8")});
		bg.setCornerRadius(UiStyle.dp(this, 26));
		hero.setBackground(bg);
		hero.setElevation(UiStyle.dp(this, 4));

		ImageView logo = new ImageView(this);
		logo.setImageResource(R.mipmap.ic_launcher);
		hero.addView(logo, new LinearLayout.LayoutParams(UiStyle.dp(this, 64), UiStyle.dp(this, 64)));

		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		tlp.setMarginStart(UiStyle.dp(this, 16));

		TextView title = new TextView(this);
		title.setText(R.string.help_hero_title);
		title.setTextColor(Color.parseColor("#04210F"));
		title.setTextSize(21);
		title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		texts.addView(title);

		TextView subtitle = new TextView(this);
		subtitle.setText(R.string.help_hero_subtitle);
		subtitle.setTextColor(Color.parseColor("#0B3B2A"));
		subtitle.setTextSize(13.5f);
		subtitle.setLineSpacing(UiStyle.dp(this, 2), 1f);
		subtitle.setPadding(0, UiStyle.dp(this, 6), 0, 0);
		texts.addView(subtitle);

		hero.addView(texts, tlp);
		return hero;
	}

	private View buildSection(int index)
	{
		final int accent = Color.parseColor(COLORS[index % COLORS.length]);

		final LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Color.parseColor("#1E293B"));
		bg.setCornerRadius(UiStyle.dp(this, 22));
		bg.setStroke(UiStyle.dp(this, 1), Color.parseColor("#334155"));
		card.setBackground(bg);

		LinearLayout header = new LinearLayout(this);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		int hp = UiStyle.dp(this, 14);
		header.setPadding(hp, hp, hp, hp);
		header.setClickable(true);
		header.setFocusable(true);
		android.util.TypedValue tv = new android.util.TypedValue();
		getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
		header.setBackgroundResource(tv.resourceId);

		FrameLayoutHolder.addIcon(this, header, ICONS[index], accent);

		TextView title = new TextView(this);
		title.setText(TITLES[index]);
		title.setTextColor(Color.parseColor("#F1F5F9"));
		title.setTextSize(16);
		title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		titleLp.setMarginStart(UiStyle.dp(this, 12));
		header.addView(title, titleLp);

		final ImageView chevron = new ImageView(this);
		chevron.setImageResource(R.drawable.ic_expand);
		chevron.setImageTintList(ColorStateList.valueOf(Color.parseColor("#94A3B8")));
		header.addView(chevron, new LinearLayout.LayoutParams(UiStyle.dp(this, 24), UiStyle.dp(this, 24)));
		card.addView(header);

		final LinearLayout body = new LinearLayout(this);
		body.setOrientation(LinearLayout.VERTICAL);
		int bp = UiStyle.dp(this, 16);
		body.setPadding(bp, 0, bp, bp);
		String[] lines = getString(BODIES[index]).split("\n");
		for (String line : lines)
		{
			String text = line.trim();
			if (text.isEmpty())
			{
				continue;
			}
			if (text.startsWith("\u2022"))
			{
				text = text.substring(1).trim();
			}
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setPadding(0, UiStyle.dp(this, 5), 0, UiStyle.dp(this, 5));

			View dot = new View(this);
			GradientDrawable dotBg = new GradientDrawable();
			dotBg.setShape(GradientDrawable.OVAL);
			dotBg.setColor(accent);
			dot.setBackground(dotBg);
			LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(UiStyle.dp(this, 7), UiStyle.dp(this, 7));
			dotLp.topMargin = UiStyle.dp(this, 8);
			dotLp.setMarginEnd(UiStyle.dp(this, 10));
			row.addView(dot, dotLp);

			TextView tx = new TextView(this);
			tx.setText(text);
			tx.setTextColor(Color.parseColor("#CBD5E1"));
			tx.setTextSize(14.5f);
			tx.setLineSpacing(UiStyle.dp(this, 3), 1f);
			row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			body.addView(row);
		}
		card.addView(body);

		final boolean[] open = {index == 0};
		body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
		chevron.setRotation(open[0] ? 180f : 0f);
		header.setOnClickListener(v -> {
			open[0] = !open[0];
			TransitionManager.beginDelayedTransition((ViewGroup) card.getParent(), new AutoTransition());
			body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
			chevron.animate().rotation(open[0] ? 180f : 0f).setDuration(220).start();
		});
		return card;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home)
		{
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private static final class FrameLayoutHolder
	{
		static void addIcon(HelpActivity activity, LinearLayout header, int iconRes, int accent)
		{
			android.widget.FrameLayout circle = new android.widget.FrameLayout(activity);
			GradientDrawable bg = new GradientDrawable();
			bg.setShape(GradientDrawable.OVAL);
			bg.setColor((accent & 0x00FFFFFF) | 0x26000000);
			circle.setBackground(bg);
			ImageView icon = new ImageView(activity);
			icon.setImageResource(iconRes);
			icon.setImageTintList(ColorStateList.valueOf(accent));
			android.widget.FrameLayout.LayoutParams ilp = new android.widget.FrameLayout.LayoutParams(UiStyle.dp(activity, 22), UiStyle.dp(activity, 22));
			ilp.gravity = Gravity.CENTER;
			circle.addView(icon, ilp);
			header.addView(circle, new LinearLayout.LayoutParams(UiStyle.dp(activity, 42), UiStyle.dp(activity, 42)));
		}
	}
}
