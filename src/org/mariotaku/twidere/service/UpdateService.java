package org.mariotaku.twidere.service;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.mariotaku.twidere.Constants;
import org.mariotaku.twidere.IUpdateService;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.provider.TweetStore;
import org.mariotaku.twidere.provider.TweetStore.Mentions;
import org.mariotaku.twidere.provider.TweetStore.Statuses;
import org.mariotaku.twidere.util.AsyncTaskManager;
import org.mariotaku.twidere.util.CommonUtils;
import org.mariotaku.twidere.util.ManagedAsyncTask;

import roboguice.service.RoboService;
import twitter4j.GeoLocation;
import twitter4j.MediaEntity;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

public class UpdateService extends RoboService implements Constants {

	private final ServiceStub mBinder = new ServiceStub(this);
	private AsyncTaskManager mAsyncTaskManager;

	private int mRefreshHomeTimelineTaskId, mRefreshMentionsTaskId;

	public int createFavorite(long[] account_ids, long status_id) {
		CreateFavoriteTask task = new CreateFavoriteTask(account_ids, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFavorite(long[] account_ids, long status_id) {
		DestroyFavoriteTask task = new DestroyFavoriteTask(account_ids, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyStatus(long account_id, long status_id) {
		DestroyStatusTask task = new DestroyStatusTask(account_id, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public boolean hasActivatedTask() {
		return mAsyncTaskManager.hasActivatedTask();
	}

	public boolean isHomeTimelineRefreshing() {
		return mAsyncTaskManager.isExcuting(mRefreshHomeTimelineTaskId);
	}

	public boolean isMentionsRefreshing() {
		return mAsyncTaskManager.isExcuting(mRefreshMentionsTaskId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mAsyncTaskManager = ((TwidereApplication) getApplication()).getAsyncTaskManager();
	}

	public int refreshHomeTimeline(long[] account_ids, long[] max_ids) {
		mAsyncTaskManager.cancel(mRefreshHomeTimelineTaskId);
		GetHomeTimelineTask task = new GetHomeTimelineTask(account_ids, max_ids);
		return mRefreshHomeTimelineTaskId = mAsyncTaskManager.add(task, true);
	}

	public int refreshMentions(long[] account_ids, long[] max_ids) {
		mAsyncTaskManager.cancel(mRefreshMentionsTaskId);
		GetMentionsTask task = new GetMentionsTask(account_ids, max_ids);
		return mRefreshMentionsTaskId = mAsyncTaskManager.add(task, true);
	}

	public int refreshMessages(long[] account_ids, long[] max_ids) {
		return -1;
	}

	public int retweetStatus(long[] account_ids, long status_id) {
		RetweetStatusTask task = new RetweetStatusTask(account_ids, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateStatus(long[] account_ids, String content, Location location, Uri image_uri,
			long in_reply_to) {
		UpdateStatusTask task = new UpdateStatusTask(account_ids, content, location, image_uri,
				in_reply_to);
		return mAsyncTaskManager.add(task, true);
	}

	private class CreateFavoriteTask extends
			ManagedAsyncTask<Object, Void, List<CreateFavoriteTask.AccountResponce>> {

		private long[] account_ids;
		private long status_id;

		public CreateFavoriteTask(long[] account_ids, long status_id) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.status_id = status_id;
		}

		@Override
		protected List<AccountResponce> doInBackground(Object... params) {

			if (account_ids == null) return null;

			List<AccountResponce> result = new ArrayList<AccountResponce>();

			for (long account_id : account_ids) {
				Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
				if (twitter != null) {
					try {
						twitter4j.Status status = twitter.createFavorite(status_id);
						result.add(new AccountResponce(account_id, status));
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<AccountResponce> result) {
			ContentResolver resolver = getContentResolver();

			for (AccountResponce responce : result) {
				ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 1);
				StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + "=" + responce.account_id);
				where.append(" AND " + Statuses.STATUS_ID + "=" + responce.status.getId());
				for (Uri uri : TweetStore.STATUSES_URIS) {
					resolver.update(uri, values, where.toString(), null);
				}
			}
			super.onPostExecute(result);
		}

		private class AccountResponce {

			public long account_id;
			public twitter4j.Status status;

			public AccountResponce(long account_id, twitter4j.Status status) {
				this.account_id = account_id;
				this.status = status;
			}
		}

	}

	private class DestroyFavoriteTask extends
			ManagedAsyncTask<Object, Void, List<DestroyFavoriteTask.AccountResponce>> {

		private long[] account_ids;
		private long status_id;

		public DestroyFavoriteTask(long[] account_ids, long status_id) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.status_id = status_id;
		}

		@Override
		protected List<AccountResponce> doInBackground(Object... params) {

			if (account_ids == null) return null;

			List<AccountResponce> result = new ArrayList<AccountResponce>();

			for (long account_id : account_ids) {
				Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
				if (twitter != null) {
					try {
						twitter4j.Status status = twitter.destroyFavorite(status_id);
						result.add(new AccountResponce(account_id, status));
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<AccountResponce> result) {
			ContentResolver resolver = getContentResolver();

			for (AccountResponce responce : result) {
				ContentValues values = new ContentValues();
				values.put(Statuses.IS_FAVORITE, 0);
				StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + "=" + responce.account_id);
				where.append(" AND " + Statuses.STATUS_ID + "=" + responce.status.getId());
				for (Uri uri : TweetStore.STATUSES_URIS) {
					resolver.update(uri, values, where.toString(), null);
				}
			}
			super.onPostExecute(result);
		}

		private class AccountResponce {

			public long account_id;
			public twitter4j.Status status;

			public AccountResponce(long account_id, twitter4j.Status status) {
				this.account_id = account_id;
				this.status = status;
			}
		}

	}

	private class DestroyStatusTask extends
			ManagedAsyncTask<Object, Void, DestroyStatusTask.AccountResponce> {

		private long account_id;
		private long status_id;

		public DestroyStatusTask(long account_id, long status_id) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected AccountResponce doInBackground(Object... params) {

			Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
			if (twitter != null) {
				try {
					twitter4j.Status status = twitter.destroyStatus(status_id);
					return new AccountResponce(account_id, status);
				} catch (TwitterException e) {
					e.printStackTrace();
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(AccountResponce result) {
			ContentResolver resolver = getContentResolver();
			StringBuilder where = new StringBuilder();
			where.append(Statuses.ACCOUNT_ID + "=" + result.account_id);
			where.append(" AND " + Statuses.STATUS_ID + "=" + result.status.getId());
			for (Uri uri : TweetStore.STATUSES_URIS) {
				resolver.delete(uri, where.toString(), null);
			}
			super.onPostExecute(result);
		}

		private class AccountResponce {

			public long account_id;
			public twitter4j.Status status;

			public AccountResponce(long account_id, twitter4j.Status status) {
				this.account_id = account_id;
				this.status = status;
			}
		}

	}

	private class GetHomeTimelineTask extends
			ManagedAsyncTask<Object, Void, List<GetHomeTimelineTask.AccountResponce>> {

		private long[] account_ids, max_ids;

		public GetHomeTimelineTask(long[] account_ids, long[] max_ids) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
		}

		@Override
		protected List<AccountResponce> doInBackground(Object... params) {

			List<AccountResponce> result = new ArrayList<AccountResponce>();

			if (account_ids == null) return result;

			boolean since_valid = max_ids != null && max_ids.length == account_ids.length;

			int idx = 0;
			for (long account_id : account_ids) {
				Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
				if (twitter != null) {
					try {
						Paging paging = new Paging();
						if (since_valid) {
							paging.setMaxId(max_ids[idx]);
						}
						ResponseList<twitter4j.Status> statuses = twitter.getHomeTimeline(paging);
						result.add(new AccountResponce(account_id, statuses));
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
				idx++;
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<AccountResponce> responces) {
			ContentResolver resolver = getContentResolver();

			for (AccountResponce responce : responces) {
				ResponseList<twitter4j.Status> statuses = responce.responselist;
				long account_id = responce.account_id;

				if (statuses == null || statuses.size() <= 0) return;
				List<ContentValues> values_list = new ArrayList<ContentValues>();

				long min_id = -1, max_id = -1;
				for (twitter4j.Status status : statuses) {
					if (status == null) continue;
					ContentValues values = new ContentValues();
					User user = status.getUser();
					long status_id = status.getId();
					MediaEntity[] medias = status.getMediaEntities();
					values.put(Statuses.STATUS_ID, status_id);
					values.put(Statuses.ACCOUNT_ID, account_id);
					values.put(Statuses.USER_ID, user.getId());
					values.put(Statuses.STATUS_TIMESTAMP, status.getCreatedAt().getTime());
					values.put(Statuses.TEXT, CommonUtils.formatStatusString(status));
					values.put(Statuses.NAME, user.getName());
					values.put(Statuses.SCREEN_NAME, user.getScreenName());
					values.put(Statuses.PROFILE_IMAGE_URL, user.getProfileImageURL().toString());
					values.put(Statuses.IS_RETWEET, status.isRetweet() ? 1 : 0);
					values.put(Statuses.IS_FAVORITE, status.isFavorited() ? 1 : 0);
					values.put(Statuses.IN_REPLY_TO_SCREEN_NAME, status.getInReplyToScreenName());
					values.put(Statuses.IN_REPLY_TO_STATUS_ID, status.getInReplyToStatusId());
					values.put(Statuses.IN_REPLY_TO_USER_ID, status.getInReplyToUserId());
					values.put(Statuses.HAS_MEDIA, medias != null && medias.length > 0 ? 1 : 0);
					values.put(Statuses.HAS_LOCATION, status.getGeoLocation() != null ? 1 : 0);
					values.put(Statuses.SOURCE, status.getSource());

					if (status_id < min_id || min_id == -1) {
						min_id = status_id;
					}
					if (status_id > max_id || max_id == -1) {
						max_id = status_id;
					}

					values_list.add(values);
				}
				// Delete all rows conflicting before new data inserted.
				int rows_deleted = -1;
				if (min_id != -1 && max_id != -1) {
					StringBuilder where = new StringBuilder();
					where.append(Statuses.STATUS_ID + ">=" + min_id);
					where.append(" AND " + Statuses.STATUS_ID + "<=" + max_id);

					rows_deleted = resolver.delete(Statuses.CONTENT_URI, where.toString(), null);
				}

				// Insert previously fetched items.
				resolver.bulkInsert(Statuses.CONTENT_URI,
						values_list.toArray(new ContentValues[values_list.size()]));

				// No row deleted, so I will insert a gap.
				if (rows_deleted == 0) {
					ContentValues values = new ContentValues();
					values.put(Statuses.IS_GAP, 1);
					StringBuilder where = new StringBuilder();
					where.append(Statuses.ACCOUNT_ID + "=" + account_id);
					where.append(" AND " + Statuses.STATUS_ID + "=" + min_id);
					resolver.update(Statuses.CONTENT_URI, values, where.toString(), null);
				}

			}
			super.onPostExecute(responces);
		}

		private class AccountResponce {

			public long account_id;
			public ResponseList<twitter4j.Status> responselist;

			public AccountResponce(long account_id, ResponseList<twitter4j.Status> responselist) {
				this.account_id = account_id;
				this.responselist = responselist;

			}
		}

	}

	private class GetMentionsTask extends
			ManagedAsyncTask<Object, Void, List<GetMentionsTask.AccountResponce>> {

		private long[] account_ids, max_ids;

		public GetMentionsTask(long[] account_ids, long[] max_ids) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
		}

		@Override
		protected List<AccountResponce> doInBackground(Object... params) {

			List<AccountResponce> result = new ArrayList<AccountResponce>();

			if (account_ids == null) return result;

			boolean since_valid = max_ids != null && max_ids.length == account_ids.length;

			int idx = 0;

			for (long account_id : account_ids) {
				Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
				if (twitter != null) {
					try {
						Paging paging = new Paging();
						if (since_valid) {
							paging.setMaxId(max_ids[idx]);
						}
						ResponseList<twitter4j.Status> mentions = twitter.getMentions(paging);
						result.add(new AccountResponce(account_id, mentions));
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
				idx++;
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<AccountResponce> responces) {
			ContentResolver resolver = getContentResolver();
			for (AccountResponce responce : responces) {
				ResponseList<twitter4j.Status> mentions = responce.responselist;
				long account_id = responce.account_id;
				if (mentions == null) return;
				List<ContentValues> values_list = new ArrayList<ContentValues>();

				long min_id = -1, max_id = -1;
				for (twitter4j.Status mention : mentions) {
					if (mention == null) continue;
					ContentValues values = new ContentValues();
					long status_id = mention.getId();
					MediaEntity[] medias = mention.getMediaEntities();
					User user = mention.getUser();
					values.put(Mentions.ACCOUNT_ID, account_id);
					values.put(Mentions.STATUS_ID, status_id);
					values.put(Mentions.USER_ID, user.getId());
					values.put(Mentions.STATUS_TIMESTAMP, mention.getCreatedAt().getTime());
					values.put(Mentions.TEXT, CommonUtils.formatStatusString(mention));
					values.put(Mentions.NAME, user.getName());
					values.put(Mentions.SCREEN_NAME, user.getScreenName());
					values.put(Mentions.PROFILE_IMAGE_URL, user.getProfileImageURL().toString());
					values.put(Mentions.IS_RETWEET, mention.isRetweet() ? 1 : 0);
					values.put(Mentions.IS_FAVORITE, mention.isFavorited() ? 1 : 0);
					values.put(Mentions.IN_REPLY_TO_SCREEN_NAME, mention.getInReplyToScreenName());
					values.put(Mentions.IN_REPLY_TO_STATUS_ID, mention.getInReplyToStatusId());
					values.put(Mentions.IN_REPLY_TO_USER_ID, mention.getInReplyToUserId());
					values.put(Mentions.HAS_MEDIA, medias != null && medias.length > 0 ? 1 : 0);
					values.put(Mentions.HAS_LOCATION, mention.getGeoLocation() != null ? 1 : 0);
					values.put(Mentions.SOURCE, mention.getSource());

					if (status_id < min_id || min_id == -1) {
						min_id = status_id;
					}
					if (status_id > max_id || max_id == -1) {
						max_id = status_id;
					}

					values_list.add(values);
				}

				// Delete all rows conflicting before new data inserted.
				int rows_deleted = -1;
				if (min_id != -1 && max_id != -1) {
					StringBuilder where = new StringBuilder();
					where.append(Mentions.STATUS_ID + ">=" + min_id);
					where.append(" AND " + Mentions.STATUS_ID + "<=" + max_id);

					rows_deleted = resolver.delete(Mentions.CONTENT_URI, where.toString(), null);
				}

				resolver.bulkInsert(Mentions.CONTENT_URI,
						values_list.toArray(new ContentValues[values_list.size()]));

				// No row deleted, so I will insert a gap.
				if (rows_deleted == 0) {
					ContentValues values = new ContentValues();
					values.put(Mentions.IS_GAP, 1);
					StringBuilder where = new StringBuilder();
					where.append(Mentions.ACCOUNT_ID + "=" + account_id);
					where.append(" AND " + Mentions.STATUS_ID + "=" + min_id);
					resolver.update(Mentions.CONTENT_URI, values, where.toString(), null);
				}
			}
			super.onPostExecute(responces);
		}

		private class AccountResponce {

			public long account_id;
			public ResponseList<twitter4j.Status> responselist;

			public AccountResponce(long account_id, ResponseList<twitter4j.Status> responselist) {
				this.account_id = account_id;
				this.responselist = responselist;

			}
		}

	}

	private class RetweetStatusTask extends
			ManagedAsyncTask<Object, Void, List<RetweetStatusTask.AccountResponce>> {

		private long[] account_ids;
		private long status_id;

		public RetweetStatusTask(long[] account_ids, long status_id) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.status_id = status_id;
		}

		@Override
		protected List<AccountResponce> doInBackground(Object... params) {

			if (account_ids == null) return null;

			List<AccountResponce> result = new ArrayList<AccountResponce>();

			for (long account_id : account_ids) {
				Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
				if (twitter != null) {
					try {
						twitter4j.Status status = twitter.retweetStatus(status_id);
						result.add(new AccountResponce(account_id, status));
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<AccountResponce> result) {
			ContentResolver resolver = getContentResolver();

			for (AccountResponce responce : result) {
				ContentValues values = new ContentValues();
				values.put(Statuses.IS_RETWEET, 1);
				StringBuilder where = new StringBuilder();
				where.append(Statuses.ACCOUNT_ID + "=" + responce.account_id);
				where.append(" AND " + Statuses.STATUS_ID + "=" + responce.status.getId());
				for (Uri uri : TweetStore.STATUSES_URIS) {
					resolver.update(uri, values, where.toString(), null);
				}
			}
			super.onPostExecute(result);
		}

		private class AccountResponce {

			public long account_id;
			public twitter4j.Status status;

			public AccountResponce(long account_id, twitter4j.Status status) {
				this.account_id = account_id;
				this.status = status;
			}
		}

	}

	private class UpdateStatusTask extends ManagedAsyncTask<Object, Void, Void> {

		private long[] account_ids;
		private String content;
		private Location location;
		private Uri image_uri;
		private long in_reply_to;

		public UpdateStatusTask(long[] account_ids, String content, Location location,
				Uri image_uri, long in_reply_to) {
			super(UpdateService.this, mAsyncTaskManager);
			this.account_ids = account_ids;
			this.content = content;
			this.location = location;
			this.image_uri = image_uri;
			this.in_reply_to = in_reply_to;
		}

		@Override
		protected Void doInBackground(Object... params) {

			if (account_ids == null) return null;

			for (long account_id : account_ids) {
				Twitter twitter = CommonUtils.getTwitterInstance(UpdateService.this, account_id);
				if (twitter != null) {
					try {
						StatusUpdate status = new StatusUpdate(content);
						status.setInReplyToStatusId(in_reply_to);
						if (location != null) {
							status.setLocation(new GeoLocation(location.getLatitude(), location
									.getLongitude()));
						}
						String image_path = CommonUtils.getImagePathFromUri(UpdateService.this,
								image_uri);
						if (image_path != null) {
							status.setMedia(new File(image_path));
						}
						twitter.updateStatus(status);
					} catch (TwitterException e) {
						e.printStackTrace();
					}
				}
			}
			return null;
		}

	}

	/*
	 * By making this a static class with a WeakReference to the Service, we
	 * ensure that the Service can be GCd even when the system process still has
	 * a remote reference to the stub.
	 */
	final static class ServiceStub extends IUpdateService.Stub {

		WeakReference<UpdateService> mService;

		public ServiceStub(UpdateService service) {

			mService = new WeakReference<UpdateService>(service);
		}

		@Override
		public int createFavorite(long[] account_ids, long status_id) throws RemoteException {
			return mService.get().createFavorite(account_ids, status_id);
		}

		@Override
		public int destroyFavorite(long[] account_ids, long status_id) throws RemoteException {
			return mService.get().destroyFavorite(account_ids, status_id);
		}

		@Override
		public int destroyStatus(long account_id, long status_id) throws RemoteException {
			return mService.get().destroyStatus(account_id, status_id);
		}

		@Override
		public int getHomeTimeline(long[] account_ids, long[] max_ids) throws RemoteException {
			return mService.get().refreshHomeTimeline(account_ids, max_ids);
		}

		@Override
		public int getMentions(long[] account_ids, long[] max_ids) throws RemoteException {
			return mService.get().refreshMentions(account_ids, max_ids);
		}

		@Override
		public int getMessages(long[] account_ids, long[] max_ids) throws RemoteException {
			return mService.get().refreshMessages(account_ids, max_ids);
		}

		@Override
		public boolean hasActivatedTask() throws RemoteException {
			return mService.get().hasActivatedTask();
		}

		@Override
		public boolean isHomeTimelineRefreshing() throws RemoteException {
			return mService.get().isHomeTimelineRefreshing();
		}

		@Override
		public boolean isMentionsRefreshing() throws RemoteException {
			return mService.get().isMentionsRefreshing();
		}

		@Override
		public int retweetStatus(long[] account_ids, long status_id) throws RemoteException {
			return mService.get().retweetStatus(account_ids, status_id);
		}

		@Override
		public int updateStatus(long[] account_ids, String content, Location location,
				Uri image_uri, long in_reply_to) throws RemoteException {
			return mService.get().updateStatus(account_ids, content, location, image_uri, in_reply_to);

		}

	}

}