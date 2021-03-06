package com.craftmend.openaudiomc.generic.networking.enums;

public enum PacketChannel {

    /**
     * All the channel names that are used in communication
     * <p>
     * SOCKET_IN is what the plugin receives via the client
     * SOCKET_OUT is what is send to the socket to proxy to the client
     */

    // socket in, so received by the minecraft server
    SOCKET_IN_REGISTER_CLIENT, // client connected
    SOCKET_IN_UNREGISTER_CLIENT, // client disconnected
    SOCKET_IN_CLIENT_ENABLED_HUE, // client connected to a hue bridge
    SOCKET_IN_CLIENT_FAILED_MEDIA, // client failed to fetch media
    SOCKET_IN_CLIENT_UPDATE_CHANNELS, // a list of current channels whenever a source started or stopped
    SOCKET_IN_CLIENT_CHANGED_VOLUME, // client updated volume


    // socket out, so received by the socket and send from the minecraft server
    SOCKET_OUT_KICK_CLIENT,
    SOCKET_OUT_ACKNOWLEDGEMENT,

    // client target, targeted to the client
    CLIENT_OUT_CREATE_MEDIA,
    CLIENT_OUT_UPDATE_MEDIA,
    CLIENT_OUT_SET_PROTOCOL_VERSION,
    CLIENT_OUT_DESTROY_MEDIA,
    CLIENT_OUT_PREFETCH,
    CLIENT_OUT_PUSH_NOTIFICATION,
    CLIENT_OUT_SET_VOLUME,
    CLIENT_OUT_SET_HUE,
    CLIENT_OUT_PLAYER_LOCATION,
    CLIENT_OUT_SPEAKER_CREATE,
    CLIENT_OUT_SPEAKER_DESTROY,

    // voice chat
    CLIENT_OUT_VOICE_UNLOCK,
    CLIENT_OUT_VOICE_DROP_STREAM,
    CLIENT_OUT_VOICE_UPDATE_PEER_LOCATIONS,
    CLIENT_OUT_VOICE_SUBSCRIBE,
    SOCKET_IN_CLIENT_INITIALIZED_RTC,

}
