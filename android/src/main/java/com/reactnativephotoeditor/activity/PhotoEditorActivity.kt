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
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
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
  private var mImgUndo: ImageView? = null

  // Self-managed state for the undo button
  private var changesCounter = 0
  private var isUndoing = false
  private var mIsBrushColorInitialized = false

  private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
    if (result.isSuccessful) {
      val croppedUri = result.uriContent
      mPhotoEditor?.clearAllViews()
      changesCounter = 0
      mPhotoEditorView?.source?.setImageURI(croppedUri)
      updateUndoButtonState()
    }
  }

  // State Management Variables
  private var mCurrentShapeView: View? = null
  private var mCurrentTextView: View? = null
  private var mIsAddingShape: Boolean = false
  private var mIsBrushMode: Boolean = false
  private var mIsShapeToolSelected: Boolean = false
  private var mLastTappedTextView: View? = null

  // Views and state for inline text editing
  private var mInlineEditText: EditText? = null
  private var mEditingTextView: View? = null
  private var mEditingTextTranslationX: Float = 0f
  private var mEditingTextTranslationY: Float = 0f

  private fun updateColorPickerVisibility() {
    val shouldBeVisible = mIsBrushMode || mCurrentShapeView != null || mIsShapeToolSelected || mCurrentTextView != null
    mRvColorPicker?.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
  }

  private fun updateUndoButtonState() {
    if (changesCounter > 0) {
        mImgUndo?.isEnabled = true
        mImgUndo?.alpha = 1.0f
    } else {
        mImgUndo?.isEnabled = false
        mImgUndo?.alpha = 0.5f
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun onCreate(savedInstanceState: Bundle?) {
    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    super.onCreate(savedInstanceState)
    window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    setContentView(R.layout.photo_editor_view)
    initViews()

    mFabMoreOptions = findViewById(R.id.fabMoreOptions)
    mFabMoreOptions?.translationY = -250f
    mFabMoreOptions?.alpha = 0f

    mFabMoreOptions?.animate()
      ?.translationY(0f)
      ?.alpha(1f)
      ?.setInterpolator(OvershootInterpolator(1.0f))
      ?.setStartDelay(100)
      ?.setDuration(800)
      ?.start()

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
    updateUndoButtonState()

    mPhotoEditorView?.setOnClickListener {
      mLastTappedTextView = null
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

  // CORE FIX #1: This listener ONLY increments for static additions (Text and Image/Sticker).
  override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
    if (!isUndoing) {
        // We only count this as a change if it's NOT a drawing.
        // Drawings will be counted by onStopViewChangeListener.
        if (viewType == ViewType.IMAGE || viewType == ViewType.TEXT) {
            changesCounter++
        }
    }

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
    updateUndoButtonState()
  }

  override fun onStartViewChangeListener(viewType: ViewType) {
    if (mEditingTextView != null) {
        return
    }
    if (mIsBrushMode && (viewType == ViewType.IMAGE || viewType == ViewType.TEXT)) {
        mPhotoEditor?.setBrushDrawingMode(false)
        mIsBrushMode = false
        mEditingToolsAdapter.clearSelection()
    }
    val topView = mPhotoEditorView?.getChildAt(mPhotoEditorView!!.childCount - 1)
    if (viewType == ViewType.IMAGE) {
        mCurrentShapeView = topView
        mCurrentTextView = null
        mLastTappedTextView = null
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
        ToolType.BRUSH -> {
            if (!mIsBrushColorInitialized) {
                mPhotoEditor!!.brushColor = Color.WHITE
                mIsBrushColorInitialized = true
            }
        }
        ToolType.CROP -> launchCrop()
        ToolType.SHAPE -> showShapes(true)
        ToolType.TEXT -> {
            val styleBuilder = TextStyleBuilder()
            styleBuilder.withTextColor(Color.WHITE)
            styleBuilder.withTextSize(40f)
            mPhotoEditor!!.addText("Text", styleBuilder)
        }
        ToolType.ERASER -> mPhotoEditor!!.brushEraser()
        ToolType.FILTER -> showFilter(true)
        ToolType.STICKER -> showBottomSheetDialogFragment(mStickerFragment)
        else -> { /* Do nothing */ }
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
    mImgUndo = findViewById(R.id.imgUndo)
    mImgUndo?.setOnClickListener(this)
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
    mInlineEditText = findViewById(R.id.inlineEditText)
  }

  override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
    if (mLastTappedTextView == rootView) {
        mLastTappedTextView = null
        if (mEditingTextView != null && mEditingTextView != rootView) {
            commitInlineTextEdit()
        }
        if (mEditingTextView == rootView) return

        mEditingTextView = rootView
        mCurrentTextView = null

        mToolsContainer?.visibility = View.GONE
        showFab(false)

        mEditingTextTranslationX = rootView.translationX
        mEditingTextTranslationY = rootView.translationY

        val txtSticker = rootView.findViewById<TextView>(ja.burhanrashid52.photoeditor.R.id.tvPhotoEditorText)
        txtSticker.visibility = View.INVISIBLE

        mInlineEditText?.apply {
            this.setText(text)
            this.setTextColor(colorCode)
            this.textSize = txtSticker.textSize / resources.displayMetrics.scaledDensity
            this.visibility = View.VISIBLE
            this.post {
                this.requestFocus()
                showKeyboard(this)
            }
        }
        mRvColorPicker?.visibility = View.VISIBLE
        mPhotoEditor?.setBrushDrawingMode(true)
    } else {
        mLastTappedTextView = rootView
    }
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
    mRvColorPicker?.visibility = View.GONE

    mToolsContainer?.visibility = View.VISIBLE
    showFab(true)

    mEditingTextView = null
    mPhotoEditor?.setBrushDrawingMode(false)
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

  override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
      // Do nothing. This is handled by the undo click.
  }

  // CORE FIX #2: This listener handles modifications and drawings.
  override fun onStopViewChangeListener(viewType: ViewType) {
    if (!isUndoing) {
      changesCounter++
    }
    updateUndoButtonState()
  }

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

  // CORE FIX #3: This is now the definitive handler for undoing.
  @SuppressLint("NonConstantResourceId")
  override fun onClick(view: View) {
    when (view.id) {
      R.id.imgUndo -> {
          if (changesCounter > 0) {
              isUndoing = true
              mPhotoEditor?.undo()
              changesCounter--
              isUndoing = false
          }
          updateUndoButtonState()
      }
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
    animateFabOutAndFinish()
  }

  private fun animateFabOutAndFinish() {
    mFabMoreOptions?.animate()
      ?.translationY(-250f)
      ?.alpha(0f)
      ?.setInterpolator(DecelerateInterpolator())
      ?.setDuration(300)
      ?.withEndAction {
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
      }
      ?.start()
  }

  override fun onFilterSelected(photoFilter: PhotoFilter) {
    if (!isUndoing) {
        changesCounter++
    }
    mPhotoEditor!!.setFilterEffect(photoFilter)
    updateUndoButtonState()
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

  private fun showFab(show: Boolean) {
      if (show) {
          mFabMoreOptions?.visibility = View.VISIBLE
          mFabMoreOptions?.animate()
              ?.alpha(1f)
              ?.scaleX(1f)
              ?.scaleY(1f)
              ?.setDuration(200)
              ?.start()
      } else {
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
    showFab(!isVisible)

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
    } else if (changesCounter > 0) {
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
