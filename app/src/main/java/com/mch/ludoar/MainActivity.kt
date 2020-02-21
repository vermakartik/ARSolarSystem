package com.mch.ludoar

import android.graphics.Point
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    val TAG = MainActivity::class.java.canonicalName
    var arScene: ArSceneView? = null

        enum class TrackingState {
            NIL,
            TRACKING,
            NOT_TRACKING
        }

        var curTrackingState = MutableLiveData<TrackingState>()
        init {
            curTrackingState.postValue(TrackingState.NIL)
        }

    var placed: Boolean = false
    var ndCubeRenderable: ModelRenderable? = null

    var currentScale = MutableLiveData<Float>()
    init {
        currentScale.postValue(1.0f)
    }

    fun initializer() {
//        val c = Color()
//        c.set(0.501f, 0.20f, 0.780f, 0.2f)
//        MaterialFactory.makeOpaqueWithColor(this.applicationContext, c).thenAccept {
//            ndCubeRenderable = ShapeFactory.makeCube(Vector3(0.2f, 0.2f, 0.2f), Vector3(0.1f, 0.1f, 0.1f), it)
//        }
        ModelRenderable.builder()
            .setSource(this, R.raw.ss)
            .build()
            .thenAccept {
                ndCubeRenderable = it
                Toast.makeText(this, "Model Loaded", Toast.LENGTH_LONG).show()
            }
    }

    lateinit var ac: MainActivity
    lateinit var ss: Node

    val cRotation = Quaternion.eulerAngles(Vector3(0f, 0.5f, 0f))

    fun changeScale(vl: Float) {
        val currentVal = currentScale.value!!
        val nValue = currentVal + vl
        currentScale.postValue(nValue)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializer()

        ac = this

        arScene = findViewById(R.id.ar_scene)
        arScene?.scene?.addOnUpdateListener {
            arScene?.arFrame?.let {
                checkPlaneState(it)
            } ?: curTrackingState.postValue(TrackingState.NIL)
            if(::ss.isInitialized) {
                val curRot = ss.localRotation
                val nRot = Quaternion.multiply(curRot, cRotation)
                ss.localRotation = nRot
            }
            Log.d(TAG, "Current Tracking State: ${curTrackingState.value.toString()}")
        }

        curTrackingState.observe(this, Observer {
            tv_status.text = it.toString()
        })

        val tapObserver: (View, MotionEvent) -> Boolean = {
            _, motionEvent ->
            val f = arScene?.arFrame
            val m = f?.hitTest(motionEvent)
            var toRet = false
            for(v in m!!){
                val t = v.trackable
                Toast.makeText(ac.applicationContext, "Hit Test", Toast.LENGTH_LONG).show()
                if(t is Plane && t.isPoseInPolygon(v.hitPose)) {
                    Toast.makeText(ac.applicationContext, "Hit Test on Detectable Plane at location: ${t.centerPose}", Toast.LENGTH_LONG).show()
                    val value = onTap(v)
                    if(!value) continue
                    else {
                        toRet = true
                        break
                    }
                }
            }
            toRet
        }

        arScene?.setOnTouchListener {
            v, event ->
            Toast.makeText(ac.applicationContext, "Detected Event", Toast.LENGTH_LONG).show()
            if(placed) return@setOnTouchListener false
            placed = tapObserver(v, event)
            Toast.makeText(ac.applicationContext, "Placed is: ${placed}", Toast.LENGTH_LONG).show()
            return@setOnTouchListener placed
        }

        currentScale.observe(this, Observer {
            if(::ss.isInitialized) {
                ss.localScale = Vector3(it, it, it)
                tv_current_scale.text = String.format("%.1f", it)
            }
        })

        btn_scale_up.setOnClickListener {
            if(currentScale.value!! >= 1.0f) return@setOnClickListener
            changeScale(+0.1f)
        }

        btn_scale_down.setOnClickListener {
            if(currentScale.value!! <= 0.1f) return@setOnClickListener
            changeScale(-0.1f)
        }
    }

    fun onTap(h: HitResult): Boolean{
        return ndCubeRenderable?.let {
            val c = h.createAnchor()
            val anchNode = AnchorNode(c)
            anchNode.setParent(arScene?.scene)

            val nd = Node()
            nd.renderable = ndCubeRenderable
            nd.localPosition = Vector3(0f, 0f, 0f)
            anchNode.addChild(nd)

            ss = nd
            true
        } ?: false


    }


    fun getScreenCenter(): Point{
        return Point(arScene!!.width/2, arScene!!.height/2)
    }

    fun checkPlaneState(f: Frame){

        if(arScene?.arFrame?.camera?.trackingState == com.google.ar.core.TrackingState.TRACKING) {
            val c = getScreenCenter()
            f.let {
                val h = it.hitTest(c.x.toFloat(), c.y.toFloat())
                h.any {
                    val t = it.trackable
                    val res = t is Plane && t.isPoseInPolygon(it.hitPose)
                    if(res) {
                        curTrackingState.postValue(TrackingState.TRACKING)
                        return@any true
                    } else {
                        curTrackingState.postValue(TrackingState.NOT_TRACKING)
                    }
                    return@any false
                }
            }
        } else {
            curTrackingState.postValue(TrackingState.NOT_TRACKING)
        }
    }

    override fun onResume() {
        super.onResume()
        arScene?.let {
            if(it.session == null) {
                val s = Session(this)
                val c = Config(s)
                c.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                c.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                s.configure(c)
                it.setupSession(s)
            }
        }
        try {
            arScene?.resume()
        } catch (e: CameraNotAvailableException) {
            Log.d(TAG, e.toString())
        }
    }
}
