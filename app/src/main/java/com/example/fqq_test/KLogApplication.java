package com.example.fqq_test;

import android.app.Application;

import com.socks.library.KLog;
import com.qq.okhttp.utils.MLog;

/**
 * Created by zhaokaiqiang on 15/11/14.
 */
public class KLogApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MLog.init(true);
        if (BuildConfig.DEBUG == true) {
            System.out.println("true");
            KLog.init(true);
        } else {
            KLog.init(false);
            System.out.println("false");
        }
    }
}
