package co.bgcs.neterraproxy;

import android.content.Context;

import com.franmontiel.persistentcookiejar.ClearableCookieJar;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;


import java.io.IOException;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

class NeterraProxy extends NanoHTTPD {
    private final String host;
    private final int port;
    private final Pipe pipe;
    private String username;
    private String password;
    private long expireTime;
    private ClearableCookieJar cookieJar;

    NeterraProxy(String host, int port, Pipe pipe) {
        super(host, port);
        this.host = host;
        this.port = port;
        this.pipe = pipe;
    }

    void init(String username, String password, Context context) {
        this.username = username;
        this.password = password;
        cookieJar = new PersistentCookieJar(new SetCookieCache(),
                new SharedPrefsCookiePersistor(context));
        expireTime = 0;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Response res = super.serve(session);

        String uri = session.getUri();
        //TODO: Add EPG. Might need to host own xmltv.
        if (uri.equals("/epg.xml")) {
            pipe.setNotification("Now serving: EPG");
            res = newFixedLengthResponse(Response.Status.REDIRECT, "application/xml", null);
            res.addHeader("Location", "ADD EPG");

        } else if (uri.startsWith("/playlist.m3u8")) {
            List<String> ch = session.getParameters().get("ch");

            if (ch == null) {
                pipe.setNotification("Now serving: Playlist");
                res = newFixedLengthResponse(Response.Status.OK, "application/x-mpegURL", getM3U8());
                res.addHeader("Content-Disposition", "attachment; filename=\"playlist.m3u8\"");
            } else {
                pipe.setNotification("Now serving: Channel " + ch.get(0));
                res = newFixedLengthResponse(Response.Status.REDIRECT, "application/x-mpegURL", null);
                res.addHeader("Location", getStream(ch.get(0)));
            }
        }
        return res;
    }

    private String getStream(String issueId) {
        long NOW = System.currentTimeMillis();
        String channelPlayLink = "";

        // Check if authentication is needed
        if (NOW > expireTime) {
            cookieJar.clear();
            if (authenticate()) {
                expireTime = NOW + 28800000;
            } else {
                pipe.setNotification("Failed to Authenticate");
            }
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
        RequestBody formBody = new FormBody.Builder()
                .add("issue_id", issueId)
                .add("quality", "0")
                .add("type", "live")
                .build();
        Request request = new Request.Builder()
                .url("http://www.neterra.tv/content/get_stream")
                .post(formBody)
                .build();
        try {
            okhttp3.Response response = client.newCall(request).execute();
            channelPlayLink = Utils.getPlayLink(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return channelPlayLink;
    }

    private boolean authenticate() {
        boolean logged = false;

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
        RequestBody formBody = new FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("login", "1")
                .add("login_type", "1")
                .build();
        Request request = new Request.Builder()
                .url("http://www.neterra.tv/user/login_page")
                .post(formBody)
                .build();
        try {
            okhttp3.Response response = client.newCall(request).execute();
            logged = response.body().string().contains("var LOGGED = '1'");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return logged;
    }

    private String getM3U8() {
        String channelJsonString = "";

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
        Request request = new Request.Builder()
                .url("http://www.neterra.tv/content/live")
                .build();
        try {
            okhttp3.Response response = client.newCall(request).execute();
            channelJsonString = response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Utils.generatePlaylist(channelJsonString, host, port);
    }
}
