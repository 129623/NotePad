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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
            new String[] {
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE,
                    NotePad.Notes.COLUMN_NAME_CATEGORY
            };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitleText;
    private AutoCompleteTextView mCategoryAutoComplete;
    private EditText mText;
    private String mOriginalContent;
    private FloatingActionButton mFabSaveNote;
    private String mCurrentCategory = "";
    
    // Category list for autocomplete
    private List<String> mCategoryList;
    private ArrayAdapter<String> mCategoryAdapter;

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Creates an Intent to use when the Activity object's result is sent back to the
         * caller.
         */
        final Intent intent = getIntent();

        /*
         *  Sets up for the edit, based on the action specified for the incoming Intent.
         */

        // Gets the action that triggered the intent filter for this Activity
        final String action = intent.getAction();

        // For an edit action:
        if (Intent.ACTION_EDIT.equals(action)) {

            // Sets the Activity state to EDIT, and gets the URI for the data to be edited.
            mState = STATE_EDIT;
            mUri = intent.getData();

            // For an insert or paste action:
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // Sets the Activity state to INSERT, gets the general note URI, and inserts an
            // empty record in the provider
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {

                // Writes the log identifier, a message, and the URI that failed.
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // Closes the activity.
                finish();
                return;
            }

            // Since the new entry was created, this sets the result to be returned
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // If the action was other than EDIT or INSERT:
        } else {

            // Logs an error that the action was not understood, finishes the Activity, and
            // returns RESULT_CANCELED to an originating Activity.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = managedQuery(
                mUri,         // The URI that gets multiple notes from the provider.
                PROJECTION,   // A projection that returns the note ID and note content for each note.
                null,         // No "where" clause selection criteria.
                null,         // No "where" clause selection values.
                null          // Use the default sort order (modification date, descending)
        );

        // For a paste, initializes the data from clipboard.
        // (Must be done after mCursor is initialized.)
        if (Intent.ACTION_PASTE.equals(action)) {
            // Does the paste
            performPaste();
            // Switches the state to EDIT so the title can be modified.
            mState = STATE_EDIT;
        }

        // Sets the layout for this Activity. See res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // Gets a handle to the EditText in the the layout.
        mTitleText = (EditText) findViewById(R.id.title);
        mCategoryAutoComplete = (AutoCompleteTextView) findViewById(R.id.category_autocomplete);
        mText = (EditText) findViewById(R.id.note);

        mFabSaveNote = findViewById(R.id.fab_save_note);
        mFabSaveNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateNote();
                setResult(RESULT_OK);
                finish();
            }
        });

        // 初始化分组自动完成
        initCategoryAutoComplete();

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }

        // 如果是新建笔记，自动聚焦到标题输入框
        if (mState == STATE_INSERT) {
            mTitleText.requestFocus();
        }
    }

    private void initCategoryAutoComplete() {
        mCategoryList = getCategoryList();
        mCategoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, mCategoryList);
        mCategoryAutoComplete.setAdapter(mCategoryAdapter);
        mCategoryAutoComplete.setThreshold(1); // 输入1个字符后开始匹配
        
        // 监听文本变化，同步更新底部显示
        mCategoryAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            mCurrentCategory = mCategoryList.get(position);
            mCategoryAutoComplete.setText(mCurrentCategory);
        });
        
        // 监听输入框点击事件，确保点击时显示所有选项
        mCategoryAutoComplete.setOnClickListener(v -> {
            mCategoryAutoComplete.showDropDown();
        });
        
        // 监听输入框文本变化
        mCategoryAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                // 失去焦点时更新当前分组
                mCurrentCategory = mCategoryAutoComplete.getText().toString();
            }
        });
    }

    private List<String> getCategoryList() {
        // Create a set to avoid duplicates
        HashSet<String> categorySet = new HashSet<>();
        // Query all notes for categories, excluding todo categories
        Cursor cursor = getContentResolver().query(
                NotePad.Notes.CONTENT_URI, 
                new String[]{NotePad.Notes.COLUMN_NAME_CATEGORY}, 
                NotePad.Notes.COLUMN_NAME_CATEGORY + " NOT IN (?, ?)",
                new String[]{"todo_pending", "todo_completed"},
                null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int categoryIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
                String category = cursor.getString(categoryIndex);
                if (category != null && !category.isEmpty()) {
                    categorySet.add(category);
                }
            }
            cursor.close();
        }
        return new ArrayList<>(categorySet);
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     *
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();

            /* Moves to the first record. Always call moveToFirst() before accessing data in
             * a Cursor for the first time. The semantics of using a Cursor are that when it is
             * created, its internal index is pointing to a "place" immediately before the first
             * record.
             */
            mCursor.moveToFirst();

            // Modifies the window title for the Activity according to the current Activity state.
            if (mState == STATE_EDIT) {
                // Set the title of the Activity to include t
                // Gets the note's title.
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);

                // Builds the title string with the notes's title
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);

                // Sets the title to the assets string
                setTitle(text);
                // If the state is INSERT, the Activity needs to be set up for a new note.
            } else if (mState == STATE_INSERT) {

                // Sets the title to the appropriate string.
                setTitle(getText(R.string.title_create));
            }

            /*
             * Gets the note text from the Cursor and inserts it into the content. Gets
             * the note category and inserts it into the category text.
             */

            // Gets the content column index
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            int colCategoryIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);


            // Gets the note's content from the cursor
            String note = mCursor.getString(colNoteIndex);
            String category = mCursor.getString(colCategoryIndex);

            // Sets the editor's content
            mText.setText(note);
            
            // 设置分组信息
            mCurrentCategory = category != null ? category : "";
            mCategoryAutoComplete.setText(mCurrentCategory);


            // Sets the title text
            mTitleText.setText(mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE)));


            // If the original content has not been backed up,
            if (mOriginalContent == null) {
                // do a backup.
                mOriginalContent = note;
            }

            /*
             * The provider sets the note's modification time date to the current time when
             * it is inserted or updated.
             */
        }
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed.
     *
     * The Activity has a state, stored in a Bundle object. This method saves the state to the
     * Bundle.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * This method is called when the Activity is going into the background, for example when
     * it receives an onPause() call.
     *
     * This Activity already saved its state in onSaveInstanceState(), so all it needs to do is
     * secured the text entered by the user, and writes it to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * Tests to see if the query operation succeeded, using the Cursor global variable.
         * If it did, resolves the current status of the note.
         */
        if (mCursor != null) {

            // Get the current note text.
            String text = mText.getText().toString();
            int length = text.length();

            /*
             * If the Activity is in the process of finishing, resolves the note. This is a
             * normal case. Procedures for finishing an Activity performing an action are
             *
             * 1. Set the result code and return an Intent with the Activity's action URI.
             * 2. Finish the Activity.
             *
             * In this case, note resolution is done first, based on the current text of
             * the note.
             */
            if (isFinishing() && (length == 0)) {
                // If the text is empty, cancels the note edit and sets the status to CANCELED.
                setResult(RESULT_CANCELED);
                deleteNote();

            /*
             * If the Activity is not finishing, and the text is not empty, then the result
             * is set to OK and the note is updated.
             */
            } else if (mState == STATE_EDIT) {
                // 获取当前输入的分组名称
                mCurrentCategory = mCategoryAutoComplete.getText().toString();
                // Commits the changes to the provider.
                updateNote();

            } else if (mState == STATE_INSERT) {
                // 获取当前输入的分组名称
                mCurrentCategory = mCategoryAutoComplete.getText().toString();
                updateNote();
                mState = STATE_EDIT;
            }
        }
    }

    /**
     * This method is called when the user clicks the device's Menu button.
     * It populates the main menu, which is displayed in the Options Menu.
     *
     * @param menu The Menu object that will be displayed.
     * @return True, which tells Android to display the menu.
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu XML file into the Menu object
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);
        inflater.inflate(R.menu.editor_options_menu_edit, menu);

        // If the note is being inserted, then the "revert" menu item is not visible.
        if (mState == STATE_INSERT) {
            menu.findItem(R.id.menu_revert).setVisible(false);
            menu.findItem(R.id.menu_delete).setVisible(false);
        }

        // Hide the old save menu item to prefer the FAB
        menu.findItem(R.id.menu_save).setVisible(false);

        return true;
    }

    /**
     * This method is called when a menu item is selected.
     *
     * @param item The selected menu item
     * @return True to indicate that the item was processed.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the menu items.
        int itemId = item.getItemId();
        if (itemId == R.id.menu_delete) {
            // Ask the user to confirm that they want to delete the note
            deleteNote();
            // Closes the Activity, returning control to the caller.
            finish();
            return true;
        } else if (itemId == R.id.menu_revert) {
            // The user wants to revert the changes.
            cancelNote();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }



    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private final void performPaste() {

        // Gets a handle to the Clipboard Manager.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard.
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // Gets the first item from the clip data.
            ClipData.Item item = clip.getItemAt(0);

            // Tries to get the item's contents as a URI.
            Uri uri = item.getUri();

            // If the clipboard item is a URI, attempts to get data from it
            if (uri != null) {

                // Tries to get the MIME type of the URI. If it's a note, then the thread
                // is not blocked, otherwise it is.
                String mimeType = cr.getType(uri);

                // If the MIME type is a note, then gets the note's data from the provider.
                if (NotePad.Notes.CONTENT_ITEM_TYPE.equals(mimeType)) {

                    // Gets the note's data from the provider.
                    Cursor c = cr.query(
                            uri,         // The URI for the note to be retrieved.
                            PROJECTION,  // A projection that returns the note ID and content.
                            null,        // No selection criteria are needed.
                            null,        // No selection arguments are needed.
                            null         // No sort order is needed.
                    );

                    // If the query succeeded, then
                    if (c != null) {

                        // Moves to the first record in the Cursor.
                        c.moveToFirst();

                        // Gets the note title
                        title = c.getString(c.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE));

                        // Gets the note's content.
                        text = c.getString(c.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));

                        // Closes the cursor.
                        c.close();
                    }
                }
            }

            // If the contents of the clipboard wasn't a reference to a note, then
            // this converts whatever it is to text.
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // Updates the note with the text from the clipboard.
            updateNote(title, text, null);
        }
    }


    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param text The new note contents to use.
     */
    private final void updateNote(String title, String text, String category) {

        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        // Always update the modification date when saving
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // If the action is to insert a new note, this creates an initial title for it.
        if (mState == STATE_INSERT) {

            // If no title was provided as an argument, create one from the note text.
            if (title == null) {

                // Get the note's length
                int length = text.length();

                // Sets the title by getting a substring of the text that is up to 10 characters long.
                title = text.substring(0, Math.min(10, length));

                // If the resulting length is more than 10 characters, try to find a space to break at
                if (length > 10) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
        }
        // If the title is not null, add it to the values map
        if (title != null) {
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // If the note text is not null, add it to the values map
        if (text != null) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
        }

        // If the category is not null, add it to the values map
        if (category != null) {
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, category);
        }


        /*
         * Updates the provider with the new values.
         */
        getContentResolver().update(
                mUri,    // The URI for the note to update.
                values,  // The map of column names and new values to use.
                null,    // No selection criteria is used, so no where columns are necessary.
                null     // No where values are used, so no where values are necessary.
        );

    }

    /**
     * This helper method closes the Activity and returns to the caller.
     */
    private final void cancelNote() {

        // If the note was being edited, revert the changes.
        if (mState == STATE_EDIT) {
            // Restore the original text.
            updateNote(null, mOriginalContent, null);
        }
        // Closes the editor without saving.
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * This helper method deletes the note.
     */
    private final void deleteNote() {
        // Deletes the note from the provider.
        getContentResolver().delete(
            mUri,  // The URI of the note to delete.
            null,  // No where clause is needed, since the URI specifies the note.
            null   // No where arguments are needed.
        );
        // Closes the editor.
        setResult(RESULT_OK);
        finish();
    }

    private void updateNote(){
        String title = mTitleText.getText().toString();
        String text = mText.getText().toString();
        
        // If title is empty, generate one from the note text
        if (title.isEmpty() && !text.isEmpty()) {
            // Get the note's length
            int length = text.length();
            
            // Sets the title by getting a substring of the text that is up to 10 characters long.
            title = text.substring(0, Math.min(10, length));
            
            // If the resulting length is more than 10 characters, try to find a space to break at
            if (length > 10) {
                int lastSpace = title.lastIndexOf(' ');
                if (lastSpace > 0) {
                    title = title.substring(0, lastSpace);
                }
            }
        }
        
        updateNote(title, text, mCurrentCategory);
    }
}