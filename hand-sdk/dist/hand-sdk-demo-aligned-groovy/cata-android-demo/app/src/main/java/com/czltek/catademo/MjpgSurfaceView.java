package com.czltek.catademo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

/**
 * MJPEG流视图组件
 * 用于展示MJPEG格式的视频流
 */
public class MjpgSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
  private static final String TAG = "MjpgSurfaceView";

  private MjpegViewThread thread;
  private String streamUrl;
  private boolean isRunning = false;
  private final Object threadLock = new Object(); // 线程同步锁
  private Rect currentImageRect = null; // 用于存储当前图像的位置和尺寸

  public MjpgSurfaceView(Context context) {
    super(context);
    init();
  }

  public MjpgSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public MjpgSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  /**
   * 初始化Surface视图
   */
  private void init() {
    getHolder().addCallback(this);
    setFocusable(true);
  }

  /**
   * 设置视频流URL
   *
   * @param url MJPEG流地址
   */
  public void setStreamUrl(String url) {
    this.streamUrl = url;
    synchronized (threadLock) {
      if (thread != null) {
        thread.setStreamUrl(url);
      }
    }
  }

  /**
   * 开始播放视频流
   */
  public void startStream() {
    isRunning = true;
    synchronized (threadLock) {
      if (thread != null) {
        thread.setRunning(true);
      }
    }
  }

  /**
   * 停止播放视频流
   */
  public void stopStream() {
    isRunning = false;
    synchronized (threadLock) {
      if (thread != null) {
        thread.setRunning(false);
      }
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    // 确保不会创建多个线程
    synchronized (threadLock) {
      if (thread == null || !thread.isAlive()) {
        thread = new MjpegViewThread(holder);
        if (streamUrl != null) {
          thread.setStreamUrl(streamUrl);
        }
        thread.setRunning(isRunning);
        thread.start();
      }
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Surface尺寸变化时的处理
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    // 安全地停止线程
    synchronized (threadLock) {
      if (thread != null) {
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
          try {
            thread.join(500); // 设置超时，防止阻塞
            if (thread.isAlive()) {
              thread.interrupt(); // 如果线程未能正常结束，强制中断
            } else {
              retry = false;
            }
          } catch (InterruptedException e) {
            Log.e(TAG, "中断异常: " + e.getMessage());
          }
        }
        thread = null; // 释放线程引用
      }
    }
  }

  /**
   * 获取当前显示图像的位置和尺寸
   * @return 包含图像位置和尺寸的Rect，如果没有图像则返回null
   */
  public Rect getCurrentImageRect() {
    synchronized (threadLock) {
      return currentImageRect != null ? new Rect(currentImageRect) : null;
    }
  }

  /**
   * MJPEG视图线程类
   * 负责连接流、读取帧并显示
   */
  private class MjpegViewThread extends Thread {
    private final SurfaceHolder surfaceHolder;
    private volatile boolean running = false;
    private volatile String url;
    private MjpegInputStream stream = null;
    private HttpURLConnection connection = null;

    public MjpegViewThread(SurfaceHolder holder) {
      this.surfaceHolder = holder;
    }

    public void setRunning(boolean run) {
      this.running = run;
      if (!run) {
        // 关闭流和连接
        closeConnection();
      }
    }

    public void setStreamUrl(String url) {
      if (this.url == null || !this.url.equals(url)) {
        this.url = url;
        // URL变更时关闭当前连接
        closeConnection();
      }
    }

    /**
     * 关闭连接和流资源
     */
    private void closeConnection() {
      if (stream != null) {
        try {
          stream.close();
        } catch (Exception e) {
          Log.e(TAG, "关闭流失败: " + e.getMessage());
        }
        stream = null;
      }

      if (connection != null) {
        connection.disconnect();
        connection = null;
      }
    }

    @Override
    public void run() {
      while (running) {
        try {
          // 如果流不存在且有URL，尝试连接
          if (stream == null && url != null) {
            try {
              stream = connectToStream(url);
            } catch (IOException e) {
              Log.e(TAG, "连接流失败: " + e.getMessage());
              Thread.sleep(3000); // 连接失败后等待一段时间再重试
              continue;
            }
          }

          if (stream != null) {
            try {
              Bitmap bitmap = stream.readMjpegFrame();
              if (bitmap != null && running) {
                Canvas canvas = null;
                try {
                  canvas = surfaceHolder.lockCanvas();
                  if (canvas != null) {
                    drawBitmap(canvas, bitmap);
                  }
                } finally {
                  if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                  }
                }
                bitmap.recycle(); // 及时回收Bitmap
              } else if (bitmap == null) {
                // 记录解码失败，但不中断流程
                Log.e(TAG, "解码MJPEG帧失败，跳过此帧");
                Thread.sleep(10); // 短暂停顿避免CPU过度使用
              }
            } catch (IOException e) {
              Log.e(TAG, "读取MJPEG帧失败: " + e.getMessage());
              closeConnection(); // 读取失败时关闭连接
              Thread.sleep(1000); // 等待一段时间后重试
            }
          } else {
            Thread.sleep(1000); // 无流时等待
          }
        } catch (InterruptedException e) {
          Log.d(TAG, "线程被中断");
          break; // 线程被中断时退出循环
        } catch (Exception e) {
          Log.e(TAG, "未预期的异常: " + e.getMessage(), e);
          try {
            Thread.sleep(1000); // 异常情况下等待
          } catch (InterruptedException ie) {
            break;
          }
        }
      }

      // 线程结束时清理资源
      closeConnection();
    }

    /**
     * 绘制位图到画布上
     *
     * @param canvas 画布
     * @param bitmap 要绘制的位图
     */
    private void drawBitmap(Canvas canvas, Bitmap bitmap) {
      if (bitmap == null || canvas == null) return;

      canvas.drawColor(Color.BLACK); // 清除背景

      // 计算缩放比例，保持原比例
      float scaleWidth = (float) canvas.getWidth() / bitmap.getWidth();
      float scaleHeight = (float) canvas.getHeight() / bitmap.getHeight();
      float scale = Math.min(scaleWidth, scaleHeight);

      // 计算居中位置
      int newWidth = (int) (bitmap.getWidth() * scale);
      int newHeight = (int) (bitmap.getHeight() * scale);

      int left = (canvas.getWidth() - newWidth) / 2;
      int top = (canvas.getHeight() - newHeight) / 2;

      Rect dest = new Rect(left, top, left + newWidth, top + newHeight);

      // 保存当前图像的位置和尺寸
      synchronized (threadLock) {
        currentImageRect = dest;
      }

      Paint paint = new Paint();
      paint.setFilterBitmap(true); // 使用滤波处理，提高缩放质量
      canvas.drawBitmap(bitmap, null, dest, paint);
    }

    /**
     * 连接到MJPEG流
     *
     * @param urlString 流地址
     * @return MJPEG输入流
     * @throws IOException 连接异常
     */
    private MjpegInputStream connectToStream(String urlString) throws IOException {
      closeConnection(); // 连接前先关闭旧连接

      connection = (HttpURLConnection) new URL(urlString).openConnection();
      connection.setRequestProperty("Cache-Control", "no-cache");
      connection.setUseCaches(false);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000); // 增加读取超时时间
      connection.connect();

      int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP错误码: " + responseCode);
      }

      return new MjpegInputStream(connection.getInputStream());
    }
  }

  /**
   * MJPEG流解析类
   * 用于解析MJPEG格式的数据流
   */
  private class MjpegInputStream extends DataInputStream {
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 200000 + HEADER_MAX_LENGTH;
    private final byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private final byte[] EOF_MARKER = {(byte) 0xFF, (byte) 0xD9};
    private final String CONTENT_LENGTH = "Content-Length";
    private int mContentLength = -1;

    MjpegInputStream(InputStream in) {
      super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSequence(DataInputStream in, byte[] sequence) throws IOException {
      int seqIndex = 0;
      byte c;
      for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
        c = (byte) in.readUnsignedByte();
        if (c == sequence[seqIndex]) {
          seqIndex++;
          if (seqIndex == sequence.length) {
            return i + 1;
          }
        } else {
          seqIndex = 0;
        }
      }
      return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
      int end = getEndOfSequence(in, sequence);
      return (end < 0) ? (-1) : (end - sequence.length);
    }

    private int parseContentLength(byte[] headerBytes) throws IOException, IllegalArgumentException {
      ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
      Properties props = new Properties();
      props.load(headerIn);
      return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    Bitmap readMjpegFrame() throws IOException {
      mark(FRAME_MAX_LENGTH);
      int headerLen = getStartOfSequence(this, SOI_MARKER);
      reset();
      byte[] header = new byte[headerLen];
      readFully(header);
      try {
        mContentLength = parseContentLength(header);
      } catch (IllegalArgumentException iae) {
        mContentLength = getEndOfSequence(this, EOF_MARKER);
      }
      reset();
      byte[] frameData = new byte[mContentLength];
      skipBytes(headerLen);
      readFully(frameData);
      return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }
  }
}
