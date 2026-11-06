package com.jianyi.yixue.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** The optional online feature's only transport. Call answer from a worker thread. */
public final class DeepSeekClient {
    public static final String MODEL = "deepseek-flash";
    public static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final int MAX_REQUEST_BYTES = 96 * 1024;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_MESSAGES = 32;
    private static final int MAX_MESSAGE_CHARACTERS = 24000;

    private DeepSeekClient() { }

    /** Cancellation is single-use and may safely be requested by the UI thread. */
    public static final class Cancellation {
        private volatile boolean cancelled;
        private HttpsURLConnection connection;
        private Thread worker;

        public void cancel() {
            HttpsURLConnection currentConnection;
            Thread currentWorker;
            synchronized (this) {
                cancelled = true;
                currentConnection = connection;
                currentWorker = worker;
            }
            if (currentWorker != null) currentWorker.interrupt();
            if (currentConnection != null) currentConnection.disconnect();
        }

        public boolean isCancelled() { return cancelled; }

        private synchronized void attachThread() throws ApiException {
            check(this);
            if (worker != null) throw new ApiException("当前请求尚未结束，请稍后再试。");
            worker = Thread.currentThread();
        }

        private synchronized void attachConnection(HttpsURLConnection current) throws ApiException {
            check(this);
            connection = current;
        }

        private synchronized void detachConnection(HttpsURLConnection current) {
            if (connection == current) connection = null;
        }

        private synchronized void detachThread() {
            if (worker == Thread.currentThread()) worker = null;
        }
    }

    /** Contains only safe, user-facing text, never a key or a provider response body. */
    public static final class ApiException extends IOException {
        private ApiException(String message) { super(message); }
    }

    public static String answer(String apiKey, JSONArray messages, Cancellation cancel) throws Exception {
        return answer(apiKey, messages, cancel, AiAnswerPolicy.GENERIC);
    }

    /**
     * Runs one initial request and, only when a deterministic local policy rejects
     * the result, one controlled rewrite.  An unverified answer is never returned
     * to the UI or persisted in the notebook.
     */
    public static String answer(String apiKey, JSONArray messages, Cancellation cancel,
                                AiAnswerPolicy.Policy policy) throws Exception {
        final Cancellation cancellation = cancel == null ? new Cancellation() : cancel;
        final String key = validatedKey(apiKey);
        final JSONArray snapshot = validatedMessages(messages);
        final AiAnswerPolicy.Policy selectedPolicy = policy == null ? AiAnswerPolicy.GENERIC : policy;
        cancellation.attachThread();
        try {
            int maxTokens=("bazi".equals(selectedPolicy.id)||"bazi_followup".equals(selectedPolicy.id)||"meihua".equals(selectedPolicy.id)||"meihua_initial".equals(selectedPolicy.id))?2500:650;
            String candidate = request(key, snapshot, cancellation,maxTokens);
            String issue = selectedPolicy.issue(candidate);
            if (issue.length() == 0) {
                check(cancellation);
                return candidate;
            }

            // Preserve the immutable chart and original question.  The rejected
            // answer is supplied only as rewrite material; one additional request
            // is the hard limit, whether the problem is length, evidence, or terms.
            JSONArray correction = new JSONArray(snapshot.toString());
            correction.put(new JSONObject().put("role", "assistant").put("content", candidate));
            correction.put(new JSONObject().put("role", "user").put("content", selectedPolicy.retryInstruction(issue)));
            String rewritten = request(key, validatedMessages(correction), cancellation,maxTokens);
            String remaining = selectedPolicy.issue(rewritten);
            if (remaining.length() > 0) {
                throw new ApiException("AI回答仍未通过“" + selectedPolicy.displayName + "”本机核验（" + remaining + "），已停止展示。请重试。");
            }
            check(cancellation);
            return rewritten;
        } catch (ApiException safe) {
            throw safe;
        } catch (Exception ignored) {
            check(cancellation);
            throw new ApiException("解答暂未完成，请稍后重试。");
        } finally {
            cancellation.detachThread();
        }
    }

