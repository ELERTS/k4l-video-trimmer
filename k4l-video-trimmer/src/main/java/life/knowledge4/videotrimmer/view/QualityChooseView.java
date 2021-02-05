package life.knowledge4.videotrimmer.view;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import life.knowledge4.videotrimmer.interfaces.OnQualityChooseListener;
import life.knowledge4.videotrimmer.utils.AndroidUtilities;

public class QualityChooseView extends View {
    private Paint paint;
    private TextPaint textPaint;

    private int circleSize;
    private int gapSize;
    private int sideSide;
    private int lineSize;

    private boolean moving;
    private boolean startMoving;
    private float startX;

    private int startMovingQuality;
    private int compressionsCount;
    private int selectedCompression;
    private String originalHeight;
    private OnQualityChooseListener onQualityChooseListener;

    public QualityChooseView(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init();
    }

    public QualityChooseView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public QualityChooseView(Context context) {
        super(context);
        init();
    }

    public void setOriginalCompression(int compressionsCount) {
        this.compressionsCount = compressionsCount;
        this.selectedCompression = compressionsCount;

        setOriginalHeight();
    }

    public void setOnQualityChooseListener(OnQualityChooseListener onQualityChooseListener) {
        this.onQualityChooseListener = onQualityChooseListener;
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setColor(0xffcdcdcd);
        this.compressionsCount = 2;
        this.selectedCompression = 2;
    }

    private void setOriginalHeight() {
        if (compressionsCount == 1) {
            originalHeight = "480";
        } else if (compressionsCount == 2) {
            originalHeight = "720";
        } else if (compressionsCount == 3) {
            originalHeight = "1080";
        }
    }

    private void setSelectedCompression(int selectedCompression) {
        if (onQualityChooseListener == null) return;
        onQualityChooseListener.selectedResolution(selectedCompression + 1);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            for (int a = 0; a < compressionsCount; a++) {
                int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                    startMoving = a == selectedCompression;
                    startX = x;
                    startMovingQuality = selectedCompression;
                    break;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (startMoving) {
                if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.5f, true)) {
                    moving = true;
                    startMoving = false;
                }
            } else if (moving) {
                for (int a = 0; a < compressionsCount; a++) {
                    int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                    int diff = lineSize / 2 + circleSize / 2 + gapSize;
                    if (x > cx - diff && x < cx + diff) {
                        if (selectedCompression != a) {
                            selectedCompression = a;
                            setSelectedCompression(selectedCompression);
                            //  didChangedCompressionLevel(false);
                            invalidate();
                        }
                        break;
                    }
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (!moving) {
                for (int a = 0; a < compressionsCount; a++) {
                    int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                    if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                        if (selectedCompression != a) {
                            selectedCompression = a;
                            setSelectedCompression(selectedCompression);
                            // didChangedCompressionLevel(true);
                            invalidate();
                        }
                        break;
                    }
                }
            } else {
                if (selectedCompression != startMovingQuality) {
                    // requestVideoPreview(1);
                }
            }
            startMoving = false;
            moving = false;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        circleSize = AndroidUtilities.dp(12);
        gapSize = AndroidUtilities.dp(2);
        sideSide = AndroidUtilities.dp(18);
        if (compressionsCount > 1) {
            lineSize = (getMeasuredWidth() - circleSize * compressionsCount - gapSize * 8 - sideSide * 2) / (compressionsCount - 1);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(6);
        for (int a = 0; a < compressionsCount; a++) {
            int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
            if (a <= selectedCompression) {
                paint.setColor(0xff53aeef);
            } else {
                paint.setColor(0xff222222);
            }
            String text;
            if (a == compressionsCount - 1) {
                text = originalHeight + "p";
            } else if (a == 0) {
                text = "480p";
            } else {
                text = "720p";
            }

            float width = textPaint.measureText(text);
            canvas.drawCircle(cx, cy, a == selectedCompression ? AndroidUtilities.dp(8) : circleSize / 2, paint);
            canvas.drawText(text, cx - width / 2, cy - AndroidUtilities.dp(16), textPaint);
            if (a != 0) {
                int x = cx - circleSize / 2 - gapSize - lineSize;
                canvas.drawRect(x, cy - AndroidUtilities.dp(1), x + lineSize, cy + AndroidUtilities.dp(2), paint);
            }
        }
    }
}
