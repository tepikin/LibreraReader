package com.foobnix.tts;

import java.io.File;
import java.io.FileFilter;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.ebookdroid.LibreraApp;
import org.greenrobot.eventbus.EventBus;

import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.ResultResponse;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.android.utils.Vibro;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.pdf.info.AppSharedPreferences;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.AppBookmark;
import com.foobnix.pdf.info.wrapper.AppState;
import com.foobnix.pdf.info.wrapper.DocumentController;
import com.foobnix.sys.TempHolder;
import com.google.common.base.Optional;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.widget.Toast;

public class TTSEngine {

    public static final String FINISHED = "Finished";
    private static final String WAV = ".wav";
    public static final String UTTERANCE_ID_DONE = "LirbiReader";
    private static final String TAG = "TTSEngine";
    volatile TextToSpeech ttsEngine;
    volatile MediaPlayer mp;
    Timer mTimer;
    Object helpObject = new Object();

    private List<LanguageProfile> languageProfiles = getLanguageProfiles();

    private static List<LanguageProfile> getLanguageProfiles()  {
        try {
            return new LanguageProfileReader().readAllBuiltIn();
        }catch (Throwable e){
            e.printStackTrace();
        }
        return null;
    }

    //build language detector:
    private LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
            .withProfiles(languageProfiles)
            .build();

    //create a text object factory
    private TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();

    private static TTSEngine INSTANCE = new TTSEngine();

    public static TTSEngine get() {
        return INSTANCE;
    }

