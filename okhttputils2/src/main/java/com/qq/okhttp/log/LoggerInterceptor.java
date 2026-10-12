package com.qq.okhttp.log;

import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * OkHttp 全局日志拦截器
 * 每个请求输出一遍：请求 + 响应
 */
public class LoggerInterceptor implements Interceptor {

    public static final String TAG = "OkHttp";

    private final String tag;

    public LoggerInterceptor() {
        this(TAG);
    }

    public LoggerInterceptor(String tag) {
        this.tag = TextUtils.isEmpty(tag) ? TAG : tag;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // ========== 请求 ==========
        Log.e(tag, "┌─────────── 请求 ───────────");
        Log.e(tag, "│ " + request.method() + " " + request.url());
        Log.e(tag, "│ 参数: " + getRequestParams(request));

        // ========== 响应 ==========
        Response response = chain.proceed(request);
        ResponseBody body = response.body();

        if (body != null && isText(body.contentType())) {
            String result = body.string();
            Log.e(tag, "│ 结果: " + result);
            Log.e(tag, "└───────────────────────────");

            // ✅ 重建 body，避免消费后下游拿不到
            response = response.newBuilder()
                    .body(ResponseBody.create(body.contentType(), result))
                    .build();
        } else {
            Log.e(tag, "│ 结果: [非文本或空]");
            Log.e(tag, "└───────────────────────────");
        }

        return response;
    }

    /**
     * 获取请求参数（GET / POST 都支持）
     */
    private String getRequestParams(Request request) {
        try {
            // GET 参数
            if ("GET".equals(request.method())) {
                return request.url().query() != null ? request.url().query() : "无";
            }

            // POST 参数
            RequestBody body = request.body();
            if (body == null) return "无";

            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception e) {
            return "参数解析失败: " + e.getMessage();
        }
    }

    /**
     * 是否是文本类型
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
        );
    }
}