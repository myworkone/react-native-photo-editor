package com.reactnativephotoeditor.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
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
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
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


open class PhotoEditorActivity : AppCompatActivity(), OnPhotoEditorListener, View.OnClickListener,
  PropertiesBSFragment.Properties, StickerListener,
  OnItemSelected, FilterListener, ShapePickerFragment.OnShapePickedListener {
  private var mPhotoEditor: PhotoEditor? = null
  private var mProgressDialog: ProgressDialog? = null
  private var mPhotoEditorView: PhotoEditorView? = null
  private var mPropertiesBSFragment: PropertiesBSFragment? = null
  private var mStickerFragment: StickerFragment? = null
  private var mRvTools: RecyclerView? = null
  private var mRvFilters: RecyclerView? = null
  private var mToolsContainer: FrameLayout? = null
  private var mFabMoreOptions: FrameLayout? = null
  private var mShapePickerContainer: FrameLayout? = null
  private val mEditingToolsAdapter = EditingToolsAdapter(this)
  private val mFilterViewAdapter = FilterViewAdapter(this)
  private var mRootView: ConstraintLayout? = null
  private val mConstraintSet = ConstraintSet()
  private var mIsFilterVisible = false
  private var mIsShapePickerVisible = false
  private var mRvColorPicker: RecyclerView? = null
  private var mColorPickerAdapter: ColorPickerAdapter? = null

  private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
    if (result.isSuccessful) {
      val croppedUri = result.uriContent
      mPhotoEditor?.clearAllViews()
      mPhotoEditorView?.source?.setImageURI(croppedUri)
    }
  }

  // State Management Variables
  private var mCurrentShapeView: View? = null
  private var mCurrentTextView: View? = null
  private var mIsAddingShape: Boolean = false
  private var mIsBrushMode: Boolean = false
  private var mIsShapeToolSelected: Boolean = false

  // Views and state for inline text editing
  private var mInlineEditText: EditText? = null
  private var mEditingTextView: View? = null // The sticker root view being edited
  private var mEditingTextTranslationX: Float = 0f
  private var mEditingTextTranslationY: Float = 0f

  private fun updateColorPickerVisibility() {
    val shouldBeVisible = mIsBrushMode || mCurrentShapeView != null || mIsShapeToolSelected || mCurrentTextView != null
    mRvColorPicker?.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
  }

  @SuppressLint("ClickableViewAccessibility")
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun onCreate(savedInstanceState: Bundle?) {
    // Override the enter transition
    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    super.onCreate(savedInstanceState)
    makeFullScreen()
    setContentView(R.layout.photo_editor_view)
    initViews()

    // --- ADD THE FAB ANIMATION HERE ---
    mFabMoreOptions = findViewById(R.id.fabMoreOptions)
    // 1. Set the initial state (off-screen top and invisible)
    mFabMoreOptions?.translationY = -250f // Start above the screen
    mFabMoreOptions?.alpha = 0f

    // 2. Animate it to its final position with a spring effect
    mFabMoreOptions?.animate()
      ?.translationY(0f) // Move to its final Y position
      ?.alpha(1f) // Fade it in
      ?.setInterpolator(OvershootInterpolator(1.0f)) // This creates the springy/bouncy effect
      ?.setStartDelay(100) // 100ms delay, matching the iOS code (0.1s)
      ?.setDuration(800) // 800ms duration, matching the iOS code (0.8s)
      ?.start()
    // --- END OF ANIMATION CODE ---

    // Setup for inline editing listener on the keyboard's "Done" action
    mInlineEditText?.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        commitInlineTextEdit()
        return@setOnEditorActionListener true
      }
      false
    }

    mColorPickerAdapter = ColorPickerAdapter(this)
    mColorPickerAdapter?.setOnColorPickerClickListener { colorCode ->
      if (mEditingTextView != null) {
        // If we are actively editing text, apply the color to the live EditText
        mInlineEditText?.setTextColor(colorCode)
      } else if (mCurrentShapeView != null) {
        val shapeImageView = mCurrentShapeView?.findViewById<ImageView>(ja.burhanrashid52.photoeditor.R.id.imgPhotoEditorImage)
        shapeImageView?.let {
          it.drawable.mutate().setColorFilter(colorCode, PorterDuff.Mode.SRC_IN)
        }
      } else if (mCurrentTextView != null) {
        val txtSticker = mCurrentTextView?.findViewById<TextView>(ja.burhanrashid52.photoeditor.R.id.tvPhotoEditorText)
        txtSticker?.let {
          val currentText = it.text.toString()
          val styleBuilder = TextStyleBuilder()
          styleBuilder.withTextColor(colorCode)
          mPhotoEditor?.editText(mCurrentTextView!!, currentText, styleBuilder)
        }
      } else if (mIsBrushMode || mIsShapeToolSelected) {
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

    val llmTools = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvTools!!.layoutManager = llmTools
    mRvTools!!.adapter = mEditingToolsAdapter
    val llmFilters = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvFilters!!.layoutManager = llmFilters
    mRvFilters!!.adapter = mFilterViewAdapter

    val rvShapes: RecyclerView = findViewById(R.id.rvShapes)
    val llmShapes = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    rvShapes.layoutManager = llmShapes
    val shapeAdapter = ShapeAdapter { shapeRes ->
      onShapePicked(shapeRes)
      closeShapePickerAndDeselectTool()
    }
    rvShapes.adapter = shapeAdapter


    val pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true)
    mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
      .setPinchTextScalable(pinchTextScalable)
      .build()
    mPhotoEditor?.setOnPhotoEditorListener(this)

    mPhotoEditorView?.setOnClickListener {
      if (mEditingTextView != null) {
        commitInlineTextEdit()
      } else if (mIsShapePickerVisible) {
        closeShapePickerAndDeselectTool()
      } else if (mCurrentShapeView != null) {
        mPhotoEditor?.clearHelperBox()
        mCurrentShapeView = null
        updateColorPickerVisibility()
      } else if (mCurrentTextView != null) {
        mPhotoEditor?.clearHelperBox()
        mCurrentTextView = null
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
    if (mEditingTextView != null) commitInlineTextEdit()
    mPhotoEditor?.clearHelperBox()
    mIsAddingShape = true
    mPhotoEditor?.addImage(vectorToBitmap(shapeSticker))
  }

  override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
    mPhotoEditor?.clearHelperBox()
    mCurrentShapeView = null
    mCurrentTextView = null

    if (viewType == ViewType.IMAGE) {
      mCurrentShapeView = mPhotoEditorView?.getChildAt(mPhotoEditorView!!.childCount - 1)
      if (mIsAddingShape) {
        mIsAddingShape = false
      }
    } else if (viewType == ViewType.TEXT) {
      mCurrentTextView = mPhotoEditorView?.getChildAt(mPhotoEditorView!!.childCount - 1)
    }
    updateColorPickerVisibility()
  }

  override fun onStartViewChangeListener(viewType: ViewType) {
    if (mIsBrushMode || mEditingTextView != null) {
      return
    }

    val topView = mPhotoEditorView?.getChildAt(mPhotoEditorView!!.childCount - 1)
    if (viewType == ViewType.IMAGE) {
      mCurrentShapeView = topView
      mCurrentTextView = null
    } else if (viewType == ViewType.TEXT) {
      mCurrentTextView = topView
      mCurrentShapeView = null
    } else {
      mCurrentShapeView = null
      mCurrentTextView = null
    }
    updateColorPickerVisibility()
  }

  override fun onToolSelected(toolType: ToolType) {
    if (mEditingTextView != null) commitInlineTextEdit()

    mPhotoEditor?.clearHelperBox()
    mCurrentShapeView = null
    mCurrentTextView = null
    mEditingToolsAdapter.setSelectedTool(toolType)
    mIsBrushMode = toolType == ToolType.BRUSH
    mIsShapeToolSelected = toolType == ToolType.SHAPE
    mPhotoEditor!!.setBrushDrawingMode(mIsBrushMode)

    when (toolType) {
      ToolType.CROP -> launchCrop()
      ToolType.SHAPE -> showShapes(true)
      ToolType.TEXT -> {
        val styleBuilder = TextStyleBuilder()
        styleBuilder.withTextColor(Color.WHITE)
        mPhotoEditor!!.addText("text", styleBuilder)
      }
      ToolType.ERASER -> mPhotoEditor!!.brushEraser()
      ToolType.FILTER -> showFilter(true)
      ToolType.STICKER -> showBottomSheetDialogFragment(mStickerFragment)
      else -> { /* Do nothing for Brush as it's handled by mIsBrushMode */ }
    }
    updateColorPickerVisibility()
  }

  override fun onStickerClick(bitmap: Bitmap) {
    if (mEditingTextView != null) commitInlineTextEdit()
    mPhotoEditor?.clearHelperBox()
    mCurrentShapeView = null
    mCurrentTextView = null
    mPhotoEditor!!.addImage(bitmap)
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
    val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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
    mToolsContainer = findViewById(R.id.tools_container)
    mShapePickerContainer = findViewById(R.id.shape_picker_container)
    mRootView = findViewById(R.id.rootView)
    mRvColorPicker = findViewById(R.id.rvColorPicker)

    // Initialize inline editing view
    mInlineEditText = findViewById(R.id.inlineEditText)
  }

override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
    // If we are already editing a text view, and the user taps a different one,
    // commit the changes to the first one before starting to edit the new one.
    if (mEditingTextView != null && mEditingTextView != rootView) {
        commitInlineTextEdit()
    }
    // If the user taps the same text view that is already being edited, do nothing.
    if (mEditingTextView == rootView) return

    mEditingTextView = rootView
    mCurrentTextView = null // Deselect for UI purposes

    // Hide the tools container when starting to edit text
    mToolsContainer?.visibility = View.GONE
    showFab(false) // MODIFIED: Animate FAB out

    mEditingTextTranslationX = rootView.translationX
    mEditingTextTranslationY = rootView.translationY

    val txtSticker = rootView.findViewById<TextView>(ja.burhanrashid52.photoeditor.R.id.tvPhotoEditorText)
    // Hide the original TextView. We will make it visible again when editing is finished.
    txtSticker.visibility = View.INVISIBLE

    mInlineEditText?.apply {
        // Set content and style to match the sticker being edited
        this.setText(text)
        this.setTextColor(colorCode)
        this.textSize = txtSticker.textSize / resources.displayMetrics.scaledDensity

        // Make the EditText visible
        this.visibility = View.VISIBLE

        // Post the focus request and keyboard show to the view's message queue.
        this.post {
            this.requestFocus()
            showKeyboard(this)
        }
    }

    // Ensure the color picker is visible for the editing session
    mRvColorPicker?.visibility = View.VISIBLE
    mPhotoEditor?.setBrushDrawingMode(true) // Disable sticker interaction while editing text
}

  private fun commitInlineTextEdit() {
    val newText = mInlineEditText?.text.toString().trim()
    val txtSticker = mEditingTextView?.findViewById<TextView>(ja.burhanrashid52.photoeditor.R.id.tvPhotoEditorText)

    if (mEditingTextView != null && txtSticker != null) {
      val styleBuilder = TextStyleBuilder()
      styleBuilder.withTextColor(mInlineEditText!!.currentTextColor)

      mPhotoEditor?.editText(mEditingTextView!!, newText, styleBuilder)
      txtSticker.visibility = View.VISIBLE

      mEditingTextView?.translationX = mEditingTextTranslationX
      mEditingTextView?.translationY = mEditingTextTranslationY
    }

    hideKeyboard(mInlineEditText!!)
    mInlineEditText?.visibility = View.GONE
    mRvColorPicker?.visibility = View.GONE // Hide color picker after editing

    // Show the tools container again
    mToolsContainer?.visibility = View.VISIBLE
    showFab(true) // MODIFIED: Animate FAB in

    mEditingTextView = null
    mPhotoEditor?.setBrushDrawingMode(false) // Re-enable sticker interaction
    mPhotoEditor?.clearHelperBox()
  }

  private fun showKeyboard(view: View) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun hideKeyboard(view: View) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
  }

  override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {}
  override fun onStopViewChangeListener(viewType: ViewType) {}

  @SuppressLint("MissingPermission")
  private fun launchCrop() {
    if (mEditingTextView != null) commitInlineTextEdit()

    mPhotoEditor?.clearHelperBox()
    mCurrentShapeView = null
    mCurrentTextView = null
    updateColorPickerVisibility()

    val viewToSave = mPhotoEditorView!!
    val currentBitmap = Bitmap.createBitmap(viewToSave.width, viewToSave.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(currentBitmap)
    viewToSave.draw(canvas)

    val cachePath = File(externalCacheDir, "temp_images")
    cachePath.mkdirs()
    val tempFile = File(cachePath, "temp_for_crop.png")

    try {
      val fos = FileOutputStream(tempFile)
      currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
      fos.flush()
      fos.close()
    } catch (e: Exception) {
      e.printStackTrace()
      mPhotoEditorView?.let {
        Snackbar.make(it, "Failed to prepare image for cropping", Snackbar.LENGTH_SHORT).show()
      }
      return
    }

    val tempUri = Uri.fromFile(tempFile)

    cropImageLauncher.launch(
      options(uri = tempUri) {
        setGuidelines(CropImageView.Guidelines.ON)
        setFixAspectRatio(false)
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
    if (mEditingTextView != null) commitInlineTextEdit()

    val fileName = System.currentTimeMillis().toString() + ".png"
    val hasStoragePermission = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    if (hasStoragePermission || isSdkHigherThan28()) {
      showLoading("Saving...")
      mPhotoEditor?.clearHelperBox()
      mCurrentShapeView = null
      mCurrentTextView = null
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
        // MODIFIED: Call the exit animation function instead of finishing directly
        animateFabOutAndFinish()
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
    // MODIFIED: Call the exit animation function instead of finishing directly
    animateFabOutAndFinish()
  }

  private fun animateFabOutAndFinish() {
    // Animate the FAB up and out of the screen
    mFabMoreOptions?.animate()
      ?.translationY(-250f) // Move up and off-screen
      ?.alpha(0f) // Fade it out
      ?.setInterpolator(DecelerateInterpolator()) // A smooth exit
      ?.setDuration(300) // A quick exit
      ?.withEndAction {
        // This code runs ONLY after the animation is complete
        finish()
        // Apply the fade-out transition for the whole activity
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
      }
      ?.start()
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
        ConstraintSet.PARENT_ID, ConstraintSet.END
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

  /**
   * NEW: Helper function to animate the FAB in or out.
   * @param show true to animate in, false to animate out
   */
  private fun showFab(show: Boolean) {
      if (show) {
          // Animate in
          mFabMoreOptions?.visibility = View.VISIBLE
          mFabMoreOptions?.animate()
              ?.alpha(1f)
              ?.scaleX(1f)
              ?.scaleY(1f)
              ?.setDuration(200)
              ?.start()
      } else {
          // Animate out
          mFabMoreOptions?.animate()
              ?.alpha(0f)
              ?.scaleX(0f)
              ?.scaleY(0f)
              ?.setDuration(200)
              ?.withEndAction { mFabMoreOptions?.visibility = View.GONE }
              ?.start()
      }
  }

  private fun showShapes(isVisible: Boolean) {
    mIsShapePickerVisible = isVisible
    showFab(!isVisible) // MODIFIED: Animate FAB based on picker visibility

    val transition = Slide(Gravity.BOTTOM)
    transition.duration = 200
    transition.interpolator = DecelerateInterpolator()
    TransitionManager.beginDelayedTransition(mToolsContainer!!, transition)
    mShapePickerContainer?.visibility = if (isVisible) View.VISIBLE else View.GONE
    mRvTools?.visibility = if (isVisible) View.GONE else View.VISIBLE
  }

  private fun closeShapePickerAndDeselectTool() {
    if (mIsShapePickerVisible) {
      showShapes(false)
      mIsShapeToolSelected = false
      mEditingToolsAdapter.clearSelection()
      updateColorPickerVisibility()
    }
  }

  override fun onBackPressed() {
    if (mEditingTextView != null) {
      commitInlineTextEdit()
      return
    }
    if (mIsShapePickerVisible) {
      closeShapePickerAndDeselectTool()
    } else if (mIsFilterVisible) {
      showFilter(false)
    } else if (!mPhotoEditor!!.isCacheEmpty) {
      showSaveDialog()
    } else {
      onCancel()
    }
  }

  inner class ShapeAdapter(private val onShapeClickListener: (Int) -> Unit) :
    RecyclerView.Adapter<ShapeAdapter.ViewHolder>() {

    private val shapes: List<Int> = listOf(
      R.drawable.ic_shape_arrow,
      R.drawable.ic_shape_circle,
      R.drawable.ic_shape_square,
      R.drawable.ic_shape_triangle
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.row_shape_picker_item, parent, false)
      return ViewHolder(view)
    }

    override fun getItemCount(): Int = shapes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.bind(shapes[position])
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      private val imgShape: ImageView = itemView.findViewById(R.id.imgShape)

      fun bind(@DrawableRes shapeRes: Int) {
        imgShape.setImageResource(shapeRes)
        itemView.setOnClickListener {
          onShapeClickListener(shapeRes)
        }
      }
    }
  }

  companion object {
    private val TAG = PhotoEditorActivity::class.java.simpleName
    const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
    const val READ_WRITE_STORAGE = 52
  }
}