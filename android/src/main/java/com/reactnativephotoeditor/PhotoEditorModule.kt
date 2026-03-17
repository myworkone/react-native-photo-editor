package com.reactnativephotoeditor

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.*
import com.reactnativephotoeditor.activity.PhotoEditorActivity
import com.reactnativephotoeditor.activity.constant.ResponseCode

enum class ERROR_CODE {

}

class PhotoEditorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val context = reactApplicationContext
  private val EDIT_SUCCESSFUL = 1
  private var promise: Promise? = null

  override fun getName(): String {
    return "PhotoEditor"
  }

  @ReactMethod
  fun open(options: ReadableMap?, promise: Promise) {
    this.promise = promise

    // ✅ Fix 1: currentActivity is no longer directly available on newer RN versions
    val activity = reactApplicationContext.currentActivity
    if (activity == null) {
      promise.reject("ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist")
      return
    }

    val intent = Intent(context, PhotoEditorActivity::class.java)

    // (kept) register listener
    context.addActivityEventListener(mActivityEventListener)

    val path = options?.getString("path")

    // ✅ Optional: avoid unsafe cast that can crash if missing/null
    val stickers = options?.getArray("stickers")

    intent.putExtra("path", path)
    if (stickers != null) {
      intent.putExtra("stickers", stickers.toArrayList())
    }

    // ✅ Fix 2: startActivityForResult from Activity is no longer the right approach
    // Use ReactApplicationContext helper (RN modern approach)
    reactApplicationContext.startActivityForResult(intent, EDIT_SUCCESSFUL, null)
  }

  private val mActivityEventListener: ActivityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, intent: Intent?) {
      if (requestCode != EDIT_SUCCESSFUL) return

      // ✅ Optional: remove listener once we handled the result (prevents leaks/duplicate callbacks)
      context.removeActivityEventListener(this)

      when (resultCode) {
        ResponseCode.RESULT_OK -> {
          val path = intent?.getStringExtra("path")
          promise?.resolve("file://$path")
        }
        ResponseCode.RESULT_CANCELED -> {
          promise?.reject("USER_CANCELLED", "User has cancelled", null)
        }
        ResponseCode.LOAD_IMAGE_FAILED -> {
          val path = intent?.getStringExtra("path")
          promise?.reject("LOAD_IMAGE_FAILED", "Load image failed: $path", null)
        }
      }
    }
  }
}