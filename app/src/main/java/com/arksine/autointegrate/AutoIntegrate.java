package com.arksine.autointegrate;

import android.app.Application;

import com.arksine.autointegrate.interfaces.MCUControlInterface;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;

/**
 * Application wrapper to provide package global access to static interfaces
 */

public class AutoIntegrate extends Application {
    private static ServiceControlInterface mServiceControlInterface = null;
    private static MCUControlInterface mMcuControlInterface = null;

    public static void setServiceControlInterface(ServiceControlInterface serviceInterface) {
        mServiceControlInterface = serviceInterface;
    }

    public static void setMcuControlInterface(MCUControlInterface mcuInterface) {
        mMcuControlInterface = mcuInterface;
    }

    public static ServiceControlInterface getServiceControlInterface() {
        return mServiceControlInterface;
    }

    public static MCUControlInterface getmMcuControlInterface() {
        return mMcuControlInterface;
    }

}
