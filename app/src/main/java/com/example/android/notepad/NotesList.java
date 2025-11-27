/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class NotesList extends Activity implements ExpandableListView.OnChildClickListener {

    private static final String[] CATEGORY_PROJECTION = new String[]{
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            "COUNT(*) AS count"
    };

    private static final String[] NOTE_PROJECTION = new String[]{
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
    };

    private static final String[] SEARCH_NOTE_PROJECTION = new String[]{
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_CATEGORY
    };

    private ExpandableListView mExpandableListView;
    private NotesExpandableListAdapter mAdapter;
    private EditText mSearchEditText;
    private FloatingActionButton mFabAddNote;

    private static final int REQUEST_CODE_EDIT_NOTE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.noteslist_expandable);

        mExpandableListView = findViewById(R.id.expandable_list);
        mAdapter = new NotesExpandableListAdapter(this);
        mExpandableListView.setAdapter(mAdapter);
        mExpandableListView.setOnChildClickListener(this);

        mSearchEditText = findViewById(R.id.search_edit_text);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mFabAddNote = findViewById(R.id.fab_add_note);
        mFabAddNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
                intent.setClassName(NotesList.this, "com.example.android.notepad.NoteEditor");
                startActivityForResult(intent, REQUEST_CODE_EDIT_NOTE);
            }
        });

        registerForContextMenu(mExpandableListView);
        mAdapter.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);
        inflater.inflate(R.menu.list_category_menu, menu);
        // Hide the old add menu item
        menu.findItem(R.id.menu_add).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_filter_by_category) {
            showCategoryFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        ExpandableListView.ExpandableListContextMenuInfo info =
                (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

        int type = ExpandableListView.getPackedPositionType(info.packedPosition);

        if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.list_context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListView.ExpandableListContextMenuInfo info =
                (ExpandableListView.ExpandableListContextMenuInfo) item.getMenuInfo();

        int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);

        long noteId = mAdapter.getChildId(groupPosition, childPosition);
        Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, noteId);

        int id = item.getItemId();
        if (id == R.id.context_open) {
            Intent intent = new Intent(Intent.ACTION_EDIT, noteUri);
            intent.setClassName(this, "com.example.android.notepad.NoteEditor");
            startActivityForResult(intent, REQUEST_CODE_EDIT_NOTE);
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            mAdapter.refresh();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, id);
        Intent intent = new Intent(Intent.ACTION_EDIT, noteUri);
        intent.setClassName(this, "com.example.android.notepad.NoteEditor");
        startActivityForResult(intent, REQUEST_CODE_EDIT_NOTE);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EDIT_NOTE && resultCode == RESULT_OK) {
            mAdapter.refresh();
        }
    }

    private void showCategoryFilterDialog() {
        Cursor cursor = getContentResolver().query(NotePad.Notes.CATEGORIES_URI, null, null, null, null);
        List<String> categories = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String category = cursor.getString(0);
                if (category != null && !category.isEmpty()) {
                    categories.add(category);
                }
            }
            cursor.close();
        }

        categories.add(0, "All");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by Category");
        builder.setItems(categories.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String category = categories.get(which);
                if (category.equals("All")) {
                    mAdapter.filterByCategory(null);
                } else {
                    mAdapter.filterByCategory(category);
                }
            }
        });
        builder.show();
    }

    private class NotesExpandableListAdapter extends BaseExpandableListAdapter {

        private Context mContext;
        private List<Group> mGroups;
        private String mQuery;
        private String mCategoryFilter;

        public NotesExpandableListAdapter(Context context) {
            mContext = context;
            mGroups = new ArrayList<>();
        }

        public void refresh() {
            mGroups.clear();

            // When searching, get all matching notes first, then group them
            if (mQuery != null && mQuery.length() > 0) {
                // Build selection criteria for all notes with search filter
                String selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
                String[] selectionArgs = new String[]{"%" + mQuery + "%", "%" + mQuery + "%"};

                // Apply category filter if present
                if (mCategoryFilter != null && !"All".equals(mCategoryFilter)) {
                    selection = "(" + selection + ") AND " + NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?";
                    selectionArgs = new String[]{"%" + mQuery + "%", "%" + mQuery + "%", mCategoryFilter};
                }

                Cursor noteCursor = mContext.getContentResolver().query(
                        NotePad.Notes.CONTENT_URI,
                        SEARCH_NOTE_PROJECTION,
                        selection,
                        selectionArgs,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC");

                // Group notes by category
                HashMap<String, List<Note>> tempNotes = new HashMap<>();
                if (noteCursor != null) {
                    while (noteCursor.moveToNext()) {
                        long id = noteCursor.getLong(0);
                        String title = noteCursor.getString(1);
                        long modDate = noteCursor.getLong(2);
                        String category = noteCursor.getString(3); // category column

                        String displayCategory = category;
                        if (category == null || category.isEmpty()) {
                            displayCategory = "Uncategorized";
                        }

                        if (!tempNotes.containsKey(displayCategory)) {
                            tempNotes.put(displayCategory, new ArrayList<Note>());
                        }
                        tempNotes.get(displayCategory).add(new Note(id, title, modDate));
                    }
                    noteCursor.close();
                }

                // Populate groups
                for (String category : tempNotes.keySet()) {
                    mGroups.add(new Group(category, tempNotes.get(category)));
                }
            } else {
                // Normal mode - get categories first, then notes for each category
                String categorySelection = null;
                String[] categorySelectionArgs = null;

                if (mCategoryFilter != null && !"All".equals(mCategoryFilter)) {
                    categorySelection = NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?";
                    categorySelectionArgs = new String[]{mCategoryFilter};
                }

                Cursor categoryCursor = mContext.getContentResolver().query(
                        NotePad.Notes.CATEGORIES_URI,
                        CATEGORY_PROJECTION,
                        categorySelection,
                        categorySelectionArgs,
                        null);

                if (categoryCursor != null) {
                    while (categoryCursor.moveToNext()) {
                        String category = categoryCursor.getString(0);
                        String displayCategory = category;
                        if (category == null || category.isEmpty()) {
                            displayCategory = "Uncategorized";
                        }

                        // Build selection criteria for notes
                        String selection = NotePad.Notes.COLUMN_NAME_CATEGORY + " = ? OR (" + NotePad.Notes.COLUMN_NAME_CATEGORY + " IS NULL AND ? = 'Uncategorized')";
                        String[] selectionArgs = new String[]{category != null ? category : "", displayCategory};

                        Cursor noteCursor = mContext.getContentResolver().query(
                                NotePad.Notes.CONTENT_URI,
                                NOTE_PROJECTION,
                                selection,
                                selectionArgs,
                                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC");
                        List<Note> notes = new ArrayList<>();
                        if (noteCursor != null) {
                            while (noteCursor.moveToNext()) {
                                notes.add(new Note(noteCursor.getLong(0), noteCursor.getString(1), noteCursor.getLong(2)));
                            }
                            noteCursor.close();
                        }

                        mGroups.add(new Group(displayCategory, notes));
                    }
                    categoryCursor.close();
                }
            }
            
            // Sort groups by timestamp (most recent note first) but keep Uncategorized at top
            Collections.sort(mGroups, new Comparator<Group>() {
                @Override
                public int compare(Group g1, Group g2) {
                    // "Uncategorized" group should always be at the top
                    if ("Uncategorized".equals(g1.name) && !"Uncategorized".equals(g2.name)) {
                        return -1;
                    } else if (!"Uncategorized".equals(g1.name) && "Uncategorized".equals(g2.name)) {
                        return 1;
                    }
                    
                    // For other groups, sort by the most recent note's timestamp
                    long g1Timestamp = g1.notes.isEmpty() ? 0 : g1.notes.get(0).modificationDate;
                    long g2Timestamp = g2.notes.isEmpty() ? 0 : g2.notes.get(0).modificationDate;
                    return Long.compare(g2Timestamp, g1Timestamp); // Descending order
                }
            });
            
            notifyDataSetChanged();
        }

        public void filter(String query) {
            mQuery = query;
            refresh();
        }

        public void filterByCategory(String category) {
            mCategoryFilter = category;
            refresh();
        }

        @Override
        public int getGroupCount() {
            return mGroups.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return mGroups.get(groupPosition).notes.size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mGroups.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return mGroups.get(groupPosition).notes.get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return ((Note) getChild(groupPosition, childPosition)).id;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.noteslist_group, null);
            }

            LinearLayout container = convertView.findViewById(R.id.group_content_container);

            // Set background based on expansion
            if (isExpanded) {
                container.setBackground(ContextCompat.getDrawable(mContext, R.drawable.group_background_top));
            } else {
                container.setBackground(ContextCompat.getDrawable(mContext, R.drawable.rounded_corners));
            }

            TextView categoryName = (TextView)convertView.findViewById(R.id.category_name);
            TextView categoryCount = (TextView)convertView.findViewById(R.id.category_count);

            Group group = (Group) getGroup(groupPosition);
            categoryName.setText(group.name);
            categoryCount.setText(String.valueOf(getChildrenCount(groupPosition)));

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.noteslist_item, null);
            }

            // Set background for child items
            if (isLastChild) {
                convertView.setBackground(ContextCompat.getDrawable(mContext, R.drawable.group_background_bottom));
            } else {
                convertView.setBackground(ContextCompat.getDrawable(mContext, R.drawable.group_background_middle));
            }

            TextView title = (TextView) convertView.findViewById(R.id.text1);
            TextView date = (TextView)convertView.findViewById(R.id.text2);
            View divider = convertView.findViewById(R.id.divider);

            if (isLastChild) {
                divider.setVisibility(View.GONE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

            Note note = (Note) getChild(groupPosition, childPosition);
            title.setText(note.title);
            date.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(note.modificationDate)));

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    private static class Group {
        String name;
        List<Note> notes;

        public Group(String name, List<Note> notes) {
            this.name = name;
            this.notes = notes;
        }
    }

    private static class Note {
        long id;
        String title;
        long modificationDate;

        public Note(long id, String title, long modificationDate) {
            this.id = id;
            this.title = title;
            this.modificationDate = modificationDate;
        }
    }
}