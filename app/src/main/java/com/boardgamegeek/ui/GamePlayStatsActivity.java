package com.boardgamegeek.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.MenuItem;

import com.boardgamegeek.R;
import com.boardgamegeek.provider.BggContract;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;

public class GamePlayStatsActivity extends SimpleSinglePaneActivity {
	private static final String KEY_GAME_ID = "GAME_ID";
	private static final String KEY_GAME_NAME = "GAME_NAME";
	private static final String KEY_HEADER_COLOR = "HEADER_COLOR";
	private static final String KEY_PLAY_COUNT_COLORS = "PLAY_COUNT_COLORS";
	private int gameId;
	private String gameName;
	@ColorInt private int headerColor;
	@ColorInt private int[] playCountColors;

	public static void start(Context context, int gameId, String gameName, @ColorInt int headerColor, @ColorInt int[] gameColors) {
		Intent starter = new Intent(context, GamePlayStatsActivity.class);
		starter.putExtra(KEY_GAME_ID, gameId);
		starter.putExtra(KEY_GAME_NAME, gameName);
		starter.putExtra(KEY_HEADER_COLOR, headerColor);
		starter.putExtra(KEY_PLAY_COUNT_COLORS, gameColors);
		context.startActivity(starter);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!TextUtils.isEmpty(gameName)) {
			ActionBar actionBar = getSupportActionBar();
			if (actionBar != null) {
				actionBar.setSubtitle(gameName);
			}
		}

		if (savedInstanceState == null) {
			Answers.getInstance().logContentView(new ContentViewEvent()
				.putContentType("GamePlayStats")
				.putContentId(String.valueOf(gameId))
				.putContentName(gameName));
		}
	}

	@Override
	protected void readIntent(Intent intent) {
		gameId = intent.getIntExtra(KEY_GAME_ID, BggContract.INVALID_ID);
		gameName = intent.getStringExtra(KEY_GAME_NAME);
		headerColor = intent.getIntExtra(KEY_HEADER_COLOR, getResources().getColor(R.color.accent));
		playCountColors = intent.getIntArrayExtra(KEY_PLAY_COUNT_COLORS);
	}

	@Override
	protected Fragment onCreatePane(Intent intent) {
		return GamePlayStatsFragment.newInstance(gameId, headerColor, playCountColors);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				GameActivity.startUp(this, gameId, gameName);
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}