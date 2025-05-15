package com.orbixtartechnologies.widgets.buttons

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.orbixtartechnologies.widgets.R
import kotlin.math.max
import kotlin.math.min
import androidx.core.content.withStyledAttributes

enum class AnimationType{
    SUCCESSFUL,
    FAILED
}

@SuppressLint("CustomViewStyleable")
@Suppress("unused")
class LoadingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object{
        private const val STATE_BUTTON = 0
        private const val STATE_ANIMATION_STEP1 = 1
        private const val STATE_ANIMATION_STEP2 = 2
        private const val STATE_ANIMATION_LOADING = 3
        private const val STATE_STOP_LOADING = 4
        private const val STATE_ANIMATION_SUCCESS = 5
        private const val STATE_ANIMATION_FAILED = 6

        private const val DEFAULT_WIDTH = 88
        private const val DEFAULT_HEIGHT = 56

        private const val DEFAULT_COLOR = Color.BLUE
        private const val DEFAULT_TEXT_COLOR = Color.WHITE
    }

    private val mDensity = resources.displayMetrics.density

    private val defaultMinHeight = 56 * mDensity

    var animationEndAction: ((AnimationType) -> Unit)? = null

    var rippleEnable = true
        set(value) {
            invalidate()
            field = value
        }

    var rippleColor = Color.BLACK
        set(value){
            mRipplePaint.color = value
            field = value
        }

    var textColor
        get() = mTextColor
        set(value) {
            mTextColor = value
            invalidate()
        }

    var typeface: Typeface
        get() = mTextPaint.typeface
        set(value) {
            mTextPaint.typeface = value
            invalidate()
        }

    var text
        get() = mText
        set(value) {
            if (text.isEmpty()) {
                return
            }
            this.mText = value
            mTextWidth = mTextPaint.measureText(mText)
            mTextHeight = measureTextHeight(mTextPaint)
            invalidate()
        }

    /**
     * set button text, dip
     */
    var textSize
        get() = (mTextPaint.textSize / mDensity).toInt()
        set(value) {
            mTextPaint.textSize = value * mDensity
            mTextWidth = mTextPaint.measureText(mText)
            invalidate()
        }

    var cornerRadius
        get() = mButtonCorner
        set(value) {
            mButtonCorner = value
            invalidate()
        }

    /** while loading data failed, reset view to normal state */
    var resetAfterFailed = true

    /**
     * set button background as shader paint
     */
    var backgroundShader: Shader?
        get() = mStrokePaint.shader
        set(value) {
            mPaint.shader = value
            mStrokePaint.shader = value
            mPathEffectPaint.shader = value
            mPathEffectPaint2.shader = value
            invalidate()
        }


    private var mCurrentState = STATE_BUTTON
    private var mMinHeight = defaultMinHeight

    private var mColorPrimary = DEFAULT_COLOR
    private var mDisabledBgColor = Color.LTGRAY
    private var mTextColor = Color.WHITE
    private var mDisabledTextColor = Color.DKGRAY
    private var mRippleAlpha = 0.2f

    private var mPadding = 7 * mDensity

    private val mPaint = Paint()
    private val mRipplePaint = Paint()
    private val mStrokePaint = Paint()
    private val mTextPaint = Paint()
    private val mPathEffectPaint = Paint()
    private val mPathEffectPaint2 = Paint()

    private var mScaleWidth = 0
    private var mScaleHeight = 0
    private var mDegree = 0
    private var mAngle = 0
    private var mEndAngle= 0

    private var mButtonCorner = 10 * mDensity
    private var mRadius = 0
    private var mTextWidth = 0f
    private var mTextHeight = 0f

    private val mMatrix = Matrix()

    private var mPath = Path()
    private var mSuccessPath: Path? = null
    private var mSuccessPathLength = 0f
    private var mSuccessPathIntervals: FloatArray? = null

    private var mFailedPath: Path? = null
    private var mFailedPath2: Path? = null
    private var mFailedPathLength = 0f
    private var mFailedPathIntervals: FloatArray? = null

    private var mTouchX = 0f
    private var mTouchY = 0f
    private var mRippleRadius = 0f

    private val mButtonRectF = RectF()
    private val mArcRectF = RectF()

    private var mText: String = "Loading"
    private var mLoadingAnimatorSet: AnimatorSet? = null

    init {
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.LoadingButton, 0, 0)
            mColorPrimary = ta.getInt(R.styleable.LoadingButton_backgroundColor, Color.BLUE)
            mDisabledBgColor = ta.getColor(R.styleable.LoadingButton_disabledColor, Color.LTGRAY)
            mDisabledTextColor = ta.getColor(R.styleable.LoadingButton_disabledTextColor, Color.DKGRAY)
            val text = ta.getString(R.styleable.LoadingButton_text)
            mText = text ?: "Loading"
            mTextColor = ta.getColor(R.styleable.LoadingButton_textColor, Color.WHITE)
            resetAfterFailed = ta.getBoolean(R.styleable.LoadingButton_resetAfterFailed, true)
            rippleColor = ta.getColor(R.styleable.LoadingButton_rippleColor, Color.BLACK)
            rippleEnable = ta.getBoolean(R.styleable.LoadingButton_rippleEnable, true)
            mRippleAlpha = ta.getFloat(R.styleable.LoadingButton_rippleAlpha, 0.3f)
            mButtonCorner = ta.getDimension(R.styleable.LoadingButton_cornerRadius, 10 * mDensity)
            mMinHeight = ta.getDimension(R.styleable.LoadingButton_minHeight, defaultMinHeight)
            ta.recycle()
        }

        mPaint.apply {
            setLayerType(LAYER_TYPE_SOFTWARE, this)
            isAntiAlias = true
            color = mColorPrimary
            style = Paint.Style.FILL
            setShadowDepth(5 * mDensity)
        }

        mRipplePaint.apply {
            isAntiAlias = true
            color = rippleColor
            alpha = (mRippleAlpha * 255).toInt()
            style = Paint.Style.FILL
        }

        mStrokePaint.apply {
            isAntiAlias = true
            color = mColorPrimary
            style = Paint.Style.STROKE
            strokeWidth = 2 * mDensity
        }

        mTextPaint.apply {
            isAntiAlias = true
            color = mTextColor
            textSize = 18 * mDensity
            isFakeBoldText = true
        }

        mTextWidth = mTextPaint.measureText(mText)
        mTextHeight = measureTextHeight(mTextPaint)

        mPathEffectPaint.apply {
            isAntiAlias = true
            color = mColorPrimary
            style = Paint.Style.STROKE
            strokeWidth = 2 * mDensity
        }

        mPathEffectPaint2.apply {
            isAntiAlias = true
            color = mColorPrimary
            style = Paint.Style.STROKE
            strokeWidth = 2 * mDensity
        }

        setLayerType(LAYER_TYPE_SOFTWARE, mPaint)
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(
            measureDimension((DEFAULT_WIDTH * mDensity).toInt(), widthMeasureSpec),
            measureDimension((DEFAULT_HEIGHT * mDensity).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val viewHeight = h
        mRadius = (viewHeight - mPadding * 2).toInt() / 2
        mButtonRectF.top = 0f
        mButtonRectF.bottom = viewHeight.toFloat()
        mArcRectF.left = (width / 2 - mRadius).toFloat()
        mArcRectF.top = mPadding
        mArcRectF.right = (width / 2 + mRadius).toFloat()
        mArcRectF.bottom = viewHeight - mPadding
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (mCurrentState == STATE_BUTTON) {
            updateButtonColor()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTouchX = event.x
                mTouchY = event.y
                playTouchDownAnimation()
            }
            MotionEvent.ACTION_UP -> if (event.x > mButtonRectF.left && event.x < mButtonRectF.right && event.y > mButtonRectF.top && event.y < mButtonRectF.bottom) {
                // only register as click if finger is up inside view
                playRippleAnimation()
            } else {
                // if finger is moved outside view and lifted up, reset view
                mTouchX = 0f
                mTouchY = 0f
                mRippleRadius = 0f
                mRipplePaint.alpha = (mRippleAlpha * 255).toInt()
                mPaint.setShadowDepth(2 * mDensity)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val viewHeight = 60 * mDensity
        when (mCurrentState) {
            STATE_BUTTON, STATE_ANIMATION_STEP1 -> {
                val cornerRadius = (mRadius - mButtonCorner) * (mScaleWidth / (width / 2 - viewHeight / 2).toFloat()) + mButtonCorner
                mButtonRectF.left = mScaleWidth.toFloat()
                mButtonRectF.right = (width - mScaleWidth).toFloat()
                canvas.drawRoundRect(mButtonRectF, (10.0 * mDensity).toFloat(), (10.0 * mDensity).toFloat(), mPaint)

                if (mCurrentState == STATE_BUTTON) {
                    canvas.drawText(mText, (width - mTextWidth) / 2, (viewHeight - mTextHeight) / 2 + mPadding * 2, mTextPaint)
                    if ((mTouchX > 0 || mTouchY > 0) && rippleEnable) {
                        canvas.clipRect(0f, mPadding, width.toFloat(), viewHeight - mPadding)
                        canvas.drawCircle(mTouchX, mTouchY, mRippleRadius, mRipplePaint)
                    }
                }
            }
            STATE_ANIMATION_STEP2 -> {
                canvas.drawCircle((width / 2).toFloat(), (viewHeight / 2).toFloat(), (mRadius - mScaleHeight).toFloat(), mPaint)
                canvas.drawCircle((width / 2).toFloat(), (viewHeight / 2).toFloat(), mRadius - mDensity, mStrokePaint)
            }
            STATE_ANIMATION_LOADING -> {
                mPath.reset()
                mPath.addArc(mArcRectF, (270 + mAngle / 2).toFloat(), (360 - mAngle).toFloat())
                if (mAngle != 0) {
                    mMatrix.setRotate(mDegree.toFloat(), (width / 2).toFloat(), (viewHeight / 2).toFloat())
                    mPath.transform(mMatrix)
                    mDegree += 10
                }
                canvas.drawPath(mPath, mStrokePaint)
            }
            STATE_STOP_LOADING -> {
                mPath.reset()
                mPath.addArc(mArcRectF, (270 + mAngle / 2).toFloat(), mEndAngle.toFloat())
                if (mEndAngle != 360) {
                    mMatrix.setRotate(mDegree.toFloat(), (width / 2).toFloat(), (viewHeight / 2).toFloat())
                    mPath.transform(mMatrix)
                    mDegree += 10
                }
                canvas.drawPath(mPath, mStrokePaint)
            }
            STATE_ANIMATION_SUCCESS -> {
                canvas.drawPath(mSuccessPath!!, mPathEffectPaint)
                canvas.drawCircle((width / 2).toFloat(), (viewHeight / 2).toFloat(), mRadius - mDensity, mStrokePaint)
            }
            STATE_ANIMATION_FAILED -> {
                canvas.drawPath(mFailedPath!!, mPathEffectPaint)
                canvas.drawPath(mFailedPath2!!, mPathEffectPaint2)
                canvas.drawCircle((width / 2).toFloat(), (viewHeight / 2).toFloat(), mRadius - mDensity, mStrokePaint)
            }
        }
    }

    /**
     * start loading,play animation
     */
    fun startLoading() {
        if (mCurrentState == STATE_ANIMATION_FAILED && !resetAfterFailed) {
            scaleFailedPath()
            return
        }

        if (mCurrentState == STATE_BUTTON) {
            mCurrentState = STATE_ANIMATION_STEP1
            mPaint.clearShadowLayer()
            playStartAnimation(false)
        }
    }

    /**
     * loading data successful
     */
    fun loadingSuccessful() {
        if (mLoadingAnimatorSet != null && mLoadingAnimatorSet!!.isStarted) {
            mLoadingAnimatorSet!!.end()
            mCurrentState = STATE_STOP_LOADING
            playSuccessAnimation()
        }
    }

    /**
     * loading data failed
     */
    fun loadingFailed() {
        if (mLoadingAnimatorSet != null && mLoadingAnimatorSet!!.isStarted) {
            mLoadingAnimatorSet!!.end()
            mCurrentState = STATE_STOP_LOADING
            playFailedAnimation()
        }
    }

    fun cancelLoading() {
        if (mCurrentState != STATE_ANIMATION_LOADING) {
            return
        }
        cancel()
    }

    /**
     * reset view to Button with animation
     */
    fun reset(){
        when(mCurrentState){
            STATE_ANIMATION_SUCCESS -> scaleSuccessPath()
            STATE_ANIMATION_FAILED -> scaleFailedPath()
        }
    }


    private fun measureTextHeight(paint: Paint): Float{
        val bounds = Rect()
        paint.getTextBounds(mText, 0, mText.length, bounds)
        return bounds.height().toFloat()
    }

    private fun createSuccessPath() {

        if (mSuccessPath != null) {
            mSuccessPath!!.reset()
        } else {
            mSuccessPath = Path()
        }

        val mLineWith = 2 * mDensity

        val left = (width / 2 - mRadius).toFloat() + (mRadius / 3).toFloat() + mLineWith
        val top = mPadding + (mRadius / 2).toFloat() + mLineWith
        val right = (width / 2 + mRadius).toFloat() - mLineWith - (mRadius / 3).toFloat()
        val bottom = (mLineWith + mRadius) * 1.5f + mPadding / 2
        val xPoint = (width / 2 - mRadius / 6).toFloat()

        mSuccessPath = Path().apply {
            moveTo(left, mPadding + mRadius.toFloat() + mLineWith)
            lineTo(xPoint, bottom)
            lineTo(right, top)
        }

        mSuccessPathLength = PathMeasure(mSuccessPath, false).length
        mSuccessPathIntervals = floatArrayOf(mSuccessPathLength, mSuccessPathLength)
    }

    private fun createFailedPath() {

        if (mFailedPath != null) {
            mFailedPath!!.reset()
            mFailedPath2!!.reset()
        } else {
            mFailedPath = Path()
            mFailedPath2 = Path()
        }

        val left = (width / 2 - mRadius + mRadius / 2).toFloat()
        val top = mRadius / 2 + mPadding

        mFailedPath!!.moveTo(left, top)
        mFailedPath!!.lineTo(left + mRadius, top + mRadius)

        mFailedPath2!!.moveTo((width / 2 + mRadius / 2).toFloat(), top)
        mFailedPath2!!.lineTo((width / 2 - mRadius + mRadius / 2).toFloat(), top + mRadius)

        mFailedPathLength = PathMeasure(mFailedPath, false).length
        mFailedPathIntervals = floatArrayOf(mFailedPathLength, mFailedPathLength)

        mPathEffectPaint2.pathEffect = DashPathEffect(mFailedPathIntervals, mFailedPathLength)
    }

    private fun measureDimension(defaultSize: Int, measureSpec: Int) =
        when (MeasureSpec.getMode(measureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(measureSpec)
            MeasureSpec.AT_MOST -> min(defaultSize, MeasureSpec.getSize(measureSpec))
            MeasureSpec.UNSPECIFIED -> defaultSize
            else -> defaultSize
        }

    private fun updateButtonColor() {
        mPaint.color = if(isEnabled) mColorPrimary else mDisabledBgColor
        mTextPaint.color = if(isEnabled) mTextColor else mDisabledTextColor
        if(backgroundShader != null){
            if(isEnabled) mPaint.shader = backgroundShader else mPaint.shader = null
        }
        invalidate()
    }

    private fun playTouchDownAnimation(){
        ValueAnimator.ofFloat(0f, 1f)
            .apply {
                duration = 240
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val progress = valueAnimator.animatedValue as Float
                    mPaint.setShadowDepth((2 + 4 * progress) * mDensity)
                    mRippleRadius = width *  progress
                    //mRipplePaint.alpha = (255 * mRippleAlpha * (1 - progress)).toInt()
                    invalidate()
                }
            }
            .start()
    }


    private fun playRippleAnimation() {
        ValueAnimator.ofFloat(
            1f,
            0f)
            .apply {
                duration = 240
                interpolator = DecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val progress = valueAnimator.animatedValue as Float
                    mRipplePaint.alpha = (255 * mRippleAlpha * progress).toInt()
                    mPaint.setShadowDepth((2 + 4 * progress) * mDensity)
                    invalidate()
                }
                doOnEnd {
                    doClick()
                }
            }.start()
    }

    private fun doClick(){
        mTouchX = 0f
        mTouchY = 0f
        mRipplePaint.alpha = (mRippleAlpha * 255).toInt()
        mRippleRadius = 0f
        invalidate()
        performClick()
    }

    private fun playStartAnimation(isReverse: Boolean) {
        val viewHeight = max(height, mMinHeight.toInt())
        val animator = ValueAnimator.ofInt(
            if (isReverse) width / 2 - viewHeight / 2 else 0,
            if (isReverse) 0 else width / 2 - viewHeight / 2)
            .apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = 100
                addUpdateListener { valueAnimator ->
                    mScaleWidth = valueAnimator.animatedValue as Int
                    invalidate()
                }
                doOnEnd {
                    mCurrentState = if (isReverse) STATE_BUTTON else STATE_ANIMATION_STEP2
                    if (mCurrentState == STATE_BUTTON) {
                        mPaint.setShadowDepth(4 * mDensity)
                        invalidate()
                    }
                }
            }

        val animator2 = ValueAnimator.ofInt(if (isReverse) mRadius else 0, if (isReverse) 0 else mRadius)
            .apply {
                duration = 240
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    mScaleHeight = valueAnimator.animatedValue as Int
                    invalidate()
                }
                doOnEnd {
                    mCurrentState = if (isReverse) STATE_ANIMATION_STEP1 else STATE_ANIMATION_LOADING
                    if (!isReverse) updateButtonColor()
                }
            }

        val loadingAnimator = ValueAnimator.ofInt(30, 300)
            .apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    mAngle = valueAnimator.animatedValue as Int
                    invalidate()
                }
            }

        mLoadingAnimatorSet?.cancel()
        mLoadingAnimatorSet = AnimatorSet()
        mLoadingAnimatorSet!!.doOnEnd {
            isEnabled = true
            updateButtonColor()
        }
        if (isReverse) {
            mLoadingAnimatorSet!!.playSequentially(animator2, animator)
            mLoadingAnimatorSet!!.start()
            return
        }
        mLoadingAnimatorSet!!.playSequentially(animator, animator2, loadingAnimator)
        mLoadingAnimatorSet!!.start()
    }

    private fun playSuccessAnimation() {
        createSuccessPath()
        val animator = ValueAnimator.ofInt(360 - mAngle, 360)
            .apply {
                duration = 240
                interpolator = DecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    mEndAngle = valueAnimator.animatedValue as Int
                    invalidate()
                }
                doOnEnd { mCurrentState = STATE_ANIMATION_SUCCESS }
            }

        val successAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)
            .apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Float
                    val pathEffect = DashPathEffect(mSuccessPathIntervals, mSuccessPathLength - mSuccessPathLength * value)
                    mPathEffectPaint.pathEffect = pathEffect
                    invalidate()
                }
            }

        AnimatorSet().apply {
            playSequentially(animator, successAnimator)
            doOnEnd {
                animationEndAction?.invoke(AnimationType.SUCCESSFUL)
            }
        }.start()
    }

    private fun playFailedAnimation() {
        createFailedPath()
        val animator = ValueAnimator.ofInt(360 - mAngle, 360)
            .apply {
                duration = 240
                interpolator = DecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    mEndAngle = valueAnimator.animatedValue as Int
                    invalidate()
                }
                doOnEnd { mCurrentState = STATE_ANIMATION_FAILED }
            }

        val failedAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)
            .apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Float
                    mPathEffectPaint.pathEffect = DashPathEffect(mFailedPathIntervals, mFailedPathLength - mFailedPathLength * value)
                    invalidate()
                }
            }

        val failedAnimator2 = ValueAnimator.ofFloat(0.0f, 1.0f)
            .apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Float
                    mPathEffectPaint2.pathEffect = DashPathEffect(mFailedPathIntervals, mFailedPathLength - mFailedPathLength * value)
                    invalidate()
                }
            }

        AnimatorSet().apply {
            playSequentially(animator, failedAnimator, failedAnimator2)
            doOnEnd {
                if (resetAfterFailed) {
                    postDelayed({ scaleFailedPath() }, 1000)
                }else{
                    animationEndAction?.invoke(AnimationType.FAILED)
                }
            }
        }.start()
    }

    private fun cancel() {
        mCurrentState = STATE_STOP_LOADING
        ValueAnimator.ofInt(360 - mAngle, 360)
            .apply {
                duration = 240
                interpolator = DecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    mEndAngle = valueAnimator.animatedValue as Int
                    invalidate()
                }
                doOnEnd {
                    mCurrentState = STATE_ANIMATION_STEP2
                    playStartAnimation(true)
                }
            }.start()
    }

    private fun scaleSuccessPath() {
        val scaleMatrix = Matrix()
        val viewHeight = max(height, mMinHeight.toInt())
        ValueAnimator.ofFloat(1.0f, 0.0f)
            .apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Float
                    scaleMatrix.setScale(value, value, (width / 2).toFloat(), (viewHeight / 2).toFloat())
                    mSuccessPath!!.transform(scaleMatrix)
                    invalidate()
                }
                doOnEnd {
                    mCurrentState = STATE_ANIMATION_STEP2
                    playStartAnimation(true)
                }
            }.start()
    }

    private fun scaleFailedPath() {
        val scaleMatrix = Matrix()
        val viewHeight = max(height, mMinHeight.toInt())
        ValueAnimator.ofFloat(1.0f, 0.0f)
            .apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Float
                    scaleMatrix.setScale(value, value, (width / 2).toFloat(), (viewHeight / 2).toFloat())
                    mFailedPath!!.transform(scaleMatrix)
                    mFailedPath2!!.transform(scaleMatrix)
                    invalidate()
                }
                doOnEnd {
                    mCurrentState = STATE_ANIMATION_STEP2
                    playStartAnimation(true)
                }
            }.start()
    }

    public fun Animator.doOnEnd(action: (animator: Animator?) -> Unit) {
        this.addListener(object : Animator.AnimatorListener{
            override fun onAnimationStart(p0: Animator) {}

            override fun onAnimationEnd(p0: Animator) = action(p0)

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
        })
    }

    private fun Paint.setShadowDepth(depth: Float){
        this.setShadowLayer(depth, 0f, 10f, 0x00687B)
    }
}