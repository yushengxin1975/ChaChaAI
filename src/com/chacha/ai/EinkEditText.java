package com.chacha.ai;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

/**
 * 墨水屏专用编辑框
 * 重写 dispatchKeyEventPreIme，在系统输入法（IME）处理硬件按键之前进行优先拦截。
 * 从而彻底解决外接蓝牙键盘按键被系统输入法截断、导致无法在应用层实现拼音输入的问题。
 */
public class EinkEditText extends EditText {

    public interface KeyPreImeListener {
        boolean onKeyPreIme(KeyEvent event);
    }

    private KeyPreImeListener keyPreImeListener;

    public EinkEditText(Context context) {
        super(context);
    }

    public EinkEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EinkEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setKeyPreImeListener(KeyPreImeListener listener) {
        this.keyPreImeListener = listener;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (keyPreImeListener != null) {
            if (keyPreImeListener.onKeyPreIme(event)) {
                return true;
            }
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        invalidate();
    }
}
