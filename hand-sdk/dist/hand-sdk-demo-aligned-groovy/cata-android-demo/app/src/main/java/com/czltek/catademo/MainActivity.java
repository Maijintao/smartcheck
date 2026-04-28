package com.czltek.catademo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.czltek.cataandroid.CataAndroid;
import com.czltek.catademo.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

  private ActivityMainBinding binding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.requestPermission();

    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    // 设置根视图的触摸监听器，点击非输入框区域时隐藏键盘
    binding.getRoot().setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        hideKeyboardAndClearFocus();
      }
      return false; // 返回false以便其他点击事件仍可正常工作
    });

    Request.GetRegister.execute(binding.logTextArea);

    binding.startButton.setOnClickListener((view) -> {
      CataAndroid.startService(this);

      for (int i = 0; i < 50; i++) {
        if (CataAndroid.isStarted()) {
          binding.startButton.setText(R.string.button_started);
          binding.logTextArea.setText(R.string.button_started + "\n" + binding.logTextArea.getText());
          return;
        }
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      binding.startButton.setText(R.string.button_started_failed);
      binding.logTextArea.setText(R.string.button_started_failed + "\n" + binding.logTextArea.getText());
    });

    binding.activeButton.setOnClickListener((view) -> {
      Request.PostRegister.execute(binding.inputPasspharse.getText().toString(), binding.logTextArea);
    });

    binding.predictButton.setOnClickListener((view) -> {
      Request.GetPredictions.execute(binding.logTextArea);
    });

    binding.groupLearn1.setOnClickListener((view) -> {
      Request.LearnItem.execute("1234", "苹果", binding.logTextArea);
    });
    binding.groupLearn2.setOnClickListener((view) -> {
      Request.LearnItem.execute("2222", "草莓", binding.logTextArea);
    });
    binding.groupLearn3.setOnClickListener((view) -> {
      Request.LearnItem.execute("3333", "香蕉", binding.logTextArea);
    });

    binding.gridButton1.setOnClickListener((view) -> {
      Request.ResetLearning.execute(binding.logTextArea);
    });

    binding.gridButton2.setOnClickListener((view) -> {
      Request.ExportLearning.execute(binding.logTextArea);
    });

    binding.gridButton3.setOnClickListener((view) -> {
      Request.ImportLearning.launchFilePicker(this);
    });

    binding.gridButton4.setOnClickListener(v -> {
      Intent intent = new Intent(MainActivity.this, VideoQuadrilateralActivity.class);
      startActivity(intent);
    });
  }

  /**
   * 隐藏软键盘并清除当前焦点
   */
  private void hideKeyboardAndClearFocus() {
    View currentFocusView = getCurrentFocus();
    if (currentFocusView != null) {
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);

      // 如果当前焦点是EditText，清除它的焦点
      if (currentFocusView instanceof EditText) {
        currentFocusView.clearFocus();
      }
    }
  }

  public void requestPermission() {
    String[] permissions = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,  // 访问所有文件权限（Android 11及以上）
            Manifest.permission.CAMERA                   // 相机权限
    };
    ActivityCompat.requestPermissions(this, permissions, 1);

    // Android 11(R)及以上版本需要特殊处理文件管理权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, 1);  // 去除this前缀，简化代码
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    // 处理文件选择器的返回结果
    if (requestCode == 123 && resultCode == RESULT_OK && data != null && data.getData() != null) {
      Uri selectedFileUri = data.getData();

      Request.ImportLearning.handleFileSelection(this, selectedFileUri, binding.logTextArea);
    }
  }
}