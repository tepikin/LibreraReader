package com.foobnix.ui2;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.session.MediaSessionCompat;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.ext.CacheZipUtils.CacheDir;
import com.foobnix.ext.EbookMeta;
import com.foobnix.pdf.info.AppSharedPreferences;
import com.foobnix.pdf.info.Clouds;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.IMG;
import com.foobnix.pdf.info.io.SearchCore;
import com.foobnix.pdf.info.wrapper.AppState;
import com.foobnix.pdf.search.activity.msg.MessageSyncFinish;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.ui2.adapter.FileMetaAdapter;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class BooksService extends IntentService {
    private MediaSessionCompat mediaSessionCompat;

    public BooksService() {
        super("BooksService");
        AppState.get().load(this);
        handler = new Handler();
        LOG.d("BooksService", "Create");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOG.d("BooksService", "onDestroy");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static String TAG = "BooksService";

    Handler handler;

    public static String INTENT_NAME = "BooksServiceIntent";
    public static String ACTION_SEARCH_ALL = "ACTION_SEARCH_ALL";
    public static String ACTION_REMOVE_DELETED = "ACTION_REMOVE_DELETED";
    public static String ACTION_SYNC_DROPBOX = "ACTION_SYNC_DROPBOX";

    public static String RESULT_SYNC_FINISH = "RESULT_SYNC_FINISH";
    public static String RESULT_SEARCH_FINISH = "RESULT_SEARCH_FINISH";
    public static String RESULT_BUILD_LIBRARY = "RESULT_BUILD_LIBRARY";
    public static String RESULT_SEARCH_COUNT = "RESULT_SEARCH_COUNT";

    private List<FileMeta> itemsMeta = new LinkedList<FileMeta>();

    public static volatile boolean isRunning = false;

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            isRunning = true;
            LOG.d(TAG, "BooksService", "Action", intent.getAction());

            if (ACTION_REMOVE_DELETED.equals(intent.getAction())) {
                List<FileMeta> list = AppDB.get().getAll();
                for (FileMeta meta : list) {
                    if (meta == null) {
                        continue;
                    }

                    if (Clouds.isCloud(meta.getPath())) {
                        continue;
                    }

                    File bookFile = new File(meta.getPath());
                    if(ExtUtils.isMounted(bookFile)){
                        LOG.d("isMounted",bookFile);
                        if (!bookFile.exists()) {
                            AppDB.get().delete(meta);
                            LOG.d(TAG, "Delete-setIsSearchBook", meta.getPath());
                        }
                    }

                }
                sendFinishMessage();

                LOG.d("BooksService , searchDate", AppState.get().searchDate, AppState.get().searchPaths);
                if (AppState.get().searchDate != 0) {

                    List<FileMeta> localMeta = new LinkedList<FileMeta>();

                    for (final String path : AppState.get().searchPaths.split(",")) {
                        if (path != null && path.trim().length() > 0) {
                            final File root = new File(path);
                            if (root.isDirectory()) {
                                LOG.d(TAG, "Searcin in " + root.getPath());
                                SearchCore.search(localMeta, root, ExtUtils.seachExts);
                            }
                        }
                    }

                    for (FileMeta meta : localMeta) {

                        File file = new File(meta.getPath());

                        if (file.lastModified() >= AppState.get().searchDate) {
                            if (AppDB.get().getDao().hasKey(meta)) {
                                LOG.d(TAG, "Skip book", file.getPath());
                                continue;
                            }

                            FileMetaCore.createMetaIfNeed(meta.getPath(), true);
                            LOG.d(TAG, "BooksService", "insert", meta.getPath());
                        }else{
                            //LOG.d("BooksService file old", file.getPath(), file.lastModified(), AppState.get().searchDate);
                        }

                    }
                    AppState.get().searchDate = System.currentTimeMillis();
                    sendFinishMessage();
                }

                Clouds.get().syncronizeGet();
                sendFinishMessage();

            } else if (ACTION_SEARCH_ALL.equals(intent.getAction())) {
                LOG.d(ACTION_SEARCH_ALL);

                IMG.clearDiscCache();
                IMG.clearMemoryCache();
                ImageExtractor.clearErrors();


                List<Uri> recent = AppSharedPreferences.get().getRecent();
                List<FileMeta> starsAndRecent = AppDB.get().deleteAllSafe();

                long time = Integer.MAX_VALUE;
                for (Uri uri : recent) {
                    FileMeta item = new FileMeta(uri.getPath());
                    item.setIsRecent(true);
                    item.setIsStarTime(time--);
                    starsAndRecent.add(item);
                }
                for (FileMeta m : starsAndRecent) {
                    if (m.getCusType() != null && FileMetaAdapter.DISPLAY_TYPE_DIRECTORY == m.getCusType()) {
                        m.setIsSearchBook(false);
                    } else {
                        m.setIsSearchBook(true);
                    }
                }

                AppSharedPreferences.get().cleanRecent();

                itemsMeta.clear();

                handler.post(timer);

                for (final String path : AppState.get().searchPaths.split(",")) {
                    if (path != null && path.trim().length() > 0) {
                        final File root = new File(path);
                        if (root.isDirectory()) {
                            LOG.d("Searcin in " + root.getPath());
                            SearchCore.search(itemsMeta, root, ExtUtils.seachExts);
                        }
                    }
                }
                AppState.get().searchDate = System.currentTimeMillis();

                for (FileMeta meta : itemsMeta) {
                    meta.setIsSearchBook(true);
                }

                itemsMeta.addAll(starsAndRecent);
                AppDB.get().saveAll(itemsMeta);

                handler.removeCallbacks(timer);

                sendFinishMessage();

                handler.post(timer2);

                for (FileMeta meta : itemsMeta) {
                    File file = new File(meta.getPath());
                    FileMetaCore.get().upadteBasicMeta(meta, file);
                }

                AppDB.get().updateAll(itemsMeta);
                sendFinishMessage();

                for (FileMeta meta : itemsMeta) {
                    EbookMeta ebookMeta = FileMetaCore.get().getEbookMeta(meta.getPath(), CacheDir.ZipService, true);
                    LOG.d("BooksService getAuthor", ebookMeta.getAuthor());
                    FileMetaCore.get().udpateFullMeta(meta, ebookMeta);
                }

                AppDB.get().updateAll(itemsMeta);


                itemsMeta.clear();

                handler.removeCallbacks(timer2);
                sendFinishMessage();
                CacheDir.ZipService.removeCacheContent();

                Clouds.get().syncronizeGet();
                sendFinishMessage();

            } else if (ACTION_SYNC_DROPBOX.equals(intent.getAction())) {
                Clouds.get().syncronizeGet();
                sendFinishMessage();
            }

        } finally {
            isRunning = false;
        }

    }

    Runnable timer = new Runnable() {

        @Override
        public void run() {
            sendProggressMessage();
            handler.postDelayed(timer, 250);
        }
    };

    Runnable timer2 = new Runnable() {

        @Override
        public void run() {
            sendBuildingLibrary();
            handler.postDelayed(timer2, 250);
        }
    };

    private void sendFinishMessage() {
        try {
            AppDB.get().getDao().detachAll();
        } catch (Exception e) {
            LOG.e(e);
        }

        sendFinishMessage(this);
        EventBus.getDefault().post(new MessageSyncFinish());
    }

    public static void sendFinishMessage(Context c) {
        Intent intent = new Intent(INTENT_NAME).putExtra(Intent.EXTRA_TEXT, RESULT_SEARCH_FINISH);
        LocalBroadcastManager.getInstance(c).sendBroadcast(intent);
    }

    private void sendProggressMessage() {
        Intent itent = new Intent(INTENT_NAME).putExtra(Intent.EXTRA_TEXT, RESULT_SEARCH_COUNT).putExtra(Intent.EXTRA_INDEX, itemsMeta.size());
        LocalBroadcastManager.getInstance(this).sendBroadcast(itent);
    }

    private void sendBuildingLibrary() {
        Intent itent = new Intent(INTENT_NAME).putExtra(Intent.EXTRA_TEXT, RESULT_BUILD_LIBRARY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(itent);
    }

}
