package com.boardgamegeek.service;

import static com.boardgamegeek.util.LogUtils.LOGD;
import static com.boardgamegeek.util.LogUtils.LOGE;
import static com.boardgamegeek.util.LogUtils.LOGW;
import static com.boardgamegeek.util.LogUtils.makeLogTag;

import org.apache.http.client.HttpClient;

import com.boardgamegeek.R;
import com.boardgamegeek.auth.Authenticator;
import com.boardgamegeek.io.RemoteExecutor;
import com.boardgamegeek.io.XmlHandler.HandlerException;
import com.boardgamegeek.provider.BggContract;
import com.boardgamegeek.util.DetachableResultReceiver;
import com.boardgamegeek.util.HttpUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;

public class UpdateService extends IntentService {
	private static final String TAG = makeLogTag(UpdateService.class);

	public static final String KEY_SYNC_TYPE = "KEY_SYNC_TYPE";
	public static final String KEY_SYNC_ID = "KEY_SYNC_ID";
	public static final String KEY_STATUS_RECEIVER = "com.boardgamegeek.extra.STATUS_RECEIVER";

	public static final int SYNC_TYPE_UNKNOWN = 0;
	public static final int SYNC_TYPE_GAME = 1;
	public static final int SYNC_TYPE_GAME_PLAYS = 2;
	public static final int SYNC_TYPE_PLAYS_UPLOAD = 3;
	public static final int SYNC_TYPE_DESIGNER = 10;
	public static final int SYNC_TYPE_ARTIST = 11;
	public static final int SYNC_TYPE_PUBLISHER = 12;

	public static final int STATUS_RUNNING = 1;
	public static final int STATUS_COMPLETE = 2;
	public static final int STATUS_ERROR = 3;

	private RemoteExecutor mRemoteExecutor;
	private ResultReceiver mResultReceiver;
	private static boolean mUseGzip = true;

	public static void start(Context context, int type, int id, DetachableResultReceiver receiver) {
		context.startService(new Intent(Intent.ACTION_SYNC, null, context, UpdateService.class)
			.putExtra(UpdateService.KEY_SYNC_TYPE, type).putExtra(KEY_SYNC_ID, id)
			.putExtra(UpdateService.KEY_STATUS_RECEIVER, receiver));
	}

	public UpdateService() {
		super(TAG);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mRemoteExecutor = createExecutor();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		LOGD(TAG, "onHandleIntent(intent=" + intent + ")");

		if (!Intent.ACTION_SYNC.equals(intent.getAction())) {
			LOGW(TAG, "Invalid intent action: " + intent.getAction());
			return;
		}
		int syncType = intent.getIntExtra(KEY_SYNC_TYPE, SYNC_TYPE_UNKNOWN);
		int syncId = intent.getIntExtra(KEY_SYNC_ID, BggContract.INVALID_ID);
		mResultReceiver = intent.getParcelableExtra(KEY_STATUS_RECEIVER);

		SyncTask task = null;
		if (syncId == BggContract.INVALID_ID) {
			switch (syncType) {
				case SYNC_TYPE_PLAYS_UPLOAD:
					task = new SyncPlaysUpload();
					break;
			}
		} else {
			switch (syncType) {
				case SYNC_TYPE_GAME:
					task = new SyncGame(syncId);
					break;
				case SYNC_TYPE_GAME_PLAYS:
					task = new SyncGamePlays(syncId);
					break;
				case SYNC_TYPE_DESIGNER:
					task = new SyncDesigner(syncId);
					break;
				case SYNC_TYPE_ARTIST:
					task = new SyncArtist(syncId);
					break;
				case SYNC_TYPE_PUBLISHER:
					task = new SyncPublisher(syncId);
					break;
			}
		}

		if (task == null) {
			sendResultToReceiver(STATUS_ERROR, "Invalid task requested.");
			return;
		}

		if (mRemoteExecutor != null) {
			mRemoteExecutor = createExecutor();
		}

		if (mRemoteExecutor == null) {
			sendResultToReceiver(STATUS_ERROR, "Unable to create executor.");
			return;
		}

		final long startTime = System.currentTimeMillis();
		sendResultToReceiver(STATUS_RUNNING);
		try {
			task.execute(mRemoteExecutor, this);
			if (task.isBggDown()) {
				String message = getResources().getString(R.string.notification_bgg_down);
				LOGD(TAG, message);
				sendResultToReceiver(STATUS_ERROR, message);
			}
			LOGD(TAG, "Sync took " + (System.currentTimeMillis() - startTime) + "ms with GZIP "
				+ (mUseGzip ? "on" : "off"));
		} catch (HandlerException e) {
			LOGE(TAG, "Failed during sync type " + syncType, e);
			LOGD(TAG, "Sync failed in " + (System.currentTimeMillis() - startTime) + "ms with GZIP "
				+ (mUseGzip ? "on" : "off"));
			sendResultToReceiver(STATUS_ERROR, e.toString());
		} finally {
			sendResultToReceiver(STATUS_COMPLETE);
			mResultReceiver = null;
		}
	}

	private RemoteExecutor createExecutor() {
		AccountManager accountManager = AccountManager.get(getApplicationContext());
		Account account = Authenticator.getAccount(accountManager);
		if (account == null) {
			LOGW(TAG, "no account set up");
			return null;
		}

		HttpClient mHttpClient = HttpUtils.createHttpClient(this, account.name, accountManager.getPassword(account),
			Long.parseLong(accountManager.getUserData(account, Authenticator.KEY_PASSWORD_EXPIRY)), mUseGzip);
		return new RemoteExecutor(mHttpClient, this);
	}

	private void sendResultToReceiver(int resultCode) {
		sendResultToReceiver(resultCode, null);
	}

	private void sendResultToReceiver(int resultCode, String message) {
		if (mResultReceiver != null) {
			Bundle bundle = Bundle.EMPTY;
			if (!TextUtils.isEmpty(message)) {
				bundle = new Bundle();
				bundle.putString(Intent.EXTRA_TEXT, message);
			}
			mResultReceiver.send(resultCode, bundle);
		}
	}
}