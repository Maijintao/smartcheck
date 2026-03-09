package com.indodo.thermometer;

import android.annotation.SuppressLint;
import android.serialport.SerialPort;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 串口工具类（单例模式）
 * 核心功能：
 * 1. 打开/关闭串口
 * 2. 监听串口温度数据 {XX.XX} 格式
 * 3. 温度数据回调（供主线程更新UI）
 */
public class SerialUtil {
    // 单例实例（双重校验锁）
    private static volatile SerialUtil INSTANCE;

    // 串口配置
    private final String port = "/dev/ttyS7";
    private final int baudrate = 115200;
    private SerialPort serialPort;

    // 日志开关
    private final boolean LOG_DEBUG = true;
    // 监听线程相关
    private ReadThread readThread;
    private boolean isReading = false;

    // 温度数据回调
    private OnTemperatureDataListener temperatureListener;

    // 私有构造函数（禁止外部实例化）
    private SerialUtil() {}

    /**
     * 获取单例实例
     */
    public static SerialUtil getInstance() {
        if (INSTANCE == null) {
            synchronized (SerialUtil.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SerialUtil();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 打开串口并启动监听线程
     * @return 打开成功返回true，失败返回false
     */
    public boolean open() {
        // 已打开则先关闭
        if (serialPort != null) {
            close();
        }

        try {
            serialPort = new SerialPort(new File(port), baudrate);
            logTxt("串口打开成功：" + port + " 波特率：" + baudrate);
            // 启动温度数据监听线程
            startReadThread();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            logTxt("串口打开失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 关闭串口并停止监听线程
     */
    public void close() {
        // 停止监听线程
        stopReadThread();

        // 关闭串口
        if (serialPort != null) {
            serialPort.tryClose();
            serialPort = null;
            logTxt("串口已关闭");
        }
    }

    /**
     * 启动串口数据监听线程
     */
    private void startReadThread() {
        if (readThread == null || !readThread.isAlive()) {
            isReading = true;
            readThread = new ReadThread();
            readThread.start();
            logTxt("温度数据监听线程已启动");
        }
    }

    /**
     * 停止串口数据监听线程
     */
    private void stopReadThread() {
        isReading = false;
        if (readThread != null) {
            try {
                readThread.interrupt();
                readThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                logTxt("停止监听线程异常：" + e.getMessage());
            }
            readThread = null;
            logTxt("温度数据监听线程已停止");
        }
    }

    /**
     * 串口温度数据监听线程
     * 解析格式：{XX.XX}{37.59}{36.69} 等温度数据
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            if (serialPort == null) {
                logTxt("串口未初始化，监听线程退出");
                return;
            }

            InputStream inputStream = serialPort.getInputStream();
            byte[] buffer = new byte[1024];
            int len;
            // 拼接完整数据（处理分包）
            StringBuilder receiveBuffer = new StringBuilder();

            while (isReading && !isInterrupted()) {
                try {
                    if (inputStream.available() > 0) {
                        len = inputStream.read(buffer);
                        if (len > 0) {
                            // 转换为ASCII字符串（适配单片机数据格式）
                            String rawData = new String(buffer, 0, len, StandardCharsets.US_ASCII);
                            receiveBuffer.append(rawData);
                            logTxt("原始接收数据：" + receiveBuffer.toString());

                            // 解析所有{XX.XX}格式的温度数据
                            parseTemperatureData(receiveBuffer.toString());

                            // 清空已解析完成的缓冲区（保留未完成的片段，比如"{36."）
                            clearParsedBuffer(receiveBuffer);
                        }
                    } else {
                        // 无数据时休眠，降低CPU占用
                        Thread.sleep(50);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    logTxt("读取串口数据异常：" + e.getMessage());
                    break;
                } catch (InterruptedException e) {
                    logTxt("监听线程被中断");
                    break;
                }
            }
        }
    }

    /**
     * 解析{XX.XX}格式的温度数据
     * @param rawData 原始接收数据
     */
    private void parseTemperatureData(String rawData) {
        int startIndex;
        int endIndex = 0;

        // 循环解析所有符合{XX.XX}格式的温度数据
        while ((startIndex = rawData.indexOf("{", endIndex)) != -1) {
            endIndex = rawData.indexOf("}", startIndex);
            // 确保有完整的闭合括号，且长度符合{XX.XX}格式（长度7）
            if (endIndex != -1 && (endIndex - startIndex + 1) == 7) {
                String tempStr = rawData.substring(startIndex, endIndex + 1);
                // 提取纯数字部分（去掉{}）
                String tempValue = tempStr.replace("{", "").replace("}", "");

                // 日志输出温度数据
                logTxt("解析到温度数据：" + tempStr + " | 数值：" + tempValue);

                // 回调给主线程（更新TextView）
                if (temperatureListener != null) {
                    try {
                        // 转换为浮点型数值，方便计算/显示
                        float temperature = Float.parseFloat(tempValue);
                        temperatureListener.onTemperatureReceived(temperature, tempStr);
                    } catch (NumberFormatException e) {
                        logTxt("温度数据格式错误：" + tempValue);
                        temperatureListener.onTemperatureError("数据格式错误：" + tempStr);
                    }
                }
            }
        }
    }

    /**
     * 清空已解析完成的缓冲区，保留未完成的片段（比如"{36."）
     * @param buffer 接收缓冲区
     */
    private void clearParsedBuffer(StringBuilder buffer) {
        // 找到最后一个闭合括号的位置
        int lastEndIndex = buffer.lastIndexOf("}");
        if (lastEndIndex != -1) {
            // 保留最后一个闭合括号之后的内容（可能是未完成的{XX.）
            String remain = buffer.substring(lastEndIndex + 1);
            buffer.setLength(0);
            buffer.append(remain);
        }
    }

    /**
     * 设置温度数据回调监听
     * @param listener 回调接口实例（主线程实现）
     */
    public void setOnTemperatureDataListener(OnTemperatureDataListener listener) {
        this.temperatureListener = listener;
    }

    /**
     * 温度数据回调接口
     * 供主线程实现，用于更新TextView等UI操作
     */
    public interface OnTemperatureDataListener {
        /**
         * 接收到有效温度数据
         * @param temperature 浮点型温度值（如36.59）
         * @param originalStr 原始格式（如{36.59}）
         */
        void onTemperatureReceived(float temperature, String originalStr);

        /**
         * 温度数据解析错误
         * @param errorMsg 错误信息
         */
        void onTemperatureError(String errorMsg);
    }

    /**
     * 日志输出封装
     */
    private void logTxt(String msg) {
        if (LOG_DEBUG) {
            Log.d("SerialUtil", msg);
        }
    }
}