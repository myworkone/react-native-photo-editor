package com.reactnativephotoeditor.activity.tools;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.reactnativephotoeditor.R;

import java.util.ArrayList;
import java.util.List;

public class EditingToolsAdapter extends RecyclerView.Adapter<EditingToolsAdapter.ViewHolder> {

    private List<ToolModel> mToolList = new ArrayList<>();
    private OnItemSelected mOnItemSelected;
    private ToolType mSelectedToolType;

    public EditingToolsAdapter(OnItemSelected onItemSelected) {
        mOnItemSelected = onItemSelected;

        mToolList.add(new ToolModel("Brush", R.drawable.selector_ic_pencil, ToolType.BRUSH));
        mToolList.add(new ToolModel("Shape", R.drawable.selector_ic_shape, ToolType.SHAPE));
        mToolList.add(new ToolModel("Text", R.drawable.selector_ic_text, ToolType.TEXT));
        mToolList.add(new ToolModel("Crop", R.drawable.selector_ic_crop, ToolType.CROP));
    }

    public void setSelectedTool(ToolType toolType) {
        mSelectedToolType = toolType;
        notifyDataSetChanged();
    }

    public interface OnItemSelected {
        void onToolSelected(ToolType toolType);
    }

    class ToolModel {
        private String mToolName;
        private int mToolIcon;
        private ToolType mToolType;

        ToolModel(String toolName, int toolIcon, ToolType toolType) {
            mToolName = toolName;
            mToolIcon = toolIcon;
            mToolType = toolType;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_editing_tools, parent, false);
        return new ViewHolder(view);
    }

     @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ToolModel item = mToolList.get(position);
        
        holder.imgToolIcon.setImageResource(item.mToolIcon);

        boolean isSelected = item.mToolType == mSelectedToolType;
        holder.imgToolIcon.setSelected(isSelected);

    }



    @Override
    public int getItemCount() {
        return mToolList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgToolIcon;

        ViewHolder(View itemView) {
            super(itemView);
            imgToolIcon = itemView.findViewById(R.id.imgToolIcon);
            
            itemView.setOnClickListener(v -> mOnItemSelected.onToolSelected(mToolList.get(getLayoutPosition()).mToolType));
        }
    }
}
