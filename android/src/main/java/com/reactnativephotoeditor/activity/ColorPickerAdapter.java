package com.reactnativephotoeditor.activity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable; // --- IMPORT THIS ---
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.reactnativephotoeditor.R;
import java.util.List;

public class ColorPickerAdapter extends RecyclerView.Adapter<ColorPickerAdapter.ViewHolder> {

    private final List<Integer> colorPickerColors;
    private final LayoutInflater inflater;
    private OnColorPickerClickListener onColorPickerClickListener;
    private int selectedPosition = 0;

    public ColorPickerAdapter(@NonNull Context context, @NonNull List<Integer> colorPickerColors) {
        this.inflater = LayoutInflater.from(context);
        this.colorPickerColors = colorPickerColors;
    }

    public ColorPickerAdapter(@NonNull Context context) {
        this(context, getDefaultColors());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // --- NOTE ---
        // Your layout file name is correct here.
        View view = inflater.inflate(R.layout.color_picker_item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int colorCode = colorPickerColors.get(position);

        // --- UPDATED LOGIC ---
        // 1. Get the background as a LayerDrawable and call mutate().
        //    mutate() is important to ensure changes to this item don't affect other items.
        LayerDrawable layerDrawable = (LayerDrawable) holder.colorPickerView.getBackground().mutate();

        // 2. Find the inner shape by its ID (the one we defined in color_circle_drawable.xml).
        GradientDrawable innerColorShape = (GradientDrawable) layerDrawable.findDrawableByLayerId(R.id.inner_color_shape);

        // 3. Set the color of that inner shape.
        innerColorShape.setColor(colorCode);

        // 4. Show or hide the selection border. This logic was already perfect.
        if (selectedPosition == position) {
            holder.selectionView.setVisibility(View.VISIBLE);
        } else {
            holder.selectionView.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return colorPickerColors.size();
    }

    public void setOnColorPickerClickListener(OnColorPickerClickListener onColorPickerClickListener) {
        this.onColorPickerClickListener = onColorPickerClickListener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View colorPickerView;
        View selectionView;

        public ViewHolder(View itemView) {
            super(itemView);
            colorPickerView = itemView.findViewById(R.id.color_picker_view);
            selectionView = itemView.findViewById(R.id.color_picker_selected);

            // This click listener logic is excellent and needs no changes.
            itemView.setOnClickListener(v -> {
                int previousPosition = selectedPosition;
                selectedPosition = getAdapterPosition();

                notifyItemChanged(previousPosition);
                notifyItemChanged(selectedPosition);

                if (onColorPickerClickListener != null) {
                    onColorPickerClickListener.onColorPickerClickListener(colorPickerColors.get(selectedPosition));
                }
            });
        }
    }

    public interface OnColorPickerClickListener {
        void onColorPickerClickListener(int colorCode);
    }

    // This is fine and needs no changes.
    public static List<Integer> getDefaultColors() {
        return List.of(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#000000"),
                Color.parseColor("#EF4444"), // Red
                Color.parseColor("#F97316"), // Orange
                Color.parseColor("#EAB308"), // Yellow
                Color.parseColor("#22C55E"), // Green
                Color.parseColor("#3B82F6"), // Blue
                Color.parseColor("#8B5CF6"), // Purple
                Color.parseColor("#EC4899")  // Pink
        );
    }
}