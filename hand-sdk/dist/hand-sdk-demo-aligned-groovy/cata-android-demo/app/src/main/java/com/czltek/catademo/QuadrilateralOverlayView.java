package com.czltek.catademo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 四边形覆盖视图
 * 用于在视频/图像上创建可调整的四边形选择区域
 */
public class QuadrilateralOverlayView extends View {
    private static final float POINT_RADIUS = 30f;      // 控制点半径
    private static final float TOUCH_TOLERANCE = 50f;   // 触摸容差

    private final Paint pointPaint;  // 控制点绘制画笔
    private final Paint linePaint;   // 线条绘制画笔
    private final Paint fillPaint;   // 填充区域画笔

    // 四边形的四个角点
    private final PointF[] points = new PointF[4];
    private int activePointIndex = -1;  // 当前激活的控制点索引

    // 图像边界范围
    private Rect imageBounds = null;

    public QuadrilateralOverlayView(Context context) {
        this(context, null);
    }

    public QuadrilateralOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuadrilateralOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 设置绘制控制点的画笔
        pointPaint = new Paint();
        pointPaint.setAntiAlias(true);
        pointPaint.setColor(Color.RED);
        pointPaint.setStyle(Paint.Style.FILL);

        // 设置绘制线条的画笔
        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setColor(Color.GREEN);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);

        // 设置填充区域的画笔（半透明黑色背景）
        fillPaint = new Paint();
        fillPaint.setAntiAlias(true);
        fillPaint.setColor(Color.argb(150, 0, 0, 0));
        fillPaint.setStyle(Paint.Style.FILL);

        // 初始化四边形点位置
        resetPoints();
    }

    /**
     * 重置四边形的控制点到默认位置
     */
    public void resetPoints() {
        // 初始化点的位置（默认情况下，如果还没有测量，使用1000作为默认值）
        float width = getWidth() > 0 ? getWidth() : 1000;
        float height = getHeight() > 0 ? getHeight() : 1000;

        // 将点初始化在距离边角20%的位置
        float insetX = width * 0.2f;
        float insetY = height * 0.2f;

        points[0] = new PointF(insetX, insetY);             // 左上
        points[1] = new PointF(width - insetX, insetY);     // 右上
        points[2] = new PointF(width - insetX, height - insetY); // 右下
        points[3] = new PointF(insetX, height - insetY);    // 左下

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 视图大小变化时重新计算点的位置
        resetPoints();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 创建四边形路径
        Path quadPath = new Path();
        quadPath.moveTo(points[0].x, points[0].y);
        quadPath.lineTo(points[1].x, points[1].y);
        quadPath.lineTo(points[2].x, points[2].y);
        quadPath.lineTo(points[3].x, points[3].y);
        quadPath.close();

        // 创建整个视图的路径
        Path backgroundPath = new Path();
        backgroundPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);

        // 使用DIFFERENCE操作使四边形区域透明
        backgroundPath.op(quadPath, Path.Op.DIFFERENCE);

        // 绘制背景（四边形区域外的部分）
        canvas.drawPath(backgroundPath, fillPaint);

        // 绘制四边形的边框
        canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, linePaint);
        canvas.drawLine(points[1].x, points[1].y, points[2].x, points[2].y, linePaint);
        canvas.drawLine(points[2].x, points[2].y, points[3].x, points[3].y, linePaint);
        canvas.drawLine(points[3].x, points[3].y, points[0].x, points[0].y, linePaint);

        // 绘制控制点
        for (PointF point : points) {
            canvas.drawCircle(point.x, point.y, POINT_RADIUS, pointPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 检查是否触摸到了控制点
                activePointIndex = findPointAt(x, y);
                return activePointIndex != -1;

            case MotionEvent.ACTION_MOVE:
                if (activePointIndex != -1) {
                    // 限制点在图像范围内移动
                    if (imageBounds != null) {
                        x = Math.max(imageBounds.left, Math.min(x, imageBounds.right));
                        y = Math.max(imageBounds.top, Math.min(y, imageBounds.bottom));
                    }

                    // 移动激活的控制点
                    points[activePointIndex].x = x;
                    points[activePointIndex].y = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointIndex = -1;
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 查找给定坐标处的控制点
     * @param x X坐标
     * @param y Y坐标
     * @return 控制点的索引，如果没有找到则返回-1
     */
    private int findPointAt(float x, float y) {
        for (int i = 0; i < points.length; i++) {
            float dx = x - points[i].x;
            float dy = y - points[i].y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= TOUCH_TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取当前四边形的控制点
     * @return 控制点数组的副本
     */
    public PointF[] getQuadPoints() {
        return points.clone();
    }

    /**
     * 设置四边形的控制点
     * @param newPoints 新的控制点数组
     */
    public void setQuadPoints(PointF[] newPoints) {
        if (newPoints != null && newPoints.length == 4) {
            System.arraycopy(newPoints, 0, points, 0, 4);
            invalidate();
        }
    }

    /**
     * 设置图像的边界，限制点的移动范围
     * @param bounds 图像的边界矩形
     */
    public void setImageBounds(Rect bounds) {
        if (bounds != null) {
            this.imageBounds = new Rect(bounds);
            // 确保现有的点都在边界内
            constrainPointsToBounds();
            invalidate();
        }
    }

    /**
     * 确保所有控制点都在图像边界内
     */
    private void constrainPointsToBounds() {
        if (imageBounds == null) return;

        for (PointF point : points) {
            point.x = Math.max(imageBounds.left, Math.min(point.x, imageBounds.right));
            point.y = Math.max(imageBounds.top, Math.min(point.y, imageBounds.bottom));
        }
    }
}
