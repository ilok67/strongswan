package org.strongswan.android.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

public class AboutActivity extends AppCompatActivity
{
	private static final String TELEGRAM_URL = "https://t.me/falconer1367";
	private static final String GITHUB_URL = "https://github.com/ilok67/strongswan";

	private int step;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (getSupportActionBar() != null)
		{
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		ScrollView scroll = new ScrollView(this);
		scroll.setBackgroundColor(Color.parseColor("#0F172A"));
		scroll.setClipToPadding(false);
		scroll.setFillViewport(true);

		LinearLayout column = new LinearLayout(this);
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

		String version = "?";
		try
		{
			version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		}
		catch (PackageManager.NameNotFoundException ignored)
		{
		}

		addAnimated(column, buildHero(version), 0, 0);

		LinearLayout appCard = card();
		addSectionTitle(appCard, R.drawable.ic_info, "#38BDF8", R.string.about_section_app);
		TextView body = new TextView(this);
		body.setText(R.string.about_app_body);
		body.setTextColor(Color.parseColor("#CBD5E1"));
		body.setTextSize(14.5f);
		body.setLineSpacing(UiStyle.dp(this, 3), 1f);
		body.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
		body.setPadding(UiStyle.dp(this, 16), 0, UiStyle.dp(this, 16), UiStyle.dp(this, 16));
		appCard.addView(body);
		addAnimated(column, appCard, 12, ++step);

		LinearLayout builtCard = card();
		addSectionTitle(builtCard, R.drawable.ic_shield, "#A78BFA", R.string.about_section_built);
		addInfoRow(builtCard, R.drawable.ic_shield, "#4ADE80", "strongSwan", getString(R.string.about_built_strongswan));
		addInfoRow(builtCard, R.drawable.ic_bolt, "#FACC15", "sing-box", getString(R.string.about_built_singbox));
		addInfoRow(builtCard, R.drawable.ic_speed, "#38BDF8", "AmneziaWG", getString(R.string.about_built_awg));
		addAnimated(column, builtCard, 12, ++step);

		LinearLayout linksCard = card();
		addSectionTitle(linksCard, R.drawable.ic_open, "#F472B6", R.string.about_section_contact);
		addLinkRow(linksCard, R.drawable.ic_telegram, "#38BDF8", getString(R.string.about_telegram),
			getString(R.string.about_telegram_sub), TELEGRAM_URL);
		addLinkRow(linksCard, R.drawable.ic_code, "#A78BFA", getString(R.string.about_github),
			getString(R.string.about_github_sub), GITHUB_URL);
		addAnimated(column, linksCard, 12, ++step);

		addAnimated(column, buildNotice(), 12, ++step);

		TextView footer = new TextView(this);
		footer.setText(R.string.about_footer);
		footer.setTextColor(Color.parseColor("#64748B"));
		footer.setTextSize(12);
		footer.setGravity(Gravity.CENTER);
		footer.setPadding(0, UiStyle.dp(this, 20), 0, UiStyle.dp(this, 8));
		column.addView(footer);
	}

	private void addAnimated(LinearLayout column, View view, int topMarginDp, int order)
	{
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = UiStyle.dp(this, topMarginDp);
		column.addView(view, lp);
		view.setAlpha(0f);
		view.setTranslationY(UiStyle.dp(this, 24));
		view.animate().alpha(1f).translationY(0f).setStartDelay(90L * order).setDuration(340).start();
	}

	private View buildHero(String version)
	{
		LinearLayout hero = new LinearLayout(this);
		hero.setOrientation(LinearLayout.VERTICAL);
		hero.setGravity(Gravity.CENTER_HORIZONTAL);
		int p = UiStyle.dp(this, 24);
		hero.setPadding(p, p + UiStyle.dp(this, 4), p, p);
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
			new int[]{Color.parseColor("#4ADE80"), Color.parseColor("#38BDF8")});
		bg.setCornerRadius(UiStyle.dp(this, 28));
		hero.setBackground(bg);
		hero.setElevation(UiStyle.dp(this, 4));

