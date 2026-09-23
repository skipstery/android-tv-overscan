package com.skipstery.projectorcalibrator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public final class MainActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final TextView[] rows = new TextView[4];
  private final String[] names = {"Left", "Right", "Top", "Bottom"};
  private Insets value = Insets.full(1920, 1080);
  private boolean original = false, active = false, reapplyOnResume = true;
  private LinearLayout panel;
  private TextView status, dimensions;
  private Button preview;
  private CalibrationGrid grid;
  private ProfileStore profiles;
  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          if (!active) return;
          try {
            if (!BridgeClient.connected())
              throw new IllegalStateException(
                  "Run projector_calibrator.py on your computer to connect");
            value = BridgeClient.transact(null, true);
            if (reapplyOnResume) {
              value = BridgeClient.transact(value, true);
              reapplyOnResume = false;
            }
            render();
            status.setVisibility(View.GONE);
          } catch (Exception error) {
            showError(error);
          }
          handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    profiles = new ProfileStore(this);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    FrameLayout root = new FrameLayout(this);
    grid = new CalibrationGrid(this);
    root.addView(grid);
    panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(22), dp(16), dp(22), dp(16));
    panel.setBackground(background(Color.rgb(16, 25, 35), Color.rgb(52, 70, 84)));
    FrameLayout.LayoutParams box =
        new FrameLayout.LayoutParams(
            dp(490), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    root.addView(panel, box);
    TextView title = label("Projector Calibrator", 23);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    panel.addView(title);
    dimensions = label("Connecting to display...", 13);
    panel.addView(dimensions);
    dimensions.setPadding(0, 0, 0, dp(14));
    for (int i = 0; i < 4; i++) {
      final int edge = i;
      TextView row = label("", 17);
      rows[i] = row;
      row.setId(View.generateViewId());
      row.setTypeface(Typeface.MONOSPACE);
      row.setFocusable(true);
      row.setFocusableInTouchMode(true);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(14), 0, dp(14), 0);
      row.setBackground(background(Color.rgb(25, 37, 50), Color.TRANSPARENT));
      row.setOnFocusChangeListener(
          (view, focused) ->
              row.setBackground(
                  background(
                      focused ? Color.rgb(37, 73, 76) : Color.rgb(25, 37, 50),
                      focused ? Color.rgb(96, 242, 192) : Color.TRANSPARENT)));
      row.setOnKeyListener(
          (view, key, event) -> {
            if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT)
              return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN)
              change(value.adjust(edge, key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
            return true;
          });
      LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(39));
      layout.bottomMargin = dp(5);
      panel.addView(row, layout);
    }
    for (int i = 0; i < 4; i++) {
      rows[i].setNextFocusUpId(rows[Math.max(0, i - 1)].getId());
      if (i < 3) rows[i].setNextFocusDownId(rows[i + 1].getId());
    }
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    panel.addView(actions);
    Button onlyGrid = button(actions, "Grid only");
    onlyGrid.setOnClickListener(v -> panel.setVisibility(View.GONE));
    preview = button(actions, "Show original");
    preview.setOnClickListener(
        v -> {
          original = !original;
          apply(false);
        });
    Button reset = button(actions, "Reset");
    reset.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("Reset all margins?")
                .setMessage("This restores the full picture and saves zero margins.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Reset", (d, w) -> change(Insets.full(value.width, value.height)))
                .show());
    Button done = button(actions, "Done");
    done.setBackgroundTintList(
        new ColorStateList(
            new int[][] {
              new int[] {android.R.attr.state_focused},
              new int[] {android.R.attr.state_pressed},
              new int[] {}
            },
            new int[] {
              Color.rgb(190, 255, 231), Color.rgb(155, 255, 219), Color.rgb(96, 242, 192)
            }));
    done.setTextColor(Color.rgb(9, 30, 26));
    done.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    done.setOnClickListener(
        v -> {
          original = false;
          if (apply(true)) {
            startActivity(
                new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finishAndRemoveTask();
          }
        });
    rows[3].setNextFocusDownId(done.getId());
    status = label("Run projector_calibrator.py on your computer to connect", 12);
    status.setPadding(0, dp(7), 0, 0);
    panel.addView(status);
    setContentView(root);
    rows[0].requestFocus();
  }

  private void change(Insets next) {
    value = next;
    original = false;
    apply(true);
  }

  private boolean apply(boolean save) {
    boolean success = false;
    try {
      value = BridgeClient.transact(value, !original);
      if (save) profiles.save(BridgeClient.display, value);
      status.setVisibility(View.GONE);
      success = true;
    } catch (Exception error) {
      original = false;
      showError(error);
      value = BridgeClient.current;
    }
    render();
    return success;
  }

  private void render() {
    Insets shown = original ? Insets.full(value.width, value.height) : value;
    dimensions.setText(
        "Output "
            + value.width
            + " × "
            + value.height
            + "   ·   Picture "
            + shown.contentWidth()
            + " × "
            + shown.contentHeight());
    for (int i = 0; i < 4; i++) {
      rows[i].setText(String.format(Locale.US, "%-8s        ‹ %4d px ›", names[i], shown.edge(i)));
      rows[i].setEnabled(!original);
      rows[i].setFocusable(!original);
      rows[i].setAlpha(original ? 0.5f : 1f);
    }
    preview.setText(original ? "Restore" : "Show original");
    grid.dimensions(value.width, value.height);
  }

  private String message(Exception error) {
    return error.getMessage() == null
        ? "Connection lost. Run the utility again."
        : error.getMessage();
  }

  private void showError(Exception error) {
    status.setText(message(error));
    status.setVisibility(View.VISIBLE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
    reapplyOnResume = true;
    handler.post(poll);
  }

  @Override
  protected void onPause() {
    if (original) {
      original = false;
      apply(false);
    }
    active = false;
    handler.removeCallbacks(poll);
    super.onPause();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (panel.getVisibility() != View.VISIBLE && event.getAction() == KeyEvent.ACTION_DOWN) {
      int key = event.getKeyCode();
      if (key == KeyEvent.KEYCODE_BACK
          || key == KeyEvent.KEYCODE_DPAD_CENTER
          || key == KeyEvent.KEYCODE_ENTER) {
        panel.setVisibility(View.VISIBLE);
        rows[0].requestFocus();
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }

  private int dp(int number) {
    return Math.round(number * getResources().getDisplayMetrics().density);
  }

  private TextView label(String text, int size) {
    TextView view = new TextView(this);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(Color.rgb(229, 241, 247));
    return view;
  }

  private GradientDrawable background(int fill, int stroke) {
    GradientDrawable result = new GradientDrawable();
    result.setColor(fill);
    result.setCornerRadius(dp(7));
    result.setStroke(dp(1), stroke);
    return result;
  }

  private Button button(LinearLayout parent, String title) {
    Button button = new Button(this);
    button.setId(View.generateViewId());
    button.setText(title);
    button.setTextSize(12);
    button.setAllCaps(false);
    button.setPadding(0, 0, 0, 0);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    parent.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
    return button;
  }
}
