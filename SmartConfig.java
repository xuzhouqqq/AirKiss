package com.elzj.camera.util.airkiss;

import android.os.AsyncTask;
import android.util.Log;
import com.xuanyuanxing.utils.Utils;

import java.io.IOException;
import java.net.*;

/**
 * Created by IntelliJ IDEA.
 * User: xuzhou
 * Date: 2019/3/8
 * Time: 10:00
 */
public class SmartConfig {

    private SmartConfigCallBack smartConfigCallBack;

    private AirKissTask airKissTask;
    private DatagramSocket udpServerSocket;
    /**
     * 是否发送数据
     */
    private boolean isSend = false;
    /**
     * 是否接受数据
     */
    private boolean isReceive = false;
    /**
     * @param port 端口
     */
    private int port; //10000

    public SmartConfig(int port, SmartConfigCallBack smartConfigCallBack) {
        this.port = port;
        this.smartConfigCallBack = smartConfigCallBack;
    }

    /**
     * 开始发送数据
     *
     * @param ssid
     * @param password
     */
    public void startSend(String ssid, String password) {
        //创建一个Airkiss线程，创建AirKiss解码器
        airKissTask = new AirKissTask(new AirKissEncoder(ssid, password));
        Log.d("wifi---->", ssid + ":" + password);
        //执行
        airKissTask.execute();
    }

    /**
     * 停止发送数据
     */
    public void stopSend() {
        //如果异步任务不为空 并且状态是 运行时  ，就把他取消这个加载任务
        if (airKissTask != null && airKissTask.getStatus() == AsyncTask.Status.RUNNING) {
            airKissTask.cancel(true);
        }
        isSend = false;
        isReceive = false;
        if (udpServerSocket != null) {
            udpServerSocket.close();
        }
    }


    private class AirKissTask extends AsyncTask<Void, Void, Void> {
        private final byte[] DUMMY_DATA = new byte[1500];
        private static final int REPLY_BYTE_CONFIRM_TIMES = 5;
        private DatagramSocket mSocket;
        private char mRandomChar;
        private AirKissEncoder mAirKissEncoder;

        private volatile boolean mDone = false;

        public AirKissTask(AirKissEncoder encoder) {
            mRandomChar = encoder.getRandomChar();
            mAirKissEncoder = encoder;
        }

        /**
         * 异步任务调用前
         */
        @Override
        protected void onPreExecute() {
            smartConfigCallBack.SmartConfigSendBefore();
            isSend = true;
            isReceive = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[15000];
                    try {
                        udpServerSocket = new DatagramSocket(port);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        int replyByteCounter = 0;
                        //udpServerSocket.setSoTimeout(3000);
                        String deviceNo = null, uid = null, ip = null;
                        while (isReceive) {
                            try {
                                udpServerSocket.receive(packet);
                                byte[] receivedData = packet.getData();
                                if (receivedData[0] == mRandomChar && receivedData.length >= 40) {
                                    Log.e("data", Utils.byteToHexString(receivedData, " "));
                                    byte[] ipBuff = new byte[16];
                                    System.arraycopy(receivedData, 1, ipBuff, 0, ipBuff.length);
                                    byte[] deviceNoBuff = new byte[8];
                                    System.arraycopy(receivedData, 17, deviceNoBuff, 0, deviceNoBuff.length);
                                    byte[] uidBuff = new byte[20];
                                    System.arraycopy(receivedData, 25, uidBuff, 0, uidBuff.length);
                                    deviceNo = Utils.bytesToHexString(deviceNoBuff);
                                    ip = Utils.toString(ipBuff);
                                    uid = new String(uidBuff);
                                    Log.e(" ip:", ip + " deviceNo:" + deviceNo + " uid:" + uid);
                                    replyByteCounter++;
                                }
                                if (replyByteCounter >= REPLY_BYTE_CONFIRM_TIMES) {
                                    mDone = true;
                                    isSend = false;
                                    smartConfigCallBack.SmartConfigCallSuccess(deviceNo, uid, ip);
                                    break;
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        udpServerSocket.close();
                        udpServerSocket = null;
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        private void sendPacketAndSleep(int length) {
            try {
                DatagramPacket pkg = new DatagramPacket(DUMMY_DATA, length, InetAddress.getByName("255.255.255.255"), port);
                mSocket.send(pkg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                int count = 0;
                mSocket = new DatagramSocket();
                mSocket.setBroadcast(true);
                while (isSend) {
                    int[] encoded_data = mAirKissEncoder.getEncodedData();
                    for (int i = 0; i < encoded_data.length; ++i) {
                        try {
                            if (mDone) {
                                break;
                            }
                            sendPacketAndSleep(encoded_data[i]);
                            if (i % 8 == 0) {
                                Thread.sleep(4);
                            }
                            if (i % 200 == 0) {
                                if (isCancelled() || mDone) {
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    count++;
                    //每次发完一轮回调一下
                    smartConfigCallBack.SmartConfigSendFinish(count);
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public interface SmartConfigCallBack {

        /**
         * 数据发送之前
         */
        void SmartConfigSendBefore();

        /**
         * 发送一轮数据完成
         *
         * @param count 已经发了几轮了
         */
        void SmartConfigSendFinish(int count);

        /**
         * 成功的回调
         *
         * @param deviceNo 设备号
         * @param uid      摄像头ID
         * @param ip       ip地址
         */
        void SmartConfigCallSuccess(String deviceNo, String uid, String ip);
    }
}
