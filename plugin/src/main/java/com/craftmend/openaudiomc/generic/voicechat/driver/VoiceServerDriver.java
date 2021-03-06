package com.craftmend.openaudiomc.generic.voicechat.driver;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.generic.authentication.AuthenticationService;
import com.craftmend.openaudiomc.generic.logging.OpenAudioLogger;
import com.craftmend.openaudiomc.generic.networking.DefaultNetworkingService;
import com.craftmend.openaudiomc.generic.networking.client.objects.player.ClientConnection;
import com.craftmend.openaudiomc.generic.networking.interfaces.NetworkingService;
import com.craftmend.openaudiomc.generic.networking.packets.client.voice.PacketClientUnlockVoiceChat;
import com.craftmend.openaudiomc.generic.networking.payloads.client.voice.ClientVoiceChatUnlockPayload;
import com.craftmend.openaudiomc.generic.networking.rest.RestRequest;
import com.craftmend.openaudiomc.generic.networking.rest.data.ErrorCode;
import com.craftmend.openaudiomc.generic.networking.rest.endpoints.RestEndpoint;
import com.craftmend.openaudiomc.generic.networking.rest.interfaces.ApiResponse;
import com.craftmend.openaudiomc.generic.voicechat.VoiceService;
import com.craftmend.openaudiomc.generic.voicechat.enums.VoiceServerEventType;
import lombok.Setter;

import java.util.*;

public class VoiceServerDriver {

    private final String host;
    private final String password;
    private VoiceService service;
    private List<UUID> subscribers = new ArrayList<>();
    private int heartbeatTask = 0;
    @Setter private int blockRadius = -1;

    /**
     * Blocking method that tries to login to a server and establish a connection
     * @param host Server full host (eg https://joostspeeltspellen.voice.openaudiomc.net/) with a trailing slash
     * @param password Server password
     * @param service Voice service to manage
     */
    public VoiceServerDriver(String host, String password, VoiceService service) {
        this.host = host;
        this.password = password;
        this.service = service;

        // try to login
        if (!login()) {
            return;
        }

        // verify login with a heartbeat
        pushEvent(VoiceServerEventType.HEARTBEAT, new HashMap<>(), true, false, true);

        // schedule heartbeat every 5 seconds
        heartbeatTask = OpenAudioMc.getInstance().getTaskProvider().scheduleAsyncRepeatingTask(() -> {
            // send heartbeat
            pushEvent(VoiceServerEventType.HEARTBEAT, new HashMap<>(), true, true, false);
        }, 100, 100);

        // might be a restart, so clean all
        OpenAudioMc.getInstance().getNetworkingService().getClients().forEach(this::handleClientConnection);

        // setup events
        NetworkingService networkingService = OpenAudioMc.getInstance().getNetworkingService();
        if (networkingService instanceof DefaultNetworkingService) {
            // client got created
            subscribers.add(networkingService.subscribeToConnections((this::handleClientConnection)));

            subscribers.add(networkingService.subscribeToDisconnections((clientConnection -> {
                // client will be removed
                pushEvent(VoiceServerEventType.REMOVE_PLAYER, new HashMap<String, String>() {{
                    put("streamKey", clientConnection.getStreamKey());
                }}, false, false, false);
            })));
        } else {
            throw new IllegalStateException("Not implemented yet");
        }

        OpenAudioLogger.toConsole("Successfully logged into a WebRTC server");
    }

    private void handleClientConnection(ClientConnection clientConnection) {
        pushEvent(VoiceServerEventType.ADD_PLAYER, new HashMap<String, String>() {{
            put("playerName", clientConnection.getPlayer().getName());
            put("playerUuid", clientConnection.getPlayer().getUniqueId().toString());
            put("streamKey", clientConnection.getStreamKey());
        }}, false, true, true);

        clientConnection.onConnect(() -> {
            // unlock capabilities
            clientConnection.sendPacket(new PacketClientUnlockVoiceChat(new ClientVoiceChatUnlockPayload(
                    clientConnection.getStreamKey(),
                    this.host,
                    blockRadius
            )));
        });
    }

    public void shutdown() {
        // logout
        pushEvent(VoiceServerEventType.LOGOUT, new HashMap<>(), true, false, false);
        NetworkingService networkingService = OpenAudioMc.getInstance().getNetworkingService();
        for (UUID subscriber : subscribers) {
            networkingService.unsubscribeClientEventHandler(subscriber);
        }
        // kick all clients who had rtc open
        for (ClientConnection client : OpenAudioMc.getInstance().getNetworkingService().getClients()) {
            if (client.getClientRtcManager().isReady()) {
                client.kick();
            }
        }
        OpenAudioMc.getInstance().getTaskProvider().cancelRepeatingTask(heartbeatTask);
    }

    private boolean login() {
        RestRequest loginRequest = new RestRequest(RestEndpoint.VOICE_LOGIN.setHost(this.host));

        // add query shit
        AuthenticationService authenticationService = OpenAudioMc.getInstance().getAuthenticationService();
        loginRequest.setQuery("publicKey", authenticationService.getServerKeySet().getPublicKey().getValue());
        loginRequest.setQuery("privateKey", authenticationService.getServerKeySet().getPrivateKey().getValue());
        loginRequest.setQuery("password", this.password);

        ApiResponse response = loginRequest.executeInThread();
        if (!response.getErrors().isEmpty()) {
            if (response.getErrors().get(0).getCode() == ErrorCode.INVALID_LOGIN) {
                throw new IllegalArgumentException("The voice server is either invalid or denies your login");
            } else {
                OpenAudioLogger.toConsole("Failed to login with the voice server because the handshake failed. Trying again in two seconds..");
                OpenAudioMc.getInstance().getTaskProvider().schduleSyncDelayedTask(() -> this.service.requestRestart(), 20 * 2);
                return false;
            }
        }
        return true;
    }

    private void pushEvent(VoiceServerEventType event, Map<String, String> arguments, boolean now, boolean failSafe, boolean canExplode) {
        RestRequest eventRequest = new RestRequest(RestEndpoint.VOICE_EVENTS.setHost(this.host));

        // add query shit
        AuthenticationService authenticationService = OpenAudioMc.getInstance().getAuthenticationService();
        eventRequest.setQuery("publicKey", authenticationService.getServerKeySet().getPublicKey().getValue());
        eventRequest.setQuery("privateKey", authenticationService.getServerKeySet().getPrivateKey().getValue());
        eventRequest.setQuery("event", event.name());

        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            eventRequest.setQuery(key, value);
        }

        if (now) {
            ApiResponse response = eventRequest.executeInThread();
            if (!response.getErrors().isEmpty()) {
                if (response.getErrors().get(0).getCode() == ErrorCode.BAD_HANDSHAKE) {
                    OpenAudioLogger.toConsole("There was an error while trying to talk with the event stream. Restarting the voice service...");
                    if (failSafe) {
                        this.service.requestRestart();
                        return;
                    }
                }
                if (canExplode) throw new IllegalArgumentException("The voice server is either invalid or denies your event");
            }
        } else {
            eventRequest.executeAsync().thenAccept(response -> {
                if (!response.getErrors().isEmpty()) {
                    if (response.getErrors().get(0).getCode() == ErrorCode.BAD_HANDSHAKE) {
                        OpenAudioLogger.toConsole("There was an error while trying to talk with the event stream. Restarting the voice service...");
                        if (failSafe) {
                            this.service.requestRestart();
                            return;
                        }
                    }
                    if (canExplode) throw new IllegalArgumentException("The voice server is either invalid or denies your event");
                }
            });
        }
    }

}
