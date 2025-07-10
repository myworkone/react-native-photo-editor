// In file: /android/src/main/java/com/reactnativephotoeditor/activity/CropperActivity.kt

package com.reactnativephotoeditor.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.reactnativephotoeditor.activity.constant.ResponseCode

class CropperActivity : AppCompatActivity() {

    private val PHOTO_EDITOR_REQUEST_CODE = 999 // A unique request code

    /**
     * This is the modern way to handle activity results.
     * We register a contract (CropImageContract) and a callback.
     * The callback will be executed when the cropper activity finishes.
     */
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // Crop was successful, get the URI of the cropped image.
            val croppedUri = result.uriContent

            // Now, prepare to start the main PhotoEditorActivity.
            val editorIntent = Intent(this, PhotoEditorActivity::class.java)

            // VERY IMPORTANT: We must pass along all the original extras (stickers, etc.)
            // that were sent from the React Native module.
            if (intent.extras != null) {
                editorIntent.putExtras(intent.extras!!)
            }

            // Overwrite the original 'path' extra with our new cropped image path.
            // PhotoEditorActivity will now load this cropped image instead of the original.
            editorIntent.putExtra("path", croppedUri.toString())

            // Start the PhotoEditorActivity and wait for its result.
            startActivityForResult(editorIntent, PHOTO_EDITOR_REQUEST_CODE)

        } else {
            // Crop failed or was canceled by the user.
            // We'll return a CANCELED result to the original React Native call.
            val intent = Intent()
            setResult(ResponseCode.RESULT_CANCELED, intent)
            finish() // Close this activity.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the original image path sent from the React Native module.
        val originalPath = intent.getStringExtra("path")
        if (originalPath == null) {
            // If there's no path, we can't do anything. Finish immediately.
            setResult(ResponseCode.RESULT_CANCELED)
            finish()
            return
        }

        val originalUri = Uri.parse(originalPath)

        // Launch the cropper.
        // The result will be handled by the `cropImage` launcher defined above.
        cropImage.launch(
            options(uri = originalUri) {
                setGuidelines(CropImageView.Guidelines.ON)
                // You can add more customization here if you want
                // setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                // setFixAspectRatio(true)
            }
        )
    }

    /**
     * This function listens for the result from the PhotoEditorActivity.
     * When the user is done editing, we need to catch the result here
     * and pass it all the way back to the React Native module.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PHOTO_EDITOR_REQUEST_CODE) {
            // The PhotoEditorActivity has finished.
            // Pass its result and data back to whoever started this CropperActivity.
            setResult(resultCode, data)
            finish() // Close this activity.
        }
    }
}