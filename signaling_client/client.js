const startBtn = document.getElementById('start')
const wsUrlInput = document.getElementById('wsUrl')
let pc = null
let ws = null

startBtn.onclick = async () => {
  const url = wsUrlInput.value.trim()
  if (!url) return alert('Enter WebSocket URL')

  ws = new WebSocket(url)
  ws.onopen = async () => {
    console.log('Connected to signaling server')
    // Send ICE config and bitrate to the TV before creating offer
    const iceText = document.getElementById('iceConfig').value
    try {
      const ice = JSON.parse(iceText)
      ws.send(JSON.stringify({type: 'ice-config', iceServers: ice}))
    } catch (e) {
      console.warn('Invalid ICE JSON, using default')
    }
    const bitrateVal = parseInt(document.getElementById('bitrate').value || '0')
    ws.send(JSON.stringify({type: 'set-bitrate', kbps: bitrateVal}))
    await startCaptureAndSend()
  }

  ws.onmessage = async (ev) => {
    const msg = JSON.parse(ev.data)
    if (!pc) return
    if (msg.type === 'answer') {
      await pc.setRemoteDescription({type: 'answer', sdp: msg.sdp})
      console.log('Remote description set (answer)')
    } else if (msg.type === 'candidate') {
      try {
        await pc.addIceCandidate(msg)
      } catch (e) { console.warn(e) }
    }
  }
}

async function startCaptureAndSend() {
  try {
    // Feature-detect getDisplayMedia across browsers
    const captureFn = (navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia)
      ? navigator.mediaDevices.getDisplayMedia.bind(navigator.mediaDevices)
      : (navigator.getDisplayMedia ? navigator.getDisplayMedia.bind(navigator) : null)

    if (!captureFn) {
      const msg = 'Screen capture not supported: use Chrome or Edge on desktop and open the page via http(s) or localhost.'
      console.error(msg)
      alert(msg)
      return
    }

    const stream = await captureFn({video: {width: {max: 3840}, height: {max: 2160}, frameRate: {max:60}}, audio: false})
    pc = new RTCPeerConnection({iceServers: [{urls: 'stun:stun.l.google.com:19302'}]})

    // Forward local ICE candidates to signaling server
    pc.onicecandidate = (e) => {
      if (e.candidate) {
        ws.send(JSON.stringify({type: 'candidate', sdpMid: e.candidate.sdpMid, sdpMLineIndex: e.candidate.sdpMLineIndex, candidate: e.candidate.candidate}))
      }
    }

    // Add tracks to connection
    stream.getTracks().forEach(track => pc.addTrack(track, stream))

    const offer = await pc.createOffer({offerToReceiveVideo: false})
    await pc.setLocalDescription(offer)

    // Send offer via signaling
    ws.send(JSON.stringify({type: 'offer', sdp: offer.sdp}))
    console.log('Offer sent')
  } catch (e) {
    console.error('Error capturing or creating offer', e)
    alert('Error: ' + e.message)
  }
}