    private static String request(String key, JSONArray messages, Cancellation cancel,int maxTokens) throws Exception {
        check(cancel);
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("thinking", new JSONObject().put("type", "disabled"));
        body.put("max_tokens", maxTokens);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new ApiException("本轮对话内容过长，请新建一次解答。");
        }

        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
            // The platform's default trust manager and hostname verification remain intact.
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setFixedLengthStreamingMode(bytes.length);
            cancel.attachConnection(connection);
            check(cancel);
            try (OutputStream output = connection.getOutputStream()) {
                check(cancel);
                output.write(bytes);
                output.flush();
            }
            check(cancel);
            int status = connection.getResponseCode();
            check(cancel);
            if (status != 200) throw statusError(status);
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_RESPONSE_BYTES) {
                throw new ApiException("服务返回内容过大，已停止读取。请稍后重试。");
            }
            String raw;
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    check(cancel);
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) {
                        throw new ApiException("服务返回内容过大，已停止读取。请稍后重试。");
                    }
                    output.write(buffer, 0, read);
                }
                check(cancel);
                try {
                    raw = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(output.toByteArray())).toString();
                } catch (CharacterCodingException invalidUtf8) {
                    throw malformed();
                }
            }
            String content = parseContent(raw);
            check(cancel);
            return content;
        } catch (ApiException safe) {
            check(cancel);
            throw safe;
        } catch (SocketTimeoutException ignored) {
            check(cancel);
            throw new ApiException("连接或等待解答超时，请检查网络后重试。");
        } catch (Exception ignored) {
            check(cancel);
            throw new ApiException("暂时无法连接DeepSeek，请检查网络后重试。");
        } finally {
            if (connection != null) {
                cancel.detachConnection(connection);
                connection.disconnect();
            }
        }
    }

    private static String parseContent(String raw) throws ApiException {
        try {
            JSONObject body = new JSONObject(raw);
            JSONArray choices = body.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw malformed();
            JSONObject first = choices.optJSONObject(0);
            if (first == null) throw malformed();
            if (!"stop".equals(first.optString("finish_reason", ""))) {
                throw new ApiException("本次解答未完整生成，已停止展示。请缩小问题范围后重试。");
            }
            JSONObject message = first.optJSONObject("message");
            if (message == null || !"assistant".equals(message.optString("role", ""))) throw malformed();
            if (!message.isNull("tool_calls") || !message.isNull("function_call")) throw malformed();
            Object reasoning = message.opt("reasoning_content");
            if (reasoning != null && reasoning != JSONObject.NULL && !"".equals(reasoning)) throw malformed();
            Object refusal = message.opt("refusal");
            if (refusal != null && refusal != JSONObject.NULL && !"".equals(refusal)) {
                throw new ApiException("本次问题未能生成解答，请调整问题后重试。");
            }
            Object content = message.opt("content");
            if (!(content instanceof String)) throw malformed();
            unicodeLength((String) content);
            String result = ((String) content).trim();
            if (result.isEmpty()) throw malformed();
            unicodeLength(result);
            return result;
        } catch (JSONException ignored) {
            throw malformed();
        }
    }

    private static JSONArray validatedMessages(JSONArray messages) throws ApiException {
        if (messages == null || messages.length() < 2 || messages.length() > MAX_MESSAGES) {
            throw new ApiException("对话内容不完整或过长，请新建一次解答。");
        }
        JSONArray result = new JSONArray();
        boolean hasUser = false;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) throw malformedInput();
            String role = item.optString("role", "");
            Object content = item.opt("content");
            if (!(content instanceof String) || ((String) content).trim().isEmpty()) throw malformedInput();
            if (unicodeLength((String) content) > MAX_MESSAGE_CHARACTERS) {
                throw new ApiException("本轮对话内容过长，请新建一次解答。");
            }
            if (i == 0) {
                if (!"system".equals(role)) throw malformedInput();
            } else if (!"user".equals(role) && !"assistant".equals(role)) {
                throw malformedInput();
            }
            if ("user".equals(role)) hasUser = true;
            try {
                result.put(new JSONObject().put("role", role).put("content", content));
            } catch (JSONException ignored) {
                throw malformedInput();
            }
        }
        if (!hasUser || !"user".equals(result.optJSONObject(result.length() - 1).optString("role", ""))) {
            throw malformedInput();
        }
        return result;
    }

    private static String validatedKey(String value) throws ApiException {
        if (value == null || value.length() > 512) throw new ApiException("请在设置中填写有效的API Key。");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c > 126) throw new ApiException("API Key只能包含可打印的英文字符，不能包含换行。");
        }
        String key = value.trim();
        if (key.isEmpty()) throw new ApiException("请在设置中填写有效的API Key。");
        return key;
    }

    private static int unicodeLength(String value) throws ApiException {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw malformed();
            } else if (Character.isLowSurrogate(c)) {
                throw malformed();
            } else if (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r') {
                throw malformed();
            }
            count++;
        }
        return count;
    }

    private static void check(Cancellation cancel) throws ApiException {
        if (cancel.cancelled || Thread.currentThread().isInterrupted()) {
            throw new ApiException("已取消本次解答。");
        }
    }

    private static ApiException statusError(int code) {
        if (code == 401) return new ApiException("API Key验证失败，请检查设置中的Key。");
        if (code == 402) return new ApiException("DeepSeek账户余额不足，请充值后重试。");
        if (code == 429) return new ApiException("请求过于频繁或达到额度限制，请稍后重试。");
        if (code >= 500 && code < 600) return new ApiException("DeepSeek服务暂时繁忙，请稍后重试。");
        if (code >= 300 && code < 400) return new ApiException("服务地址发生跳转，已停止连接。请更新应用后重试。");
        return new ApiException("DeepSeek未接受本次请求，请检查设置或稍后重试。");
    }

    private static ApiException malformed() {
        return new ApiException("服务未返回有效的完整解答，请稍后重试。");
    }

    private static ApiException malformedInput() {
        return new ApiException("本次问题或对话格式有误，请新建一次解答。");
    }
}
