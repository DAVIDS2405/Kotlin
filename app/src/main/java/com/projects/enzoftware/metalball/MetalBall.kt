package com.projects.enzoftware.metalball

import android.app.Service
import android.bluetooth.BluetoothClass
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MetalBall : AppCompatActivity() , SensorEventListener {

    private var mSensorManager : SensorManager ?= null
    private var mAccelerometer : Sensor ?= null
    var ground : GroundView ?= null


    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        // get reference of the service
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // focus in accelerometer
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // setup the window
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            window.decorView.systemUiVisibility =   View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            View.SYSTEM_UI_FLAG_FULLSCREEN
            View.SYSTEM_UI_FLAG_IMMERSIVE
        }

        // set the view
        ground = GroundView(this)
        setContentView(ground)
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            ground!!.updateMe(event.values[1] , event.values[0])
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager!!.registerListener(this,mAccelerometer,
            SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager!!.unregisterListener(this)
    }

    class DrawThread(surfaceHolder: SurfaceHolder, panel: GroundView) : Thread() {
        private var surfaceHolder: SurfaceHolder? = null
        private var panel: GroundView? = null
        private var run = false

        init {
            this.surfaceHolder = surfaceHolder
            this.panel = panel
        }

        fun setRunning(run: Boolean) {
            this.run = run
        }

        override fun run() {
            while (run) {
                var c: Canvas? = null
                try {
                    // Verificar que surfaceHolder y panel no sean nulos antes de usarlos
                    if (surfaceHolder != null && panel != null) {
                        c = surfaceHolder!!.lockCanvas(null)
                        if (c != null) {
                            synchronized(surfaceHolder!!) {
                                panel!!.drawSurface(c)
                            }
                        }
                    }
                } finally {
                    if (c != null) {
                        surfaceHolder!!.unlockCanvasAndPost(c)
                    }
                }
            }
        }
    }


}


class GroundView(context: Context?) : SurfaceView(context), SurfaceHolder.Callback{

    // ball coordinates
    var cx : Float = 10.toFloat()
    var cy : Float = 10.toFloat()

    // last position increment

    var lastGx : Float = 0.toFloat()
    var lastGy : Float = 0.toFloat()

    // graphic size of the ball

    var picHeight: Int = 0
    var picWidth : Int = 0

    var icon:Bitmap ?= null

    // window size

    var Windowwidth : Int = 0
    var Windowheight : Int = 0

    // is touching the edge ?

    var noBorderX = false
    var noBorderY = false

    var vibratorService : Vibrator ?= null
    var thread : MetalBall.DrawThread?= null



    init {
        holder.addCallback(this)
        //create a thread
        thread = MetalBall.DrawThread(holder, this)
        // get references and sizes of the objects
        val display: Display = (getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val size:Point = Point()
        display.getSize(size)
        Windowwidth = size.x
        Windowheight = size.y
        icon = BitmapFactory.decodeResource(resources,R.drawable.ball)
        picHeight = icon!!.height
        picWidth = icon!!.width
        vibratorService = (getContext().getSystemService(Service.VIBRATOR_SERVICE)) as Vibrator
    }

    override fun surfaceChanged(p0: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        thread!!.setRunning(true)
        thread!!.start()
    }

    val database = Firebase.database
    val myRef = database.getReference("bola")
    private var lastSaveTime: Long = 0
    private val saveInterval = 5000


    fun drawSurface(canvas: Canvas?) {
        if (canvas != null) {
            super.draw(canvas)
        }
        if (canvas != null){
            canvas.drawColor(0xFFAAAAA)
            icon?.let { canvas.drawBitmap(it,cx,cy,null) }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSaveTime >= saveInterval) {
                saveBallPositionToFirebase()
                lastSaveTime = currentTime
            }
        }
    }

    private fun saveBallPositionToFirebase() {
        val ballPosition = hashMapOf(
            "cx" to cx,
            "cy" to cy
        )

        myRef.setValue(ballPosition)
            .addOnSuccessListener {
                Log.d("RealtimeDatabase", "Posición de la pelota guardada correctamente.")
            }
            .addOnFailureListener {
                Log.e("RealtimeDatabase", "Error al guardar la posición de la pelota: $it")
            }
    }

    fun onDrawSurface(canvas: Canvas?) {

        if (canvas != null){
            canvas.drawColor(0xFFAAAAA)
            icon?.let { canvas.drawBitmap(it,cx,cy,null) }
        }
    }

    fun updateMe(inx : Float , iny : Float){
        lastGx += inx
        lastGy += iny

        cx += lastGx
        cy += lastGy

        if(cx > (Windowwidth - picWidth)){
            cx = (Windowwidth - picWidth).toFloat()
            lastGx = 0F
            if (noBorderX){
                vibratorService!!.vibrate(100)
                noBorderX = false
            }
        }
        else if(cx < (0)){
            cx = 0F
            lastGx = 0F
            if(noBorderX){
                vibratorService!!.vibrate(100)
                noBorderX = false
            }
        }
        else{ noBorderX = true }

        if (cy > (Windowheight - picHeight)){
            cy = (Windowheight - picHeight).toFloat()
            lastGy = 0F
            if (noBorderY){
                vibratorService!!.vibrate(100)
                noBorderY = false
            }
        }

        else if(cy < (0)){
            cy = 0F
            lastGy = 0F
            if (noBorderY){
                vibratorService!!.vibrate(100)
                noBorderY= false
            }
        }
        else{ noBorderY = true }

        invalidate()

    }


}