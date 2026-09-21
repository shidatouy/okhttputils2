package com.qq.okhttp.log;

import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * OkHttp 日志拦截器
 * 支持分别控制请求/响应日志
 */
public class LoggerInterceptor implements Interceptor {

    public static final String TAG = "OkHttpUtils2";

    private final String tag;
    private final boolean showRequest;   // 是否打印请求
    private final boolean showResponse;  // 是否打印响应

    public LoggerInterceptor(String tag, boolean showRequest, boolean showResponse) {
        this.tag = TextUtils.isEmpty(tag) ? TAG : tag;
        this.showRequest = showRequest;
        this.showResponse = showResponse;
    }

    public LoggerInterceptor(String tag) {
        this(tag, true, true);
    }

    public LoggerInterceptor() {
        this(TAG, true, true);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        if (showRequest) {
            logForRequest(request);
        }

        Response response = chain.proceed(request);

        if (showResponse) {
            response = logForResponse(response);
        }

        return response;
    }

    // ==================== 请求日志 ====================

    private void logForRequest(Request request) {
        try {
            Log.e(tag, "========request'log=======");
            Log.e(tag, "method  : " + request.method());
            Log.e(tag, "url     : " + request.url());

            Headers headers = request.headers();
            if (headers.size() > 0) {
                Log.e(tag, "headers : " + headers);
            }

            RequestBody requestBody = request.body();
            if (requestBody != null) {
                MediaType mediaType = requestBody.contentType();
                if (mediaType != null) {
                    Log.e(tag, "contentType : " + mediaType);
                    if (isText(mediaType)) {
                        Log.e(tag, "body : " + bodyToString(request));
                    } else {
                        Log.e(tag, "body : maybe [file part], too large to print, ignored!");
                    }
                }
            }

            Log.e(tag, "========request'log=======end");
        } catch (Exception e) {
            Log.e(tag, "logForRequest error: " + e.getMessage());
        }
    }

    // ==================== 响应日志 ====================

    private Response logForResponse(Response response) {
        try {
            Log.e(tag, "========response'log=======");
            Log.e(tag, "url      : " + response.request().url());
            Log.e(tag, "code     : " + response.code());
            Log.e(tag, "protocol : " + response.protocol());

            if (!TextUtils.isEmpty(response.message())) {
                Log.e(tag, "message  : " + response.message());
            }

            ResponseBody body = response.body();
            if (body != null) {
                MediaType mediaType = body.contentType();
                if (mediaType != null) {
                    Log.e(tag, "contentType : " + mediaType);

                    if (isText(mediaType)) {
                        String resp = body.string();
                        Log.e(tag, "body : " + resp);

                        // ✅ 重建 body，避免消费后下游拿不到
                        ResponseBody newBody = ResponseBody.create(mediaType, resp);
                        response = response.newBuilder().body(newBody).build();
                    } else {
                        Log.e(tag, "body : maybe [file part], too large to print, ignored!");
                    }
                }
            }

            Log.e(tag, "========response'log=======end");
        } catch (Exception e) {
            Log.e(tag, "logForResponse error: " + e.getMessage());
        }

        return response;
    }

    // ==================== 工具方法 ====================

    private boolean isText(MediaType mediaType) {
        if (mediaType == null) return false;

        if ("text".equals(mediaType.type())) {
            return true;
        }

        String subtype = mediaType.subtype();
        if (subtype != null) {
            return subtype.equals("json")
                    || subtype.equals("xml")
                    || subtype.equals("html")
                    || subtype.equals("webviewhtml")
                    || subtype.equals("plain");
        }
        return false;
    }

    private String bodyToString(Request request) {
        try {
            Request copy = request.newBuilder().build();
            Buffer buffer = new Buffer();
            if (copy.body() == null) return "";
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            return "something error when show requestBody.";
        }
    }
}