package com.skipstery.projectorcalibrator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class CalibrationGrid extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int width = 1920, height = 1080;

  CalibrationGrid(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void dimensions(int w, int h) {
    width = w;
    height = h;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.save();
    canvas.scale((float) getWidth() / width, (float) getHeight() / height);
    canvas.drawColor(Color.rgb(9, 15, 23));
    paint.setStrokeWidth(1);
    paint.setColor(Color.rgb(34, 48, 61));
    for (int x = 0; x < width; x += 40) canvas.drawLine(x, 0, x, height, paint);
    for (int y = 0; y < height; y += 40) canvas.drawLine(0, y, width, y, paint);
    paint.setColor(Color.rgb(73, 102, 122));
    canvas.drawLine(width / 2f, 0, width / 2f, height, paint);
    canvas.drawLine(0, height / 2f, width, height / 2f, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(2);
    paint.setColor(Color.rgb(96, 242, 192));
    canvas.drawRect(1, 1, width - 1, height - 1, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setTextSize(17);
    paint.setTextAlign(Paint.Align.CENTER);
    // Rulers count source pixels inward from each edge. Original view is 1:1.
    for (int n = 10; n <= 160; n += 10) {
      paint.setColor(n % 20 == 0 ? Color.rgb(211, 229, 237) : Color.rgb(88, 113, 133));
      int tick = n % 20 == 0 ? 22 : 12;
      for (float y : new float[] {height * .25f, height * .75f}) {
        canvas.drawLine(n, y - tick, n, y + tick, paint);
        canvas.drawLine(width - n, y - tick, width - n, y + tick, paint);
        if (n % 20 == 0) {
          verticalLabel(canvas, Integer.toString(n), n, y - 52);
          verticalLabel(canvas, Integer.toString(n), width - n, y - 52);
        }
      }
      for (float x : new float[] {width * .25f, width * .75f}) {
        canvas.drawLine(x - tick, n, x + tick, n, paint);
        canvas.drawLine(x - tick, height - n, x + tick, height - n, paint);
        if (n % 20 == 0) {
          canvas.drawText(Integer.toString(n), x + 43, n + 6, paint);
          canvas.drawText(Integer.toString(n), x + 43, height - n + 6, paint);
        }
      }
    }
    canvas.restore();
  }

  private void verticalLabel(Canvas canvas, String text, float x, float y) {
    canvas.save();
    canvas.translate(x, y);
    canvas.rotate(-90);
    canvas.drawText(text, 0, 5, paint);
    canvas.restore();
  }
}
