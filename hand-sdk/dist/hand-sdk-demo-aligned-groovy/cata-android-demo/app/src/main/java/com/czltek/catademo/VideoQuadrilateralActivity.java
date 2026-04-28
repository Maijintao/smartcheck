package com.czltek.catademo;

import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.czltek.catademo.databinding.VideoQuadrilateralLayoutBinding;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 视频四边形裁剪活动
 * 用于设置摄像头画面的裁剪区域
 */
public class VideoQuadrilateralActivity extends AppCompatActivity {

  private static final String TAG = "VideoQuadActivity";
  private VideoQuadrilateralLayoutBinding binding;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable boundsCheckRunnable;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    binding = VideoQuadrilateralLayoutBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    // 配置MJPG流地址
    binding.mjpgView.setStreamUrl("http://127.0.0.1:17339/camera-images?stream=true");

    // 创建一个定期检查图像边界的Runnable
    boundsCheckRunnable = new Runnable() {
      @Override
      public void run() {
        updateQuadrilateralBounds();
        // 每500ms检查一次边界更新
        handler.postDelayed(this, 500);
      }
    };

    // 启动视频流
    binding.mjpgView.startStream();

    // 返回按钮
    binding.btnBack.setOnClickListener(view -> finish());

    // 提交按钮 - 保存裁剪区域设置
    binding.btnSubmit.setOnClickListener(view -> {
      submitCropArea();
    });
  }

  /**
   * 提交裁剪区域设置
   */
  private void submitCropArea() {
    // 获取四边形的四个顶点
    PointF[] quadPoints = binding.quadrilateralView.getQuadPoints();
    // 获取当前图像在SurfaceView中的位置和尺寸
    Rect imageRect = binding.mjpgView.getCurrentImageRect();

    if (imageRect != null && quadPoints != null) {
      // 计算归一化坐标（相对于图像）
      PointF[] normalizedPoints = new PointF[4];

      for (int i = 0; i < 4; i++) {
        // 计算点相对于图像区域的归一化坐标 (0.0-1.0)
        float normalizedX = (quadPoints[i].x - imageRect.left) / imageRect.width();
        float normalizedY = (quadPoints[i].y - imageRect.top) / imageRect.height();
        normalizedPoints[i] = new PointF(normalizedX, normalizedY);

        Log.d(TAG, "点 " + i + ": (" + normalizedX + ", " + normalizedY + ")");
      }

      // 发送裁剪坐标到服务器
      Request.CameraCrop.execute(normalizedPoints, null);
    } else {
      Toast.makeText(this, "无法获取图像或四边形坐标", Toast.LENGTH_SHORT).show();
    }

    finish();
  }

  /**
   * 更新四边形视图的边界限制
   */
  private void updateQuadrilateralBounds() {
    Rect imageRect = binding.mjpgView.getCurrentImageRect();
    if (imageRect != null) {
      binding.quadrilateralView.setImageBounds(imageRect);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    // 停止边界检查
    handler.removeCallbacks(boundsCheckRunnable);
    // 暂停视频流
    binding.mjpgView.stopStream();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // 恢复视频流
    binding.mjpgView.startStream();
    // 启动边界检查
    handler.post(boundsCheckRunnable);
  }
}
