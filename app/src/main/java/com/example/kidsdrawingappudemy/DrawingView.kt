package com.example.kidsdrawingappudemy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attributes: AttributeSet) : View(context, attributes) {
    private var mDrawPath : CustomPath? = null
    private var mCanvasBitmap: Bitmap? = null //bitmap is a rectangular grid of pixels
    private var mDrawPaint: Paint? = null
    private var mCanvasPaint: Paint? = null
    private var mBrushSize: Float = 0.toFloat()
    private var color: Int = Color.BLACK
    private var canvas: Canvas? = null
    private val mPaths = ArrayList<CustomPath>()
    private val mUndoPaths = ArrayList<CustomPath>()

    init{
        setUpDrawing()
    }

    //for us to paint we need a painting path, and a paint
    private fun setUpDrawing(){
        mDrawPath = CustomPath(color, mBrushSize) //path of drawing
        mDrawPaint = Paint() //setting up the paint brush
        mDrawPaint!!.color = color
        mDrawPaint!!.style = Paint.Style.STROKE
        mDrawPaint!!.strokeJoin = Paint.Join.ROUND
        mDrawPaint!!.strokeCap = Paint.Cap.ROUND
        mCanvasPaint = Paint(Paint.DITHER_FLAG)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) //the bitmap is created with width and height and 8 bit pixel configuration
        canvas = Canvas(mCanvasBitmap!!) //the canvas is made from the bitmap, you can draw on it
    }


    /*The mCanvasPaint variable is not directly used when drawing the path,
    but it is used when drawing the bitmap onto the canvas in the onDraw() method.
    Specifically, it is used to set the filter property of the Bitmap object that is being drawn onto the canvas.
    This can be useful for improving the quality of the scaled bitmap image,
    by using a filtering algorithm to smooth out the pixels and reduce pixelation.*/
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(mCanvasBitmap!!, 0f, 0f, mCanvasPaint) //firstly the bitmap is drawn on the canvas

        //onDraw always draws from scratch and it doesn't keep 'memory' of old drawn paths, so we have to draw the paths first
        for (path in mPaths){
            mDrawPaint!!.strokeWidth = path.brushThickness
            mDrawPaint!!.color = path.color
            canvas.drawPath(path, mDrawPaint!!)
        }

        //then we check if the user is currently drawing
        if(!mDrawPath!!.isEmpty) { //we check if it's empty, but in onTouchEvent we fill it so it won't be empty, but just to be sure
            mDrawPaint!!.strokeWidth = mDrawPath!!.brushThickness
            mDrawPaint!!.color = mDrawPath!!.color
            canvas.drawPath(mDrawPath!!, mDrawPaint!!)
        }
    }

    //takes the size of the screen into consideration
    fun setBrushSize(size: Float){
        mBrushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        size, resources.displayMetrics)
        mDrawPaint!!.strokeWidth = mBrushSize
    }

    fun setColor(hexString: String){
        color = Color.parseColor(hexString)
        mDrawPaint!!.color = color
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y

        when(event?.action){
            MotionEvent.ACTION_DOWN -> {
                //set the colors and brushSize
                mDrawPath!!.color = color
                mDrawPath!!.brushThickness = mBrushSize

                //move the path to a specific point
                mDrawPath!!.reset()
                mDrawPath!!.moveTo(touchX!!, touchY!!)
            }
            MotionEvent.ACTION_MOVE -> {
                //drawing line to specific point, move
                mDrawPath!!.lineTo(touchX!!, touchY!!)
            }
            //create new path, removing the drawing on action up
            MotionEvent.ACTION_UP -> {
                mPaths.add(mDrawPath!!)

                mDrawPath = CustomPath(color, mBrushSize)
            }
            else -> return false
        }

        //important for rendering the screen, onDraw will be called at some point in the future
        invalidate()
        return true
    }

    fun undoPath(){
        if(mPaths.size > 0){
            mUndoPaths.add(mPaths.removeAt(mPaths.size - 1))
            invalidate() //we have to invalidate in order to be called onDraw
        }
    }

    fun redoPath(){
        if(mUndoPaths.size > 0){
            mPaths.add(mUndoPaths.removeAt(mUndoPaths.size - 1))
            invalidate()
        }
    }
    internal inner class CustomPath (var color: Int, var brushThickness: Float) : Path() {

    }
}