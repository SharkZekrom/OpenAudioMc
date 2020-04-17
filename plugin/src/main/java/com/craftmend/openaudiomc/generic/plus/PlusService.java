package com.craftmend.openaudiomc.generic.plus;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.generic.plus.response.ClientSettingsResponse;
import com.craftmend.openaudiomc.generic.plus.response.PlusLoginToken;
import com.craftmend.openaudiomc.generic.plus.tasks.PlayerSynchroniser;
import com.craftmend.openaudiomc.generic.plus.updates.CreateLoginPayload;
import com.craftmend.openaudiomc.generic.rest.RestRequest;
import com.craftmend.openaudiomc.generic.rest.interfaces.GenericApiResponse;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

public class PlusService {

    private PlayerSynchroniser playerSynchroniser;
    @Getter
    private String baseUrl;
    @Getter private boolean plusEnabled;

    public PlusService(OpenAudioMc openAudioMc) {
        getPlusSettings();
        playerSynchroniser = new PlayerSynchroniser(this, openAudioMc);
    }

    public CompletableFuture<String> createLoginToken(String playerName) {
        CompletableFuture<String> cf = new CompletableFuture<>();
        OpenAudioMc.getInstance().getTaskProvider().runAsync(() -> {
            CreateLoginPayload createLoginPayload = new CreateLoginPayload(playerName, OpenAudioMc.getInstance().getAuthenticationService().getServerKeySet().getPrivateKey().getValue());
            RestRequest keyRequest = new RestRequest("/api/v1/servers/createlogin");
            keyRequest.setBody(OpenAudioMc.getGson().toJson(createLoginPayload));
            GenericApiResponse response = keyRequest.executeSync();
            if (!response.getErrors().isEmpty()) throw new IllegalArgumentException("Auth failed!");

            PlusLoginToken plusLoginToken = response.getResponse(PlusLoginToken.class);
            cf.complete(plusLoginToken.getToken());
        });
        return cf;
    }

    public void getPlusSettings() {
        RestRequest keyRequest = new RestRequest("/api/v1/public/settings/" + OpenAudioMc.getInstance().getAuthenticationService().getServerKeySet().getPublicKey().getValue());
        ClientSettingsResponse response = keyRequest.executeSync().getResponse(ClientSettingsResponse.class);
        baseUrl = response.getDomain() + "?&data=";
        plusEnabled = response.getPlayerSync();
    }

    public void shutdown() {
        playerSynchroniser.deleteAll();
        createLoginToken(null);
    }

}
