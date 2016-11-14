package com.arksine.autointegrate.radio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by Eric on 11/7/2016.
 */

public class RadioMessageHandler extends Handler {

    private static final String TAG = "RadioMessageHandler";

    private Context mContext;
    private RadioController mRadioController;

    // Packet parsing vars
    private ByteBuffer mDataBuffer = ByteBuffer.allocate(256);
    private int mPacketLength = 0;
    private int mPacketBytesRecd = 0;
    private int mPacketCheckSum = 0;
    private boolean mIsEscaped = false;
    private boolean mIsLengthByte = false;
    private boolean mIsCheckSum = false;
    private boolean mPacketStarted = false;


    public RadioMessageHandler(Looper looper, Context context, RadioController rc) {
        super(looper);
        mContext =  context;
        mRadioController = rc;
    }

    @Override
    public void handleMessage(Message msg) {

        parseRadioPacket((byte[]) msg.obj);

    }

    private void parseRadioPacket(byte[] packet) {
        // TODO: Verify the parsing implementation below, also check to see if it needs to be synchronized

        /**
         * TODO: I know the following about packets
         * 1st byte is 0xA4, which is the header.
         * 2nd byte is length
         * 0x1B is escape byte, when active 0x1B is escaped as 1B, 0x48 is escaped as 0xA4.  It
         * seems that the length and data are escaped.  I haven't seen examples of the checksum
         * but I assume it is escaped as well, considering no
         *
         * The checksum is the entire packet added up, modulo\\ 256
         */
        for (byte b : packet) {
            if ((b != (byte)0xA4) && !mPacketStarted) {
                Log.i(TAG, "Received byte without a start header, discarding");
                break;
            }

            if ((b == (byte)0xA4)) {
                // If we receive 0xA4 it is the start of a new packet.  Original HDCR app had
                // logic to assume the length and checksum can also be 0xA4, but examples I have
                // seen show that the length can be escaped.  I assume that the checksum can be
                // as well, so the only time 0xA4 is received is when a new packet is beginning

                if (mPacketStarted) {
                    Log.i(TAG, "New header received during previous packet, discarding");
                }

                // Start byte is received and it isn't the length byte or the checksum
                mPacketLength = 0;
                mPacketBytesRecd = 0;
                mDataBuffer.clear();
                mPacketStarted = true;
                mIsLengthByte = true;
                mPacketCheckSum = (b & 0xFF);

            } else if (b == (byte)0x1B && !mIsEscaped) {
                // Escape byte recd
                mIsEscaped = true;

            } else if (mIsLengthByte) {
                // Length byte received

                if (mIsEscaped) {
                    if (b == (byte)0x48) {
                        b = (byte) 0xA4;
                    }
                    mIsEscaped = false;
                }

                mPacketLength = (b & 0xFF);
                mPacketCheckSum += (b & 0xFF);
                mIsLengthByte = false;

                if (mPacketLength == 0) {
                    // Received a header with an empty packet, not sure what to do
                    Log.wtf(TAG, "Packet length received is zero");
                }

            } else if (mPacketBytesRecd < mPacketLength) {
                // byte is part of the packet packet

                if (mIsEscaped) {
                    if (b == (byte)0x48) {
                        b = (byte) 0xA4;
                    }
                    mIsEscaped = false;
                }

                mDataBuffer.put(b);
                mPacketBytesRecd++;
                mPacketCheckSum += (b & 0xFF);

                if (mPacketBytesRecd == mPacketLength) {
                    // The next byte to be received will be the checksum
                    mIsCheckSum = true;
                }

            } else if (mIsCheckSum){
                // checksum received

                if (mIsEscaped) {
                    if (b == (byte)0x48) {
                        b = (byte) 0xA4;
                    }
                    mIsEscaped = false;
                }

                if ((mPacketCheckSum % 256) == (b & 0xFF)) {
                    // Packet is received and valid, send to input handler
                    byte[] data = new byte[mPacketLength];
                    mDataBuffer.get(data);
                    mRadioController.parseDataPacket(data);
                } else {
                    Log.i(TAG, "Packet Checksum is not valid, discarded");
                }
                mIsCheckSum = false;
                mPacketStarted = false;
                mPacketLength = 0;
                mPacketBytesRecd = 0;
            }
        }
    }


}
