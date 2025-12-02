package com.example.android.notepad;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodoFragment extends Fragment {
    private ExpandableListView mListTodos;
    private TodoExpandableListAdapter mAdapter;
    
    // 待办事项状态标识
    private static final String TODO_STATUS_PENDING = "todo_pending";
    private static final String TODO_STATUS_COMPLETED = "todo_completed";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todo, container, false);
        
        mListTodos = view.findViewById(R.id.list_todos);
        
        setupListView();
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        refreshTodos();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // 确保在视图完全加载后展开所有分组
        mListTodos.post(new Runnable() {
            @Override
            public void run() {
                expandAllGroups();
            }
        });
    }
    
    private void setupListView() {
        mAdapter = new TodoExpandableListAdapter();
        mListTodos.setAdapter(mAdapter);
        
        // 设置子项点击监听器
        mListTodos.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                // 点击待办事项，切换完成状态
                toggleTodoStatus(id);
                return true;
            }
        });
    }
    
    // 展开所有分组
    private void expandAllGroups() {
        if (mAdapter != null) {
            for (int i = 0; i < mAdapter.getGroupCount(); i++) {
                mListTodos.expandGroup(i);
            }
        }
    }
    
    // 将此方法改为公共方法，以便从外部调用
    public void showAddTodoDialog() {
        // 加载自定义布局
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_todo, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(dialogView);
        
        EditText etTodoContent = dialogView.findViewById(R.id.et_todo_content);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = etTodoContent.getText().toString().trim();
                if (!content.isEmpty()) {
                    addTodo(content);
                    dialog.dismiss();
                }
            }
        });
        
        dialog.show();
    }
    
    private void addTodo(String content) {
        // 创建新的待办事项
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, content);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, TODO_STATUS_PENDING); // 默认为未完成状态
        
        // 插入到数据库
        Uri uri = getActivity().getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
        
        // 刷新列表
        refreshTodos();
    }
    
    private void refreshTodos() {
        mAdapter.loadData();
        mAdapter.notifyDataSetChanged();
        
        // 数据刷新后重新展开所有分组
        expandAllGroups();
    }
    
    private void toggleTodoStatus(long todoId) {
        // 获取待办事项当前状态
        Cursor cursor = getActivity().getContentResolver().query(
                Uri.withAppendedPath(NotePad.Notes.CONTENT_ID_URI_BASE, String.valueOf(todoId)),
                new String[]{NotePad.Notes.COLUMN_NAME_CATEGORY},
                null,
                null,
                null
        );
        
        if (cursor != null && cursor.moveToFirst()) {
            int categoryIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
            String currentCategory = cursor.getString(categoryIndex);
            cursor.close();
            
            // 切换状态
            String newCategory;
            if (TODO_STATUS_PENDING.equals(currentCategory)) {
                newCategory = TODO_STATUS_COMPLETED; // 标记为已完成
            } else {
                newCategory = TODO_STATUS_PENDING; // 标记为未完成
            }
            
            // 更新数据库
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, newCategory);
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
            
            getActivity().getContentResolver().update(
                    Uri.withAppendedPath(NotePad.Notes.CONTENT_ID_URI_BASE, String.valueOf(todoId)),
                    values,
                    null,
                    null
            );
            
            // 刷新列表
            refreshTodos();
        }
        
        if (cursor != null) {
            cursor.close();
        }
    }
    
    /**
     * 自定义适配器，用于显示分组的待办事项列表
     */
    private class TodoExpandableListAdapter extends BaseExpandableListAdapter {
        private List<GroupItem> groupItems;
        private Map<String, List<Map<String, String>>> childData;
        private Map<String, List<Long>> childIds;
        
        public TodoExpandableListAdapter() {
            groupItems = new ArrayList<>();
            childData = new HashMap<>();
            childIds = new HashMap<>();
            loadData();
        }
        
        public void loadData() {
            groupItems.clear();
            childData.clear();
            childIds.clear();
            
            // 查询未完成的待办事项
            int pendingCount = loadTodosForCategory(TODO_STATUS_PENDING, "未完成");
            
            // 查询已完成的待办事项
            int completedCount = loadTodosForCategory(TODO_STATUS_COMPLETED, "已完成");
            
            // 添加分组标题和数量
            groupItems.add(new GroupItem("未完成", pendingCount));
            groupItems.add(new GroupItem("已完成", completedCount));
        }
        
        private int loadTodosForCategory(String category, String groupName) {
            List<Map<String, String>> groupData = new ArrayList<>();
            List<Long> groupIds = new ArrayList<>();
            
            Cursor cursor = getActivity().getContentResolver().query(
                    NotePad.Notes.CONTENT_URI,
                    new String[]{
                            NotePad.Notes._ID,
                            NotePad.Notes.COLUMN_NAME_TITLE,
                            NotePad.Notes.COLUMN_NAME_CREATE_DATE
                    },
                    NotePad.Notes.COLUMN_NAME_CATEGORY + "=?",
                    new String[]{category},
                    NotePad.Notes.DEFAULT_SORT_ORDER
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int idIndex = cursor.getColumnIndex(NotePad.Notes._ID);
                    int titleIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                    
                    long id = cursor.getLong(idIndex);
                    String title = cursor.getString(titleIndex);
                    
                    Map<String, String> item = new HashMap<>();
                    item.put("TITLE", title);
                    item.put("CATEGORY", category); // 添加类别信息
                    groupData.add(item);
                    groupIds.add(id);
                }
                cursor.close();
            }
            
            childData.put(groupName, groupData);
            childIds.put(groupName, groupIds);
            
            return groupData.size(); // 返回数量
        }
        
        @Override
        public int getGroupCount() {
            return groupItems.size();
        }
        
        @Override
        public int getChildrenCount(int groupPosition) {
            GroupItem groupItem = groupItems.get(groupPosition);
            String groupName = groupItem.title;
            List<Map<String, String>> children = childData.get(groupName);
            return children != null ? children.size() : 0;
        }
        
        @Override
        public Object getGroup(int groupPosition) {
            return groupItems.get(groupPosition);
        }
        
        @Override
        public Object getChild(int groupPosition, int childPosition) {
            GroupItem groupItem = groupItems.get(groupPosition);
            String groupName = groupItem.title;
            List<Map<String, String>> children = childData.get(groupName);
            return children != null && childPosition < children.size() ? children.get(childPosition) : null;
        }
        
        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }
        
        @Override
        public long getChildId(int groupPosition, int childPosition) {
            GroupItem groupItem = groupItems.get(groupPosition);
            String groupName = groupItem.title;
            List<Long> ids = childIds.get(groupName);
            return ids != null && childPosition < ids.size() ? ids.get(childPosition) : -1;
        }
        
        @Override
        public boolean hasStableIds() {
            return true;
        }
        
        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            GroupViewHolder holder;
            
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.list_group_todo, parent, false);
                holder = new GroupViewHolder();
                holder.title = convertView.findViewById(R.id.group_title);
                holder.count = convertView.findViewById(R.id.group_count);
                convertView.setTag(holder);
            } else {
                holder = (GroupViewHolder) convertView.getTag();
            }
            
            GroupItem groupItem = groupItems.get(groupPosition);
            holder.title.setText(groupItem.title);
            holder.count.setText(String.valueOf(groupItem.count));
            
            // 为了防止文本与展开图标重叠，增加左边距
            convertView.setPadding(60, 30, 30, 30);
            
            return convertView;
        }
        
        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            ChildViewHolder holder;
            
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.list_item_todo, parent, false);
                holder = new ChildViewHolder();
                holder.icon = convertView.findViewById(R.id.todo_status_icon);
                holder.title = convertView.findViewById(R.id.todo_title);
                convertView.setTag(holder);
            } else {
                holder = (ChildViewHolder) convertView.getTag();
            }
            
            Map<String, String> child = (Map<String, String>) getChild(groupPosition, childPosition);
            if (child != null) {
                String title = child.get("TITLE");
                String category = child.get("CATEGORY");
                
                holder.title.setText(title);
                
                // 根据状态设置图标和颜色
                if (TODO_STATUS_PENDING.equals(category)) {
                    holder.icon.setImageResource(R.drawable.ic_todo_pending);
                    holder.title.setTextColor(0xFF000000); // 黑色
                    convertView.setAlpha(1.0f); // 不透明
                } else if (TODO_STATUS_COMPLETED.equals(category)) {
                    holder.icon.setImageResource(R.drawable.ic_todo_completed);
                    holder.title.setTextColor(0xFF888888); // 灰色
                    convertView.setAlpha(0.6f); // 半透明
                }
            }
            
            return convertView;
        }
        
        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
        
        // 分组项数据类
        private class GroupItem {
            String title;
            int count;
            
            GroupItem(String title, int count) {
                this.title = title;
                this.count = count;
            }
        }
        
        // 分组视图持有者
        private class GroupViewHolder {
            TextView title;
            TextView count;
        }
        
        // 子项视图持有者
        private class ChildViewHolder {
            ImageView icon;
            TextView title;
        }
    }
}