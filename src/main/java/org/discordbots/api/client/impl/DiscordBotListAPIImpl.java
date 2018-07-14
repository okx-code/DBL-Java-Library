package org.discordbots.api.client.impl;

import com.google.gson.Gson;
import okhttp3.*;
import org.discordbots.api.client.DiscordBotListAPI;
import org.discordbots.api.client.entity.*;
import org.discordbots.api.client.io.DefaultResponseTransformer;
import org.discordbots.api.client.io.ResponseTransformer;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class DiscordBotListAPIImpl implements DiscordBotListAPI {

    private static final HttpUrl baseUrl = new HttpUrl.Builder()
            .scheme("https")
            .host("discordbots.org")
            .addPathSegment("api")
            .build();

    private final OkHttpClient httpClient;
    private final Gson gson;

    private final String token, botId;

    public DiscordBotListAPIImpl(String token, String botId, Gson gson, OkHttpClient httpClient) {
        this.token = token;
        this.botId = botId;
        this.gson = gson;
        this.httpClient = httpClient.newBuilder()
                .addInterceptor((chain) -> {
                    Request req = chain.request().newBuilder()
                            .addHeader("Authorization", this.token)
                            .build();
                    return chain.proceed(req);
                })
                .build();
    }

    public Future<Void> setStats(int shardId, int shardTotal, int serverCount) {
        JSONObject json = new JSONObject()
                .put("shard_id", shardId)
                .put("shard_total", shardTotal)
                .put("server_count", serverCount);

        return setStats(json);
    }

    public Future<Void> setStats(List<Integer> shardServerCounts) {
        JSONObject json = new JSONObject()
                .put("shards", shardServerCounts);

        return setStats(json);
    }

    public Future<Void> setStats(int serverCount) {
        JSONObject json = new JSONObject()
                .put("server_count", serverCount);

        return setStats(json);
    }

    private Future<Void> setStats(JSONObject jsonBody) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("votes")
                .build();

        return post(url, jsonBody, Void.class);
    }

    public Future<BotStats> getStats(String botId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("stats")
                .build();

        return get(url, BotStats.class);
    }

    public Future<SimpleUser[]> getVoters(String botId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("votes")
                .build();

        return get(url, SimpleUser[].class);
    }

    public Future<Bot> getBot(String botId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .build();

        return get(url, Bot.class);
    }

    public Future<BotResult> getBots(Map<String, Object> search, String sort, int limit, int offset, List<String> fields) {
        String searchString = search.entrySet().stream()
                .map((entry) -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(" "));

        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addQueryParameter("search", searchString)
                .build();

        return get(url, BotResult.class);
    }

    public Future<User> getUser(String userId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("users")
                .addPathSegment(userId)
                .build();

        return get(url, User.class);
    }

    public Future<Boolean> hasVoted(String userId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("check")
                .addQueryParameter("userId", userId)
                .build();

        return get(url, (resp) -> {
            JSONObject json = new JSONObject(resp.body().string());
            return json.getInt("voted") == 1;
        });
    }

    private <E> Future<E> get(HttpUrl url, Class<E> clazz) {
        return get(url, new DefaultResponseTransformer<>(clazz, gson));
    }

    private <E> Future<E> get(HttpUrl url, ResponseTransformer<E> responseTransformer) {
        Request req = new Request.Builder()
                .get()
                .url(url)
                .build();

        return execute(req, responseTransformer);
    }

    // The class provided in this is kinda unneeded because the only thing ever given to it
    // is Void, but I wanted to make it expandable (maybe some post methods will return objects
    // in the future)
    private <E> Future<E> post(HttpUrl url, JSONObject jsonBody, Class<E> clazz) {
        return post(url, jsonBody, new DefaultResponseTransformer<>(clazz, gson));
    }

    private <E> Future<E> post(HttpUrl url, JSONObject jsonBody, ResponseTransformer<E> responseTransformer) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonBody.toString());

        Request req = new Request.Builder()
                .post(body)
                .url(url)
                .build();

        return execute(req, responseTransformer);
    }

    private <E> Future<E> execute(Request request, ResponseTransformer<E> responseTransformer) {
        Call call = httpClient.newCall(request);

        final CompletableFuture<E> future = new CompletableFuture<>();

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    E transformed = responseTransformer.transform(response);
                    future.complete(transformed);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

}