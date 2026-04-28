package com.czltek.catademo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 网络请求工具类
 * 处理与服务器的各种API交互
 */
public class Request {
  // 常量定义
  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final String TAG = "CataDemo";
  private static final String BASE_URL = "http://127.0.0.1:17339";

  // 线程和工具实例
  private static final ExecutorService executor = Executors.newCachedThreadPool();
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());
  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 通用后台任务执行方法
   * @param param 任务参数
   * @param backgroundTask 后台任务接口实现
   * @param textView 用于显示结果的文本视图
   */
  private static <T> void executeTask(T param, BackgroundTask<T, String> backgroundTask, TextView textView) {
    executor.execute(() -> {
      String result = backgroundTask.doInBackground(param);
      mainHandler.post(() -> {
        if (textView != null) {
          textView.setText(result + "\n\n" + textView.getText());
        }
      });
    });
  }

  /**
   * 后台任务接口定义
   */
  private interface BackgroundTask<P, R> {
    R doInBackground(P param);
  }

  /**
   * 获取设备注册状态
   */
  public static class GetRegister {
    public static void execute(TextView textView) {
      executeTask(textView, (TextView view) -> {
        try {
          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/register")
                  .build();

          try (Response response = client.newCall(request).execute()) {
            String responseText = response.body().string();
            ObjectNode result = objectMapper.readValue(responseText, ObjectNode.class);

            return result.hasNonNull("deviceId") ? "已注册" : "未注册";
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "注册检查失败:" + ex;
        }
      }, textView);
    }
  }

  /**
   * 注册设备
   */
  public static class PostRegister {
    public static void execute(String passphrase, TextView textView) {
      executeTask(new Pair<>(passphrase, textView), (Pair<String, TextView> pair) -> {
        try {
          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/register")
                  .post(RequestBody.create("{\"passphrase\":\"" + pair.first + "\"}", JSON))
                  .build();

          try (Response response = client.newCall(request).execute()) {
            String responseText = response.body().string();
            ObjectNode result = objectMapper.readValue(responseText, ObjectNode.class);

            return result.hasNonNull("deviceId") ? "已注册" : "注册失败";
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "注册请求失败:" + ex;
        }
      }, textView);
    }
  }

  /**
   * 获取商品识别结果
   */
  public static class GetPredictions {
    public static void execute(TextView textView) {
      executeTask(textView, (TextView view) -> {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/predict-product-simple")
                  .build();

          try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
              return "状态码: " + response.code();
            }

            String prediction = response.body().string();
            long passed = stopwatch.elapsed(TimeUnit.MILLISECONDS);

            return "识别结果: " + prediction + "\n耗时: " + passed / 1000.0 + "秒";
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "识别失败: " + ex.toString();
        }
      }, textView);
    }
  }

  /**
   * 学习商品项目
   */
  public static class LearnItem {
    public static void execute(String sku, String name, TextView textView) {
      Map<String, String> params = Map.of("sku", sku, "name", name);

      executeTask(new Pair<>(params, textView), (Pair<Map<String, String>, TextView> pair) -> {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
          String param = objectMapper.writeValueAsString(pair.first);

          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/prediction-feedback-simple")
                  .post(RequestBody.create(param, JSON))
                  .build();

          try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
              return "状态码: " + response.code();
            }

            long passed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            return "学习结果: \n耗时: " + passed / 1000.0 + "秒";
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "学习失败: " + ex;
        }
      }, textView);
    }
  }

  /**
   * 重置学习数据
   */
  public static class ResetLearning {
    public static void execute(TextView textView) {
      executeTask(textView, (TextView view) -> {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/reset-dynamic-mapping")
                  .post(RequestBody.create("", JSON))
                  .build();

          try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
              return "状态码: " + response.code();
            }

            long passed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            return "重置学习数据成功\n耗时: " + passed / 1000.0 + "秒";
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "重置失败: " + ex;
        }
      }, textView);
    }
  }

  /**
   * 设置相机裁剪区域
   */
  public static class CameraCrop {
    public static void execute(PointF[] points, TextView textView) {
      executeTask(new Pair<>(points, textView), (pair) -> {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
          // 创建包含裁剪点的数据对象
          Map<String, PointF[]> data = Map.of("points", points);
          String param = objectMapper.writeValueAsString(data);

          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/config/image-crop")
                  .post(RequestBody.create(param, JSON))
                  .build();

          try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
              return "状态码: " + response.code();
            }

            long passed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            return "图像裁剪设置成功\n耗时: " + passed / 1000.0 + "秒";
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "图像裁剪失败: " + ex;
        }
      }, textView);
    }
  }

  /**
   * 导出学习数据
   */
  public static class ExportLearning {
    public static void execute(TextView textView) {
      executeTask(textView, (TextView view) -> {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
          OkHttpClient client = new OkHttpClient();
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/dynamic-mappings/export/metadata")
                  .build();

          try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
              return "状态码: " + response.code();
            }

            // 创建带时间戳的文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = "metadata_" + timestamp + ".bin";

            // 获取下载目录
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File outputFile = new File(downloadsDir, fileName);

            // 保存文件
            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {

              byte[] buffer = new byte[4096];
              int bytesRead;
              long totalBytesRead = 0;

              while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
              }

              long passed = stopwatch.elapsed(TimeUnit.MILLISECONDS);

              return "元数据已下载到: " + outputFile.getAbsolutePath() +
                      "\n文件大小: " + totalBytesRead / 1024 + " KB" +
                      "\n耗时: " + passed / 1000.0 + "秒";
            }
          }
        } catch (Exception ex) {
          Log.d(TAG, ex.toString());
          return "导出失败: " + ex;
        }
      }, textView);
    }
  }

  /**
   * 导入学习数据
   */
  public static class ImportLearning {
    private static final int FILE_PICKER_REQUEST_CODE = 123;

    /**
     * 启动文件选择器
     */
    public static void launchFilePicker(Activity activity) {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("*/*");  // 允许所有文件类型

      // 尝试从下载文件夹开始浏览
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        Uri downloadsUri = Uri.parse(downloadsDir.getAbsolutePath());
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri);
      }

      activity.startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    /**
     * 处理选中的文件
     */
    public static void handleFileSelection(Context context, Uri fileUri, TextView textView) {
      executeTask(new Pair<>(context, fileUri), (Pair<Context, Uri> pair) -> {
        Context ctx = pair.first;
        Uri uri = pair.second;
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
          // 获取文件元数据
          String fileName = getFileName(ctx, uri);

          // 读取文件内容
          byte[] fileData;
          try (InputStream inputStream = ctx.getContentResolver().openInputStream(uri);
               ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {

            if (inputStream == null) {
              return "无法读取所选文件";
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
              byteBuffer.write(buffer, 0, bytesRead);
            }
            fileData = byteBuffer.toByteArray();
          }

          long fileSize = fileData.length;

          // 创建支持较长超时的客户端
          OkHttpClient client = new OkHttpClient.Builder()
                  .writeTimeout(60, TimeUnit.SECONDS)
                  .readTimeout(60, TimeUnit.SECONDS)
                  .build();

          // 创建文件请求体
          RequestBody fileRequestBody = RequestBody.create(
                  fileData,
                  MediaType.parse("application/octet-stream")
          );

          // 创建多部分表单请求
          okhttp3.MultipartBody.Builder multipartBuilder = new okhttp3.MultipartBody.Builder()
                  .setType(okhttp3.MultipartBody.FORM)
                  .addFormDataPart("file", fileName, fileRequestBody);

          // 创建并执行请求
          okhttp3.Request request = new okhttp3.Request.Builder()
                  .url(BASE_URL + "/dynamic-mappings/import/metadata")
                  .post(multipartBuilder.build())
                  .build();

          try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
              return "导入失败，状态码: " + response.code() +
                      "\n响应: " + (response.body() != null ? response.body().string() : "无响应内容");
            }

            long passed = stopwatch.elapsed(TimeUnit.MILLISECONDS);

            return "元数据已导入成功\n" +
                    "文件: " + fileName + "\n" +
                    "大小: " + fileSize / 1024 + " KB\n" +
                    "耗时: " + passed / 1000.0 + "秒";
          }
        } catch (Exception ex) {
          Log.d(TAG, "导入错误", ex);
          return "导入失败: " + ex.getMessage();
        }
      }, textView);
    }

    /**
     * 从URI获取文件名
     */
    private static String getFileName(Context context, Uri uri) {
      String result = "unknown_file";
      if (uri.getScheme().equals("content")) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
          if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex != -1) {
              result = cursor.getString(nameIndex);
            }
          }
        } catch (Exception e) {
          Log.e(TAG, "获取文件名出错", e);
        }
      } else if (uri.getScheme().equals("file")) {
        result = new File(uri.getPath()).getName();
      }
      return result;
    }
  }
}

