import {oalog} from "../../../helpers/log";
import {Vector3} from "../../../helpers/math/Vector3";
import {Position} from "../../../helpers/math/Position";

export class IncomingVoiceStream {

    constructor(openAudioMc, server, streamKey, peerStreamKey, volume) {
        this.openAudioMc = openAudioMc;
        this.server = server;
        this.streamKey = streamKey;
        this.peerStreamKey = peerStreamKey;
        this.volume = volume;
    }

    start(whenFinished) {
        let endpoint = this.server + "webrtc/listener/sdp" +
            "/m/" + tokenCache.publicServerKey +
            "/pu/" + tokenCache.uuid +
            "/pn/" + tokenCache.name +
            "/tg/" + this.peerStreamKey +
            "/sk/" + this.streamKey;

        this.pcReceiver = new RTCPeerConnection({
            iceServers: [
                {
                    urls: 'stun:stun.l.google.com:19302'
                }
            ]
        });

        this.pcReceiver.onicecandidate = event => {
            if (event.candidate === null && this.pcReceiver.signalingState !== 'closed') {
                fetch(endpoint, {
                    method: "POST",
                    body: JSON.stringify({"sdp": btoa(JSON.stringify(this.pcReceiver.localDescription))})
                })
                    .then(response => response.json())
                    .then(response => this.pcReceiver.setRemoteDescription(new RTCSessionDescription(JSON.parse(atob(response.Sdp)))))
                    .catch((e) => {
                        console.error(e);
                        window.location.reload();
                    })
            } else if (event.candidate === null) {
                oalog("Signalisng state can't be clsoed")
            }
        };

        this.pcReceiver.onconnectionstatechange = (event) => {
            if (this.pcReceiver.connectionState === 'connected') {
                whenFinished();
            }
        };

        this.pcReceiver.ontrack = (event) => {
            const stream = event.streams[0];
            const ctx = this.openAudioMc.world.player.audioCtx;
            this.gainNode = ctx.createGain();
            const audio = new Audio();
            audio.srcObject = stream;

            this.gainNode.gain.value = this.volume / 100;

            audio.onloadedmetadata = () => {
                const source = ctx.createMediaStreamSource(audio.srcObject);
                audio.play();
                audio.muted = true;
                const gainNode = this.gainNode;

                this.pannerNode = ctx.createPanner();

                this.pannerNode.panningModel = 'HRTF';
                this.pannerNode.maxDistance = this.openAudioMc.voiceModule.blocksRadius;
                this.pannerNode.rolloffFactor = 1;
                this.pannerNode.distanceModel = "exponential";

                this.setLocation(this.x, this.y, this.z, true);

                source.connect(gainNode);
                gainNode.connect(this.pannerNode);
                this.pannerNode.connect(ctx.destination);
            }
        }

        this.pcReceiver.addTransceiver('audio', {'direction': 'recvonly'})
        this.pcReceiver.createOffer()
            .then(d => this.pcReceiver.setLocalDescription(d))
            .catch(console.error)

    }

    setLocation(x, y, z, update) {
        if (update && this.pannerNode != null) {
            const position = new Position(new Vector3(
                this.x,
                this.y,
                this.z
            ));
            position.applyTo(this.pannerNode);
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    setVolume(volume) {
        this.volume = volume;
        this.gainNode.gain.value = this.volume / 100;
    }

    stop() {
        oalog("Closing voice link with " + this.peerStreamKey);
        for (let receiver of this.pcReceiver.getReceivers()) {
            receiver.track.stop();
        }
        this.pcReceiver.close();
    }

}