    HashMap<String, String> map = new HashMap<String, String>();
    {
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_DONE);
    }

    HashMap<String, String> mapTemp = new HashMap<String, String>();
    {
        mapTemp.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Temp");
    }

    public void shutdown() {
        LOG.d(TAG, "shutdown");

        synchronized (helpObject) {
            if (ttsEngine != null) {
                ttsEngine.shutdown();
            }
            ttsEngine = null;
        }

    }

    OnInitListener listener = new OnInitListener() {

        @Override
        public void onInit(int status) {
            LOG.d(TAG, "onInit", "SUCCESS", status == TextToSpeech.SUCCESS);
            if (status == TextToSpeech.ERROR) {
                Toast.makeText(LibreraApp.context, R.string.msg_unexpected_error, Toast.LENGTH_LONG).show();
            }

        }
    };

    public TextToSpeech getTTS() {
        return getTTS(null);
    }

    public synchronized TextToSpeech getTTS(OnInitListener onLisnter) {
        if (LibreraApp.context == null) {
            return null;
        }

        synchronized (helpObject) {

            if (TTSEngine.get().isMp3() && mp == null) {
                TTSEngine.get().loadMP3(AppState.get().mp3BookPath);
            }

            if (ttsEngine != null) {
                return ttsEngine;
            }
            if (onLisnter == null) {
                onLisnter = listener;
            }
            ttsEngine = new TextToSpeech(LibreraApp.context, onLisnter);
        }

        return ttsEngine;

    }

    public synchronized boolean isShutdown() {
        return ttsEngine == null;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void stop() {
        LOG.d(TAG, "stop");
        synchronized (helpObject) {

            if (ttsEngine != null) {
                if (Build.VERSION.SDK_INT >= 15) {
                    ttsEngine.setOnUtteranceProgressListener(null);
                } else {
                    ttsEngine.setOnUtteranceCompletedListener(null);
                }
                ttsEngine.stop();
                EventBus.getDefault().post(new TtsStatus());
            }
        }
    }

    public void stopDestroy() {
        LOG.d(TAG, "stop");
        synchronized (helpObject) {
            if (ttsEngine != null) {
                ttsEngine.shutdown();
            }
            ttsEngine = null;
        }
        AppState.get().lastBookParagraph = 0;
    }

    public synchronized TextToSpeech setTTSWithEngine(String engine) {
        shutdown();
        synchronized (helpObject) {
            ttsEngine = new TextToSpeech(LibreraApp.context, listener, engine);
        }
        return ttsEngine;
    }

    private String text = "";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public synchronized void speek(final String text) {
        this.text = text;

        if (AppState.get().tempBookPage != AppState.get().lastBookPage) {
            AppState.get().tempBookPage = AppState.get().lastBookPage;
            AppState.get().lastBookParagraph = 0;
        }

        LOG.d(TAG, "speek", AppState.get().lastBookPage, "par", AppState.get().lastBookParagraph);

        if (TxtUtils.isEmpty(text)) {
            return;
        }
        if (ttsEngine == null) {
            LOG.d("getTTS-status was null");
        } else {
            LOG.d("getTTS-status not null");
        }

        ttsEngine = getTTS(new OnInitListener() {

            @Override
            public void onInit(int status) {
                LOG.d("getTTS-status", status);
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    speek(text);
                }
            }
        });

        ttsEngine.setPitch(AppState.get().ttsPitch);
        if (AppState.get().ttsSpeed == 0.0f) {
            AppState.get().ttsSpeed = 0.01f;
        }
        ttsEngine.setSpeechRate(AppState.get().ttsSpeed);
        LOG.d(TAG, "Speek speed", AppState.get().ttsSpeed);
        LOG.d(TAG, "Speek AppState.get().lastBookParagraph", AppState.get().lastBookParagraph);

        if (AppState.get().ttsPauseDuration > 0 && text.contains(TxtUtils.TTS_PAUSE)) {
            String[] parts = text.split(TxtUtils.TTS_PAUSE);
            ttsEngine.speak(" ", TextToSpeech.QUEUE_FLUSH, mapTemp);
            for (int i = AppState.get().lastBookParagraph; i < parts.length; i++) {

                String big = parts[i];
                big = big.trim();
                if (TxtUtils.isNotEmpty(big)) {

                    HashMap<String, String> mapTemp1 = new HashMap<String, String>();
                    mapTemp1.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, FINISHED + i);
                    Log.e("Speek_part", "text = "+ big);
try {
    TextObject textObject = textObjectFactory.forText(big);
    List<DetectedLanguage> probabilities = languageDetector.getProbabilities(textObject);
    for (int i1 = 0; i1 < probabilities.size(); i1++) {
        DetectedLanguage detectedLanguage = probabilities.get(i1);
        Log.e("Speek_part", "detectedLanguage = "+ detectedLanguage);
        String language = detectedLanguage.getLocale().getLanguage();
        Log.e("Speek_part", "detectedLanguage = "+ language);
        Locale locale = new Locale(language);
        if (ttsEngine.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE ||
                ttsEngine.isLanguageAvailable(locale) == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                ttsEngine.isLanguageAvailable(locale) == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
            ttsEngine.setLanguage(locale);
            Log.e("Speek_part", "locale set = "+ locale);
            break;
        }
    }
}catch (Throwable e){
    e.printStackTrace();
}
                    ttsEngine.speak(big, TextToSpeech.QUEUE_ADD, mapTemp1);
                    if (!big.endsWith(".")) {
                        LOG.d("pageHTML-parts", i, "[playSilence]");
                        ttsEngine.playSilence(AppState.get().ttsPauseDuration, TextToSpeech.QUEUE_ADD, mapTemp);
                    }
                    LOG.d("pageHTML-parts", i, big);
                }
            }
            ttsEngine.playSilence(0L, TextToSpeech.QUEUE_ADD, map);
        } else {
            String textToPlay = text.replace(TxtUtils.TTS_PAUSE, "");
            LOG.d("pageHTML-parts-single", text);
            ttsEngine.speak(textToPlay, TextToSpeech.QUEUE_FLUSH, map);
        }

    }

    public void speakToFile(final DocumentController controller, final ResultResponse<String> info) {
        File dirFolder = new File(AppState.get().ttsSpeakPath, "TTS_" + controller.getCurrentBook().getName());
        if (!dirFolder.exists()) {
            dirFolder.mkdirs();
        }
        if (!dirFolder.exists()) {
            info.onResultRecive(controller.getActivity().getString(R.string.file_not_found) + " " + dirFolder.getPath());
            return;
        }
        CacheZipUtils.removeFiles(dirFolder.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(WAV);
            }
        }));

        String path = dirFolder.getPath();
        speakToFile(controller, 0, path, info);
    }

    public void speakToFile(final DocumentController controller, final int page, final String folder, final ResultResponse<String> info) {
        LOG.d("speakToFile", page, controller.getPageCount());
        if (ttsEngine == null) {
            LOG.d("TTS is null");
            if (controller != null && controller.getActivity() != null) {
                Toast.makeText(controller.getActivity(), R.string.msg_unexpected_error, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (page >= controller.getPageCount() || !TempHolder.isRecordTTS) {
            LOG.d("speakToFile finish", page, controller.getPageCount());
            info.onResultRecive((controller.getActivity().getString(R.string.success)));
            return;
        }

        info.onResultRecive((page + 1) + " / " + controller.getPageCount());

        DecimalFormat df = new DecimalFormat("0000");
        String pageName = "page-" + df.format(page + 1);
        final String wav = new File(folder, pageName + WAV).getPath();
        String fileText = controller.getTextForPage(page);

        ttsEngine.synthesizeToFile(fileText, map, wav);

        TTSEngine.get().getTTS().setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {

            @Override
            public void onUtteranceCompleted(String utteranceId) {
                LOG.d("speakToFile onUtteranceCompleted", page, controller.getPageCount());
                speakToFile(controller, page + 1, folder, info);
            }

        });

    }

    public static void fastTTSBookmakr(Context c, float percent) {
        int page = AppState.get().lastBookPage + 1;
        boolean hasBookmark = AppSharedPreferences.get().hasBookmark(AppState.get().lastBookPath, page);

        if (!hasBookmark) {
            final AppBookmark bookmark = new AppBookmark(AppState.get().lastBookPath, c.getString(R.string.fast_bookmark), page, AppState.get().lastBookTitle, percent);
            AppSharedPreferences.get().addBookMark(bookmark);

        }
        Vibro.vibrate();
        LOG.d("Fast-bookmark", AppState.get().lastBookPage);
    }

    public synchronized boolean isPlaying() {
        if (TempHolder.isRecordTTS) {
            return false;
        }
        if (isMp3()) {
            return mp != null && mp.isPlaying();
        }

        synchronized (helpObject) {
            if (ttsEngine == null) {
                return false;
            }
            return ttsEngine != null && ttsEngine.isSpeaking();
        }
    }

    public void playCurrent() {
        speek(text);
    }

    public boolean hasNoEngines() {
        try {
            return ttsEngine != null && (ttsEngine.getEngines() == null || ttsEngine.getEngines().size() == 0);
        } catch (Exception e) {
            return true;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String getCurrentLang() {
        try {
            if (ttsEngine != null && Build.VERSION.SDK_INT >= 21) {
                return ttsEngine.getDefaultVoice().getLocale().getDisplayLanguage();
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return "---";
    }

    public String getCurrentEngineName() {
        try {
            if (ttsEngine != null) {
                String enginePackage = ttsEngine.getDefaultEngine();
                List<EngineInfo> engines = ttsEngine.getEngines();
                for (final EngineInfo eInfo : engines) {
                    if (eInfo.name.equals(enginePackage)) {
                        return engineToString(eInfo);
                    }
                }
            }
        } catch (Exception e) {
            LOG.e(e);
        }
        return "---";
    }

    public static String engineToString(EngineInfo info) {
        return info.label;
    }

    public void loadMP3(String ttsPlayMp3Path) {
        loadMP3(ttsPlayMp3Path, false);
    }

    public void loadMP3(String ttsPlayMp3Path, final boolean play) {
        try {
            mp3Destroy();
            mp = new MediaPlayer();
            mp.setDataSource(ttsPlayMp3Path);
            mp.prepare();
            mp.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.pause();
                }
            });
            if (play) {
                mp.start();
            }

            mTimer = new Timer();

            mTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    AppState.get().mp3seek = mp.getCurrentPosition();
                    LOG.d("Run timer-task");
                    EventBus.getDefault().post(new TtsStatus());
                };
            }, 1000, 1000);

        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public MediaPlayer getMP() {
        return mp;
    }

    public void mp3Destroy() {
        if (mp != null) {
            mp.stop();
            mp.reset();
            mp = null;
            if (mTimer != null) {
                mTimer.purge();
                mTimer.cancel();
                mTimer = null;
            }
        }
        LOG.d("mp3Desproy");
    }

    public void mp3Next() {
        int seek = mp.getCurrentPosition();
        mp.seekTo(seek + 5 * 1000);
    }

    public void mp3Prev() {
        int seek = mp.getCurrentPosition();
        mp.seekTo(seek - 5 * 1000);
    }

    public boolean isMp3PlayPause() {
        if (isMp3()) {
            if (mp == null) {
                loadMP3(AppState.get().mp3BookPath);
            }
            if (mp.isPlaying()) {
                mp.pause();
            } else {
                mp.start();
            }
            TTSNotification.showLast();
            return true;
        }
        return false;
    }

    public void playMp3() {
        if (mp != null) {
            mp.start();
        }
    }

    public void pauseMp3() {
        if (mp != null) {
            mp.pause();
        }
    }

    public boolean isMp3() {
        return TxtUtils.isNotEmpty(AppState.get().mp3BookPath);
    }

    public void seekTo(int i) {
        if (mp != null) {
            mp.seekTo(i);
        }

    }

}
