package islam.adhanalarm;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.Date;
import java.util.List;

import islam.adhanalarm.handler.ScheduleHandler;
import islam.adhanalarm.service.AdhanService;

/**
 * Everything about one prayer's alert: how it calls you, the adhan's sound, a reminder before, and its time adjustment
 */
public class PrayerAlertSheet extends BottomSheetDialogFragment {

    private static final String ARG_PRAYER = "prayer";
    private static final int[] REMINDER_MINUTES = {0, 5, 10, 15, 30};
    private static final int[] ICONS = {R.drawable.ic_alert_off, R.drawable.ic_alert_notification, R.drawable.ic_alert_chime, R.drawable.ic_alert_adhan};
    private static final int[] LABELS = {R.string.alert_off, R.string.alert_notification, R.string.alert_chime, R.string.alert_adhan};
    private static final int[] HINTS = {R.string.alert_off_hint, R.string.alert_notification_hint, R.string.alert_chime_hint, R.string.alert_adhan_hint};

    private short mPrayer;
    private SharedPreferences mSettings;
    private View mView;
    private MediaPlayer mPreview;

    private final ActivityResultLauncher<String[]> mChooseSound = registerForActivityResult(new ActivityResultContracts.OpenDocument(), new ActivityResultCallback<Uri>() {
        @Override
        public void onActivityResult(Uri uri) {
            if (uri == null) return;
            try {
                // Keep the right to read it after a restart, for as long as the file is there
                requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ex) {
                Toast.makeText(requireContext(), R.string.sound_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            mSettings.edit().putString(getSoundKey(), uri.toString()).apply();
            refresh();
        }
    });

    public static PrayerAlertSheet newInstance(short prayer) {
        PrayerAlertSheet sheet = new PrayerAlertSheet();
        Bundle arguments = new Bundle();
        arguments.putShort(ARG_PRAYER, prayer);
        sheet.setArguments(arguments);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mPrayer = requireArguments().getShort(ARG_PRAYER);
        mSettings = App.getSettings(requireContext());
        mView = inflater.inflate(R.layout.sheet_prayer_alert, container, false);

        mView.findViewById(R.id.sheet_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        // How the prayer calls you; sunrise isn't a prayer, so it has no adhan
        LinearLayout options = mView.findViewById(R.id.sheet_options);
        int count = mPrayer == CONSTANT.SUNRISE ? CONSTANT.NOTIFICATION_PLAY : CONSTANT.NOTIFICATION_PLAY + 1;
        for (short type = CONSTANT.NOTIFICATION_NONE; type < count; type++) {
            View option = inflater.inflate(R.layout.alert_option, options, false);
            ((ImageView) option.findViewById(R.id.option_icon)).setImageResource(ICONS[type]);
            ((TextView) option.findViewById(R.id.option_label)).setText(LABELS[type]);
            ((TextView) option.findViewById(R.id.option_hint)).setText(type == CONSTANT.NOTIFICATION_PLAY && mPrayer == CONSTANT.FAJR ? R.string.alert_adhan_fajr_hint : HINTS[type]);
            final short chosen = type;
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Prefs.setAlert(mSettings, mPrayer, chosen);
                    changed();
                }
            });
            options.addView(option);
        }

        mView.findViewById(R.id.sheet_sound_choose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseSound();
            }
        });
        mView.findViewById(R.id.sheet_preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePreview();
            }
        });

        ((TextView) mView.findViewById(R.id.sheet_reminder_title)).setText(mPrayer == CONSTANT.SUNRISE ? R.string.remind_before_sunrise : R.string.remind_before);
        ChipGroup reminders = mView.findViewById(R.id.sheet_reminders);
        for (int minutes : REMINDER_MINUTES) {
            Chip chip = (Chip) inflater.inflate(R.layout.reminder_chip, reminders, false);
            chip.setId(View.generateViewId());
            chip.setText(minutes == 0 ? getString(R.string.reminder_off) : getString(R.string.reminder_minutes, minutes));
            chip.setTag(minutes);
            reminders.addView(chip);
        }
        reminders.setOnCheckedStateChangeListener(new ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull ChipGroup group, @NonNull List<Integer> checkedIds) {
                if (checkedIds.isEmpty()) return;
                int minutes = (Integer) group.findViewById(checkedIds.get(0)).getTag();
                if (minutes != Prefs.getReminder(mSettings, mPrayer)) {
                    mSettings.edit().putInt(Prefs.REMINDER + mPrayer, minutes).apply();
                    changed();
                }
            }
        });

        mView.findViewById(R.id.sheet_earlier).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adjust(-1);
            }
        });
        mView.findViewById(R.id.sheet_later).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adjust(1);
            }
        });

        refresh();
        return mView;
    }

    private String getSoundKey() {
        return mPrayer == CONSTANT.FAJR ? Prefs.FAJR_ADHAN_SOUND : Prefs.ADHAN_SOUND;
    }

    private void adjust(int minutes) {
        int offset = Math.max(-60, Math.min(60, Prefs.getOffset(mSettings, mPrayer) + minutes));
        mSettings.edit().putInt(Prefs.OFFSET + mPrayer, offset).apply();
        changed();
    }

    private void changed() {
        App.broadcastPrayerTimeUpdate();
        refresh();
    }

    private void refresh() {
        Context context = requireContext();
        ScheduleHandler schedule = new ScheduleHandler(mSettings);
        ((TextView) mView.findViewById(R.id.sheet_name)).setText(CONSTANT.TIME_NAMES[mPrayer]);
        TextView arabic = mView.findViewById(R.id.sheet_arabic);
        arabic.setText(CONSTANT.ARABIC_NAMES[mPrayer]);
        arabic.setVisibility(Prefs.showArabicNames(context, mSettings) ? View.VISIBLE : View.GONE);
        ((TextView) mView.findViewById(R.id.sheet_time)).setText(Prefs.getTimeFormat(context, mSettings).format(new Date(schedule.getTime(mPrayer).getTimeInMillis())));

        short alert = Prefs.getAlert(mSettings, mPrayer);
        LinearLayout options = mView.findViewById(R.id.sheet_options);
        for (int i = 0; i < options.getChildCount(); i++) {
            final View option = options.getChildAt(i);
            final boolean checked = i == alert;
            ((MaterialRadioButton) option.findViewById(R.id.option_radio)).setChecked(checked);
            option.setBackgroundResource(checked ? R.drawable.option_selected : 0);
            option.setContentDescription(getString(LABELS[i]) + ", " + ((TextView) option.findViewById(R.id.option_hint)).getText());
            ViewCompat.setAccessibilityDelegate(option, new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(checked ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
                }
            });
        }

        // The adhan's sound: built in, or one on the phone. Fajr has its own.
        View sound = mView.findViewById(R.id.sheet_sound);
        sound.setVisibility(alert == CONSTANT.NOTIFICATION_PLAY ? View.VISIBLE : View.GONE);
        ((TextView) mView.findViewById(R.id.sheet_sound_title)).setText(mPrayer == CONSTANT.FAJR ? R.string.fajr_adhan_sound : R.string.adhan_sound);
        Uri chosen = AdhanService.getChosenSound(mSettings, mPrayer);
        String soundName = chosen != null ? getDisplayName(context, chosen) : getString(mPrayer == CONSTANT.FAJR ? R.string.sound_built_in_fajr : R.string.sound_built_in);
        ((TextView) mView.findViewById(R.id.sheet_sound_value)).setText(mPrayer == CONSTANT.FAJR ? soundName : getString(R.string.sound_shared, soundName));
        updatePreviewButton();

        ChipGroup reminders = mView.findViewById(R.id.sheet_reminders);
        int reminder = Prefs.getReminder(mSettings, mPrayer);
        for (int i = 0; i < reminders.getChildCount(); i++) {
            Chip chip = (Chip) reminders.getChildAt(i);
            if ((Integer) chip.getTag() == reminder && !chip.isChecked()) chip.setChecked(true);
        }

        ((TextView) mView.findViewById(R.id.sheet_offset)).setText(Formats.adjustment(context, Prefs.getOffset(mSettings, mPrayer)));
    }

    private void chooseSound() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(mPrayer == CONSTANT.FAJR ? R.string.fajr_adhan_sound : R.string.adhan_sound)
                .setItems(new CharSequence[]{getString(mPrayer == CONSTANT.FAJR ? R.string.sound_built_in_fajr : R.string.sound_built_in), getString(R.string.choose_sound)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    mSettings.edit().remove(getSoundKey()).apply();
                                    refresh();
                                } else {
                                    mChooseSound.launch(new String[]{"audio/*"});
                                }
                            }
                        })
                .show();
    }

    private void togglePreview() {
        if (mPreview != null) {
            stopPreview();
            return;
        }
        Context context = requireContext();
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        Uri chosen = AdhanService.getChosenSound(mSettings, mPrayer);
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(attributes);
            if (chosen != null) {
                player.setDataSource(context, chosen);
            } else {
                player.setDataSource(context, Uri.parse("android.resource://" + context.getPackageName() + "/" + AdhanService.getBuiltInAdhan(mPrayer)));
            }
            player.prepare();
        } catch (Exception ex) {
            player.release();
            Toast.makeText(context, R.string.sound_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopPreview();
            }
        });
        player.start();
        mPreview = player;
        updatePreviewButton();
    }

    private void stopPreview() {
        if (mPreview != null) {
            mPreview.release();
            mPreview = null;
        }
        if (mView != null) updatePreviewButton();
    }

    private void updatePreviewButton() {
        MaterialButton preview = mView.findViewById(R.id.sheet_preview);
        preview.setIconResource(mPreview != null ? R.drawable.ic_stop : R.drawable.ic_play);
        preview.setContentDescription(getString(mPreview != null ? R.string.stop_preview : R.string.play_preview));
    }

    @Override
    public void onStop() {
        stopPreview();
        super.onStop();
    }

    static String getDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ex) {
            // The file has gone; the built-in adhan plays instead
        }
        return context.getString(R.string.sound_missing);
    }
}
