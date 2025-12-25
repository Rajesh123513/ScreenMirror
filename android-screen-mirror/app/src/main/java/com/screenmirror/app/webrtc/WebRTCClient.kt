package com.screenmirror.app.webrtc

import android.content.Context
import android.util.Log
import android.view.Surface
import org.webrtc.SurfaceViewRenderer
import org.webrtc.*

class WebRTCClient(private val context: Context) {
    private val TAG = "WebRTCClient"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var surface: Surface? = null
    private var surfaceViewRenderer: SurfaceViewRenderer? = null
    private var eglBase: EglBase = EglBase.create()
    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var maxBitrateKbps: Int = 0

    fun init() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val pcOptions = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(pcOptions)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "PeerConnectionFactory initialized")
    }

    fun setSurface(s: Surface) {
        this.surface = s
    }

    fun setSurfaceViewRenderer(renderer: SurfaceViewRenderer) {
        this.surfaceViewRenderer = renderer
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(false)
        renderer.setZOrderMediaOverlay(true)
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer> = listOf()) {
        if (peerConnectionFactory == null) init()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE connection state: $newState")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "New ICE candidate: $candidate")
                    onIceCandidate?.invoke(candidate)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    Log.d(TAG, "Remote video track received")
                    // Render to provided SurfaceViewRenderer if available
                    surfaceViewRenderer?.let { rv ->
                        track.addSink(rv)
                    }
                }
            }
        })
    }

    fun handleOffer(sdp: String, setLocalAnswer: (String) -> Unit, sendCandidate: (IceCandidate) -> Unit) {
        val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
                // Create answer
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        if (answer != null) {
                            // Optionally modify answer SDP to include bandwidth limit
                            var answerSdp = answer.description
                            if (maxBitrateKbps > 0) {
                                answerSdp = mungeSdpBandwidth(answerSdp, maxBitrateKbps)
                            }
                            val modifiedAnswer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    setLocalAnswer(modifiedAnswer.description)
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, modifiedAnswer)
                        }
                    }

                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description (answer) set successfully")
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, MediaConstraints())
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Failed to set remote description: $p0")
            }
        }, desc)

        // When new candidates are gathered, report them via onIceCandidate override
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, desc)
    }

    fun addIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        val ok = peerConnection?.addIceCandidate(ice)
        Log.d(TAG, "addIceCandidate result: $ok, sdpMid=$sdpMid, sdpMLineIndex=$sdpMLineIndex")
    }

    private fun mungeSdpBandwidth(sdp: String, kbps: Int): String {
        try {
            val lines = sdp.split("\r\n").toMutableList()
            val out = mutableListOf<String>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                out.add(line)
                if (line.startsWith("m=video")) {
                    // Insert bandwidth line after m=video
                    out.add("b=AS:$kbps")
                }
                i++
            }
            return out.joinToString("\r\n")
        } catch (e: Exception) {
            return sdp
        }
    }

    fun close() {
        try {
            peerConnection?.close()
            peerConnection = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRTC client", e)
        }
    }
}