		FrameLayout logoFrame = new FrameLayout(this);
		GradientDrawable logoBg = new GradientDrawable();
		logoBg.setShape(GradientDrawable.OVAL);
		logoBg.setColor(Color.parseColor("#33FFFFFF"));
		logoFrame.setBackground(logoBg);
		ImageView logo = new ImageView(this);
		logo.setImageResource(R.mipmap.ic_launcher);
		FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(UiStyle.dp(this, 76), UiStyle.dp(this, 76));
		llp.gravity = Gravity.CENTER;
		logoFrame.addView(logo, llp);
		hero.addView(logoFrame, new LinearLayout.LayoutParams(UiStyle.dp(this, 104), UiStyle.dp(this, 104)));

		TextView name = new TextView(this);
		name.setText(R.string.app_name);
		name.setTextColor(Color.parseColor("#04210F"));
		name.setTextSize(24);
		name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		name.setGravity(Gravity.CENTER);
		name.setPadding(0, UiStyle.dp(this, 14), 0, 0);
		hero.addView(name);

		TextView tagline = new TextView(this);
		tagline.setText(R.string.about_tagline);
		tagline.setTextColor(Color.parseColor("#0B3B2A"));
		tagline.setTextSize(14);
		tagline.setGravity(Gravity.CENTER);
		tagline.setPadding(0, UiStyle.dp(this, 4), 0, UiStyle.dp(this, 14));
		hero.addView(tagline);

