package com.reactnativephotoeditor.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.reactnativephotoeditor.R;

public class ShapePickerFragment extends BottomSheetDialogFragment {

    private OnShapePickedListener mOnShapePickedListener;

    public interface OnShapePickedListener {
        // This is simplified. It now passes the resource ID of the shape sticker.
        void onShapePicked(@DrawableRes int shapeSticker);
    }

    public void setOnShapePickedListener(OnShapePickedListener listener) {
        mOnShapePickedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shape_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView arrow = view.findViewById(R.id.shapeArrow);
        ImageView triangle = view.findViewById(R.id.shapeTriangle);
        ImageView square = view.findViewById(R.id.shapeSquare);
        ImageView circle = view.findViewById(R.id.shapeCircle);

        arrow.setOnClickListener(v -> {
            if (mOnShapePickedListener != null) {
                // Pass the new sticker drawable
                mOnShapePickedListener.onShapePicked(R.drawable.ic_arrow_sticker);
                dismiss();
            }
        });

        triangle.setOnClickListener(v -> {
            if (mOnShapePickedListener != null) {
                mOnShapePickedListener.onShapePicked(R.drawable.ic_triangle_sticker);
                dismiss();
            }
        });

        square.setOnClickListener(v -> {
            if (mOnShapePickedListener != null) {
                // Pass the new sticker drawable
                mOnShapePickedListener.onShapePicked(R.drawable.ic_square_sticker);
                dismiss();
            }
        });

        circle.setOnClickListener(v -> {
            if (mOnShapePickedListener != null) {
                // Pass the new sticker drawable
                mOnShapePickedListener.onShapePicked(R.drawable.ic_circle_sticker);
                dismiss();
            }
        });
    }
}