package local.cloudcli.shell;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.webkit.WebView;

/**
 * A WebView that preserves vertical chat scrolling while preventing Chromium
 * from interpreting horizontal drags as browser-history navigation.
 */
public final class GestureSafeWebView extends WebView {
    private static final float DIRECTION_BIAS = 1.2f;

    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean directionLocked;
    private boolean blockingHorizontalGesture;

    public GestureSafeWebView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            directionLocked = false;
            blockingHorizontalGesture = false;
            return super.onTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_MOVE && !directionLocked) {
            float deltaX = Math.abs(event.getX() - downX);
            float deltaY = Math.abs(event.getY() - downY);
            if (Math.max(deltaX, deltaY) >= touchSlop) {
                directionLocked = true;
                blockingHorizontalGesture = deltaX > deltaY * DIRECTION_BIAS;
                if (blockingHorizontalGesture) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(cancel);
                    cancel.recycle();
                }
            }
        }

        if (blockingHorizontalGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                directionLocked = false;
                blockingHorizontalGesture = false;
            }
            return true;
        }

        boolean handled = super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            directionLocked = false;
        }
        return handled;
    }
}
