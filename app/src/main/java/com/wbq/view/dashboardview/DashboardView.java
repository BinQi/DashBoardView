package com.wbq.view.dashboardview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * DashboardView
 * @author jerrywu
 * @created 2019-09-18 20:36
 */

public class DashboardView extends View {

    private int mStartColor = -1;
    private int mRadius; // 画布边缘半径（去除padding后的半径）
    private int mStartAngle = 150; // 起始角度
    private int mSweepAngle = 240; // 绘制角度
    private int mMin = 0; // 最小值
    private int mMax = 100; // 最大值
    private int mSection = 10; // 值域（mMax-mMin）等分份数
    private int mPortion = 1; // 一个mSection等分份数
    private int mValue = 0; // 动画过程中的百分点值
    private int mSolidValue = mValue; // 百分点值(设定后不变)
    private int mThresholdValue = Integer.MAX_VALUE;
    private int mSparkleWidth; // 亮点宽度
    private int mProgressWidth; // 进度圆弧宽度
    private float mLength1; // 刻度顶部相对边缘的长度
    private int mCalibrationWidth; // 刻度圆弧宽度
    private float mLength2; // 刻度读数顶部相对边缘的长度

    private int mPRadius; // 指针半径

    private int mPadding;
    private float mCenterX, mCenterY; // 圆心坐标
    private Paint mPaint;
    private RectF mRectFProgressArc;
    private RectF mRectFCalibrationFArc;
    private RectF mRectFTextArc;
    private Path mPath;
    private Rect mRectText;
    private CharSequence[] mTexts;
    private int mBackgroundColor;
    private int[] mBgColors;

    private RectF mRectFPointerArc;

    private int mNumSize, mPercentSize;

    private int[] mPrimaryColors;

    /**
     * 先计算出最终到达角度，以扫过的角度为线性参考，动画就流畅了
     */
    private boolean isAnimFinish = true;
    private float mAngleWhenAnim;

    private AnimatorSet mAnimatorSet;

    public DashboardView(Context context) {
        this(context, null);
    }

