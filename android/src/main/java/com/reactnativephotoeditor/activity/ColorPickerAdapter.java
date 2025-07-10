package com.reactnativephotoeditor.activity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

    // --- NEW ---
    // We need to keep track of the selected item's position
    private int selectedPosition = 0; // Default to the first color being selected

    public ColorPickerAdapter(@NonNull Context context, @NonNull List<Integer> colorPickerColors) {
        this.inflater = LayoutInflater.from(context);
        this.colorPickerColors = colorPickerColors;
    }

    public ColorPickerAdapter(@NonNull Context context) {
        // We now use a new getDefaultColors() that matches the requested image
        this(context, getDefaultColors());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Make sure this layout name matches the one we just created
        View view = inflater.inflate(R.layout.color_picker_item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int colorCode = colorPickerColors.get(position);

        // --- UPDATED ---
        // Get the drawable for the color circle and set its color
        GradientDrawable colorCircle = (GradientDrawable) holder.colorPickerView.getBackground();
        colorCircle.setColor(colorCode);

        // Show or hide the selection border based on the selected position
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

    // --- DELETED ---
    // The complex buildColorPickerView method is no longer needed. We use XML drawables instead.

    public void setOnColorPickerClickListener(OnColorPickerClickListener onColorPickerClickListener) {
        this.onColorPickerClickListener = onColorPickerClickListener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        // --- UPDATED ---
        // We now need references to both the color view and the selection view
        View colorPickerView;
        View selectionView;

        public ViewHolder(View itemView) {
            super(itemView);
            colorPickerView = itemView.findViewById(R.id.color_picker_view);
            selectionView = itemView.findViewById(R.id.color_picker_selected); // Get reference to selection border
            
            // --- UPDATED CLICK LISTENER ---
            itemView.setOnClickListener(v -> {
                int previousPosition = selectedPosition;
                selectedPosition = getAdapterPosition();

                // Notify the adapter to re-render the old and new selected items.
                // This is more efficient than notifyDataSetChanged().
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

    // --- UPDATED ---
    // This new list of colors matches the colors in your target image.
    public static List<Integer> getDefaultColors() {
        return List.of(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#000000"),
                Color.parseColor("#EF5350"), // Coral Red
                Color.parseColor("#FFA726"), // Orange
                Color.parseColor("#66BB6A"), // Green
                Color.parseColor("#42A5F5"), // Blue
                Color.parseColor("#AB47BC"), // Purple
                Color.parseColor("#BDBDBD")  // Grey
        );
    }
}