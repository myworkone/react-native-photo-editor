package com.reactnativephotoeditor.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.reactnativephotoeditor.R
import com.reactnativephotoeditor.activity.StickerFragment.StickerListener
import com.reactnativephotoeditor.activity.constant.ResponseCode
import com.reactnativephotoeditor.activity.filters.FilterListener
import com.reactnativephotoeditor.activity.filters.FilterViewAdapter
import com.reactnativephotoeditor.activity.tools.EditingToolsAdapter
import com.reactnativephotoeditor.activity.tools.EditingToolsAdapter.OnItemSelected
import com.reactnativephotoeditor.activity.tools.ToolType
import ja.burhanrashid52.photoeditor.*
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options


open class PhotoEditorActivity : AppCompatActivity(), OnPhotoEditorListener, View.OnClickListener,
  PropertiesBSFragment.Properties, StickerListener,
  OnItemSelected, FilterListener, ShapePickerFragment.OnShapePickedListener {
  private var mPhotoEditor: PhotoEditor? = null
  private var mProgressDialog: ProgressDialog? = null
  private var mPhotoEditorView: PhotoEditorView? = null
  private var mPropertiesBSFragment: PropertiesBSFragment? = null
  private var mStickerFragment: StickerFragment? = null
  private var mShapePickerFragment: ShapePickerFragment? = null
  private var mRvTools: RecyclerView? = null
  private var mRvFilters: RecyclerView? = null
  private val mEditingToolsAdapter = EditingToolsAdapter(this)
  private val mFilterViewAdapter = FilterViewAdapter(this)
  private var mRootView: ConstraintLayout? = null
  private val mConstraintSet = ConstraintSet()
  private var mIsFilterVisible = false
  private var mRvColorPicker: RecyclerView? = null
  private var mColorPickerAdapter: ColorPickerAdapter? = null

  private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
      if (result.isSuccessful) {
          // The user successfully cropped the image.
          // The result contains a URI to the new cropped image.
          val croppedUri = result.uriContent

          // IMPORTANT: Clear all existing views (stickers, text, etc.) from the editor.
          mPhotoEditor?.clearAllViews()

          // Load the new, cropped image as the main source image.
          mPhotoEditorView?.source?.setImageURI(croppedUri)
      } else {
          // Handle the error or if the user cancelled the crop.
          // You can show a message here if you want, but for now we do nothing.
      }
  }


  // State Management Variables
  private var mCurrentShapeView: View? = null
  private var mIsAddingShape: Boolean = false
  private var mIsBrushMode: Boolean = false
  private var mIsShapeToolSelected: Boolean = false

  // The single source of truth for color picker visibility.
  private fun updateColorPickerVisibility() {
      val shouldBeVisible = mIsBrushMode || mCurrentShapeView != null || mIsShapeToolSelected
      mRvColorPicker?.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
  }

  @SuppressLint("ClickableViewAccessibility")
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    makeFullScreen()
    setContentView(R.layout.photo_editor_view)
    initViews()

    mColorPickerAdapter = ColorPickerAdapter(this)
    mColorPickerAdapter?.setOnColorPickerClickListener { colorCode ->
        if (mCurrentShapeView != null) {
        val shapeImageView = mCurrentShapeView?.findViewById<ImageView>(ja.burhanrashid52.photoeditor.R.id.imgPhotoEditorImage)
        shapeImageView?.let {
            it.drawable.mutate().setColorFilter(colorCode, PorterDuff.Mode.SRC_IN)
        }
    } 
    else if (mIsBrushMode || mIsShapeToolSelected) {
        mPhotoEditor!!.brushColor = colorCode
    }
}
    val llmColors = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvColorPicker!!.layoutManager = llmColors
    mRvColorPicker!!.adapter = mColorPickerAdapter

    val value = intent.extras
    val path = value?.getString("path")
    val stickers =
      value?.getStringArrayList("stickers")?.plus(
        assets.list("Stickers")!!
          .map { item -> "/android_asset/Stickers/$item" }) as ArrayList<String>

    mPropertiesBSFragment = PropertiesBSFragment()
    mPropertiesBSFragment!!.setPropertiesChangeListener(this)
    mStickerFragment = StickerFragment()
    mStickerFragment!!.setStickerListener(this)
    mStickerFragment!!.setData(stickers)
    mShapePickerFragment = ShapePickerFragment()
    mShapePickerFragment!!.setOnShapePickedListener(this)

    val llmTools = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvTools!!.layoutManager = llmTools
    mRvTools!!.adapter = mEditingToolsAdapter
    val llmFilters = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvFilters!!.layoutManager = llmFilters
    mRvFilters!!.adapter = mFilterViewAdapter

    val pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true)
    mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
      .setPinchTextScalable(pinchTextScalable)
      .build()
    mPhotoEditor?.setOnPhotoEditorListener(this)

    // Listener for tapping the background to deselect a shape.
    mPhotoEditorView?.setOnClickListener {
        if (mCurrentShapeView != null) {
            mPhotoEditor?.clearHelperBox()
            mCurrentShapeView = null
            updateColorPickerVisibility()
        }
    }

    Glide
      .with(this)
      .load(path)
      .listener(object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
          val intent = Intent()
          intent.putExtra("path", path)
          setResult(ResponseCode.LOAD_IMAGE_FAILED, intent)
          return false
        }
        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
          return false
        }
      })
      .into(mPhotoEditorView!!.source);
  }

  private fun vectorToBitmap(@DrawableRes id: Int): Bitmap {
    val vectorDrawable = ContextCompat.getDrawable(this, id)!!
    val bitmap = Bitmap.createBitmap(
      vectorDrawable.intrinsicWidth,
      vectorDrawable.intrinsicHeight,
      Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
    vectorDrawable.draw(canvas)
    return bitmap
  }

  override fun onShapePicked(@DrawableRes shapeSticker: Int) {
      mPhotoEditor?.clearHelperBox()
      mIsAddingShape = true
      mPhotoEditor?.addImage(vectorToBitmap(shapeSticker))
  }

  // Listener for when a new view (shape, sticker, text) is added to the editor.
  override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
    if (mIsAddingShape && viewType == ViewType.IMAGE) {
      val shapeView = mPhotoEditorView?.getChildAt(mPhotoEditorView!!.childCount - 1)
      shapeView?.tag = "SHAPE"
      mCurrentShapeView = shapeView
      mIsAddingShape = false
      updateColorPickerVisibility()
    }
  }

  // Listener for when any view on the editor is selected (tapped, dragged, etc.).
    // Listener for when any view on the editor is selected (tapped, dragged, etc.).
  override fun onStartViewChangeListener(viewType: ViewType) {
    // FIX: If we are in brush mode, we must NOT allow re-selection of a shape.
    // When the user starts drawing, this listener is fired, and this check prevents
    // it from incorrectly re-assigning mCurrentShapeView.
    if (mIsBrushMode) {
      // Explicitly ensure no shape is selected and exit.
      mCurrentShapeView = null
      updateColorPickerVisibility()
      return
    }

    // Original logic for when not in brush mode (e.g., selecting a sticker or shape to edit).
    val selectedView = mPhotoEditorView?.getChildAt(mPhotoEditorView!!.childCount - 1)
    mCurrentShapeView = if (selectedView?.tag == "SHAPE") {
        selectedView
    } else {
        null
    }
    updateColorPickerVisibility()
  }

  // When a tool is selected from the bottom toolbar.
  override fun onToolSelected(toolType: ToolType) {
    mPhotoEditor?.clearHelperBox()
    mCurrentShapeView = null
    mEditingToolsAdapter.setSelectedTool(toolType)
    mIsBrushMode = toolType == ToolType.BRUSH
    mIsShapeToolSelected = toolType == ToolType.SHAPE
    mPhotoEditor!!.setBrushDrawingMode(mIsBrushMode)

    when (toolType) {
      ToolType.CROP -> launchCrop()
      ToolType.SHAPE -> showBottomSheetDialogFragment(mShapePickerFragment)
      ToolType.TEXT -> {
        val textEditorDialogFragment = TextEditorDialogFragment.show(this)
        textEditorDialogFragment.setOnTextEditorListener { inputText: String?, colorCode: Int ->
          val styleBuilder = TextStyleBuilder()
          styleBuilder.withTextColor(colorCode)
          mPhotoEditor!!.addText(inputText, styleBuilder)
        }
      }
      ToolType.ERASER -> mPhotoEditor!!.brushEraser()
      ToolType.FILTER -> showFilter(true)
      ToolType.STICKER -> showBottomSheetDialogFragment(mStickerFragment)
      else -> { /* Do nothing for Brush as it's handled by mIsBrushMode */ }
    }
    updateColorPickerVisibility()
  }

    override fun onStickerClick(bitmap: Bitmap) {
        mPhotoEditor?.clearHelperBox()
        mCurrentShapeView = null
        mPhotoEditor!!.addImage(bitmap)
        updateColorPickerVisibility()
    }

    private fun showLoading(message: String) {
        mProgressDialog = ProgressDialog(this)
        mProgressDialog!!.setMessage(message)
        mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        mProgressDialog!!.setCancelable(false)
        mProgressDialog!!.show()
    }

    protected fun hideLoading() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun requestPermission(permission: String) {
        val isGranted =
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) {
            ActivityCompat.requestPermissions(
                    this, arrayOf(permission),
                    READ_WRITE_STORAGE
            )
        }
    }

    private fun makeFullScreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun initViews() {
        val imgUndo: ImageView = findViewById(R.id.imgUndo)
        imgUndo.setOnClickListener(this)
        val btnCancel: TextView = findViewById(R.id.btnCancel)
        btnCancel.setOnClickListener(this)
        val btnDone: TextView = findViewById(R.id.btnDone)
        btnDone.setOnClickListener(this)
        mPhotoEditorView = findViewById(R.id.photoEditorView)
        mRvTools = findViewById(R.id.rvConstraintTools)
        mRvFilters = findViewById(R.id.rvFilterView)
        mRootView = findViewById(R.id.rootView)
        mRvColorPicker = findViewById(R.id.rvColorPicker)
    }

    override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
        val textEditorDialogFragment = TextEditorDialogFragment.show(this, text, colorCode)
        textEditorDialogFragment.setOnTextEditorListener { inputText: String?, newColorCode: Int ->
            val styleBuilder = TextStyleBuilder()
            styleBuilder.withTextColor(newColorCode)
            mPhotoEditor!!.editText(rootView, inputText, styleBuilder)
        }
    }

    override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) { }
    override fun onStopViewChangeListener(viewType: ViewType) { }

    @SuppressLint("MissingPermission")
    private fun launchCrop() {
        // Hide any selection boxes on the current views.
        mPhotoEditor?.clearHelperBox()
        mCurrentShapeView = null
        updateColorPickerVisibility()

        // Create a bitmap of the current state of the PhotoEditorView.
        // This includes the base image and all stickers, text, and drawings.
        val viewToSave = mPhotoEditorView!!
        val currentBitmap = Bitmap.createBitmap(viewToSave.width, viewToSave.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(currentBitmap)
        viewToSave.draw(canvas)

        // The cropper library needs a file URI to work with, so we save the bitmap to a temporary file.
        // We use the app's cache directory, which is perfect for temporary data.
        val cachePath = File(externalCacheDir, "temp_images")
        cachePath.mkdirs() // Create the directory if it doesn't exist.
        val tempFile = File(cachePath, "temp_for_crop.png")

        try {
            // Write the bitmap data to the temporary file.
            val fos = FileOutputStream(tempFile)
            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // If saving fails, show an error and stop.
            mPhotoEditorView?.let {
                Snackbar.make(it, "Failed to prepare image for cropping", Snackbar.LENGTH_SHORT).show()
            }
            return
        }

        // Now that we have a file, get its URI.
        val tempUri = Uri.fromFile(tempFile)

        // Launch the cropper using the launcher we defined earlier.
        cropImageLauncher.launch(
            options(uri = tempUri) {
                // You can customize the cropper's appearance and behavior here.
                setGuidelines(CropImageView.Guidelines.ON)
                setActivityTitle("Crop Image")
                setFixAspectRatio(false) // Allow free-form cropping. Set to 'true' to lock aspect ratio.
                setBackgroundColor(Color.parseColor("#B3000000"))
            }
        )
    }

    @SuppressLint("NonConstantResourceId")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.imgUndo -> mPhotoEditor!!.undo()
            R.id.btnDone -> saveImage()
            R.id.btnCancel -> onBackPressed()
        }
    }

    private fun isSdkHigherThan28(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    @SuppressLint("MissingPermission")
    private fun saveImage() {
        val fileName = System.currentTimeMillis().toString() + ".png"
        val hasStoragePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasStoragePermission || isSdkHigherThan28()) {
            showLoading("Saving...")
            mPhotoEditor?.clearHelperBox()
            mCurrentShapeView = null
            updateColorPickerVisibility()
            val viewToSave = mPhotoEditorView!!
            val savedBitmap = Bitmap.createBitmap(viewToSave.width, viewToSave.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(savedBitmap)
            viewToSave.draw(canvas)
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = File(path, fileName)
            path.mkdirs()
            try {
                val fos = FileOutputStream(file)
                savedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()
                hideLoading()
                val intent = Intent()
                intent.putExtra("path", file.absolutePath)
                setResult(ResponseCode.RESULT_OK, intent)
                finish()
            } catch (e: Exception) {
                hideLoading()
                e.printStackTrace()
                mPhotoEditorView?.let {
                    val snackBar = Snackbar.make(it, R.string.save_error, Snackbar.LENGTH_SHORT)
                    snackBar.show()
                }
            }
        } else {
            requestPer()
        }
    }

    private fun requestPer() {
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onColorChanged(colorCode: Int) {
        if (mIsBrushMode || mIsShapeToolSelected) {
            mPhotoEditor!!.brushColor = colorCode
        }
    }

    override fun onOpacityChanged(opacity: Int) {
        if (mIsBrushMode) {
            mPhotoEditor!!.setOpacity(opacity)
        }
    }

    override fun onShapeSizeChanged(shapeSize: Int) {
        if (mIsBrushMode) {
            mPhotoEditor!!.brushSize = shapeSize.toFloat()
        }
    }

    private fun showSaveDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.msg_save_image))
        builder.setPositiveButton("Save") { _: DialogInterface?, _: Int -> saveImage() }
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setNeutralButton("Discard") { _: DialogInterface?, _: Int -> onCancel() }
        builder.create().show()
    }

    private fun onCancel() {
        val intent = Intent()
        setResult(ResponseCode.RESULT_CANCELED, intent)
        finish()
    }

    override fun onFilterSelected(photoFilter: PhotoFilter) {
        mPhotoEditor!!.setFilterEffect(photoFilter)
    }

    private fun showBottomSheetDialogFragment(fragment: BottomSheetDialogFragment?) {
        if (fragment == null || fragment.isAdded) {
            return
        }
        fragment.show(supportFragmentManager, fragment.tag)
    }

    fun showFilter(isVisible: Boolean) {
        mIsFilterVisible = isVisible
        mConstraintSet.clone(mRootView)
        if (isVisible) {
            mConstraintSet.clear(mRvFilters!!.id, ConstraintSet.START)
            mConstraintSet.connect(
                    mRvFilters!!.id, ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START
            )
            mConstraintSet.connect(
                    mRvFilters!!.id, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END // <-- THIS WAS THE FIX
            )
        } else {
            mConstraintSet.connect(
                    mRvFilters!!.id, ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.END
            )
            mConstraintSet.clear(mRvFilters!!.id, ConstraintSet.END)
        }
        val changeBounds = ChangeBounds()
        changeBounds.duration = 350
        changeBounds.interpolator = AnticipateOvershootInterpolator(1.0f)
        TransitionManager.beginDelayedTransition(mRootView!!, changeBounds)
        mConstraintSet.applyTo(mRootView)
    }

    override fun onBackPressed() {
        if (mIsFilterVisible) {
            showFilter(false)
        } else if (!mPhotoEditor!!.isCacheEmpty) {
            showSaveDialog()
        } else {
            onCancel()
        }
    }

    companion object {
        private val TAG = PhotoEditorActivity::class.java.simpleName
        const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
        const val READ_WRITE_STORAGE = 52
    }
}