    public DashboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DashboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr);
    }

    public void setThresholdValue(int thredholdValue) {
        this.mThresholdValue = thredholdValue;
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        final Resources resources = context.getResources();
        final Resources.Theme theme = context.getTheme();

        /*
         * Look the appearance up without checking first if it exists because
         * almost every TextView has one and it greatly simplifies the logic
         * to be able to parse the appearance first and then let specific tags
         * for this View override it.
         */
        TypedArray a = theme.obtainStyledAttributes(attrs,
                R.styleable.DashboardView, defStyleAttr, 0);

        mStartColor = a.getColor(R.styleable.DashboardView_startColor, Color.TRANSPARENT);
        mTexts = a.getTextArray(R.styleable.DashboardView_titles);
        mSection = a.getInt(R.styleable.DashboardView_section, 10);
        mPortion = a.getInt(R.styleable.DashboardView_portion, 1);

        int resId = a.getResourceId(R.styleable.DashboardView_bgColors, -1);
        if (resId != -1) {
            final TypedArray ta = resources.obtainTypedArray(resId);
            mBgColors = new int[ta.length()];
            for (int i = 0; i < ta.length(); i++) {
                mBgColors[i] = ta.getColor(i, 0);
            }
            ta.recycle();
        }

        resId = a.getResourceId(R.styleable.DashboardView_primaryColors, -1);
        if (resId != -1) {
            final TypedArray ta = resources.obtainTypedArray(resId);
            mPrimaryColors = new int[2];
            for (int i = 0; i < ta.length(); i++) {
                mPrimaryColors[i] = ta.getColor(i, 0);
            }
            ta.recycle();
        } else {
            mPrimaryColors = new int[] {
                    ContextCompat.getColor(getContext(), R.color.dbv_normal),
                    ContextCompat.getColor(getContext(), R.color.dbv_urge)
            };
        }

        a.recycle();

        mSparkleWidth = dp2px(5);
        mProgressWidth = dp2px(2);
        mCalibrationWidth = 2 * mProgressWidth;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStrokeCap(Paint.Cap.ROUND);

        mRectFProgressArc = new RectF();
        mRectFCalibrationFArc = new RectF();
        mRectFTextArc = new RectF();
        mPath = new Path();
        mRectText = new Rect();

        mBackgroundColor = Color.TRANSPARENT;

        mRectFPointerArc = new RectF();

        mNumSize = sp2px(24);
        mPercentSize = sp2px(14);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mPadding = Math.max(
                Math.max(getPaddingLeft(), getPaddingTop()),
                Math.max(getPaddingRight(), getPaddingBottom())
        );
        setPadding(mPadding, mPadding, mPadding, mPadding);

        mLength1 = mPadding + mSparkleWidth / 2f + dp2px(2);
        mLength2 = mLength1 + mCalibrationWidth + dp2px(1) + dp2px(5);

        int width = resolveSize(dp2px(220), widthMeasureSpec);
        mRadius = (width - mPadding * 2) / 2;

//        setMeasuredDimension(width, width - dp2px(30));
        setMeasuredDimension(width, width);

        mCenterX = mCenterY = getMeasuredWidth() / 2f;
        mRectFProgressArc.set(
                mPadding + mSparkleWidth / 2f,
                mPadding + mSparkleWidth / 2f,
                getMeasuredWidth() - mPadding - mSparkleWidth / 2f,
                getMeasuredWidth() - mPadding - mSparkleWidth / 2f
        );

        mRectFCalibrationFArc.set(
                mLength1 + mCalibrationWidth / 2f,
                mLength1 + mCalibrationWidth / 2f,
                getMeasuredWidth() - mLength1 - mCalibrationWidth / 2f,
                getMeasuredWidth() - mLength1 - mCalibrationWidth / 2f
        );

        mPaint.setTextSize(sp2px(10));
        mPaint.getTextBounds("0", 0, "0".length(), mRectText);
        mRectFTextArc.set(
                mLength2 + mRectText.height(),
                mLength2 + mRectText.height(),
                getMeasuredWidth() - mLength2 - mRectText.height(),
                getMeasuredWidth() - mLength2 - mRectText.height()
        );

        mPRadius = (int) (mRadius - (mPadding + mSparkleWidth / 2f));

        mRectFPointerArc.set(mCenterX - mPRadius, mCenterY - mPRadius, mCenterX + mPRadius, mCenterY + mPRadius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(mBackgroundColor);

        /**
         * 画进度圆弧背景
         */
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mProgressWidth);
        mPaint.setAlpha(80);
        canvas.drawArc(mRectFProgressArc, mStartAngle + 1, mSweepAngle - 2, false, mPaint);

        mPaint.setAlpha(255);

        /**
         * 画进度圆弧(起始到百分点值)
         */
        final float sweepAngle, pointAngle;
        if (isAnimFinish) {
            sweepAngle = calculateRelativeAngleWithValue(mValue);
            pointAngle = mStartAngle + calculateRelativeAngleWithValue(mValue);
        } else {
            sweepAngle = mAngleWhenAnim - mStartAngle;
            pointAngle = mAngleWhenAnim;
        }
        mPaint.setShader(generateSweepGradient());
        canvas.drawArc(mRectFProgressArc, mStartAngle + 1,
                sweepAngle - 2, false, mPaint);
        /**
         * 画百分点值指示亮点
         */
        float[] point = getCoordinatePoint(
                mRadius - mSparkleWidth / 2f,
                pointAngle
        );
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(null);
        canvas.drawCircle(point[0], point[1], mSparkleWidth / 2f, mPaint);

        /**
         * 画长刻度
         * 画好起始角度的一条刻度后通过canvas绕着原点旋转来画剩下的长刻度
         */
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(getPrimaryColor());
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(dp2px(1));
        mPaint.setAlpha(255);
        float x0 = mCenterX;
        float y0 = mPadding + mLength1 + dp2px(2);
        float x1 = mCenterX;
        float y1 = y0 + mCalibrationWidth;
        // 逆时针到开始处
        canvas.save();
        canvas.drawLine(x0, y0, x1, y1, mPaint);
        float degree = mSweepAngle / mSection;
        for (int i = 0; i < mSection / 2; i++) {
            canvas.rotate(-degree, mCenterX, mCenterY);
            canvas.drawLine(x0, y0, x1, y1, mPaint);
        }
        canvas.restore();
        // 顺时针到结尾处
        canvas.save();
        for (int i = 0; i < mSection / 2; i++) {
            canvas.rotate(degree, mCenterX, mCenterY);
            canvas.drawLine(x0, y0, x1, y1, mPaint);
        }
        canvas.restore();

        /**
         * 画短刻度
         * 同样采用canvas的旋转原理
         */
        mPaint.setStrokeWidth(dp2px(1));
        mPaint.setAlpha(80);
        float x2 = mCenterX;
        float y2 = y0 + mCalibrationWidth - dp2px(2);
        // 逆时针到开始处
        canvas.save();
        canvas.drawLine(x0, y0, x2, y2, mPaint);
        degree = mSweepAngle / (mSection * mPortion);
        for (int i = 0; i < (mSection * mPortion) / 2; i++) {
            canvas.rotate(-degree, mCenterX, mCenterY);
            canvas.drawLine(x0, y0, x2, y2, mPaint);
        }
        canvas.restore();
        // 顺时针到结尾处
        canvas.save();
        for (int i = 0; i < (mSection * mPortion) / 2; i++) {
            canvas.rotate(degree, mCenterX, mCenterY);
            canvas.drawLine(x0, y0, x2, y2, mPaint);
        }
        canvas.restore();

        if (mTexts != null && mTexts.length > 0) {
            /**
             * 画长刻度读数
             * 添加一个圆弧path，文字沿着path绘制
             */
            mPaint.setTextSize(sp2px(10));
            mPaint.setTextAlign(Paint.Align.LEFT);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setAlpha(160);
            final int end = Math.min(mTexts.length, mSection + 1);
            for (int i = 0; i < end; i++) {
                mPaint.getTextBounds(mTexts[i], 0, mTexts[i].length(), mRectText);
                // 粗略把文字的宽度视为圆心角2*θ对应的弧长，利用弧长公式得到θ，下面用于修正角度
                float θ = (float) (180 * mRectText.width() / 2 /
                        (Math.PI * (mRadius - mLength2 - mRectText.height())));

                mPath.reset();
                mPath.addArc(
                        mRectFTextArc,
                        mStartAngle + i * (mSweepAngle / mSection) - θ, // 正起始角度减去θ使文字居中对准长刻度
                        mSweepAngle
                );
                canvas.drawTextOnPath(mTexts[i].toString(), mPath, 0, 0, mPaint);
            }
        }

        /**
         * 画实时度数值
         */
        mPaint.setColor(getPrimaryColor());
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAlpha(255);
        mPaint.setTextSize(mNumSize);
        mPaint.setTextAlign(Paint.Align.CENTER);
        final String value = "" + mValue;
        canvas.drawText(value, mCenterX, getHeight() - 1, mPaint);
        final int valueSize = (int) (mPaint.measureText(value + "9") / 2);
        mPaint.setTextSize(mPercentSize);
        canvas.drawText("%", mCenterX + valueSize, getHeight(), mPaint);

        /**
         * 画扇形
         */
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAlpha(100);
        mPaint.setShader(generateSectorSweepGradient());
        canvas.drawArc(mRectFPointerArc, mStartAngle + 1, calculateRelativeAngleWithValue(mValue) - 1, true, mPaint);

        /**
         * 画指针
         */
        mPaint.setAlpha(255);
        mPaint.setStyle(Paint.Style.FILL);
        float θ = mStartAngle + mSweepAngle * (mValue - mMin) / (mMax - mMin); // 指针与水平线夹角
        int d = dp2px(1); // 指针由两个等腰三角形构成，d为共底边长的一半
        mPath.reset();
        float[] p1 = getCoordinatePoint(d, θ - 90);
        mPath.moveTo(p1[0], p1[1]);
        float[] p2 = getCoordinatePoint(mPRadius, θ);
        mPath.lineTo(p2[0], p2[1]);
        float[] p3 = getCoordinatePoint(d, θ + 90);
        mPath.lineTo(p3[0], p3[1]);
        mPath.close();
        mPaint.setShader(generatePGradient(p2[0], p2[1]));
        canvas.drawPath(mPath, mPaint);

        /**
         * 画指针围绕的圆心
         */
        mPaint.setColor(Color.WHITE);
        mPaint.setShader(generateRadialGradient(mCenterX, mCenterY));
        canvas.drawCircle(mCenterX, mCenterY, dp2px(5), mPaint);

    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                Resources.getSystem().getDisplayMetrics());
    }

    private int sp2px(int sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                Resources.getSystem().getDisplayMetrics());
    }

    private int getPrimaryColor() {
        if (mValue < mThresholdValue) {
            return mPrimaryColors[0];
        } else {
            return mPrimaryColors[1];
        }
    }

    private SweepGradient generateSweepGradient() {
        final int sr = Color.red(mStartColor);
        final int sg = Color.green(mStartColor);
        final int sb = Color.blue(mStartColor);

        final int color = getPrimaryColor();
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        SweepGradient sweepGradient = new SweepGradient(mCenterX, mCenterY,
                new int[]{Color.argb(1, sr, sg, sb), Color.argb(255, r, g, b)},
                new float[]{0, calculateRelativeAngleWithValue(mValue) / 360}
        );
        Matrix matrix = new Matrix();
        matrix.setRotate(mStartAngle - 1, mCenterX, mCenterY);
        sweepGradient.setLocalMatrix(matrix);

        return sweepGradient;
    }

    private SweepGradient generateSectorSweepGradient() {
        final int color = getPrimaryColor();
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        final float sweepAngle = calculateRelativeAngleWithValue(mValue);
        SweepGradient sweepGradient = new SweepGradient(mCenterX, mCenterY,
                new int[]{Color.argb(0, r, g, b), Color.argb(100, r, g, b), Color.argb(80, r, g, b)},
                new float[]{0, (4 * sweepAngle / 5) / 360, sweepAngle / 360}
        );
        Matrix matrix = new Matrix();
        matrix.setRotate(mStartAngle - 1, mCenterX, mCenterY);
        sweepGradient.setLocalMatrix(matrix);

        return sweepGradient;
    }

    private RadialGradient generateRadialGradient(float x, float y) {
        return new RadialGradient(x, y, mSparkleWidth / 2f,
                new int[]{Color.argb(255, 255, 255, 255), Color.argb(80, 255, 255, 255)},
                new float[]{0.4f, 1},
                Shader.TileMode.CLAMP
        );
    }

    private LinearGradient generatePGradient(float x, float y) {
        return new LinearGradient(mCenterX, mCenterY, x, y, Color.WHITE, Color.argb(100, 255, 255, 255), Shader.TileMode.CLAMP);
    }

    private float[] getCoordinatePoint(float radius, float angle) {
        float[] point = new float[2];

        double arcAngle = Math.toRadians(angle); //将角度转换为弧度
        if (angle < 90) {
            point[0] = (float) (mCenterX + Math.cos(arcAngle) * radius);
            point[1] = (float) (mCenterY + Math.sin(arcAngle) * radius);
        } else if (angle == 90) {
            point[0] = mCenterX;
            point[1] = mCenterY + radius;
        } else if (angle > 90 && angle < 180) {
            arcAngle = Math.PI * (180 - angle) / 180.0;
            point[0] = (float) (mCenterX - Math.cos(arcAngle) * radius);
            point[1] = (float) (mCenterY + Math.sin(arcAngle) * radius);
        } else if (angle == 180) {
            point[0] = mCenterX - radius;
            point[1] = mCenterY;
        } else if (angle > 180 && angle < 270) {
            arcAngle = Math.PI * (angle - 180) / 180.0;
            point[0] = (float) (mCenterX - Math.cos(arcAngle) * radius);
            point[1] = (float) (mCenterY - Math.sin(arcAngle) * radius);
        } else if (angle == 270) {
            point[0] = mCenterX;
            point[1] = mCenterY - radius;
        } else {
            arcAngle = Math.PI * (360 - angle) / 180.0;
            point[0] = (float) (mCenterX + Math.cos(arcAngle) * radius);
            point[1] = (float) (mCenterY - Math.sin(arcAngle) * radius);
        }

        return point;
    }

    /**
     * 相对起始角度计算百分点值所对应的角度大小
     */
    private float calculateRelativeAngleWithValue(int value) {
//        float degreePerSection = 1f * mSweepAngle / mSection;
        return 1f * value / (mMax - mMin) * mSweepAngle;
//        if (value > Level.l4) {
//            return 8 * degreePerSection + 2 * degreePerSection / 250 * (value - Level.l4);
//        } else if (value > Level.l3) {
//            return 6 * degreePerSection + 2 * degreePerSection / 50 * (value - Level.l3);
//        } else if (value > Level.l2) {
//            return 4 * degreePerSection + 2 * degreePerSection / 50 * (value - Level.l2);
//        } else if (value > Level.l1) {
//            return 2 * degreePerSection + 2 * degreePerSection / 50 * (value - Level.l1);
//        } else {
//            return 2 * degreePerSection / 200 * (value - mMin);
//        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @interface Level {
        int l4 = 80;
        int l3 = 60;
        int l2 = 40;
        int l1 = 20;
    }

    private SimpleDateFormat mDateFormat;

    private String getFormatTimeStr() {
        if (mDateFormat == null) {
            mDateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA);
        }
        return String.format("评估时间:%s", mDateFormat.format(new Date()));
    }

    public int getCreditValue() {
        return mValue;
    }

    /**
     * 设置百分点值
     *
     * @param value 百分点值
     */
    public void setPercentValue(int value) {
        if (mSolidValue == value || value < mMin || value > mMax) {
            return;
        }

        mSolidValue = value;
        mValue = value;
        postInvalidate();
    }

    public void setPercentWithAnim(float value) {
        setPercentValueWithAnim((int) (value * mMax + 0.5f));
    }

    /**
     * 设置百分点值并播放动画
     *
     * @param value 百分点值
     */
    public void setPercentValueWithAnim(int value) {
        if (value < mMin || value > mMax) {
            return;
        }

        if (mAnimatorSet != null) {
            mAnimatorSet.cancel();
            isAnimFinish = true;
        }

        mSolidValue = value;

        ValueAnimator valueAnimator = ValueAnimator.ofInt(mMin, mSolidValue);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mValue = (int) animation.getAnimatedValue();
                postInvalidate();
            }
        });

        // 计算最终值对应的角度，以扫过的角度的线性变化来播放动画
        float degree = calculateRelativeAngleWithValue(mSolidValue);

        ValueAnimator degreeValueAnimator = ValueAnimator.ofFloat(mStartAngle, mStartAngle + degree);
        degreeValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAngleWhenAnim = (float) animation.getAnimatedValue();
            }
        });

        long delay = 1000;
        if (mSolidValue > Level.l4) {
            delay = 3000;
        } else if (mSolidValue > Level.l3) {
            delay = 2500;
        } else if (mSolidValue > Level.l2) {
            delay = 2000;
        } else if (mSolidValue > Level.l1) {
            delay = 1500;
        }

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.setDuration(delay)
                .setInterpolator(new LinearInterpolator());
        if (mBgColors != null && mBgColors.length > 0) {
            // 实时百分点值对应的背景色的变化
            ObjectAnimator colorAnimator = ObjectAnimator.ofInt(this, "mBackgroundColor", mBgColors[0], mBgColors[0]);
            if (mSolidValue > Level.l4) {
                colorAnimator.setIntValues(mBgColors[0], mBgColors[1], mBgColors[2], mBgColors[3], mBgColors[4]);
            } else if (mSolidValue > Level.l3) {
                colorAnimator.setIntValues(mBgColors[0], mBgColors[1], mBgColors[2], mBgColors[3]);
            } else if (mSolidValue > Level.l2) {
                colorAnimator.setIntValues(mBgColors[0], mBgColors[1], mBgColors[2]);
            } else if (mSolidValue > Level.l1) {
                colorAnimator.setIntValues(mBgColors[0], mBgColors[1]);
            }
            colorAnimator.setEvaluator(new ArgbEvaluator());
            colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mBackgroundColor = (int) animation.getAnimatedValue();
                }
            });
            mAnimatorSet.playTogether(valueAnimator, degreeValueAnimator, colorAnimator);
        } else {
            mAnimatorSet.playTogether(valueAnimator, degreeValueAnimator);
        }
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                isAnimFinish = false;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isAnimFinish = true;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                isAnimFinish = true;
            }
        });
        mAnimatorSet.start();
    }

}
