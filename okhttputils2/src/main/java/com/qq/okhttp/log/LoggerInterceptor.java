package com.qq.okhttp.log;

import android.text.TextUtils;
import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/**
 * OkHttp 全局日志拦截器（增强版）
 * 特点：
 * 1. 请求和响应成对输出，带唯一 ID
 * 2. 接口失败也会打印请求信息
 * 3. 输出耗时
 * 4. 大 body 自动分段打印
 * 5. 响应流式安全（SSE 不会卡死）
 */
public class LoggerInterceptor implements Interceptor {

    public static final String TAG = "OkHttp";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long MAX_LOG_LEN = 3000L; // 单条日志最大长度，避免 Logcat 截断

    private final String tag;
    private final AtomicLong idGenerator = new AtomicLong(0);

    public LoggerInterceptor() {
        this(TAG);
    }

    public LoggerInterceptor(String tag) {
        this.tag = TextUtils.isEmpty(tag) ? TAG : tag;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long id = idGenerator.incrementAndGet();
        Request request = chain.request();
        long startNs = System.nanoTime();

        // ========== 1. 先打印请求（不管成功失败都能看到）==========
        String reqLine = buildRequestLine(request);
        String reqParams = getRequestParams(request);

        Log.e(tag, String.format("┌── #%d 请求 ──", id));
        Log.e(tag, String.format("│ %s", reqLine));
        if (!TextUtils.isEmpty(reqParams)) {
            Log.e(tag, String.format("│ 参数: %s", reqParams));
        }

        // ========== 2. 执行请求（异常也捕获）==========
        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            Log.e(tag, String.format("│ ❌ 请求失败 (%dms): %s", costMs, e.getMessage()));
            Log.e(tag, String.format("└── #%d 结束 ──", id));
            throw e; // 继续抛，让上层处理
        }

        // ========== 3. 打印响应 ==========
        long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        ResponseBody body = response.body();

        if (body != null && isText(body.contentType())) {
            // 判断是否流式（SSE），流式不能 body.string() 会卡死
            MediaType contentType = body.contentType();
            boolean isEventStream = contentType != null
                    && "text".equals(contentType.type())
                    && "event-stream".equals(contentType.subtype());

            if (isEventStream) {
                Log.e(tag, String.format("│ ✅ %d (%dms) [流式响应，不读取]", response.code(), costMs));
                Log.e(tag, String.format("└── #%d 结束 ──", id));
                return response;
            }

            // 非流式：读出来打印，再重建 body
            String result = body.string();
            Log.e(tag, String.format("│ ✅ %d (%dms)", response.code(), costMs));
            logLong(String.format("│ 结果: %s", result));
            Log.e(tag, String.format("└── #%d 结束 ──", id));

            response = response.newBuilder()
                    .body(ResponseBody.create(contentType, result))
                    .build();
        } else {
            Log.e(tag, String.format("│ ✅ %d (%dms) [非文本或空]", response.code(), costMs));
            Log.e(tag, String.format("└── #%d 结束 ──", id));
        }

        return response;
    }

    /**
     * ✅ 请求行：方法 + 完整 URL（含路径参数）
     */
    private String buildRequestLine(Request request) {
        return request.method() + " " + request.url();
    }

    /**
     * ✅ 请求参数：GET 取 query，POST 取 body，同时打印 headers
     */
    private String getRequestParams(Request request) {
        StringBuilder sb = new StringBuilder();

        // Headers
        Headers headers = request.headers();
        if (headers.size() > 0) {
            sb.append("\n│   Headers:");
            for (int i = 0; i < headers.size(); i++) {
                sb.append("\n│     ").append(headers.name(i)).append(": ").append(headers.value(i));
            }
        }

        // GET query
        if ("GET".equals(request.method())) {
            String query = request.url().query();
            if (!TextUtils.isEmpty(query)) {
                sb.append("\n│   Query: ").append(query);
            }
            return sb.toString();
        }

        // POST body
        RequestBody body = request.body();
        if (body != null) {
            try {
                Buffer buffer = new Buffer();
                body.writeTo(buffer);
                MediaType type = body.contentType();
                Charset charset = UTF8;
                if (type != null && type.charset() != null) {
                    charset = type.charset();
                }
                String bodyStr = buffer.readString(charset);
                if (!TextUtils.isEmpty(bodyStr)) {
                    sb.append("\n│   Body: ").append(bodyStr);
                }
            } catch (Exception e) {
                sb.append("\n│   Body: [解析失败] ").append(e.getMessage());
            }
        }

        return sb.toString();
    }

    /**
     * ✅ 长内容分段打印，避免被 Logcat 截断
     */
    private void logLong(String msg) {
        if (msg == null) return;
        if (msg.length() <= MAX_LOG_LEN) {
            Log.e(tag, msg);
            return;
        }
        int start = 0;
        int len = msg.length();
        int idx = 0;
        while (start < len) {
            int end = (int) Math.min(start + MAX_LOG_LEN, len);
            String part = msg.substring(start, end);
            Log.e(tag, String.format("[%d] %s", idx++, part));
            start = end;
        }
    }

    /**
     * ✅ 判断是否文本类型
     */
    private boolean isText(MediaType mediaType) {
        if (mediaType == null) return false;
        if ("text".equals(mediaType.type())) return true;
        String subtype = mediaType.subtype();
        return subtype != null && (
                subtype.equals("json")
                        || subtype.equals("xml")
                        || subtype.equals("html")
                        || subtype.equals("plain")
                        || subtype.equals("event-stream")
        );
    }
}