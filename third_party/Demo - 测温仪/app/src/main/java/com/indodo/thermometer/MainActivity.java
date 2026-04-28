package com.indodo.thermometer;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView txt_temperature;
    private SerialUtil serialUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txt_temperature = findViewById(R.id.txt_temperature);

        // 获取串口单例
        serialUtil = SerialUtil.getInstance();

        // 设置温度数据回调（更新TextView）
        serialUtil.setOnTemperatureDataListener(new SerialUtil.OnTemperatureDataListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onTemperatureReceived(float temperature, String originalStr) {
                // 主线程更新UI（回调已在主线程执行，若不在则需用runOnUiThread）
                runOnUiThread(() -> {
                    txt_temperature.setText("当前温度：" + temperature + "℃");
                    Log.d("MainActivity", "更新温度显示：" + originalStr);
                });
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onTemperatureError(String errorMsg) {
                runOnUiThread(() -> {
                    txt_temperature.setText("温度解析错误：" + errorMsg);
                });
            }
        });

        // 打开串口（自动启动监听）
        boolean isOpen = serialUtil.open();
        if (!isOpen) {
            txt_temperature.setText("串口打开失败");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭串口，释放资源
        if (serialUtil != null) {
            serialUtil.close();
        }
    }
}