		LinearLayout chips = new LinearLayout(this);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.setGravity(Gravity.CENTER);
		chips.addView(chip(getString(R.string.help_version, version), true));
		chips.addView(chip("IKEv2", false));
		chips.addView(chip("sing-box", false));
		chips.addView(chip("AmneziaWG", false));
		HorizontalWrap.wrap(hero, chips);
		return hero;
	}

	private TextView chip(String text, boolean filled)
	{
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextSize(12);
		tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		tv.setTextColor(filled ? Color.parseColor("#F0FDF4") : Color.parseColor("#04210F"));
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(UiStyle.dp(this, 20));
		bg.setColor(filled ? Color.parseColor("#0F3D2A") : Color.parseColor("#40FFFFFF"));
		tv.setBackground(bg);
		tv.setPadding(UiStyle.dp(this, 10), UiStyle.dp(this, 5), UiStyle.dp(this, 10), UiStyle.dp(this, 5));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.setMargins(UiStyle.dp(this, 3), 0, UiStyle.dp(this, 3), 0);
		tv.setLayoutParams(lp);
		return tv;
	}

	private LinearLayout card()
	{
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Color.parseColor("#1E293B"));
		bg.setCornerRadius(UiStyle.dp(this, 22));
		bg.setStroke(UiStyle.dp(this, 1), Color.parseColor("#334155"));
		card.setBackground(bg);
		return card;
	}

	private View iconCircle(int iconRes, int accent, int sizeDp)
	{
		FrameLayout circle = new FrameLayout(this);
		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor((accent & 0x00FFFFFF) | 0x26000000);
		circle.setBackground(bg);
		ImageView icon = new ImageView(this);
		icon.setImageResource(iconRes);
		icon.setImageTintList(ColorStateList.valueOf(accent));
		FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(UiStyle.dp(this, sizeDp / 2 + 2), UiStyle.dp(this, sizeDp / 2 + 2));
		ilp.gravity = Gravity.CENTER;
		circle.addView(icon, ilp);
		circle.setLayoutParams(new LinearLayout.LayoutParams(UiStyle.dp(this, sizeDp), UiStyle.dp(this, sizeDp)));
		return circle;
	}

	private void addSectionTitle(LinearLayout card, int iconRes, String color, int titleRes)
	{
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		int hp = UiStyle.dp(this, 14);
		row.setPadding(hp, hp, hp, hp);
		row.addView(iconCircle(iconRes, Color.parseColor(color), 40));
		TextView title = new TextView(this);
		title.setText(titleRes);
		title.setTextColor(Color.parseColor("#F1F5F9"));
		title.setTextSize(16);
		title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		lp.setMarginStart(UiStyle.dp(this, 12));
		row.addView(title, lp);
		card.addView(row);
	}

	private void addInfoRow(LinearLayout card, int iconRes, String color, String title, String subtitle)
	{
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(UiStyle.dp(this, 16), UiStyle.dp(this, 6), UiStyle.dp(this, 16), UiStyle.dp(this, 12));
		row.addView(iconCircle(iconRes, Color.parseColor(color), 36));
		row.addView(twoLineText(title, subtitle));
		card.addView(row);
	}

	private void addLinkRow(LinearLayout card, int iconRes, String color, String title, String subtitle, final String url)
	{
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(UiStyle.dp(this, 16), UiStyle.dp(this, 10), UiStyle.dp(this, 16), UiStyle.dp(this, 10));
		TypedValue tv = new TypedValue();
		getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
		row.setBackgroundResource(tv.resourceId);
		row.setClickable(true);
		row.setFocusable(true);
		row.addView(iconCircle(iconRes, Color.parseColor(color), 40));
		row.addView(twoLineText(title, subtitle));
		ImageView open = new ImageView(this);
		open.setImageResource(R.drawable.ic_open);
		open.setImageTintList(ColorStateList.valueOf(Color.parseColor("#94A3B8")));
		row.addView(open, new LinearLayout.LayoutParams(UiStyle.dp(this, 20), UiStyle.dp(this, 20)));
		row.setOnClickListener(v -> openUrl(url));
		card.addView(row);
	}

	private View twoLineText(String title, String subtitle)
	{
		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		lp.setMarginStart(UiStyle.dp(this, 12));
		lp.setMarginEnd(UiStyle.dp(this, 8));
		texts.setLayoutParams(lp);

		TextView t = new TextView(this);
		t.setText(title);
		t.setTextColor(Color.parseColor("#F1F5F9"));
		t.setTextSize(15);
		t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
		t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
		t.setTextDirection(View.TEXT_DIRECTION_LOCALE);
		texts.addView(t);

		TextView s = new TextView(this);
		s.setText(subtitle);
		s.setTextColor(Color.parseColor("#94A3B8"));
		s.setTextSize(12.5f);
		s.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
		s.setTextDirection(View.TEXT_DIRECTION_LOCALE);
		texts.addView(s);
		return texts;
	}

	private View buildNotice()
	{
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		int p = UiStyle.dp(this, 14);
		card.setPadding(p, p, p, p);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Color.parseColor("#2A2110"));
		bg.setCornerRadius(UiStyle.dp(this, 22));
		bg.setStroke(UiStyle.dp(this, 1), Color.parseColor("#5C4A1A"));
		card.setBackground(bg);
		card.addView(iconCircle(R.drawable.ic_info, Color.parseColor("#FBBF24"), 40));

		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		lp.setMarginStart(UiStyle.dp(this, 12));
		TextView t = new TextView(this);
		t.setText(R.string.about_notice_title);
		t.setTextColor(Color.parseColor("#FDE68A"));
		t.setTextSize(15);
		t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
		t.setTextDirection(View.TEXT_DIRECTION_LOCALE);
		texts.addView(t);
		TextView s = new TextView(this);
		s.setText(R.string.about_notice_body);
		s.setTextColor(Color.parseColor("#E7D8A8"));
		s.setTextSize(13);
		s.setLineSpacing(UiStyle.dp(this, 2), 1f);
		s.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
		s.setTextDirection(View.TEXT_DIRECTION_LOCALE);
		texts.addView(s);
		card.addView(texts, lp);
		return card;
	}

	private void openUrl(String url)
	{
		try
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		}
		catch (ActivityNotFoundException ignored)
		{
		}
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

	private static final class HorizontalWrap
	{
		static void wrap(LinearLayout parent, LinearLayout chips)
		{
			android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(parent.getContext());
			scroller.setHorizontalScrollBarEnabled(false);
			scroller.setFillViewport(true);
			scroller.addView(chips, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			parent.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
	}
}
