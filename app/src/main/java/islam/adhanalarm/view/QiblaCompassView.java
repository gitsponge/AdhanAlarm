package islam.adhanalarm.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import islam.adhanalarm.R;

/**
 * A compass dial that turns with the phone, with the Qibla marked on it. It's drawn on a 300 by 300 grid
 * and scaled to the view, with the letters and the Kaaba kept upright as the dial turns.
 */
public class QiblaCompassView extends View {

	private static final float GRID = 300f, CENTRE = 150f;

	private float mHeading;
	private float mQibla;
	private boolean mAligned;

	private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path mTicks = new Path();
	private final Path mMajorTicks = new Path();
	private final Path mPointer = new Path();
	private final RectF mRect = new RectF();
	private final String[] mLetters;

	private int mDial, mRing, mTick, mMajorTick, mInk, mInkVariant, mQiblaColour, mOnQiblaColour;

	public QiblaCompassView(Context context) {
		this(context, null);
	}

	public QiblaCompassView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public QiblaCompassView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mLetters = new String[]{context.getString(R.string.compass_n), context.getString(R.string.compass_e), context.getString(R.string.compass_s), context.getString(R.string.compass_w)};
		mStroke.setStyle(Paint.Style.STROKE);
		mStroke.setStrokeCap(Paint.Cap.ROUND);
		mText.setTextAlign(Paint.Align.CENTER);
		for (int degrees = 0; degrees < 360; degrees += 5) {
			addTick(degrees % 30 == 0 ? mMajorTicks : mTicks, degrees, degrees % 30 == 0 ? 118 : 125);
		}
		mPointer.moveTo(141, 2);
		mPointer.lineTo(159, 2);
		mPointer.lineTo(150, 16);
		mPointer.close();
		loadColours();
	}

	private void addTick(Path path, int degrees, float inner) {
		double angle = Math.toRadians(degrees);
		path.moveTo(CENTRE + 132 * (float) Math.sin(angle), CENTRE - 132 * (float) Math.cos(angle));
		path.lineTo(CENTRE + inner * (float) Math.sin(angle), CENTRE - inner * (float) Math.cos(angle));
	}

	private void loadColours() {
		mDial = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer);
		mRing = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
		mTick = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
		mMajorTick = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
		mInk = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
		mInkVariant = mMajorTick;
		mQiblaColour = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary);
		mOnQiblaColour = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnTertiary);
	}

	/**
	 * @param heading degrees from true north that the top of the phone points
	 * @param qibla the Qibla's bearing from true north
	 * @param aligned whether the phone points at the Qibla
	 */
	public void setDirections(float heading, float qibla, boolean aligned) {
		mHeading = heading;
		mQibla = qibla;
		mAligned = aligned;
		invalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int fallback = (int) (300 * getResources().getDisplayMetrics().density);
		int width = resolveSize(fallback, widthMeasureSpec);
		int height = resolveSize(fallback, heightMeasureSpec);
		int size = Math.min(width, height);
		setMeasuredDimension(size, size);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float scale = Math.min(getWidth(), getHeight()) / GRID;
		canvas.save();
		canvas.translate((getWidth() - GRID * scale) / 2, (getHeight() - GRID * scale) / 2);
		canvas.scale(scale, scale);

		mFill.setColor(mDial);
		canvas.drawCircle(CENTRE, CENTRE, 146, mFill);

		canvas.save();
		canvas.rotate(-mHeading, CENTRE, CENTRE);
		mStroke.setColor(mRing);
		mStroke.setStrokeWidth(1.5f);
		canvas.drawCircle(CENTRE, CENTRE, 132, mStroke);
		mStroke.setColor(mTick);
		canvas.drawPath(mTicks, mStroke);
		mStroke.setColor(mMajorTick);
		mStroke.setStrokeWidth(2.5f);
		canvas.drawPath(mMajorTicks, mStroke);

		for (int i = 0; i < 4; i++) {
			double angle = Math.toRadians(i * 90);
			float x = CENTRE + 100 * (float) Math.sin(angle);
			float y = CENTRE - 100 * (float) Math.cos(angle);
			mText.setColor(i == 0 ? mInk : mInkVariant);
			mText.setTextSize(i == 0 ? 18 : 16);
			mText.setTypeface(i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
			canvas.save();
			canvas.rotate(mHeading, x, y);
			canvas.drawText(mLetters[i], x, y - (mText.ascent() + mText.descent()) / 2, mText);
			canvas.restore();
		}

		double qibla = Math.toRadians(mQibla);
		float sin = (float) Math.sin(qibla), cos = (float) Math.cos(qibla);
		mStroke.setColor(mQiblaColour);
		mStroke.setStrokeWidth(4);
		canvas.drawLine(CENTRE, CENTRE, CENTRE + 96 * sin, CENTRE - 96 * cos, mStroke);
		float markerX = CENTRE + 114 * sin, markerY = CENTRE - 114 * cos;
		mFill.setColor(mQiblaColour);
		canvas.drawCircle(markerX, markerY, 17, mFill);
		canvas.save();
		canvas.rotate(mHeading, markerX, markerY);
		mFill.setColor(mOnQiblaColour);
		mRect.set(markerX - 7, markerY - 7, markerX + 7, markerY + 7);
		canvas.drawRoundRect(mRect, 1.5f, 1.5f, mFill); // The Kaaba
		mFill.setColor(mQiblaColour);
		mRect.set(markerX - 7, markerY - 3.5f, markerX + 7, markerY - 1);
		canvas.drawRect(mRect, mFill); // Its gold band
		canvas.restore();
		canvas.restore();

		mFill.setColor(mAligned ? mQiblaColour : mInkVariant);
		canvas.drawPath(mPointer, mFill);
		mFill.setColor(mInk);
		canvas.drawCircle(CENTRE, CENTRE, 7, mFill);
		canvas.restore();
	}
}
