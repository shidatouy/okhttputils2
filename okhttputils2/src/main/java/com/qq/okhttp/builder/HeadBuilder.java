package com.qq.okhttp.builder;

import com.qq.okhttp.OkHttpUtils2;
import com.qq.okhttp.request.OtherRequest;
import com.qq.okhttp.request.RequestCall;

/**
 * Created by zhy on 16/3/2.
 */
public class HeadBuilder extends GetBuilder
{
    @Override
    public RequestCall build()
    {
        return new OtherRequest(null, null, OkHttpUtils2.METHOD.HEAD, url, tag, params, headers,id).build();
    }
}
