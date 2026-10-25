package com.ruih.framedeck;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.util.Locale;
import java.util.List;
import java.util.Collections;
import java.text.DateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int OPEN_VIDEO = 10, SAVE_FRAME = 11, OPEN_ARCHIVE = 12;
    private static final int BG = 0xff101513, PANEL = 0xff1d2722, ACCENT = 0xff78e1be;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final ExecutorService exporter = Executors.newSingleThreadExecutor();
    private final ExecutorService archives = Executors.newSingleThreadExecutor();
    private final AtomicInteger session = new AtomicInteger(), frameRequest = new AtomicInteger();
    private final AtomicInteger archiveRequest = new AtomicInteger();
    private AlertDialog archiveDialog;
    private AlertDialog cacheDialog;
    private ArchiveSession playlistArchive;
    private List<ZipArchive.Entry> playlistEntries = Collections.emptyList();
    private Uri playlistUri;
    private String playlistName = "";
    private String currentReadMode = "当前未播放视频";
    private boolean cacheBusy;
    private FrameSource source; // Only accessed on decoder executor.
    private FrameTimeline timeline;
    private Uri currentUri;
    private long currentArchiveOffset = -1;
    private String fileName = "未打开视频";
    private long durationUs;
    private int currentFrame, displayedFrame = -1, videoWidth, videoHeight;
    private Bitmap currentBitmap, exportBitmap;
    private int exportFrame;
    private MediaPlayer player;
    private Surface playbackSurface;
    private boolean prepared, playing, pendingPlay, foreground, destroyed, dragging;
    private boolean controlsVisible = true;
    private boolean pinnedFramesEnabled, gestureHadMultiplePointers;
    private final VideoTransform videoTransform = new VideoTransform();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float speed = 1f;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private FrameLayout root;
    private LinearLayout header, controls, transport, actions;
    private TextureView texture;
    private ImageView still;
    private FrameLayout stage, videoLayer;
    private TextView fileLabel, detailLabel, clock, frameLabel;
    private EmptyPlayerView empty;
    private ProgressBar busy;
    private SeekBar seek;
    private Button previousVideo, nextVideo, previous, next, play, jump, save, rate, exitFull, open, playlist;
    private Button pinnedPrevious, pinnedNext;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pinnedFramesEnabled = getSharedPreferences("player", MODE_PRIVATE)
                .getBoolean("pinned_frame_controls", true);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change <= 0 && (playing || pendingPlay)) pauseAndShowFrame();
                }, ui).build();
        buildUi();
        archives.execute(() -> recordCleanup(TempStore.clearIdle(cacheDirectory()), "启动时自动清理"));
        Uri restore = state == null ? null : state.getParcelable("uri");
        int restoredFrame = state == null ? 0 : state.getInt("frame", 0);
        if (restore != null && state.getLong("archiveOffset", -1) >= 0)
            openArchive(restore, restoredFrame, state.getLong("archiveOffset", -1));
        else if (restore != null) openVideo(restore, restoredFrame);
        else if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) {
            Uri uri = getIntent().getData();
            String mime = getContentResolver().getType(uri);
            if ((mime != null && mime.contains("zip")) || displayName(uri).toLowerCase(Locale.ROOT).endsWith(".zip"))
                openArchive(uri, 0, -1);
            else openVideo(uri, 0);
        }
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowManager.LayoutParams windowParams = getWindow().getAttributes();
        windowParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(windowParams);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        // The video always fills the window. Controls overlay it without resizing it.
        stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        videoLayer = new FrameLayout(this);
        stage.addView(videoLayer, new FrameLayout.LayoutParams(-1, -1));
        texture = new TextureView(this);
        videoLayer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        still = new ImageView(this);
        still.setScaleType(ImageView.ScaleType.FIT_CENTER);
        still.setBackgroundColor(Color.BLACK);
        videoLayer.addView(still, new FrameLayout.LayoutParams(-1, -1));
        empty = new EmptyPlayerView(this);
        empty.setOnClickListener(v -> pickVideo());
        stage.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        busy = new ProgressBar(this);
        stage.addView(busy, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        busy.setVisibility(View.GONE);
        stage.setOnClickListener(v -> {
            if (controlsVisible && timeline != null) hideControls(); else showControls();
        });
        installVideoGestures();
        videoLayer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitVideo());

        pinnedPrevious = button("◀ 上一帧", false);
        pinnedPrevious.setContentDescription("上一帧（常驻按钮）");
        pinnedPrevious.setOnClickListener(v -> step(-1, false));
        pinnedNext = button("下一帧 ▶", false);
        pinnedNext.setContentDescription("下一帧（常驻按钮）");
        pinnedNext.setOnClickListener(v -> step(1, false));
        for (Button pinned : new Button[]{pinnedPrevious, pinnedNext}) {
            pinned.setTextSize(12);
            pinned.setBackground(round(0xb31d2722, 12));
            pinned.setVisibility(View.GONE);
        }
        root.addView(pinnedPrevious, new FrameLayout.LayoutParams(dp(104), dp(48), Gravity.BOTTOM | Gravity.START));
        root.addView(pinnedNext, new FrameLayout.LayoutParams(dp(104), dp(48), Gravity.BOTTOM | Gravity.END));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setClickable(true);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xb8000000, 0x00000000}));
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        fileLabel = label("FrameDeck · 逐帧播放器", 16, Color.WHITE);
        fileLabel.setTypeface(null, Typeface.BOLD);
        fileLabel.setSingleLine(true);
        fileLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout titleRow = row();
        titleRow.addView(fileLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button settings = button("设置", false);
        settings.setContentDescription("播放设置");
        settings.setOnClickListener(v -> showPlaybackSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(56), dp(40));
        settingsParams.setMarginStart(dp(12));
        titleRow.addView(settings, settingsParams);
        header.addView(titleRow);
        detailLabel = label("轻触画面隐藏或显示控制栏", 11, 0xffbbc7c0);
        header.addView(detailLabel);
        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setClickable(true);
        controls.setPadding(dp(18), dp(18), dp(18), dp(12));
        controls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xc8000000, 0xf5000000}));
        root.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        LinearLayout times = row();
        clock = label("00:00:00.000", 13, ACCENT);
        clock.setTypeface(Typeface.MONOSPACE);
        frameLabel = label("第 — / — 帧", 13, 0xffd0d9d4);
        frameLabel.setGravity(Gravity.END);
        times.addView(clock, new LinearLayout.LayoutParams(0, dp(28), 1));
        times.addView(frameLabel, new LinearLayout.LayoutParams(0, dp(28), 1));
        controls.addView(times);
        seek = new SeekBar(this);
        seek.setContentDescription("逐帧进度条");
        controls.addView(seek, new LinearLayout.LayoutParams(-1, dp(36)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                pauseInternal();
                showControls();
            }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user && timeline != null) requestFrame(value);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                scheduleControlsHide();
            }
        });
        transport = row();
        previousVideo = button("上个\n视频", false);
        previousVideo.setContentDescription("上一个视频");
        previousVideo.setOnClickListener(v -> switchPlaylistVideo(-1));
        nextVideo = button("下个\n视频", false);
        nextVideo.setContentDescription("下一个视频");
        nextVideo.setOnClickListener(v -> switchPlaylistVideo(1));
        previous = button("上一帧", false);
        play = button("播放", true);
        next = button("下一帧", false);
        addWeighted(transport, previousVideo, 48);
        addWeighted(transport, previous, 48);
        addWeighted(transport, play, 48);
        addWeighted(transport, next, 48);
        addWeighted(transport, nextVideo, 48);
        controls.addView(transport);
        previous.setOnClickListener(v -> step(-1));
        next.setOnClickListener(v -> step(1));
        play.setOnClickListener(v -> { if (playing || pendingPlay) pauseAndShowFrame(); else startPlayback(); });

        actions = row();
        open = button("打开", false);
        open.setContentDescription("打开本地视频");
        open.setOnClickListener(v -> pickVideo());
        playlist = button("列表", false);
        playlist.setContentDescription("当前压缩包的视频播放列表");
        playlist.setOnClickListener(v -> showPlaylist());
        rate = button("1×", false);
        rate.setContentDescription("播放倍速");
        jump = button("跳帧", false);
        jump.setContentDescription("跳到指定帧");
        save = button("截图", false);
        save.setContentDescription("保存当前帧为 PNG");
        exitFull = button("横屏", false);
        exitFull.setOnClickListener(v -> toggleFullScreen());
        for (Button action : new Button[]{open, playlist, rate, jump, save, exitFull}) addWeighted(actions, action, 48);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(8);
        controls.addView(actions, ap);
        rate.setOnClickListener(v -> chooseSpeed());
        jump.setOnClickListener(v -> jumpToFrame());
        save.setOnClickListener(v -> exportFrame());
        // Insets protect only the overlays; the video remains edge-to-edge.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
            } else {
                left = insets.getStableInsetLeft(); top = insets.getStableInsetTop();
                right = insets.getStableInsetRight(); bottom = insets.getStableInsetBottom();
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            header.setPadding(dp(18) + left, dp(8) + top, dp(18) + right, dp(16));
            controls.setPadding(dp(18) + left, dp(16), dp(18) + right, dp(10) + bottom);
            positionPinnedFrameButtons(left, right, bottom);
            return insets;
        });
        root.requestApplyInsets();
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                playbackSurface = new Surface(st);
                if (player != null) player.setSurface(playbackSurface);
                fitVideo();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { fitVideo(); }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {
                if (playing) still.setVisibility(View.INVISIBLE);
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                pauseAndShowFrame();
                if (player != null) player.setSurface(null);
                if (playbackSurface != null) playbackSurface.release();
                playbackSurface = null;
                return true;
            }
        });
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateEmptyFrame());
        applyOrientation();
        applyImmersiveMode();
        refreshButtons();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @SuppressLint("ClickableViewAccessibility") // Confirmed single taps call performClick below.
    private void installVideoGestures() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                gestureHadMultiplePointers = true;
                return timeline != null;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (timeline == null) return false;
                videoTransform.zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                applyVideoZoom();
                return true;
            }
        });
        scaleDetector.setQuickScaleEnabled(false);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                if (!destroyed && !gestureHadMultiplePointers) stage.performClick();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                if (!destroyed && timeline != null) resetVideoZoom();
                return true;
            }
            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (timeline != null && !gestureHadMultiplePointers && !scaleDetector.isInProgress()) {
                    videoTransform.panBy(-dx, -dy);
                    applyVideoZoom();
                }
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
        stage.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) gestureHadMultiplePointers = false;
            if (event.getPointerCount() > 1) gestureHadMultiplePointers = true;
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void applyVideoZoom() {
        if (videoLayer == null) return;
        videoLayer.setPivotX(videoLayer.getWidth() / 2f);
        videoLayer.setPivotY(videoLayer.getHeight() / 2f);
        videoLayer.setScaleX(videoTransform.scale());
        videoLayer.setScaleY(videoTransform.scale());
        videoLayer.setTranslationX(videoTransform.x());
        videoLayer.setTranslationY(videoTransform.y());
    }

    private void resetVideoZoom() { videoTransform.reset(); applyVideoZoom(); }

    private void showPlaybackSettings() {
        Switch pinned = new Switch(this);
        pinned.setText("横屏常驻逐帧按钮");
        pinned.setTextColor(Color.WHITE);
        pinned.setTextSize(15);
        pinned.setMinHeight(dp(52));
        pinned.setPadding(dp(2), dp(8), dp(2), dp(8));
        pinned.setChecked(pinnedFramesEnabled);
        pinned.setOnCheckedChangeListener((button, checked) -> {
            pinnedFramesEnabled = checked;
            getSharedPreferences("player", MODE_PRIVATE).edit()
                    .putBoolean("pinned_frame_controls", checked).apply();
            updatePinnedFrameButtons();
        });
        new AppDialog.Builder(this).setTitle("播放设置").setIconKind("settings")
                .setMessage("双指缩放画面，放大后可单指拖动，双击恢复原始大小。开启常驻后，横屏隐藏控制栏时仍可在左右下角逐帧查看。")
                .setView(pinned).setNeutralButton("重置缩放", (d, which) -> resetVideoZoom())
                .setPositiveButton("完成", null).show();
    }

    private void positionPinnedFrameButtons(int left, int right, int bottom) {
        boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        FrameLayout.LayoutParams prev = (FrameLayout.LayoutParams) pinnedPrevious.getLayoutParams();
        prev.setMarginStart(dp(12) + (rtl ? right : left));
        prev.bottomMargin = dp(12) + bottom;
        pinnedPrevious.setLayoutParams(prev);
        FrameLayout.LayoutParams next = (FrameLayout.LayoutParams) pinnedNext.getLayoutParams();
        next.setMarginEnd(dp(12) + (rtl ? left : right));
        next.bottomMargin = dp(12) + bottom;
        pinnedNext.setLayoutParams(next);
    }

    private void updatePinnedFrameButtons() {
        if (pinnedPrevious == null) return;
        boolean show = pinnedFramesEnabled && !controlsVisible && timeline != null
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        for (Button button : new Button[]{pinnedPrevious, pinnedNext}) {
            button.setVisibility(show ? View.VISIBLE : View.GONE);
            button.setEnabled(timeline != null);
        }
    }

    private void pickVideo() {
        if (cacheBusy) { toast("正在清理临时文件，请稍候"); return; }
        pauseAndShowFrame();
        new AppDialog.Builder(this).setTitle("打开文件").setIconKind("folder")
                .setItemDetails(new String[]{"从设备中选择视频", "浏览压缩包中的视频", "继续查看已暂存的列表", "最近打开的 5 个压缩包", "查看占用与清理结果"},
                        new String[]{"video", "zip", "playlist", "history", "cache"})
                .setItems(new String[]{"打开视频", "打开 ZIP 压缩包", "当前播放列表", "最近压缩包", "临时文件管理"},
                        (dialog, which) -> {
                            if (which <= 1) pickDocument(which == 1);
                            else if (which == 2) showPlaylist();
                            else if (which == 3) showRecentArchives();
                            else showCacheManager();
                        }).setNegativeButton("取消", null).show();
    }

    private void pickDocument(boolean archive) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(archive ? "*/*" : "video/*");
        if (archive) intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, archive ? OPEN_ARCHIVE : OPEN_VIDEO);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request == OPEN_VIDEO || request == OPEN_ARCHIVE) && result == RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            if (request == OPEN_ARCHIVE) openArchive(uri, 0, -1); else openVideo(uri, 0);
        } else if (request == SAVE_FRAME) {
            Bitmap image = exportBitmap;
            int number = exportFrame;
            exportBitmap = null;
            if (result != RESULT_OK || data == null || data.getData() == null || image == null) return;
            Uri destination = data.getData();
            toast(getString(R.string.saving_frame, number));
            exporter.execute(() -> {
                String message;
                try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                    if (out == null || !image.compress(Bitmap.CompressFormat.PNG, 100, out))
                        throw new IOException("无法写入图片");
                    message = "已保存第 " + number + " 帧（PNG）";
                } catch (Exception e) { message = "保存失败：" + errorText(e); }
                String finalMessage = message;
                ui.post(() -> { if (!destroyed) toast(finalMessage); });
            });
        }
    }

    private void openArchive(Uri uri, int resumeFrame, long resumeEntryOffset) {
        if (destroyed || cacheBusy) return;
        pauseAndShowFrame();
        int token = archiveRequest.incrementAndGet();
        if (archiveDialog != null) archiveDialog.dismiss();
        if (playlistArchive != null && uri.equals(playlistUri)) {
            selectArchiveEntry(playlistArchive, playlistEntries, token, resumeFrame, resumeEntryOffset);
            return;
        }
        showArchiveProgress("打开 ZIP 压缩包", "正在读取压缩包目录…", token);
        archives.execute(() -> {
            ArchiveSession opened = null;
            try {
                opened = ArchiveSession.open(getApplicationContext(), uri,
                        () -> destroyed || archiveRequest.get() != token,
                        bytes -> archiveMessage(token, String.format(Locale.CHINA,
                                "正在准备文件读取… %.1f MB", bytes / 1048576.0)));
                ArchiveSession handle = opened;
                List<ZipArchive.Entry> entries = handle.videos(() -> destroyed || archiveRequest.get() != token);
                if (entries.isEmpty()) throw new IOException("ZIP 内没有找到视频文件，支持扫描子文件夹");
                entries.sort((a, b) -> NaturalFileOrder.compare(a.name, b.name));
                ui.post(() -> {
                    if (destroyed || archiveRequest.get() != token) { handle.close(); return; }
                    if (archiveDialog != null) archiveDialog.dismiss();
                    closePlaylist();
                    playlistArchive = handle;
                    playlistEntries = Collections.unmodifiableList(entries);
                    playlistUri = uri;
                    playlistName = displayName(uri);
                    rememberArchive(uri, playlistName);
                    refreshButtons();
                    selectArchiveEntry(handle, entries, token, resumeFrame, resumeEntryOffset);
                });
            } catch (Exception | OutOfMemoryError e) {
                if (opened != null) opened.close();
                archiveError(token, e);
            }
        });
    }

    private void selectArchiveEntry(ArchiveSession handle, List<ZipArchive.Entry> entries,
            int token, int resumeFrame, long resumeEntryOffset) {
        if (resumeEntryOffset >= 0) {
            for (ZipArchive.Entry entry : entries) {
                if (entry.localOffset == resumeEntryOffset) {
                    prepareArchiveVideo(handle, entry, token, resumeFrame);
                    return;
                }
            }
            showError("视频位置已改变", "原视频位置已改变，可在已保留的播放列表中重新选择。");
        } else showArchiveEntries(handle, entries, token);
    }

    private void showPlaylist() {
        if (cacheBusy) { toast("正在清理临时文件，请稍候"); return; }
        if (playlistArchive == null) { showRecentArchives(); return; }
        pauseAndShowFrame();
        int token = archiveRequest.incrementAndGet();
        if (archiveDialog != null) archiveDialog.dismiss();
        showArchiveEntries(playlistArchive, playlistEntries, token);
    }

    private int currentPlaylistIndex() {
        if (playlistArchive == null || !playlistArchive.uri.equals(currentUri)) return -1;
        for (int i = 0; i < playlistEntries.size(); i++) {
            if (playlistEntries.get(i).localOffset == currentArchiveOffset) return i;
        }
        return -1;
    }

    private void switchPlaylistVideo(int delta) {
        if (destroyed || cacheBusy || timeline == null
                || (archiveDialog != null && archiveDialog.isShowing())) return;
        int current = currentPlaylistIndex();
        if (current < 0) return;
        int target = current + delta;
        if (target < 0 || target >= playlistEntries.size()) return;
        int token = archiveRequest.incrementAndGet();
        pauseInternal();
        showControls();
        // Reuse the playlist preparation path, including cancellation and cache leases.
        prepareArchiveVideo(playlistArchive, playlistEntries.get(target), token, 0);
    }

    private void closePlaylist() {
        ArchiveSession old = playlistArchive;
        playlistArchive = null;
        playlistEntries = Collections.emptyList();
        playlistUri = null;
        playlistName = "";
        if (old != null) old.close();
        refreshButtons();
    }

    private JSONArray recentArchives() {
        try { return new JSONArray(getSharedPreferences("player", MODE_PRIVATE)
                .getString("recent_archives", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private void rememberArchive(Uri uri, String name) {
        try {
            JSONArray saved = new JSONArray();
            saved.put(new JSONObject().put("uri", uri.toString()).put("name", name));
            JSONArray old = recentArchives();
            for (int i = 0; i < old.length() && saved.length() < 5; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !uri.toString().equals(item.optString("uri"))) saved.put(item);
            }
            getSharedPreferences("player", MODE_PRIVATE).edit().putString("recent_archives", saved.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showRecentArchives() {
        if (cacheBusy) { toast("正在清理临时文件，请稍候"); return; }
        pauseAndShowFrame();
        JSONArray recent = recentArchives();
        if (recent.length() == 0) {
            new AppDialog.Builder(this).setTitle("还没有播放列表").setIconKind("playlist")
                    .setMessage("打开 ZIP 后，可从底部「列表」直接切换其中的视频。")
                    .setPositiveButton("打开 ZIP", (d, which) -> pickDocument(true))
                    .setNegativeButton("关闭", null).show();
            return;
        }
        String[] names = new String[recent.length()];
        for (int i = 0; i < names.length; i++) {
            JSONObject item = recent.optJSONObject(i);
            names[i] = item == null ? "无法读取的记录" : item.optString("name", "ZIP 压缩包");
        }
        new AppDialog.Builder(this).setTitle("最近压缩包").setIconKind("history")
                .setItems(names, (dialog, which) -> {
                    JSONObject item = recent.optJSONObject(which);
                    if (item != null) openArchive(Uri.parse(item.optString("uri")), 0, -1);
                }).setNeutralButton("清空记录", (d, which) -> getSharedPreferences("player", MODE_PRIVATE)
                        .edit().remove("recent_archives").apply())
                .setNegativeButton("关闭", null).show();
    }

    private File cacheDirectory() { return new File(getCacheDir(), "archives"); }

    private static String sizeText(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.CHINA, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.CHINA, "%.2f GB", bytes / 1073741824.0);
    }

    private void recordCleanup(TempStore.Cleanup result, String reason) {
        String text = String.format(Locale.CHINA, "%s：删除 %d 个，释放 %s；失败 %d 个，使用中保留 %d 个",
                reason, result.deleted, sizeText(result.freed), result.failed, result.skipped);
        getSharedPreferences("player", MODE_PRIVATE).edit().putString("last_cleanup", text)
                .putLong("last_cleanup_time", System.currentTimeMillis()).apply();
    }

    private void showCacheManager() {
        if (destroyed || cacheBusy) return;
        pauseAndShowFrame();
        archives.execute(() -> {
            TempStore.Snapshot snapshot = TempStore.snapshot(cacheDirectory());
            ui.post(() -> {
                if (destroyed || cacheBusy) return;
                String last = getSharedPreferences("player", MODE_PRIVATE).getString("last_cleanup", "暂无清理记录");
                long at = getSharedPreferences("player", MODE_PRIVATE).getLong("last_cleanup_time", 0);
                String when = at == 0 ? "" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(at)) + "\n";
                String message = snapshot.unreadable ? "暂时无法读取临时文件目录，请稍后重试。"
                        : String.format(Locale.CHINA,
                                "当前占用：%s（%d 个文件）\n视频缓存：%s\n压缩包暂存：%s\n\n使用中：%s（%d 个）\n可清理：%s（%d 个）\n\n%s\n播放列表：%s\n\n%s%s\n\n清理空闲文件会保留正在播放和准备中的文件。",
                                sizeText(snapshot.total), snapshot.count, sizeText(snapshot.videoBytes),
                                sizeText(snapshot.archiveBytes), sizeText(snapshot.activeBytes), snapshot.activeCount,
                                sizeText(snapshot.idleBytes), snapshot.idleCount, currentReadMode,
                                playlistArchive == null ? "未暂存压缩包" : playlistName, when, last);
                if (cacheDialog != null) cacheDialog.dismiss();
                cacheDialog = new AppDialog.Builder(this).setTitle("临时文件管理").setIconKind("cache").setMessage(message)
                        .setPositiveButton("清理空闲", (d, which) -> cleanIdleFiles())
                        .setNeutralButton("全部清理", (d, which) -> confirmClearAll())
                        .setNegativeButton("关闭", null).create();
                cacheDialog.show();
            });
        });
    }

    private void cleanIdleFiles() {
        if (destroyed || cacheBusy) return;
        cacheBusy = true;
        archives.execute(() -> {
            TempStore.Cleanup result = TempStore.clearIdle(cacheDirectory());
            recordCleanup(result, "手动清理");
            TempStore.Snapshot remaining = TempStore.snapshot(cacheDirectory());
            ui.post(() -> {
                cacheBusy = false;
                if (destroyed) return;
                String message = !remaining.unreadable && remaining.count == 0 ? "临时文件已清空（0 B）"
                        : String.format(Locale.CHINA, "已释放 %s，剩余 %s；使用中 %d 个，清理失败 %d 个",
                                sizeText(result.freed), sizeText(remaining.total), remaining.activeCount, result.failed);
                toast(message);
                showCacheManager();
            });
        });
    }

    private void confirmClearAll() {
        cacheDialog = new AppDialog.Builder(this).setTitle("停止播放并清理？").setIconKind("cache")
                .setMessage("将停止当前视频、关闭暂存的播放列表并清理临时文件。原视频、原压缩包和已保存的截图会保留。")
                .setPositiveButton("停止并清理", (d, which) -> stopAndClearAll())
                .setNegativeButton("取消", null).create();
        cacheDialog.show();
    }

    private void stopAndClearAll() {
        if (destroyed || cacheBusy) return;
        cacheBusy = true;
        archiveRequest.incrementAndGet();
        session.incrementAndGet();
        frameRequest.incrementAndGet();
        if (archiveDialog != null) { archiveDialog.dismiss(); archiveDialog = null; }
        releasePlayer();
        closePlaylist();
        currentUri = null;
        currentArchiveOffset = -1;
        currentReadMode = "当前未播放视频";
        timeline = null;
        currentBitmap = null;
        displayedFrame = -1;
        still.setImageDrawable(null);
        still.setVisibility(View.VISIBLE);
        empty.setVisibility(View.VISIBLE);
        busy.setVisibility(View.GONE);
        fileLabel.setText("FrameDeck · 逐帧播放器");
        detailLabel.setText("正在停止播放并清理临时文件…");
        clock.setText("00:00:00.000");
        frameLabel.setText("第 — / — 帧");
        seek.setProgress(0);
        refreshButtons();
        // Drain decoder and archive work, then drain their posted UI handoffs before deleting.
        decoder.execute(() -> {
            if (source != null) { source.close(); source = null; }
            ui.post(() -> {
                if (destroyed) return;
                archives.execute(() -> ui.post(() -> {
                    if (destroyed) return;
                    cacheBusy = false;
                    cleanIdleFiles();
                }));
            });
        });
    }

    private void showArchiveEntries(ArchiveSession handle, List<ZipArchive.Entry> entries, int token) {
        String[] names = new String[entries.size()];
        int selected = -1;
        for (int i = 0; i < names.length; i++) {
            ZipArchive.Entry entry = entries.get(i);
            String mode = entry.encrypted() ? "加密，暂不支持" : entry.method == 0 ? "直接读取" : "临时准备";
            names[i] = String.format(Locale.CHINA, "%s\n%.1f MB · %s",
                    entry.name.replaceAll("[\\p{Cntrl}]", " "), entry.size / 1048576.0, mode);
            if (handle.uri.equals(currentUri) && entry.localOffset == currentArchiveOffset) selected = i;
        }
        AlertDialog dialog = new AppDialog.Builder(this).setTitle(playlistName + " · " + entries.size() + " 个视频").setIconKind("playlist")
                .setSingleChoiceItems(names, selected, (d, which) -> {
                    d.dismiss();
                    prepareArchiveVideo(handle, entries.get(which), token, 0);
                }).setNeutralButton("更换压缩包", (d, which) -> pickDocument(true))
                .setNegativeButton("关闭", null).create();
        archiveDialog = dialog;
        dialog.show();
    }

    private void prepareArchiveVideo(ArchiveSession handle, ZipArchive.Entry entry, int token, int resumeFrame) {
        showArchiveProgress("准备视频", entry.method == 0 ? "正在直接读取所选视频…" : "正在准备所选视频…", token);
        archives.execute(() -> {
            try {
                MediaInput input = handle.prepare(entry, () -> destroyed || archiveRequest.get() != token,
                        done -> archiveMessage(token, String.format(Locale.CHINA,
                                "正在准备所选视频… %.1f / %.1f MB", done / 1048576.0, entry.size / 1048576.0)));
                if (destroyed || archiveRequest.get() != token) { input.close(); return; }
                ui.post(() -> {
                    if (destroyed || archiveRequest.get() != token) { input.close(); return; }
                    if (archiveDialog != null) { archiveDialog.dismiss(); archiveDialog = null; }
                    openMedia(input, resumeFrame);
                });
            } catch (Exception | OutOfMemoryError e) {
                archiveError(token, e);
            }
        });
    }

    private void showArchiveProgress(String title, String message, int token) {
        ProgressBar progress = new ProgressBar(this);
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(dp(24), dp(16), dp(24), dp(16));
        holder.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(40)));
        archiveDialog = new AppDialog.Builder(this).setTitle(title).setIconKind("zip").setMessage(message).setView(holder)
                .setNegativeButton("取消", (dialog, which) -> archiveRequest.compareAndSet(token, token + 1)).create();
        archiveDialog.setOnCancelListener(d -> archiveRequest.compareAndSet(token, token + 1));
        archiveDialog.setCanceledOnTouchOutside(false);
        archiveDialog.show();
    }

    private void archiveMessage(int token, String text) {
        ui.post(() -> {
            if (!destroyed && archiveRequest.get() == token && archiveDialog != null)
                archiveDialog.setMessage(text);
        });
    }

    private void archiveError(int token, Throwable error) {
        ui.post(() -> {
            if (destroyed || archiveRequest.get() != token) return;
            if (archiveDialog != null) { archiveDialog.dismiss(); archiveDialog = null; }
            showError("无法读取压缩包", errorText(error));
        });
    }

    private void openVideo(Uri uri, int resumeFrame) {
        openMedia(new MediaInput(uri, displayName(uri)), resumeFrame);
    }

    private void openMedia(MediaInput input, int resumeFrame) {
        if (destroyed) { input.close(); return; }
        archiveRequest.incrementAndGet();
        if (archiveDialog != null) { archiveDialog.dismiss(); archiveDialog = null; }
        int token = session.incrementAndGet();
        showControls();
        frameRequest.incrementAndGet();
        releasePlayer();
        currentUri = input.originalUri;
        currentArchiveOffset = input.archiveOffset;
        currentReadMode = input.archiveEntry == null ? "普通视频（无压缩包缓存）"
                : input.usesVideoCache() ? "包内视频：正在使用临时视频缓存" : "包内视频：直接读取，无视频副本";
        timeline = null;
        resetVideoZoom();
        currentFrame = 0;
        displayedFrame = -1;
        currentBitmap = null;
        still.setImageDrawable(null);
        still.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        busy.setVisibility(View.VISIBLE);
        fileName = input.displayName;
        fileLabel.setText(fileName);
        detailLabel.setText("正在建立帧索引…");
        refreshButtons();
        decoder.execute(() -> {
            if (source != null) { source.close(); source = null; }
            if (session.get() != token) { input.close(); return; }
            try {
                source = new FrameSource(getApplicationContext(), input,
                        () -> session.get() != token,
                        progress -> ui.post(() -> {
                            if (session.get() == token && !destroyed)
                                detailLabel.setText(getString(R.string.index_progress, progress));
                        }));
                FrameSource loaded = source;
                int frame = loaded.timeline.clamp(resumeFrame);
                Bitmap first = loaded.frame(frame);
                ui.post(() -> {
                    if (destroyed || session.get() != token) { first.recycle(); return; }
                    timeline = loaded.timeline;
                    durationUs = loaded.durationUs;
                    videoWidth = loaded.width;
                    videoHeight = loaded.height;
                    currentFrame = frame;
                    seek.setMax(timeline.size() - 1);
                    double fps = timeline.size() * 1_000_000.0 / durationUs;
                    detailLabel.setText(String.format(Locale.CHINA, "%d × %d  ·  %s  ·  平均 %.2f fps",
                            videoWidth, videoHeight, FrameTimeline.format(durationUs), fps));
                    showBitmap(first, frame);
                    // Loading media must preserve the current screen orientation.
                    fitVideo();
                    createPlayer(input, token);
                });
            } catch (Exception | OutOfMemoryError e) {
                if (source != null) { source.close(); source = null; }
                input.close();
                ui.post(() -> {
                    if (destroyed || session.get() != token) return;
                    busy.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                    detailLabel.setText("打开失败，可重新选择视频");
                    showError("无法打开视频", errorText(e));
                    refreshButtons();
                });
            }
        });
    }

    private void createPlayer(MediaInput input, int token) {
        try {
            player = new MediaPlayer();
            MediaPlayer created = player;
            created.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            if (playbackSurface != null) created.setSurface(playbackSurface);
            input.attach(this, created);
            created.setOnPreparedListener(mp -> {
                if (destroyed || session.get() != token || player != mp) return;
                prepared = true;
                refreshButtons();
            });
            created.setOnSeekCompleteListener(mp -> {
                if (player != mp || !pendingPlay || !foreground || !prepared) return;
                try {
                    mp.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
                    mp.start();
                    pendingPlay = false;
                    playing = true;
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    refreshButtons();
                    ui.removeCallbacks(tick);
                    ui.post(tick);
                    scheduleControlsHide();
                } catch (RuntimeException e) { playbackError(errorText(e)); }
            });
            created.setOnCompletionListener(mp -> {
                if (player != mp || timeline == null) return;
                pauseInternal();
                requestFrame(timeline.size() - 1);
            });
            created.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) playbackError("系统播放器错误 " + what + "/" + extra + "，仍可尝试逐帧查看");
                return true;
            });
            created.prepareAsync();
        } catch (Exception e) { playbackError(errorText(e)); }
    }

    private void requestFrame(int index) {
        requestFrame(index, true);
    }

    private void requestFrame(int index, boolean revealControls) {
        if (timeline == null || destroyed) return;
        if (revealControls) showControls();
        pauseInternal();
        currentFrame = timeline.clamp(index);
        int frame = currentFrame, token = session.get(), request = frameRequest.incrementAndGet();
        still.setVisibility(View.VISIBLE);
        busy.setVisibility(View.VISIBLE);
        refreshPosition();
        refreshButtons();
        decoder.execute(() -> {
            if (session.get() != token || frameRequest.get() != request || source == null) return;
            try {
                Bitmap image = source.frame(frame);
                ui.post(() -> {
                    if (destroyed || session.get() != token || frameRequest.get() != request) {
                        image.recycle(); return;
                    }
                    showBitmap(image, frame);
                });
            } catch (Exception | OutOfMemoryError e) {
                ui.post(() -> {
                    if (destroyed || session.get() != token || frameRequest.get() != request) return;
                    busy.setVisibility(View.GONE);
                    // Keep labels tied to the image actually visible after a failed decode.
                    currentFrame = Math.max(0, displayedFrame);
                    refreshPosition();
                    refreshButtons();
                    toast("读取失败：" + errorText(e));
                });
            }
        });
    }

    private void showBitmap(Bitmap image, int index) {
        currentBitmap = image;
        displayedFrame = index;
        still.setImageBitmap(image);
        still.setVisibility(View.VISIBLE);
        busy.setVisibility(View.GONE);
        refreshPosition();
        refreshButtons();
    }

    private void step(int delta) {
        step(delta, true);
    }

    private void step(int delta, boolean revealControls) {
        if (timeline == null) return;
        if (playing && player != null) currentFrame = timeline.atTime(player.getCurrentPosition() * 1000L);
        requestFrame(currentFrame + delta, revealControls);
    }

    private void startPlayback() {
        if (!prepared || player == null || timeline == null || !foreground) return;
        if (playbackSurface == null) { toast("视频画面尚未准备好，请稍后重试"); return; }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            toast("其他应用正在使用音频，请稍后重试"); return;
        }
        frameRequest.incrementAndGet();
        busy.setVisibility(View.GONE);
        if (currentFrame == timeline.size() - 1) currentFrame = 0;
        pendingPlay = true;
        refreshButtons();
        try { player.seekTo(Math.max(0, timeline.timeUs(currentFrame) / 1000), MediaPlayer.SEEK_CLOSEST); }
        catch (RuntimeException e) { playbackError(errorText(e)); }
    }

    private void pauseAndShowFrame() {
        if (timeline == null) { pauseInternal(); return; }
        int target = currentFrame;
        if (playing && player != null) {
            try { target = timeline.atTime(player.getCurrentPosition() * 1000L); }
            catch (IllegalStateException ignored) { }
        }
        pauseInternal();
        showControls();
        requestFrame(target);
    }

    private void pauseInternal() {
        pendingPlay = false;
        if (playing && player != null) {
            try { player.pause(); } catch (IllegalStateException ignored) { }
        }
        playing = false;
        ui.removeCallbacks(tick);
        ui.removeCallbacks(hideControlsLater);
        if (audio != null) audio.abandonAudioFocusRequest(focus);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshButtons();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!playing || player == null || timeline == null || destroyed) return;
            try {
                currentFrame = timeline.atTime(player.getCurrentPosition() * 1000L);
                refreshPosition();
                ui.postDelayed(this, 70);
            } catch (IllegalStateException e) { playbackError(errorText(e)); }
        }
    };

    private void refreshPosition() {
        if (timeline == null) return;
        clock.setText(FrameTimeline.format(timeline.timeUs(currentFrame)));
        frameLabel.setText(getString(R.string.frame_counter, currentFrame + 1, timeline.size()));
        if (!dragging) seek.setProgress(currentFrame);
    }

    private void refreshButtons() {
        if (play == null) return;
        boolean ready = timeline != null;
        int entry = ready && !cacheBusy ? currentPlaylistIndex() : -1;
        previousVideo.setEnabled(entry > 0);
        nextVideo.setEnabled(entry >= 0 && entry + 1 < playlistEntries.size());
        previous.setEnabled(ready);
        next.setEnabled(ready);
        jump.setEnabled(ready);
        rate.setEnabled(ready);
        seek.setEnabled(ready);
        play.setEnabled(ready && prepared);
        play.setText(playing || pendingPlay ? "暂停" : "播放");
        save.setEnabled(ready && !playing && !pendingPlay && currentBitmap != null
                && currentFrame == displayedFrame && busy.getVisibility() != View.VISIBLE);
        for (View view : new View[]{previousVideo, nextVideo, previous, next, jump, rate, seek, play, save})
            view.setAlpha(view.isEnabled() ? 1f : 0.38f);
        updatePinnedFrameButtons();
    }

    private void jumpToFrame() {
        if (timeline == null) return;
        pauseAndShowFrame();
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(String.valueOf(currentFrame + 1));
        field.selectAll();
        field.setTextColor(Color.WHITE);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(24), dp(8), dp(24), 0);
        holder.addView(field, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AppDialog.Builder(this).setTitle("跳到指定帧").setIconKind("jump")
                .setMessage("输入 1 到 " + timeline.size() + " 之间的帧号")
                .setView(holder).setNegativeButton("取消", null).setPositiveButton("跳转", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long number = Long.parseLong(field.getText().toString().trim());
                if (number < 1 || number > timeline.size()) throw new NumberFormatException();
                requestFrame((int) number - 1);
                dialog.dismiss();
            } catch (NumberFormatException e) { field.setError("请输入有效的帧号"); }
        }));
        dialog.show();
    }

    private void chooseSpeed() {
        float[] speeds = {0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f};
        String[] labels = {"0.25×", "0.5×", "0.75×", "1×", "1.5×", "2×"};
        int selected = 3;
        for (int i = 0; i < speeds.length; i++) if (speeds[i] == speed) selected = i;
        new AppDialog.Builder(this).setTitle("播放速度").setIconKind("speed")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    float oldSpeed = speed;
                    speed = speeds[which];
                    try {
                        if (playing && player != null) player.setPlaybackParams(
                                new PlaybackParams().setSpeed(speed).setPitch(1f));
                        rate.setText(labels[which]);
                    } catch (RuntimeException e) { speed = oldSpeed; toast("此设备不支持该播放速度"); }
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }

    private void exportFrame() {
        if (currentBitmap == null || playing || displayedFrame != currentFrame) return;
        exportBitmap = currentBitmap; // Stable snapshot while the system save picker is open.
        exportFrame = displayedFrame + 1;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        String base = fileName.replaceFirst("\\.[^.]+$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        if (base.length() > 70) base = base.substring(0, 70);
        intent.putExtra(Intent.EXTRA_TITLE, base + String.format(Locale.US, "_frame_%06d.png", exportFrame));
        startActivityForResult(intent, SAVE_FRAME);
    }

    private void fitVideo() {
        if (videoWidth <= 0 || videoHeight <= 0 || texture.getWidth() == 0 || texture.getHeight() == 0) return;
        float w = texture.getWidth(), h = texture.getHeight();
        float scale = Math.min(w / videoWidth, h / videoHeight);
        float fittedW = videoWidth * scale, fittedH = videoHeight * scale;
        Matrix matrix = new Matrix();
        matrix.setScale(fittedW / w, fittedH / h);
        matrix.postTranslate((w - fittedW) / 2, (h - fittedH) / 2);
        texture.setTransform(matrix);
        videoTransform.setGeometry(w, h, fittedW, fittedH);
        applyVideoZoom();
    }

    private void updateEmptyFrame() {
        if (empty != null && header != null && controls != null)
            empty.setFrameInsets(header.getHeight() + dp(18), controls.getHeight() + dp(22));
    }

    private void toggleFullScreen() {
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        setRequestedOrientation(wide ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        showControls();
    }

    private void applyOrientation() {
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        exitFull.setText(wide ? "竖屏" : "横屏");
        exitFull.setContentDescription(wide ? "切换为竖屏播放" : "切换为横屏全屏播放");
        // Keep video/frame navigation and file actions in separate rows in both orientations.
        transport.removeAllViews();
        actions.removeAllViews();
        previousVideo.setText(wide ? "上一个视频" : "上个\n视频");
        nextVideo.setText(wide ? "下一个视频" : "下个\n视频");
        for (Button action : new Button[]{previousVideo, previous, play, next, nextVideo})
            addWeighted(transport, action, 48);
        for (Button action : new Button[]{open, playlist, rate, jump, save, exitFull})
            addWeighted(actions, action, 48);
        root.requestApplyInsets();
        root.post(this::updateEmptyFrame);
        updatePinnedFrameButtons();
    }

    private void applyImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private final Runnable hideControlsLater = () -> {
        if (playing && !dragging && foreground && timeline != null) hideControls();
    };

    private void showControls() {
        controlsVisible = true;
        header.setVisibility(View.VISIBLE);
        controls.setVisibility(View.VISIBLE);
        updatePinnedFrameButtons();
        scheduleControlsHide();
    }

    private void hideControls() {
        ui.removeCallbacks(hideControlsLater);
        controlsVisible = false;
        header.setVisibility(View.GONE);
        controls.setVisibility(View.GONE);
        updatePinnedFrameButtons();
    }

    private void scheduleControlsHide() {
        ui.removeCallbacks(hideControlsLater);
        if (playing && controlsVisible && !dragging && foreground)
            ui.postDelayed(hideControlsLater, 3000);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) ui.removeCallbacks(hideControlsLater);
        boolean result = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) scheduleControlsHide();
        return result;
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void handleBack() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            showControls();
        } else finish();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { handleBack(); return true; }
        if (timeline != null) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { step(-1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { step(1); return true; }
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (playing || pendingPlay) pauseAndShowFrame(); else startPlayback();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        applyOrientation();
        texture.post(this::fitVideo);
    }
    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        applyImmersiveMode();
    }
    @Override protected void onPause() {
        foreground = false;
        ui.removeCallbacks(hideControlsLater);
        if (playing || pendingPlay) pauseAndShowFrame();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (currentUri != null) out.putParcelable("uri", currentUri);
        out.putLong("archiveOffset", currentArchiveOffset);
        out.putInt("frame", currentFrame);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        archiveRequest.incrementAndGet();
        if (archiveDialog != null) { archiveDialog.dismiss(); archiveDialog = null; }
        if (cacheDialog != null) { cacheDialog.dismiss(); cacheDialog = null; }
        closePlaylist();
        ui.removeCallbacks(hideControlsLater);
        session.incrementAndGet();
        frameRequest.incrementAndGet();
        releasePlayer();
        decoder.execute(() -> { if (source != null) { source.close(); source = null; } });
        decoder.shutdown();
        exporter.shutdown();
        archives.shutdown();
        if (playbackSurface != null) { playbackSurface.release(); playbackSurface = null; }
        super.onDestroy();
    }

    private void playbackError(String text) {
        releasePlayer();
        if (timeline != null) requestFrame(currentFrame);
        toast("连续播放不可用：" + text);
    }
    private void releasePlayer() {
        pauseInternal();
        prepared = false;
        if (player != null) { player.release(); player = null; }
        refreshButtons();
    }
    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) { }
        return "本地视频";
    }
    private String errorText(Throwable e) {
        if (e instanceof OutOfMemoryError) return "视频分辨率过高，内存不足，请先降低分辨率";
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    private void showError(String title, String message) {
        new AppDialog.Builder(this).setTitle(title).setIconKind("info").setMessage(message).setPositiveButton("知道了", null).show();
    }
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Button button(String text, boolean primary) {
        Button view = new Button(this);
        view.setText(text);
        view.setTextSize(13);
        view.setAllCaps(false);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(4), 0, dp(4), 0);
        view.setTextColor(primary ? BG : Color.WHITE);
        view.setBackground(round(primary ? ACCENT : PANEL, 12));
        view.setStateListAnimator(null);
        view.setContentDescription(text);
        return view;
    }
    private void addWeighted(LinearLayout row, View child, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), 1);
        if (row.getChildCount() > 0) p.leftMargin = dp(8);
        row.addView(child, p);
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
