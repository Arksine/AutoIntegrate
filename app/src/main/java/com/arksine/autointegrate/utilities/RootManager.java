package com.arksine.autointegrate.utilities;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.chainfire.libsuperuser.Shell;
import timber.log.Timber;

/**
 * A singleton class that manages basic root functionality
 */

public class RootManager {
    private final static Object ROOTLOCK = new Object();
    private static AtomicBoolean mIsRootAvailable = new AtomicBoolean(false);
    private static AtomicBoolean mInitialized = new AtomicBoolean(false);
    private static AtomicBoolean mIsWaiting = new AtomicBoolean(false);
    private static Thread mCheckSuThread;

    public interface RootCallback {
        void OnRootInitialized(boolean rootStatus);
    }

    public interface SuFinishedCallback {
        void onSuComplete(List<String> output);
    }


    // TODO: gives error on startup, should do this manually

    private RootManager() {}

    public static void initSuperUser() {
        mCheckSuThread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (ROOTLOCK) {
                    mIsRootAvailable.set(Shell.SU.available());
                    mInitialized.set(true);
                    Timber.i("Root availability status: %b", mIsRootAvailable);
                    if (mIsWaiting.compareAndSet(true, false)) {
                        ROOTLOCK.notify();
                    }
                }

            }
        });
        mCheckSuThread.start();
    }


    public static boolean isInitialized() {
        return mInitialized.get();
    }

    public static boolean isRootAvailable() {

        return mIsRootAvailable.get();
    }

    public static void checkRootWithCallback(final RootCallback cb) {
        Thread waitForInitThread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (ROOTLOCK) {
                    if (!mInitialized.get()) {
                        try {
                            mIsWaiting.set(true);
                            ROOTLOCK.wait(10000);
                        } catch (InterruptedException e) {
                            Timber.v(e);
                        } finally {
                            if (mIsWaiting.compareAndSet(true, false)) {
                                Timber.w("Error initializing root, wait timed out");
                                if (mCheckSuThread != null) {
                                    mCheckSuThread.interrupt();
                                }
                            }
                        }
                    }
                }

                cb.OnRootInitialized(mIsRootAvailable.get());
            }
        });
        waitForInitThread.start();

    }

    public static void runCommand(final String command) {
        if (mIsRootAvailable.get()) {
            Thread suThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Shell.SU.run(command);
                }
            });
            suThread.start();
        }
    }

    public static void runCommand(final String command, final SuFinishedCallback outputCb) {
        if (mIsRootAvailable.get()) {
            Thread suThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    List<String> output = Shell.SU.run(command);
                    if (outputCb != null) {
                        outputCb.onSuComplete(output);
                    }
                }
            });
            suThread.start();
        }
    }


    public static void runCommand(final String[] commands) {
        if (mIsRootAvailable.get()) {
            Thread suThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Shell.SU.run(commands);
                }
            });
            suThread.start();
        }
    }


    public static void runCommand(final String[] commands, final SuFinishedCallback outputCb) {
        if (mIsRootAvailable.get()) {
            Thread suThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    List<String> output = Shell.SU.run(commands);
                    if (outputCb != null) {
                        outputCb.onSuComplete(output);
                    }
                }
            });
            suThread.start();
        }
    }
}
