package com.wix.reactnativenotifications.fcm;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.FirebaseApp;
import com.wix.reactnativenotifications.BuildConfig;
import com.wix.reactnativenotifications.core.JsIOHelper;

import static com.wix.reactnativenotifications.Defs.LOGTAG;
import static com.wix.reactnativenotifications.Defs.TOKEN_RECEIVED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.TOKEN_REGISTRATION_FAILED_EVENT_NAME;

public class FcmToken implements IFcmToken {

    final protected Context mAppContext;
    final protected JsIOHelper mJsIOHelper;

    protected static String sToken;

    protected FcmToken(Context appContext) {
        if (!(appContext instanceof ReactApplication)) {
            throw new IllegalStateException("Application instance isn't a react-application");
        }
        mJsIOHelper = new JsIOHelper();
        mAppContext = appContext;
    }

    public static IFcmToken get(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof INotificationsFcmApplication) {
            return ((INotificationsFcmApplication) appContext).getFcmToken(context);
        }
        return new FcmToken(appContext);
    }

    @Override
    public void onNewTokenReady() {
        synchronized (mAppContext) {
            refreshToken();
        }
    }

    @Override
    public void onManualRefresh() {
        synchronized (mAppContext) {
            if (sToken == null) {
                if(BuildConfig.DEBUG) Log.i(LOGTAG, "Manual token refresh => asking for new token");
                refreshToken();
            } else {
                if(BuildConfig.DEBUG) Log.i(LOGTAG, "Manual token refresh => publishing existing token ("+sToken+")");
                sendTokenToJS(true);
            }
        }
    }

    @Override
    public void onAppReady() {
        synchronized (mAppContext) {
            if (sToken == null) {
                if(BuildConfig.DEBUG) Log.i(LOGTAG, "App initialized => asking for new token");
                refreshToken();
            } else {
                // Except for first run, this should be the case.
                if(BuildConfig.DEBUG) Log.i(LOGTAG, "App initialized => publishing existing token ("+sToken+")");
                sendTokenToJS(true);
            }
        }
    }

    protected void refreshToken() {
        FirebaseMessaging firebaseMessaging;
        try {
            firebaseMessaging =  FirebaseApp.getInstance("messaging").get(FirebaseMessaging.class);
        } catch (Exception err) {
            firebaseMessaging = FirebaseMessaging.getInstance();
        }
        firebaseMessaging.getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                if (BuildConfig.DEBUG)
                    Log.w(LOGTAG, "Fetching FCM registration token failed", task.getException());
                sendTokenToJS(false);
                return;
            }
            sToken = task.getResult();
            if (mAppContext instanceof IFcmTokenListenerApplication) {
                ((IFcmTokenListenerApplication) mAppContext).onNewFCMToken(sToken);
            }
            if (BuildConfig.DEBUG) Log.i(LOGTAG, "FCM has a new token" + "=" + sToken);
            sendTokenToJS(true);
        });
    }

    protected void sendTokenToJS(boolean isSuccessful) {
        final ReactInstanceManager instanceManager = ((ReactApplication) mAppContext).getReactNativeHost().getReactInstanceManager();
        final ReactContext reactContext = instanceManager.getCurrentReactContext();

        // Note: Cannot assume react-context exists cause this is an async dispatched service.
        if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
            Bundle tokenMap = new Bundle();

            if (isSuccessful) {
                tokenMap.putString("deviceToken", sToken);
                mJsIOHelper.sendEventToJS(TOKEN_RECEIVED_EVENT_NAME, tokenMap, reactContext);
            }
            else {
                mJsIOHelper.sendEventToJS(TOKEN_REGISTRATION_FAILED_EVENT_NAME, tokenMap, reactContext);
            }
        }
    }
}
