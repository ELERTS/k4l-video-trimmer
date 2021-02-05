/*
 * MIT License
 *
 * Copyright (c) 2016 Knowledge, education for life.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package life.knowledge4.videotrimmer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.ui.widget.VideoView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import life.knowledge4.videotrimmer.events.MediaDoneEvent;
import life.knowledge4.videotrimmer.events.MediaErrorEvent;
import life.knowledge4.videotrimmer.events.MediaProgressEvent;
import life.knowledge4.videotrimmer.interfaces.OnK4LVideoListener;
import life.knowledge4.videotrimmer.interfaces.OnProgressVideoListener;
import life.knowledge4.videotrimmer.interfaces.OnQualityChooseListener;
import life.knowledge4.videotrimmer.interfaces.OnRangeSeekBarListener;
import life.knowledge4.videotrimmer.interfaces.OnTrimVideoListener;
import life.knowledge4.videotrimmer.utils.AndroidUtilities;
import life.knowledge4.videotrimmer.utils.BackgroundExecutor;
import life.knowledge4.videotrimmer.utils.TranscodeVideoUtils;
import life.knowledge4.videotrimmer.utils.TrimVideoUtils;
import life.knowledge4.videotrimmer.utils.UiThreadExecutor;
import life.knowledge4.videotrimmer.view.ProgressBarView;
import life.knowledge4.videotrimmer.view.QualityChooseView;
import life.knowledge4.videotrimmer.view.RangeSeekBarView;
import life.knowledge4.videotrimmer.view.Thumb;
import life.knowledge4.videotrimmer.view.TimeLineView;
import life.knowledge4.videotrimmer.view.VideoTimelineView;

import static life.knowledge4.videotrimmer.utils.TrimVideoUtils.stringForTime;

public class K4LVideoTrimmer extends FrameLayout {

    private static final String TAG = K4LVideoTrimmer.class.getSimpleName();
    private static final int MIN_TIME_FRAME = 200;
    private static final int SHOW_PROGRESS = 2;
    private static final int MAX_FILE_SIZE = 1012;
    private static final int MAX_FILE_SIZE_BEFORE_DECODE = 1512;

    private SeekBar mHolderTopView;
    private RangeSeekBarView mRangeSeekBarView;
    private View mTimeInfoContainer;
    private VideoView mVideoView;
    private ImageView mPlayView;
    private ImageView compressItem;
    private ImageView muteItem;
    private LinearLayout mControlWrapper;
    private TextView mTextSize;
    private TextView mTextTimeFrame;
    private TextView mTextTime;
    private TimeLineView mTimeLineView;
    private VideoTimelineView videoTimelineView;
    private QualityChooseView qualityChooseView;

    private ProgressBarView mVideoProgressIndicator;
    private Uri mSrc;
    private String mFinalPath;

    private int mMaxDuration;
    private List<OnProgressVideoListener> mListeners;

    private OnTrimVideoListener mOnTrimVideoListener;
    private OnK4LVideoListener mOnK4LVideoListener;

    private long mDuration = 0;
    private long mTimeVideo = 0;
    private long mStartPosition = 0;
    private long mEndPosition = 0;

    private long mOriginSizeFile;
    private boolean mResetSeekBar = true;
    private final MessageHandler mMessageHandler = new MessageHandler(this);
    private int defaultVideoWidth;
    private int defaultVideoHeight;
    private int compressionsCount = 1;
    private int selectedCompression = -1;
    private float qualitySize = 1;
    private boolean muteVideo = false;
    private boolean needPrepared = true;
    private Context mContext;

    public K4LVideoTrimmer(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public K4LVideoTrimmer(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context.getApplicationContext());
    }

    private void init(Context context) {
        if (isInEditMode()) {
            return;
        }

        EventBus.getDefault().register(this);
        mContext = context.getApplicationContext();

        AndroidUtilities.checkDisplaySize(context);

        LayoutInflater.from(context).inflate(R.layout.view_time_line_v2, this, true);

        mHolderTopView = ((SeekBar) findViewById(R.id.handlerTop));
        mVideoProgressIndicator = ((ProgressBarView) findViewById(R.id.timeVideoView));
        mRangeSeekBarView = ((RangeSeekBarView) findViewById(R.id.timeLineBar));
        mVideoView = ((VideoView) findViewById(R.id.video_view));
        mPlayView = ((ImageView) findViewById(R.id.icon_video_play));
        mTimeInfoContainer = findViewById(R.id.timeText);
        mTextSize = ((TextView) findViewById(R.id.textSize));
        mTextTimeFrame = ((TextView) findViewById(R.id.textTimeSelection));
        mTextTime = ((TextView) findViewById(R.id.textTime));
        mTimeLineView = ((TimeLineView) findViewById(R.id.timeLineView));
        videoTimelineView = ((VideoTimelineView) findViewById(R.id.videoTimelineView));
        mControlWrapper = ((LinearLayout) findViewById(R.id.controlWrapper));
        compressItem = ((ImageView) findViewById(R.id.compressItem));
        muteItem = ((ImageView) findViewById(R.id.muteItem));
        qualityChooseView = ((QualityChooseView) findViewById(R.id.qualityChooseView));

        setUpListeners();
        setUpMargins();
    }

    private void setUpListeners() {
        mListeners = new ArrayList<>();
        mListeners.add(new OnProgressVideoListener() {
            @Override
            public void updateProgress(long time, long max, float scale) {
                updateVideoProgress(time);
            }
        });
        mListeners.add(mVideoProgressIndicator);

        findViewById(R.id.btCancel)
                .setOnClickListener(
                        new OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                onCancelClicked();
                            }
                        }
                );

        findViewById(R.id.btSave)
                .setOnClickListener(
                        new OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                onSaveClicked();
                            }
                        }
                );

        final GestureDetector gestureDetector = new
                GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        onClickVideoPlayPause();
                        return true;
                    }
                }
        );

        mVideoView.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(Exception e) {
                if (mOnTrimVideoListener != null)
                    exceptionHandler();
                return false;
            }
        });

        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, @NonNull MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });

        mRangeSeekBarView.addOnRangeSeekBarListener(new OnRangeSeekBarListener() {
            @Override
            public void onCreate(RangeSeekBarView rangeSeekBarView, int index, float value) {
                // Do nothing
            }

            @Override
            public void onSeek(RangeSeekBarView rangeSeekBarView, int index, float value) {
                onSeekThumbs(index, value);
            }

            @Override
            public void onSeekStart(RangeSeekBarView rangeSeekBarView, int index, float value) {
                // Do nothing
            }

            @Override
            public void onSeekStop(RangeSeekBarView rangeSeekBarView, int index, float value) {
                onStopSeekThumbs();
            }
        });
        mRangeSeekBarView.addOnRangeSeekBarListener(mVideoProgressIndicator);

        mHolderTopView.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onPlayerIndicatorSeekChanged(progress, fromUser);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                onPlayerIndicatorSeekStart();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                onPlayerIndicatorSeekStop(seekBar);
            }
        });

        mVideoView.setOnPreparedListener(new OnPreparedListener() {
            @Override
            public void onPrepared() {
                if (needPrepared) {
                    onVideoPrepared();
                } else {
                    onClickVideoPlayPause();
                }
            }
        });

        mVideoView.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion() {
                onVideoCompleted();
            }
        });

        muteItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onMuteClick();
            }
        });

        compressItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (qualityChooseView.isShown()) {
                    qualityChooseView.setVisibility(GONE);
                } else if (compressionsCount > 1) {
                    qualityChooseView.setVisibility(VISIBLE);
                }
            }
        });
        qualityChooseView.setOnQualityChooseListener(new OnQualityChooseListener() {
            @Override
            public void selectedResolution(int selectedCompression) {
                onQualityChoseChanges(selectedCompression);
            }
        });
    }

    private void onQualityChoseChanges(int selectedCompression) {
        qualitySize = calcQualitySize(selectedCompression);
        this.selectedCompression = selectedCompression;
        setDefaultVideoResolution(selectedCompression);
        setNewSize();
    }

    private float calcQualitySize(int selectedCompression) {
        if (defaultVideoWidth * defaultVideoHeight > 0) {
            return TranscodeVideoUtils.getResolutionX(selectedCompression, defaultVideoHeight, defaultVideoWidth) / (defaultVideoWidth * defaultVideoHeight);
        }
        return 1;
    }

    private void setUpMargins() {
        int marge = mRangeSeekBarView.getThumbs().get(0).getWidthBitmap();
        int widthSeek = mHolderTopView.getThumb().getMinimumWidth() / 2;

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHolderTopView.getLayoutParams();
        lp.setMargins(marge - widthSeek, 0, marge - widthSeek, 0);
        mHolderTopView.setLayoutParams(lp);

        lp = (RelativeLayout.LayoutParams) mTimeLineView.getLayoutParams();
        lp.setMargins(marge, 0, marge, 0);
        mTimeLineView.setLayoutParams(lp);

        lp = (RelativeLayout.LayoutParams) mVideoProgressIndicator.getLayoutParams();
        lp.setMargins(marge, 0, marge, 0);
        mVideoProgressIndicator.setLayoutParams(lp);
    }

    private void onMuteClick() {
        muteVideo = !muteVideo;
        if (mVideoView != null) {
            float volume = muteVideo ? 0.0f : 1.0f;
            mVideoView.setVolume(volume);
        }
        if (muteVideo) {
            muteItem.setImageResource(R.drawable.volume_off);
        } else {
            muteItem.setImageResource(R.drawable.volume_on);
        }
    }

    private void getVideoResolution() throws NumberFormatException {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(getContext(), mSrc);
        defaultVideoWidth = Integer.valueOf(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        defaultVideoHeight = Integer.valueOf(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        getResolutionType();
    }

    private void getResolutionType() {
        if (defaultVideoWidth >= 1920 || defaultVideoHeight >= 1920) {
            compressionsCount = 3;
        } else if (defaultVideoWidth >= 1080 || defaultVideoHeight >= 1080) {
            compressionsCount = 2;
        } else if (defaultVideoWidth >= 720 || defaultVideoHeight >= 720) {
            compressionsCount = 1;
        } else {
            compressionsCount = 1;
        }
    }

    private void setDefaultVideoResolution(int compressionsCount) {
        if (compressionsCount == 1) {
            compressItem.setImageResource(R.drawable.video_480);
        } else if (compressionsCount == 2) {
            compressItem.setImageResource(R.drawable.video_720);
        } else if (compressionsCount == 3) {
            compressItem.setImageResource(R.drawable.video_1080);
        }
    }

    private String getResolutionName() {
        if (selectedCompression == 1) {
            return "480p_";
        } else if (selectedCompression == 2) {
            return "720p_";
        } else if (selectedCompression == 3) {
            return "1080p_";
        }
        return "";
    }

    private void copyFileToDest(String destPath) {
        try {
            FileInputStream input = new FileInputStream(new File(mSrc.toString()));
            FileOutputStream out = new FileOutputStream(destPath);
            byte buf[] = new byte[1024];
            do {
                int numRead = input.read(buf);
                if (numRead <= 0)
                    break;
                out.write(buf, 0, numRead);
            } while (true);
            try {
                input.close();
                out.close();
            } catch (IOException ignore) {
            }
        } catch (IOException ignore) {
        }
        mOnTrimVideoListener.getResult(Uri.parse(destPath));
    }

    public void onSaveClicked() {
        final boolean needTrim = !(mStartPosition <= 0 && mEndPosition >= mDuration) || muteVideo;
        final boolean needCompression = !((selectedCompression == compressionsCount) || selectedCompression == -1);
        final String destPath = getDestinationPath();

        if (isFileOverSize()) {
            if (mOnTrimVideoListener != null)
                mOnTrimVideoListener.cancelAction();
            return;
        }

        //notify that video trimming started
        if (mOnTrimVideoListener != null) {
            mOnTrimVideoListener.onTrimStarted();
            mOnTrimVideoListener.onProgress(0.0f);
        }

        if (!needTrim && !needCompression) {
            if (mOnTrimVideoListener != null)
                copyFileToDest(destPath);
        } else {
            mPlayView.setVisibility(View.VISIBLE);
            mVideoView.pause();

            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(getContext(), mSrc);
            long METADATA_KEY_DURATION = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

            final File file = new File(mSrc.getPath());

            if (mTimeVideo < MIN_TIME_FRAME) {

                if ((METADATA_KEY_DURATION - mEndPosition) > (MIN_TIME_FRAME - mTimeVideo)) {
                    mEndPosition += (MIN_TIME_FRAME - mTimeVideo);
                } else if (mStartPosition > (MIN_TIME_FRAME - mTimeVideo)) {
                    mStartPosition -= (MIN_TIME_FRAME - mTimeVideo);
                }
            }

            BackgroundExecutor.execute(
                    new BackgroundExecutor.Task("", 0L, "") {
                        @Override
                        public void execute() {
                            mOnTrimVideoListener.onProgress(0.01f);
                            try {
                                if (needTrim) {
                                    TrimVideoUtils.startTrim(file, destPath, mStartPosition, mEndPosition, muteVideo, mOnTrimVideoListener);
                                }

                                mOnTrimVideoListener.onProgress(0.13f);

                                if (needCompression) {
                                    File destFile = new File(destPath);
                                    long length = destFile.length();

                                    Uri mediaUri;
                                    if (needTrim && length > 30000) {
                                        mediaUri = Uri.fromFile(destFile);
                                    } else {
                                        mediaUri = mSrc;
                                    }
                                    String destPath = getTranscodeDestinationPath();

                                    TranscodeVideoUtils.getDefaultFileInfo(mContext, mediaUri, destPath);
                                    TranscodeVideoUtils.setResolutionAndQuality(selectedCompression);
                                    TranscodeVideoUtils.startTranscode(mContext);
                                } else {
                                    if (new File(destPath).length() > 30000) {
                                        mOnTrimVideoListener.getResult(Uri.parse(destPath));
                                    } else {
                                        copyFile(new File(mSrc.getPath()), new File(destPath));
                                        mOnTrimVideoListener.getResult(Uri.parse(destPath));
                                    }
                                }
                            } catch (Exception e) {
                                try {
                                    copyFile(new File(mSrc.getPath()), new File(destPath));
                                } catch (IOException ignore) {
                                }
                                mOnTrimVideoListener.getResult(Uri.parse(destPath));
                            }
                        }
                    }
            );
        }
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (destFile.delete() || !destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    public static File removeSuffixFile(File sourceFile, File destFile) throws IOException {
        sourceFile.delete();
        if (sourceFile.exists()) {
            sourceFile.getCanonicalFile().delete();
        }
        destFile.renameTo(sourceFile);
        return sourceFile;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMediaDoneEvent(MediaDoneEvent event) {
        mOnTrimVideoListener.onProgress(1.0f);
        try {
            File file = removeSuffixFile(new File(getDestinationPath()), new File(getTranscodeDestinationPath()));
            Uri uri = Uri.fromFile(file);
            mOnTrimVideoListener.getResult(uri);
        } catch (IOException ignore) {
            mOnTrimVideoListener.getResult(Uri.parse(getDestinationPath()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMediaErrorEvent(MediaErrorEvent event) {
        exceptionHandler();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMediaProgressEvent(MediaProgressEvent event) {
        if (event.getProgress() > 0.13f) mOnTrimVideoListener.onProgress(event.getProgress());
    }

    private void exceptionHandler() {
        if (new File(getDestinationPath()).length() > 30000) {
            mOnTrimVideoListener.getResult(Uri.parse(getDestinationPath()));
        } else {
            BackgroundExecutor.execute(
                    new BackgroundExecutor.Task("", 0L, "") {
                        @Override
                        public void execute() {
                            try {
                                copyFile(new File(mSrc.getPath()), new File(getDestinationPath()));
                                mOnTrimVideoListener.getResult(Uri.parse(getDestinationPath()));
                            } catch (IOException e) {
                                mOnTrimVideoListener.cancelAction();
                            }
                        }
                    });
        }
    }

    private void onClickVideoPlayPause() {
        if (mVideoView.isPlaying()) {
            mPlayView.setVisibility(View.VISIBLE);
            mMessageHandler.removeMessages(SHOW_PROGRESS);
            mVideoView.pause();
        } else {
            mPlayView.setVisibility(View.GONE);

            if (mResetSeekBar) {
                mResetSeekBar = false;
                mVideoView.seekTo(mStartPosition);
            }

            mMessageHandler.sendEmptyMessage(SHOW_PROGRESS);
            mVideoView.start();
        }
    }

    public void onCancelClicked() {
        mVideoView.stopPlayback();
        if (mOnTrimVideoListener != null) {
            mOnTrimVideoListener.cancelAction();
        }
        TranscodeVideoUtils.stopTranscode();
    }

    private String getDestinationPath() {
        if (mFinalPath == null) {
            final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final String fileName = "MP4_" + timeStamp + ".mp4";
            File folder = Environment.getExternalStorageDirectory();
            mFinalPath = folder.getPath() + File.separator + fileName;
        }
        return mFinalPath;
    }

    private String getTranscodeDestinationPath() {
        if (mFinalPath == null) {
            final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final String fileName = "MP4_" + getResolutionName() + timeStamp + ".mp4";
            File folder = Environment.getExternalStorageDirectory();
            return folder.getPath() + File.separator + fileName;
        } else {
            File originalFile = new File(mFinalPath);
            String name = getResolutionName() + originalFile.getName();
            File transcodeFile = new File(mFinalPath.substring(0, mFinalPath.indexOf(originalFile.getName())) + name);
            return transcodeFile.getPath();
        }
    }

    private void onPlayerIndicatorSeekChanged(int progress, boolean fromUser) {

        long duration = ((mDuration * progress) / 1000L);

        if (fromUser) {
            if (duration < mStartPosition) {
                setProgressBarPosition(mStartPosition);
                duration = mStartPosition;
            } else if (duration > mEndPosition) {
                setProgressBarPosition(mEndPosition);
                duration = mEndPosition;
            }
            setTimeVideo(duration);
        }
    }

    private void onPlayerIndicatorSeekStart() {
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
        mPlayView.setVisibility(View.VISIBLE);
        notifyProgressUpdate(false);
    }

    private void onPlayerIndicatorSeekStop(@NonNull SeekBar seekBar) {
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
        mPlayView.setVisibility(View.VISIBLE);

        long duration = ((mDuration * seekBar.getProgress()) / 1000);
        mVideoView.seekTo(duration);
        setTimeVideo(duration);
        notifyProgressUpdate(false);
    }

    private void onVideoPrepared() {
        mPlayView.setVisibility(View.VISIBLE);

        mDuration = mVideoView.getDuration();
        setSeekBarPosition();

        setTimeFrames();
        setTimeVideo(0);

        if (mOnK4LVideoListener != null) {
            mOnK4LVideoListener.onVideoPrepared();
        }
        try {
            getVideoResolution();
        } catch (IllegalArgumentException e) {
            try {
                copyFile(new File(mSrc.getPath()), new File(getDestinationPath()));
            } catch (IOException ignore) {
            }
            mOnTrimVideoListener.getResult(Uri.parse(getDestinationPath()));
            return;
        }
        setDefaultVideoResolution(compressionsCount);
        qualityChooseView.setOriginalCompression(compressionsCount);
    }

    private void setSeekBarPosition() {

        if (mDuration >= mMaxDuration) {
            mStartPosition = mDuration / 2 - mMaxDuration / 2;
            mEndPosition = mDuration / 2 + mMaxDuration / 2;

            mRangeSeekBarView.setThumbValue(0, (mStartPosition * 100) / mDuration);
            mRangeSeekBarView.setThumbValue(1, (mEndPosition * 100) / mDuration);

        } else {
            mStartPosition = 0;
            mEndPosition = mDuration;
        }

        setProgressBarPosition(mStartPosition);
        mVideoView.seekTo(mStartPosition);

        mTimeVideo = mDuration;
        mRangeSeekBarView.initMaxWidth();
    }

    private void setTimeFrames() {
        String seconds = getContext().getString(R.string.short_seconds);
        mTextTimeFrame.setText(String.format("%s %s - %s %s", stringForTime(mStartPosition), seconds, stringForTime(mEndPosition), seconds));
    }

    private void setTimeVideo(long position) {
        String seconds = getContext().getString(R.string.short_seconds);
        mTextTime.setText(String.format("%s %s", stringForTime(position), seconds));
    }

    private void setNewSize() {
        long fileSizeInKB = (long) ((mOriginSizeFile / 1024) * calcNewFileSizeRatio());

        if (fileSizeInKB > 1000) {
            double fileSizeInMB = (float) fileSizeInKB / 1024f;
            if (fileSizeInMB < MAX_FILE_SIZE) {
                mTextSize.setText(String.format("~%s %s", new DecimalFormat("##.##").format(fileSizeInMB), getContext().getString(R.string.megabyte)));
            } else {
                mTextSize.setText(String.format("%s%s", String.format("~%s %s! ", new DecimalFormat("##.##").format(fileSizeInMB), getContext().getString(R.string.megabyte)), getContext().getString(R.string.size_file_overflow)));
            }
        } else {
            mTextSize.setText(String.format("~%s %s", fileSizeInKB, getContext().getString(R.string.kilobyte)));
        }
    }

    private boolean isFileOverSize() {
        long fileSizeInKB = (mOriginSizeFile / 1024);
        long fileSizeInKBAfterDecode = (long) ((mOriginSizeFile / 1024) * calcNewFileSizeRatio());

        if (fileSizeInKB > 1000) {
            double fileSizeInMB = (float) fileSizeInKB / 1024f;
            double fileSizeInMBAfterDecode = (float) fileSizeInKBAfterDecode / 1024f;
            if (fileSizeInMB < MAX_FILE_SIZE) {
                return false;
            } else {
                if (fileSizeInMB < MAX_FILE_SIZE_BEFORE_DECODE && fileSizeInMBAfterDecode < MAX_FILE_SIZE) {
                    return false;
                }
                if (fileSizeInMB >= MAX_FILE_SIZE_BEFORE_DECODE) {
                    mTextSize.setText(String.format("%s%s", String.format("~%s %s! ", new DecimalFormat("##.##").format(fileSizeInMB),
                            getContext().getString(R.string.megabyte)), getContext().getString(R.string.size_file_overflow_original)));
                }
                return true;
            }
        } else {
            return false;
        }
    }

    private float calcNewFileSizeRatio() {
        if (mDuration == 0) return 0;
        float newFileSizeRatio = (float) (mEndPosition - mStartPosition) / mDuration;
        if (newFileSizeRatio < 1.0f) {
            newFileSizeRatio += ((1 - newFileSizeRatio) * 0.24f);
        }
        if (selectedCompression != compressionsCount && selectedCompression != -1) {
            if (compressionsCount > 3) {
                newFileSizeRatio *= (qualitySize * 0.285);
            } else {
                newFileSizeRatio *= (qualitySize * 0.85);
            }
        }
        return newFileSizeRatio;
    }

    private void onSeekThumbs(int index, float value) {
        switch (index) {
            case Thumb.LEFT: {
                mStartPosition = (long) ((mDuration * value) / 100L);
                mVideoView.seekTo(mStartPosition);
                break;
            }
            case Thumb.RIGHT: {
                mEndPosition = (long) ((mDuration * value) / 100L);
                break;
            }
        }
        setProgressBarPosition(mStartPosition);

        setTimeFrames();
        mTimeVideo = mEndPosition - mStartPosition;
    }

    private void onStopSeekThumbs() {
        mMessageHandler.removeMessages(SHOW_PROGRESS);
        mVideoView.pause();
        mPlayView.setVisibility(View.VISIBLE);
    }

    private void onVideoCompleted() {
        needPrepared = false;
        mVideoView.restart();
        mVideoView.seekTo(mStartPosition);
        setProgressBarPosition(mStartPosition);
        setTimeFrames();
        mResetSeekBar = true;
    }

    private void notifyProgressUpdate(boolean all) {
        if (mDuration == 0) return;

        long position = mVideoView.getCurrentPosition();
        if (all) {
            for (OnProgressVideoListener item : mListeners) {
                item.updateProgress(position, mDuration, ((position * 100) / mDuration));
            }
        } else {
            mListeners.get(1).updateProgress(position, mDuration, ((position * 100) / mDuration));
        }
    }

    private void updateVideoProgress(long time) {
        if (mVideoView == null) {
            return;
        }

        if (time >= mEndPosition) {
            mMessageHandler.removeMessages(SHOW_PROGRESS);
            mVideoView.pause();
            mPlayView.setVisibility(View.VISIBLE);
            mResetSeekBar = true;
            return;
        }

        if (mHolderTopView != null) {
            // use long to avoid overflow
            setProgressBarPosition(time);
        }
        setTimeVideo(time);
    }

    private void setProgressBarPosition(long position) {
        if (mDuration > 0) {
            long pos = 1000L * position / mDuration;
            mHolderTopView.setProgress((int) pos);
        }
    }

    public boolean isOriginalFileOverSize() {
        long fileSizeInKB = (mOriginSizeFile / 1024);
        if (fileSizeInKB > 1000) {
            double fileSizeInMB = (float) fileSizeInKB / 1024f;
            if (fileSizeInMB > MAX_FILE_SIZE_BEFORE_DECODE) {
                return true;
            }
        }
        return false;
    }

    public void showControlButton(boolean show) {
        mControlWrapper.setVisibility(show ? VISIBLE : GONE);
    }

    public void setCompressItemVisibility(int visibility) {
        compressItem.setVisibility(visibility);
    }

    public void disableCompressItem() {
        compressItem.setOnClickListener(null);
    }

    public void setCompressItemAlpha(float alpha) {
        compressItem.setAlpha(alpha);
    }

    /**
     * Set video information visibility.
     * For now this is for debugging
     *
     * @param visible whether or not the videoInformation will be visible
     */
    public void setVideoInformationVisibility(boolean visible) {
        mTimeInfoContainer.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * Listener for events such as trimming operation success and cancel
     *
     * @param onTrimVideoListener interface for events
     */
    @SuppressWarnings("unused")
    public void setOnTrimVideoListener(OnTrimVideoListener onTrimVideoListener) {
        mOnTrimVideoListener = onTrimVideoListener;
    }

    /**
     * Listener for some {@link VideoView} events
     *
     * @param onK4LVideoListener interface for events
     */
    @SuppressWarnings("unused")
    public void setOnK4LVideoListener(OnK4LVideoListener onK4LVideoListener) {
        mOnK4LVideoListener = onK4LVideoListener;
    }

    /**
     * Sets the path where the trimmed video will be saved
     * Ex: /storage/emulated/0/MyAppFolder/
     *
     * @param finalPath the full path
     */
    @SuppressWarnings("unused")
    public void setDestinationPath(final String finalPath) {
        mFinalPath = finalPath;
        // Log.d(TAG, "Setting custom path " + mFinalPath);
    }

    /**
     * Cancel all current operations
     */
    public void destroy() {
        BackgroundExecutor.cancelAll("", true);
        UiThreadExecutor.cancelAll("");
        mOnTrimVideoListener = null;
        mOnK4LVideoListener = null;
        EventBus.getDefault().unregister(this);
    }

    /**
     * Set the maximum duration of the trimmed video.
     * The trimmer interface wont allow the user to set duration longer than maxDuration
     *
     * @param maxDuration the maximum duration of the trimmed video in seconds
     */
    @SuppressWarnings("unused")
    public void setMaxDuration(int maxDuration) {
        mMaxDuration = maxDuration * 1000;
    }

    /**
     * Sets the uri of the video to be trimmer
     *
     * @param videoURI Uri of the video
     */
    @SuppressWarnings("unused")
    public void setVideoURI(final Uri videoURI) {
        mSrc = videoURI;

        if (mOriginSizeFile == 0) {
            File file = new File(mSrc.getPath());

            mOriginSizeFile = file.length();
            long fileSizeInKB = mOriginSizeFile / 1024;

            if (fileSizeInKB > 1000) {
                long fileSizeInMB = fileSizeInKB / 1024;
                if (fileSizeInMB < MAX_FILE_SIZE) {
                    mTextSize.setText(String.format("~%s %s", new DecimalFormat("##.##").format(fileSizeInMB), getContext().getString(R.string.megabyte)));
                } else {
                    mTextSize.setText(String.format("%s%s", String.format("~%s %s! ", new DecimalFormat("##.##").format(fileSizeInMB), getContext().getString(R.string.megabyte)), getContext().getString(R.string.size_file_overflow)));
                }
                if (fileSizeInMB > MAX_FILE_SIZE_BEFORE_DECODE) {
                    Toast.makeText(mContext, R.string.size_file_overflow_original, Toast.LENGTH_SHORT).show();
                    if (mOnTrimVideoListener != null) {
                        mOnTrimVideoListener.cancelAction();
                    }
                    return;
                }
            } else {
                mTextSize.setText(String.format("%s %s", fileSizeInKB, getContext().getString(R.string.kilobyte)));
            }
        }

        mVideoView.setVideoURI(mSrc);
        mVideoView.requestFocus();

        mTimeLineView.setVideo(mSrc);

        setVideoTimelineView();
    }

    private static class MessageHandler extends Handler {

        @NonNull
        private final WeakReference<K4LVideoTrimmer> mView;

        MessageHandler(K4LVideoTrimmer view) {
            mView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            K4LVideoTrimmer view = mView.get();
            if (view == null || view.mVideoView == null) {
                return;
            }

            view.notifyProgressUpdate(true);
            if (view.mVideoView.isPlaying()) {
                sendEmptyMessageDelayed(0, 10);
            }
        }
    }

    private void setVideoTimelineView() {
        videoTimelineView.setVideoPath(mSrc.getPath());
        videoTimelineView.setDelegate(new VideoTimelineView.VideoTimelineViewDelegate() {
            @Override
            public void onLeftProgressChanged(float progress) {
                onSeekThumbs(Thumb.LEFT, progress * 100);
                setNewSize();
            }

            @Override
            public void onRifhtProgressChanged(float progress) {
                onSeekThumbs(Thumb.RIGHT, progress * 100);
                setNewSize();
            }

            @Override
            public void didStartDragging() {

            }

            @Override
            public void didStopDragging() {

            }
        });
    }
}