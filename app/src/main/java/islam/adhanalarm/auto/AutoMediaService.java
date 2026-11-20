package islam.adhanalarm.auto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionError;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.clock.ClockActivity;
import islam.adhanalarm.clock.ClockWords;
import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.AdhanService;
import islam.adhanalarm.widget.DialArt;

/**
 * The app in Android Auto, as a media app like a Quran player's: the car's screen lists what's next, with
 * the dial beside it, then each of today's prayers and its time, and any of them plays its adhan through the
 * car's speakers, as does "play the adhan" said to the assistant. Nothing leaves the phone, and only the car,
 * the assistant, the system and the app itself may look in, as the times say roughly where the phone is.
 */
public class AutoMediaService extends MediaLibraryService {

    private static final String ROOT = "root", NEXT = "next", PRAYER = "prayer/";

    private static final Set<String> CALLERS = new HashSet<>(Arrays.asList(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.googlequicksearchbox", // The assistant, for "play the adhan"
            "com.google.android.carassistant",
            "com.android.systemui")); // The phone's own media controls

    private ExoPlayer mPlayer;
    private MediaLibrarySession mSession;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** The list's countdown moves on each minute */
    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            mSession.notifyChildrenChanged(ROOT, 6, null);
            mHandler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 250);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // As media, so the car plays it through its speakers, pausing whatever else was playing until it ends
        mPlayer = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        mSession = new MediaLibrarySession.Builder(this, mPlayer, new Library())
                .setSessionActivity(ClockActivity.withToday(this)) // From the phone's media controls, with Today beneath for Back
                .build();
        mHandler.postDelayed(mTick, 60000 - System.currentTimeMillis() % 60000 + 250);
    }

    @Nullable
    @Override
    public MediaLibrarySession onGetSession(@NonNull MediaSession.ControllerInfo controller) {
        boolean allowed = controller.getUid() == Process.myUid() || controller.getUid() == Process.SYSTEM_UID
                || CALLERS.contains(controller.getPackageName());
        return allowed ? mSession : null;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (!mPlayer.getPlayWhenReady() || mPlayer.getMediaItemCount() == 0) {
            stopSelf(); // Nothing playing, and the app has been swiped away
        }
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mTick);
        mSession.release();
        mPlayer.release();
        super.onDestroy();
    }

    /** Today's prayers to browse, and what each plays */
    private class Library implements MediaLibrarySession.Callback {

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @Nullable LibraryParams params) {
            // Playable items show as a list, which suits names and times
            MediaItem root = new MediaItem.Builder()
                    .setMediaId(ROOT)
                    .setMediaMetadata(new MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setTitle(getString(R.string.app_name)).build())
                    .build();
            return Futures.immediateFuture(LibraryResult.ofItem(root, params));
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser,
                                                                                         @NonNull String parentId, int page, int pageSize, @Nullable LibraryParams params) {
            if (!ROOT.equals(parentId)) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(today()), params));
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @NonNull String mediaId) {
            for (MediaItem item : today()) {
                if (item.mediaId.equals(mediaId)) return Futures.immediateFuture(LibraryResult.ofItem(item, null));
            }
            return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
        }

        /** What a tap or a spoken request asks for, made playable */
        @NonNull
        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller, @NonNull List<MediaItem> mediaItems) {
            List<MediaItem> playable = new ArrayList<>();
            for (MediaItem item : mediaItems) {
                String query = item.requestMetadata.searchQuery;
                playable.add(playable(query != null ? forSearch(query) : item.mediaId));
            }
            return Futures.immediateFuture(playable);
        }
    }

    /** What's next, with the dial as it is now, then each of today's prayers, the short ones marked in copper */
    private List<MediaItem> today() {
        SharedPreferences settings = App.getSettings(this);
        ScheduleHandler schedule = new ScheduleHandler(settings);
        DateFormat format = Prefs.getTimeFormat(this, settings);
        Calendar now = Calendar.getInstance();
        List<MediaItem> items = new ArrayList<>();
        ClockWords words = ClockWords.at(this, schedule, format, now);
        items.add(browsable(NEXT, words.headline, words.sub, png(DialArt.picture(this, schedule, now, 144))));
        short nextTime = schedule.getNextTimeIndex(now);
        for (int window = 0; window < 5; window++) {
            short start = DialArt.startOf(window, nextTime);
            String time = schedule.getFormattedTime(start, format);
            boolean isShort = DialArt.isShort(windowLength(schedule, window));
            items.add(browsable(PRAYER + window, getString(CONSTANT.TIME_NAMES[start]), isShort ? getString(R.string.dial_short_window, time) : time, png(dot(isShort))));
        }
        return items;
    }

    private static MediaItem browsable(String id, String title, String subtitle, byte[] artwork) {
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build())
                .build();
    }

    /** The adhan for a list item: fajr's own for fajr, and the sound chosen on the phone where there is one */
    private MediaItem playable(String mediaId) {
        SharedPreferences settings = App.getSettings(this);
        ScheduleHandler schedule = new ScheduleHandler(settings);
        Calendar now = Calendar.getInstance();
        int window = mediaId != null && mediaId.startsWith(PRAYER) ? Integer.parseInt(mediaId.substring(PRAYER.length()))
                : DialArt.nextWindow(schedule.getNextTimeIndex(now));
        short timeIndex = ClockWords.windowStart(window);
        Uri chosen = AdhanService.getChosenSound(settings, timeIndex);
        Uri sound = chosen != null ? chosen : Uri.parse("android.resource://" + getPackageName() + "/" + AdhanService.getBuiltInAdhan(timeIndex));
        String time = schedule.getFormattedTime(DialArt.startOf(window, schedule.getNextTimeIndex(now)), Prefs.getTimeFormat(this, settings));
        return new MediaItem.Builder()
                .setMediaId(mediaId == null ? NEXT : mediaId)
                .setUri(sound)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(getString(CONSTANT.TIME_NAMES[timeIndex]))
                        .setArtist(getString(R.string.app_name))
                        .setSubtitle(time)
                        .setArtworkData(png(DialArt.picture(this, schedule, now, 256)), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build())
                .build();
    }

    /** "Play the adhan" plays the next prayer's; naming a prayer plays that one's */
    private String forSearch(String query) {
        String asked = query.toLowerCase(Locale.getDefault());
        for (int window = 0; window < 5; window++) {
            String name = getString(CONSTANT.TIME_NAMES[ClockWords.windowStart(window)]).toLowerCase(Locale.getDefault()).replace("`", "").replace("'", "");
            if (!name.isEmpty() && asked.contains(name)) return PRAYER + window;
        }
        return NEXT;
    }

    /** A small dot in the window's colour, as beside the line under the full-screen dial's time */
    private Bitmap dot(boolean isShort) {
        int size = 64;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(this, isShort ? R.color.dial_copper : R.color.dial_cream));
        new Canvas(bitmap).drawCircle(size / 2f, size / 2f, size * 0.22f, paint);
        return bitmap;
    }

    private static byte[] png(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        return bytes.toByteArray();
    }

    private static long windowLength(ScheduleHandler schedule, int window) {
        short[] starts = {CONSTANT.FAJR, CONSTANT.DHUHR, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA};
        short[] ends = {CONSTANT.SUNRISE, CONSTANT.ASR, CONSTANT.MAGHRIB, CONSTANT.ISHAA, CONSTANT.NEXT_FAJR};
        return schedule.getTime(ends[window]).getTimeInMillis() - schedule.getTime(starts[window]).getTimeInMillis();
    }
}
